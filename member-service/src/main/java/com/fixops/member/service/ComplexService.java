package com.fixops.member.service;

import com.fixops.member.repo.OrganizationRepository;
import com.fixops.member.repo.MemberRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class ComplexService {
    private final JdbcTemplate db;
    private final OrganizationRepository orgs;
    private final MemberRepository members;
    private final AuthService auth;
    private final KaptClient kapt;
    private final OrganizationService onboarding;
    public ComplexService(JdbcTemplate db, OrganizationRepository orgs, MemberRepository members, AuthService auth,KaptClient kapt,OrganizationService onboarding) {
        this.db=db; this.orgs=orgs; this.members=members; this.auth=auth;this.kapt=kapt;this.onboarding=onboarding;
    }
    private ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"ORG_ACCESS_DENIED");}
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");}
    private ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"INVALID_STATE_TRANSITION");}
    private boolean exists(String sql,Object... args){return Boolean.TRUE.equals(db.queryForObject(sql,Boolean.class,args));}
    private void platform(long actor){if(!members.findById(actor).map(m->m.platformAdmin).orElse(false))throw denied();}
    private void lockComplex(long id){if(db.queryForList("select id from apartment_complexes where id=? for update",id).isEmpty())throw missing();}
    private void audit(long actor,long org,long complex,String operation,String before,String after){
        db.update("insert into audit_logs(actor_id,operation,target_type,target_id,organization_id,before_state,after_state) values (?,?,'complex',?,?,?,?)",actor,operation,Long.toString(complex),org,before,after);
    }
    @Transactional
    public Map<String,Object> register(long actor,long org,String code,String name,String address){
        var organization=orgs.findByIdForUpdate(org).orElseThrow(this::missing);
        if(!"BUILDING".equals(organization.kind))throw conflict();
        boolean applicant="PENDING_APPROVAL".equals(organization.status)&&organization.applicantId.equals(actor);
        boolean admin="ACTIVE".equals(organization.status)&&exists("select exists(select 1 from organization_members where organization_id=? and member_id=? and role='ADMIN' and status='ACTIVE')",org,actor);
        if(!applicant&&!admin)throw denied();
        String normalized=code==null||code.isBlank()?null:code.trim().toUpperCase(Locale.ROOT);
        Long id;
        if(normalized!=null){
            var official=kapt.detail(normalized);
            if(official.isPresent()){
                name=official.get().kaptName();
                address=official.get().roadAddress();
            }else{
                // No authoritative match: keep user-supplied information for manual review.
                // Do not reserve an unverified official identifier.
                normalized=null;
            }
        }
        // Serialize address identity decisions across organizations, including manual applications.
        db.queryForList("select pg_advisory_xact_lock(hashtextextended(regexp_replace(lower(?), '[[:space:][:punct:]]', '', 'g'), 17))",address.trim());
        if(normalized!=null){
            db.update("insert into apartment_complexes(kapt_code,display_name,road_address,verification_status) values (?,?,?,'MANUAL_REVIEW') on conflict(kapt_code) do nothing",normalized,name.trim(),address.trim());
            id=db.queryForObject("select id from apartment_complexes where kapt_code=?",Long.class,normalized);
        }else{
            id=db.queryForObject("insert into apartment_complexes(display_name,road_address,verification_status) values (?,?,'MANUAL_REVIEW') returning id",Long.class,name.trim(),address.trim());
        }
        lockComplex(id);
        if(exists("select exists(select 1 from complex_managements where complex_id=? and status='ACTIVE')",id))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"COMPLEX_ALREADY_REGISTERED");
        if(exists("select exists(select 1 from complex_managements where complex_id=? and status='PENDING_APPROVAL')",id))
            throw conflict();
        Long management=db.queryForObject("insert into complex_managements(complex_id,organization_id,status,requested_by) values (?,?,'PENDING_APPROVAL',?) returning id",Long.class,id,org,actor);
        audit(actor,org,id,"COMPLEX_APPLY",null,"PENDING_APPROVAL");
        return Map.of("complexId",id,"managementId",management,"status","PENDING_APPROVAL","verificationStatus","MANUAL_REVIEW");
    }
    private boolean addressCollision(long complex){
        return exists("select exists(select 1 from apartment_complexes c join apartment_complexes other on other.id<>c.id and regexp_replace(lower(other.road_address), '[[:space:][:punct:]]', '', 'g')=regexp_replace(lower(c.road_address), '[[:space:][:punct:]]', '', 'g') where c.id=? and exists(select 1 from complex_managements m where m.complex_id=other.id and m.status in ('ACTIVE','PENDING_APPROVAL','ENDED')))",complex);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> candidates(long actor,long management,int page,int size){
        platform(actor);
        var rows=db.queryForList("select complex_id from complex_managements where id=?",management);
        if(rows.isEmpty())throw missing();
        return db.queryForList("select other.id,other.kapt_code,other.display_name,other.road_address,other.verification_status from apartment_complexes c join apartment_complexes other on other.id<>c.id and regexp_replace(lower(other.road_address), '[[:space:][:punct:]]', '', 'g')=regexp_replace(lower(c.road_address), '[[:space:][:punct:]]', '', 'g') where c.id=? and exists(select 1 from complex_managements m where m.complex_id=other.id and m.status in ('ACTIVE','PENDING_APPROVAL','ENDED')) order by other.id limit ? offset ?",rows.getFirst().get("complex_id"),size,(long)page*size);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> pending(long actor,int page,int size){
        platform(actor);
        return db.queryForList("select m.id as management_id,m.organization_id,c.id as complex_id,c.kapt_code,c.display_name,c.road_address,c.verification_status,m.review_reason,m.reviewed_at from complex_managements m join apartment_complexes c on c.id=m.complex_id where m.status='PENDING_APPROVAL' order by m.id limit ? offset ?",size,(long)page*size);
    }
    @Transactional
    public Map<String,Object> decide(long actor,long management,boolean approve,String reason,boolean confirmDistinctAddress){
        platform(actor);
        var initial=db.queryForList("select organization_id,complex_id from complex_managements where id=?",management);
        if(initial.isEmpty())throw missing();
        long org=((Number)initial.getFirst().get("organization_id")).longValue();
        long complex=((Number)initial.getFirst().get("complex_id")).longValue();
        var organization=orgs.findByIdForUpdate(org).orElseThrow(this::missing);
        db.queryForList("select pg_advisory_xact_lock(hashtextextended(regexp_replace(lower(road_address), '[[:space:][:punct:]]', '', 'g'), 17)) from apartment_complexes where id=?",complex);
        lockComplex(complex);
        if(!exists("select exists(select 1 from complex_managements where id=? and status='PENDING_APPROVAL')",management))throw conflict();
        if(reason==null||reason.isBlank()||reason.length()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        if(approve){
            boolean collision=addressCollision(complex);
            if(collision&&!confirmDistinctAddress)throw new ResponseStatusException(HttpStatus.CONFLICT,"COMPLEX_ADDRESS_REVIEW_REQUIRED");
            if(!Set.of("ACTIVE","PENDING_APPROVAL").contains(organization.status))throw conflict();
            if(exists("select exists(select 1 from complex_managements where complex_id=? and status='ACTIVE')",complex))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"COMPLEX_ALREADY_REGISTERED");
            if("ACTIVE".equals(organization.status) && !exists("select exists(select 1 from organization_members where organization_id=? and role='ADMIN' and status='ACTIVE')",org))throw conflict();
            db.update("update apartment_complexes set verification_status='VERIFIED' where id=?",complex);
            db.update("update complex_managements set status='ACTIVE',started_at=now(),reviewed_by=?,review_reason=?,reviewed_at=now(),address_collision_confirmed=? where id=?",actor,reason.trim(),collision&&confirmDistinctAddress,management);
            db.update("insert into member_complex_access(organization_member_id,complex_id,access_scope,status) select id,?,'ADMIN','ACTIVE' from organization_members where organization_id=? and role='ADMIN' and status='ACTIVE' on conflict(organization_member_id,complex_id) do update set access_scope='ADMIN',status='ACTIVE'",complex,org);
        }else{
            db.update("update complex_managements set status='REJECTED',reviewed_by=?,review_reason=?,reviewed_at=now() where id=?",actor,reason.trim(),management);
        }
        if(approve) onboarding.activateInvitedBuilding(actor,org);
        String next=approve?"ACTIVE":"REJECTED";
        audit(actor,org,complex,"COMPLEX_DECISION","PENDING_APPROVAL",next);
        return Map.of("managementId",management,"complexId",complex,"status",next);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> managements(long actor,long org,int page,int size){
        var organization=orgs.findById(org).orElseThrow(this::missing);
        if(!"BUILDING".equals(organization.kind))throw denied();
        boolean applicant="PENDING_APPROVAL".equals(organization.status)&&organization.applicantId.equals(actor);
        boolean admin="ACTIVE".equals(organization.status)&&exists("select exists(select 1 from organization_members where organization_id=? and member_id=? and role='ADMIN' and status='ACTIVE')",org,actor);
        if(!applicant&&!admin)throw denied();
        return db.queryForList("""
            select m.id as management_id,c.id as complex_id,c.kapt_code,c.display_name,c.road_address,
                   c.verification_status,m.status,m.review_reason,m.reviewed_at
            from complex_managements m join apartment_complexes c on c.id=m.complex_id
            where m.organization_id=? order by m.id desc limit ? offset ?
            """,org,size,(long)page*size);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> mine(long actor,long org){
        return db.queryForList("select c.id,c.kapt_code,c.display_name,c.road_address,a.access_scope from member_complex_access a join organization_members om on om.id=a.organization_member_id join organizations o on o.id=om.organization_id join apartment_complexes c on c.id=a.complex_id join complex_managements m on m.complex_id=c.id and m.organization_id=om.organization_id and m.status='ACTIVE' where om.member_id=? and om.organization_id=? and om.status='ACTIVE' and o.status='ACTIVE' and a.status='ACTIVE' and c.verification_status='VERIFIED' and ((om.role='ADMIN' and a.access_scope='ADMIN') or (om.role='TECHNICIAN' and a.access_scope='TECHNICIAN') or (om.role='USER' and a.access_scope='RESIDENT')) order by c.id",actor,org);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> accessMembers(long actor,long org,long complex,int page,int size){
        if(!exists("select exists(select 1 from organizations o join complex_managements cm on cm.organization_id=o.id join organization_members om on om.organization_id=o.id join member_complex_access a on a.organization_member_id=om.id and a.complex_id=cm.complex_id where o.id=? and o.status='ACTIVE' and cm.complex_id=? and cm.status='ACTIVE' and om.member_id=? and om.role='ADMIN' and om.status='ACTIVE' and a.access_scope='ADMIN' and a.status='ACTIVE')",org,complex,actor))throw denied();
        return db.queryForList("""
            select om.member_id,om.id as membership_id,m.display_name,m.email,om.role,
                   coalesce(a.status,'NONE') as access_status,a.access_scope
            from organization_members om join members m on m.id=om.member_id
            left join member_complex_access a on a.organization_member_id=om.id and a.complex_id=?
            where om.organization_id=? and om.status='ACTIVE' and om.role in ('ADMIN','TECHNICIAN')
            order by om.id limit ? offset ?
            """,complex,org,size,(long)page*size);
    }
    @Transactional
    public Map<String,Object> access(long actor,long org,long complex,long target,boolean grant){
        var organization=orgs.findByIdForUpdate(org).orElseThrow(this::missing);
        if(!"ACTIVE".equals(organization.status))throw denied();
        lockComplex(complex);
        if(!exists("select exists(select 1 from complex_managements where complex_id=? and organization_id=? and status='ACTIVE')",complex,org))throw denied();
        if(!exists("select exists(select 1 from organization_members om join member_complex_access a on a.organization_member_id=om.id where om.organization_id=? and om.member_id=? and om.role='ADMIN' and om.status='ACTIVE' and a.complex_id=? and a.access_scope='ADMIN' and a.status='ACTIVE')",org,actor,complex))throw denied();
        var rows=db.queryForList("select id,role from organization_members where organization_id=? and member_id=? and status='ACTIVE'",org,target);
        if(rows.isEmpty())throw missing();
        var row=rows.getFirst();
        String role=(String)row.get("role");
        // Resident scope must be established by the verified resident-enrollment workflow.
        if(grant&&!Set.of("ADMIN","TECHNICIAN").contains(role))throw denied();
        if(!grant&&"ADMIN".equals(role)&&db.queryForObject("select count(*) from member_complex_access a join organization_members om on om.id=a.organization_member_id where a.complex_id=? and a.status='ACTIVE' and a.access_scope='ADMIN' and om.organization_id=? and om.role='ADMIN' and om.status='ACTIVE'",Long.class,complex,org)<=1)throw conflict();
        if(grant)db.update("insert into member_complex_access(organization_member_id,complex_id,access_scope,status) values (?,?,?,'ACTIVE') on conflict(organization_member_id,complex_id) do update set access_scope=excluded.access_scope,status='ACTIVE'",row.get("id"),complex,role);
        else db.update("update member_complex_access set status='REVOKED' where organization_member_id=? and complex_id=?",row.get("id"),complex);
        audit(actor,org,complex,"COMPLEX_ACCESS_"+(grant?"GRANT":"REVOKE"),null,Long.toString(target));
        auth.revokeMemberSessions(target);
        return Map.of("memberId",target,"complexId",complex,"status",grant?"ACTIVE":"REVOKED");
    }
}
