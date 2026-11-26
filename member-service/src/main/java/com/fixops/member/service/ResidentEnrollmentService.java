package com.fixops.member.service;

import com.fixops.member.security.RosterCrypto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;

@Service
public class ResidentEnrollmentService {
    private final JdbcTemplate db;
    private final VerificationService verification;
    private final RosterCrypto crypto;
    private final AssetUnitClient asset;
    private final AuthService auth;
    private final SecureRandom registrationRandom=new SecureRandom();
    @Value("${fixops.verification.hash-key:}") private String registrationSecret;
    private String registrationHash(String code){
        try {
            byte[] key=Base64.getDecoder().decode(registrationSecret);
            if(key.length!=32)throw new IllegalArgumentException();
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("resident-registration:"+code).getBytes(StandardCharsets.UTF_8)));
        } catch(Exception ex){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"DEPENDENCY_UNAVAILABLE");}
    }
    public ResidentEnrollmentService(JdbcTemplate db,VerificationService verification,RosterCrypto crypto,AssetUnitClient asset,AuthService auth){
        this.db=db;this.verification=verification;this.crypto=crypto;this.asset=asset;this.auth=auth;
    }
    private ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"ORG_ACCESS_DENIED");}
    private ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND");}
    private ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"INVALID_STATE_TRANSITION");}
    private long number(Map<String,Object> row,String key){return ((Number)row.get(key)).longValue();}
    private boolean exists(String sql,Object... args){return Boolean.TRUE.equals(db.queryForObject(sql,Boolean.class,args));}
    private long lockScope(long complex){
        var owners=db.queryForList("select organization_id from complex_managements where complex_id=? and status='ACTIVE'",complex);
        if(owners.isEmpty())throw missing();
        long org=number(owners.getFirst(),"organization_id");
        db.queryForList("select id from organizations where id=? for update",org);
        db.queryForList("select id from apartment_complexes where id=? for update",complex);
        if(!exists("select exists(select 1 from complex_managements m join organizations o on o.id=m.organization_id join apartment_complexes c on c.id=m.complex_id where m.organization_id=? and m.complex_id=? and m.status='ACTIVE' and o.status='ACTIVE' and c.verification_status='VERIFIED')",org,complex))throw missing();
        return org;
    }
    private void admin(long actor,long org,long complex){
        if(!exists("select exists(select 1 from organization_members om join member_complex_access a on a.organization_member_id=om.id where om.member_id=? and om.organization_id=? and om.role='ADMIN' and om.status='ACTIVE' and a.complex_id=? and a.access_scope='ADMIN' and a.status='ACTIVE')",actor,org,complex))throw denied();
    }
    private void audit(long actor,long org,long id,String operation,String before,String after){
        db.update("insert into audit_logs(actor_id,operation,target_type,target_id,organization_id,before_state,after_state) values (?,?,'resident_enrollment',?,?,?,?)",actor,operation,Long.toString(id),org,before,after);
    }
    private boolean pendingRegistration(long entry){
        return exists("select exists(select 1 from resident_enrollments n join roster_entries e on e.roster_id=n.roster_id and e.building_no=n.building_no and e.unit_no=n.unit_no and e.contact_lookup_hmac=n.contact_lookup_hmac where e.id=? and n.status='PENDING_REVIEW')",entry);
    }
    private void codeAudit(long actor,long org,long id,String operation,String before,String after){
        db.update("insert into audit_logs(actor_id,operation,target_type,target_id,organization_id,before_state,after_state) values (?,?,'resident_registration_code',?,?,?,?)",actor,operation,Long.toString(id),org,before,after);
    }
    @Transactional
    public Map<String,Object> issueRegistrationCode(long actor,long complex,long entry){
        long org=lockScope(complex);admin(actor,org,complex);
        var rows=db.queryForList("select e.id from roster_entries e join resident_rosters r on r.id=e.roster_id where e.id=? and r.organization_id=? and r.complex_id=? and r.status='ACTIVE' and e.status='ACTIVE' for update of e",entry,org,complex);
        if(rows.isEmpty())throw missing();
        if(pendingRegistration(entry)||exists("select exists(select 1 from resident_units where roster_entry_id=? and status='ACTIVE')",entry))throw conflict();
        db.update("update resident_registration_codes set revoked_at=now() where roster_entry_id=? and consumed_at is null and revoked_at is null",entry);
        byte[] random=new byte[16];registrationRandom.nextBytes(random);
        String code=HexFormat.of().withUpperCase().formatHex(random);
        Long id=db.queryForObject("insert into resident_registration_codes(roster_entry_id,organization_id,complex_id,code_hash,issued_by,expires_at) values (?,?,?,?,?,now()+interval '24 hours') returning id",Long.class,entry,org,complex,registrationHash(code),actor);
        codeAudit(actor,org,id,"RESIDENT_CODE_ISSUE",null,"ISSUED");
        return Map.of("id",id,"code",code,"expiresInSeconds",86400);
    }
    @Transactional
    public List<Map<String,Object>> registrationEntries(long actor,long complex,int page,int size){
        long org=lockScope(complex);admin(actor,org,complex);
        return db.queryForList("""
            select e.id,e.building_no,e.unit_no,e.name,
              exists(select 1 from resident_units u where u.roster_entry_id=e.id and u.status='ACTIVE') as linked,
              exists(select 1 from resident_enrollments n where n.roster_id=e.roster_id and n.building_no=e.building_no and n.unit_no=e.unit_no and n.contact_lookup_hmac=e.contact_lookup_hmac and n.status='PENDING_REVIEW') as pending_review,
              c.id as code_id,c.expires_at
            from roster_entries e join resident_rosters r on r.id=e.roster_id
            left join resident_registration_codes c on c.roster_entry_id=e.id and c.consumed_at is null and c.revoked_at is null and c.expires_at>now()
            where r.organization_id=? and r.complex_id=? and r.status='ACTIVE' and e.status='ACTIVE'
            order by e.id limit ? offset ?
            """,org,complex,size,(long)page*size);
    }
    @Transactional
    public void revokeRegistrationCode(long actor,long complex,long id){
        long org=lockScope(complex);admin(actor,org,complex);
        if(db.update("update resident_registration_codes set revoked_at=now() where id=? and organization_id=? and complex_id=? and consumed_at is null and revoked_at is null",id,org,complex)!=1)throw conflict();
        codeAudit(actor,org,id,"RESIDENT_CODE_REVOKE","ISSUED","REVOKED");
    }
    public Map<String,Object> verificationMethods(){
        return Map.of("registrationCodeEnabled",true,"smsEnabled",verification.residentSmsEnabled());
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> enrollable(long actor,String q,int page,int size){
        if(!exists("select exists(select 1 from members where id=? and email_verified=true)",actor))throw denied();
        return db.queryForList("""
            select c.id,c.display_name,c.road_address
            from apartment_complexes c
            where c.verification_status='VERIFIED'
              and exists(select 1 from complex_managements cm join organizations o on o.id=cm.organization_id
                  join resident_rosters r on r.organization_id=o.id and r.complex_id=c.id
                  where cm.complex_id=c.id and cm.status='ACTIVE' and o.status='ACTIVE' and r.status='ACTIVE')
              and (strpos(lower(c.display_name),lower(?))>0 or strpos(lower(c.road_address),lower(?))>0)
            order by c.display_name,c.id limit ? offset ?
            """,q,q,size,(long)page*size);
    }
    @Transactional
    public Map<String,Object> redeemRegistrationCode(long actor,long complex,String rawCode){
        long org=lockScope(complex);
        db.queryForList("select id from members where id=? for update",actor);
        if(!exists("select exists(select 1 from members where id=? and email_verified=true)",actor))throw denied();
        db.update("insert into resident_registration_attempts(member_id) values (?) on conflict do nothing",actor);
        db.update("update resident_registration_attempts set attempts=0,window_started_at=now() where member_id=? and window_started_at<=now()-interval '15 minutes'",actor);
        int attempts=db.queryForObject("select attempts from resident_registration_attempts where member_id=? for update",Integer.class,actor);
        if(attempts>=5)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED");
        var rows=db.queryForList("""
            select c.id as registration_code_id,e.id as entry_id,e.roster_id,e.building_no,e.unit_no,e.contact_lookup_hmac
            from resident_registration_codes c join roster_entries e on e.id=c.roster_entry_id
            join resident_rosters r on r.id=e.roster_id
            where c.code_hash=? and c.organization_id=? and c.complex_id=?
              and c.consumed_at is null and c.revoked_at is null and c.expires_at>now()
              and r.organization_id=? and r.complex_id=? and r.status='ACTIVE' and e.status='ACTIVE'
              and not exists(select 1 from resident_units u where u.roster_entry_id=e.id and u.status='ACTIVE')
            for update of c,e
            """,registrationHash(rawCode.trim().toUpperCase(Locale.ROOT)),org,complex,org,complex);
        if(rows.isEmpty()){
            db.update("update resident_registration_attempts set attempts=attempts+1 where member_id=?",actor);
            return Map.of("verified",false);
        }
        var row=rows.getFirst();
        long codeId=number(row,"registration_code_id"),entry=number(row,"entry_id");
        if(pendingRegistration(entry))throw conflict();
        // Reject incompatible memberships before consuming the credential.
        if(exists("select exists(select 1 from organization_members where organization_id=? and member_id=? and (role<>'USER' or status<>'ACTIVE'))",org,actor))throw conflict();
        db.update("update resident_registration_codes set consumed_by=?,consumed_at=now() where id=?",actor,codeId);
        Long id=db.queryForObject("""
            insert into resident_enrollments(member_id,organization_id,complex_id,roster_id,building_no,unit_no,contact_lookup_hmac,verification_method,registration_code_id,status,verified_at)
            values (?,?,?,?,?,?,?,'REGISTRATION_CODE',?,'PENDING_REVIEW',now()) returning id
            """,Long.class,actor,org,complex,row.get("roster_id"),row.get("building_no"),row.get("unit_no"),row.get("contact_lookup_hmac"),codeId);
        db.update("update resident_registration_attempts set attempts=0,window_started_at=now() where member_id=?",actor);
        audit(actor,org,id,"RESIDENT_CODE_VERIFIED",null,"PENDING_REVIEW");
        var enrollment=db.queryForMap("select * from resident_enrollments where id=?",id);
        boolean approved=connect(actor,org,complex,id,enrollment,true);
        return Map.of("verified",true,"id",id,"status",approved?"APPROVED":"PENDING_REVIEW","verificationMethod","REGISTRATION_CODE");
    }
    @Transactional
    public Map<String,Object> start(long actor,long complex,String building,String unit,String contact){
        long org=lockScope(complex);
        if(!exists("select exists(select 1 from members where id=? and email_verified=true)",actor))throw denied();
        String normalized;
        try{normalized=crypto.normalize(contact);}catch(IllegalArgumentException ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");}
        var rosters=db.queryForList("select id from resident_rosters where complex_id=? and organization_id=? and status='ACTIVE'",complex,org);
        if(rosters.isEmpty())throw missing();
        long roster=number(rosters.getFirst(),"id");
        String lookup=crypto.lookup(roster,normalized);
        // Challenge issuance does not reveal whether a resident entry matches.
        var issued=verification.issue(actor,"RESIDENT",normalized);
        db.update("update resident_enrollments set status='REJECTED' where member_id=? and status='PENDING_VERIFICATION'",actor);
        Long id=db.queryForObject("insert into resident_enrollments(member_id,organization_id,complex_id,roster_id,building_no,unit_no,contact_lookup_hmac,challenge_id,status) values (?,?,?,?,?,?,?,?,'PENDING_VERIFICATION') returning id",Long.class,actor,org,complex,roster,building.trim(),unit.trim(),lookup,issued.challengeId());
        audit(actor,org,id,"RESIDENT_APPLY",null,"PENDING_VERIFICATION");
        var result=new LinkedHashMap<String,Object>();
        result.put("id",id);result.put("challengeId",issued.challengeId());result.put("status","PENDING_VERIFICATION");
        if(issued.demoCode()!=null)result.put("demoCode",issued.demoCode());
        return result;
    }
    @Transactional
    public Map<String,Object> verify(long actor,long id,String code){
        var initial=db.queryForList("select complex_id from resident_enrollments where id=? and member_id=?",id,actor);
        if(initial.isEmpty())throw missing();
        long complex=number(initial.getFirst(),"complex_id"),org=lockScope(complex);
        var rows=db.queryForList("select * from resident_enrollments where id=? and member_id=? and organization_id=? for update",id,actor,org);
        if(rows.isEmpty())throw missing();
        var row=rows.getFirst();
        if(!"PENDING_VERIFICATION".equals(row.get("status")))throw conflict();
        if(!verification.consume(actor,"RESIDENT",(UUID)row.get("challenge_id"),code,null))return Map.of("verified",false);

        // Check only after proof of contact, avoiding disclosure of roster membership at issuance.
        if(exists("select exists(select 1 from resident_enrollments where id<>? and organization_id=? and complex_id=? and roster_id=? and building_no=? and unit_no=? and contact_lookup_hmac=? and status='PENDING_REVIEW')",id,org,complex,row.get("roster_id"),row.get("building_no"),row.get("unit_no"),row.get("contact_lookup_hmac")))throw conflict();
        db.update("update resident_enrollments set status='PENDING_REVIEW',verified_at=now() where id=?",id);
        audit(actor,org,id,"RESIDENT_OTP_VERIFIED","PENDING_VERIFICATION","PENDING_REVIEW");
        boolean approved=connect(actor,org,complex,id,row,true);
        return Map.of("verified",true,"id",id,"status",approved?"APPROVED":"PENDING_REVIEW");
    }
    // Caller holds organization and complex locks. Verified proof and exact roster match are mandatory.
    private boolean connect(long actor,long org,long complex,long id,Map<String,Object> row,boolean automatic){
        long member=number(row,"member_id"),roster=number(row,"roster_id");
        db.queryForList("select id from members where id=? for update",member);
        if(!exists("select exists(select 1 from members where id=? and email_verified=true)",member))return false;
        var matches=db.queryForList("select e.id from roster_entries e join resident_rosters r on r.id=e.roster_id where e.roster_id=? and r.organization_id=? and r.complex_id=? and r.status='ACTIVE' and e.building_no=? and e.unit_no=? and e.contact_lookup_hmac=? and e.status='ACTIVE' for update of e",roster,org,complex,row.get("building_no"),row.get("unit_no"),row.get("contact_lookup_hmac"));
        if(matches.size()!=1)return false;
        long entry=number(matches.getFirst(),"id");
        if("REGISTRATION_CODE".equals(row.get("verification_method"))&&!exists("select exists(select 1 from resident_registration_codes where id=? and roster_entry_id=? and organization_id=? and complex_id=? and consumed_by=? and consumed_at is not null and revoked_at is null)",row.get("registration_code_id"),entry,org,complex,member))return false;
        if(exists("select exists(select 1 from resident_units where roster_entry_id=? and status='ACTIVE')",entry))return false;
        var membership=db.queryForList("select id,role,status from organization_members where organization_id=? and member_id=? for update",org,member);
        if(!membership.isEmpty()&&(!"USER".equals(membership.getFirst().get("role"))||!"ACTIVE".equals(membership.getFirst().get("status"))))return false;
        Optional<Long> location;
        try { location=asset.resolve(org,complex,(String)row.get("building_no"),(String)row.get("unit_no")); }
        catch(ResponseStatusException ex){if(automatic&&ex.getStatusCode().value()==503)return false;throw ex;}
        if(location.isEmpty())return false;
        if(membership.isEmpty())db.update("insert into organization_members(organization_id,member_id,role,status) values (?,?,'USER','ACTIVE')",org,member);
        long membershipId=db.queryForObject("select id from organization_members where organization_id=? and member_id=?",Long.class,org,member);
        db.update("update roster_entries set location_id=? where id=?",location.get(),entry);
        db.update("insert into resident_units(organization_member_id,roster_entry_id,complex_id,location_id,status) values (?,?,?,?,'ACTIVE')",membershipId,entry,complex,location.get());
        db.update("insert into member_complex_access(organization_member_id,complex_id,access_scope,status) values (?,?,'RESIDENT','ACTIVE') on conflict(organization_member_id,complex_id) do update set access_scope='RESIDENT',status='ACTIVE'",membershipId,complex);
        db.update("update resident_enrollments set status='APPROVED',reviewed_by=?,reviewed_at=now() where id=?",actor,id);
        audit(actor,org,id,"RESIDENT_APPROVE","PENDING_REVIEW","APPROVED");
        auth.revokeMemberSessions(member);
        return true;
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> mine(long actor,int page,int size){
        return db.queryForList("select id,complex_id,building_no,unit_no,status,verification_method,created_at,verified_at,reviewed_at from resident_enrollments where member_id=? order by id desc limit ? offset ?",actor,size,(long)page*size);
    }
    @Transactional
    public List<Map<String,Object>> pending(long actor,long complex,int page,int size){
        long org=lockScope(complex);admin(actor,org,complex);
        return db.queryForList("select e.id,e.member_id,e.building_no,e.unit_no,e.status,e.verification_method,e.verified_at,(select count(*) from roster_entries r where r.roster_id=e.roster_id and r.building_no=e.building_no and r.unit_no=e.unit_no and r.contact_lookup_hmac=e.contact_lookup_hmac and r.status='ACTIVE') as match_count from resident_enrollments e where e.organization_id=? and e.complex_id=? and e.status='PENDING_REVIEW' order by e.id limit ? offset ?",org,complex,size,(long)page*size);
    }
    @Transactional
    public Map<String,Object> decide(long actor,long id,boolean approve){
        var initial=db.queryForList("select complex_id from resident_enrollments where id=?",id);
        if(initial.isEmpty())throw missing();
        long complex=number(initial.getFirst(),"complex_id"),org=lockScope(complex);
        admin(actor,org,complex);
        var rows=db.queryForList("select * from resident_enrollments where id=? and organization_id=? for update",id,org);
        if(rows.isEmpty())throw missing();
        if(!"PENDING_REVIEW".equals(rows.getFirst().get("status")))throw conflict();
        if(approve){
            if(!connect(actor,org,complex,id,rows.getFirst(),false))throw conflict();
            return Map.of("id",id,"status","APPROVED");
        }
        db.update("update resident_enrollments set status='REJECTED',reviewed_by=?,reviewed_at=now() where id=?",actor,id);
        audit(actor,org,id,"RESIDENT_REJECT","PENDING_REVIEW","REJECTED");
        return Map.of("id",id,"status","REJECTED");
    }
}
