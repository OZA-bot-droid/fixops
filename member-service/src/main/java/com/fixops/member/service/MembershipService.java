package com.fixops.member.service;

import com.fixops.member.model.Organization;
import com.fixops.member.repo.OrganizationRepository;
import com.fixops.member.repo.MemberRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class MembershipService {
    private final JdbcTemplate db;
    private final OrganizationRepository orgs;
    private final MemberRepository members;
    private final AuthService auth;
    private final SecureRandom random = new SecureRandom();

    public MembershipService(JdbcTemplate db, OrganizationRepository orgs, MemberRepository members, AuthService auth) {
        this.db=db; this.orgs=orgs; this.members=members; this.auth=auth;
    }

    private ResponseStatusException denied() { return new ResponseStatusException(HttpStatus.FORBIDDEN,"ORG_ACCESS_DENIED"); }
    private ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT,"INVALID_STATE_TRANSITION"); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND"); }
    private Organization lockOrg(long org) {
        var o=orgs.findByIdForUpdate(org).orElseThrow(this::missing);
        if (!"ACTIVE".equals(o.status)) throw conflict();
        return o;
    }
    private boolean verified(long member) {
        return members.findById(member).map(m->m.emailVerified).orElse(false);
    }
    private boolean active(long org,long member) {
        return Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from organization_members where organization_id=? and member_id=? and status='ACTIVE')",Boolean.class,org,member));
    }
    private void admin(long org,long actor) {
        if (!Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from organization_members where organization_id=? and member_id=? and status='ACTIVE' and role='ADMIN')",Boolean.class,org,actor))) throw denied();
    }
    private void audit(long org,long actor,String op,String target,String before,String after) {
        db.update("insert into audit_logs(actor_id,operation,target_type,target_id,organization_id,before_state,after_state) values (?,?,'membership',?,?,?,?)",actor,op,target,org,before,after);
    }
    private String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public List<Map<String,Object>> search(String q,int page,int size) {
        return db.queryForList("select id,name,kind from organizations where status='ACTIVE' and position(lower(?) in lower(name))>0 order by id limit ? offset ?",q,size,(long)page*size);
    }
    @Transactional
    public List<Map<String,Object>> listMembers(long org,long actor,int page,int size) {
        lockOrg(org); admin(org,actor);
        return db.queryForList("""
            select om.id as membership_id, om.member_id, m.display_name, m.email, om.role, om.status
            from organization_members om join members m on m.id=om.member_id
            where om.organization_id=? and om.status='ACTIVE'
            order by om.id limit ? offset ?
            """,org,size,(long)page*size);
    }
    @Transactional
    public Map<String,Object> request(long org,long actor) {
        lockOrg(org);
        if (!verified(actor)) throw denied();
        if (active(org,actor)) throw conflict();
        var existing=db.queryForList("select id,status from join_requests where organization_id=? and member_id=? and status='PENDING_APPROVAL'",org,actor);
        if (!existing.isEmpty()) return existing.getFirst();
        Long id=db.queryForObject("insert into join_requests(organization_id,member_id,status) values (?,?,'PENDING_APPROVAL') returning id",Long.class,org,actor);
        audit(org,actor,"JOIN_REQUEST",id.toString(),null,"PENDING_APPROVAL");
        return Map.of("id",id,"status","PENDING_APPROVAL");
    }
    @Transactional
    public List<Map<String,Object>> pending(long org,long actor,int page,int size) {
        lockOrg(org); admin(org,actor);
        return db.queryForList("select id,member_id,status,created_at from join_requests where organization_id=? and status='PENDING_APPROVAL' order by id limit ? offset ?",org,size,(long)page*size);
    }
    @Transactional
    public List<Map<String,Object>> mine(long org,long actor,int page,int size) {
        return db.queryForList("select id,status,created_at,reviewed_at from join_requests where organization_id=? and member_id=? order by id desc limit ? offset ?",org,actor,size,(long)page*size);
    }
    @Transactional
    public Map<String,Object> decide(long org,long actor,long request,boolean approve) {
        lockOrg(org); admin(org,actor);
        var rows=db.queryForList("select member_id,status from join_requests where id=? and organization_id=? for update",request,org);
        if(rows.isEmpty()) throw missing();
        var row=rows.getFirst();
        if (!"PENDING_APPROVAL".equals(row.get("status"))) throw conflict();
        long target=((Number)row.get("member_id")).longValue();
        if (approve) { if (!verified(target)||active(org,target)) throw conflict(); grant(org,target,"USER"); }
        String next=approve?"APPROVED":"REJECTED";
        db.update("update join_requests set status=?,reviewed_by=?,reviewed_at=now() where id=?",next,actor,request);
        audit(org,actor,"JOIN_DECISION",Long.toString(target),"PENDING_APPROVAL",next);
        if(approve) auth.revokeMemberSessions(target);
        return Map.of("id",request,"status",next);
    }
    private void grant(long org,long member,String role) {
        db.update("insert into organization_members(organization_id,member_id,role,status) values (?,?,?,'ACTIVE') on conflict (organization_id,member_id) do update set role=excluded.role,status='ACTIVE'",org,member,role);
    }
    @Transactional
    public Map<String,Object> invite(long org,long actor,String email,String role) {
        lockOrg(org); admin(org,actor);
        if (!Set.of("USER","TECHNICIAN").contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Long id=db.queryForObject("insert into invitations(organization_id,email,token_hash,role,created_by,expires_at) values (?,?,?,?,?,now()+interval '24 hours') returning id",Long.class,org,email.trim().toLowerCase(Locale.ROOT),hash(token),role,actor);
        audit(org,actor,"INVITATION_CREATE",id.toString(),null,role);
        return Map.of("id",id,"token",token,"expiresIn",86400);
    }
    @Transactional
    public Map<String,Object> accept(long actor,String token) {
        var member=members.findById(actor).orElseThrow(this::denied);
        if (!member.emailVerified) throw denied();
        String digest=hash(token);
        var candidates=db.queryForList("select organization_id from invitations where token_hash=?",digest);
        if(candidates.isEmpty()) throw missing();
        long org=((Number)candidates.getFirst().get("organization_id")).longValue();
        lockOrg(org);
        var rows=db.queryForList("select id,email,role from invitations where token_hash=? and organization_id=? and accepted_at is null and expires_at>now() for update",digest,org);
        if(rows.isEmpty()) throw missing();
        var row=rows.getFirst();
        if(!member.email.equalsIgnoreCase((String)row.get("email"))) throw denied();
        if(active(org,actor)) throw conflict();
        String role=(String)row.get("role");
        grant(org,actor,role);
        db.update("update invitations set accepted_at=now(),accepted_by=? where id=?",actor,row.get("id"));
        audit(org,actor,"INVITATION_ACCEPT",row.get("id").toString(),null,role);
        auth.revokeMemberSessions(actor);
        return Map.of("organizationId",org,"role",role);
    }
    @Transactional
    public Map<String,Object> changeRole(long org,long actor,long target,String role) {
        lockOrg(org); admin(org,actor);
        if (!Set.of("ADMIN","TECHNICIAN","USER").contains(role)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        var rows=db.queryForList("select role from organization_members where organization_id=? and member_id=? and status='ACTIVE' for update",org,target);
        if(rows.isEmpty()) throw missing();
        String before=(String)rows.getFirst().get("role");
        if(before.equals(role)) return Map.of("memberId",target,"role",role);
        protectLastAdmin(org,before,target);
        // Never inherit a different role's scope. Reassignment requires explicit approval.
        revokeScopes(org,target);
        db.update("update organization_members set role=? where organization_id=? and member_id=?",role,org,target);
        audit(org,actor,"MEMBER_ROLE_CHANGE",Long.toString(target),before,role);
        auth.revokeMemberSessions(target);
        return Map.of("memberId",target,"role",role);
    }
    @Transactional
    public Map<String,Object> deactivate(long org,long actor,long target) {
        lockOrg(org);
        if(actor!=target)admin(org,actor);
        var rows=db.queryForList("select role,status from organization_members where organization_id=? and member_id=? for update",org,target);
        if(rows.isEmpty())throw missing();
        var row=rows.getFirst();
        if("INACTIVE".equals(row.get("status")))return Map.of("memberId",target,"status","INACTIVE");
        protectLastAdmin(org,(String)row.get("role"),target);
        revokeScopes(org,target);
        db.update("update organization_members set status='INACTIVE' where organization_id=? and member_id=?",org,target);
        db.update("update join_requests set status='REJECTED',reviewed_by=?,reviewed_at=now() where organization_id=? and member_id=? and status='PENDING_APPROVAL'",actor,org,target);
        db.update("update resident_enrollments set status='REJECTED',reviewed_by=?,reviewed_at=now() where organization_id=? and member_id=? and status in ('PENDING_VERIFICATION','PENDING_REVIEW')",actor,org,target);
        db.update("update invitations set expires_at=now() where organization_id=? and email=(select email from members where id=?) and accepted_at is null and expires_at>now()",org,target);
        audit(org,actor,"MEMBER_DEACTIVATE",Long.toString(target),"ACTIVE","INACTIVE");
        auth.revokeMemberSessions(target);
        return Map.of("memberId",target,"status","INACTIVE");
    }
    private void revokeScopes(long org,long target){
        db.update("update member_complex_access a set status='REVOKED' from organization_members om where a.organization_member_id=om.id and om.organization_id=? and om.member_id=? and a.status='ACTIVE'",org,target);
        db.update("update resident_units u set status='REVOKED',revoked_at=now() from organization_members om where u.organization_member_id=om.id and om.organization_id=? and om.member_id=? and u.status='ACTIVE'",org,target);
    }
    private void protectLastAdmin(long org,String before,long target) {
        if ("ADMIN".equals(before) && db.queryForObject("select count(*) from organization_members where organization_id=? and status='ACTIVE' and role='ADMIN'",Long.class,org)<=1) throw conflict();
        if ("ADMIN".equals(before) && Boolean.TRUE.equals(db.queryForObject("""
            select exists(select 1 from member_complex_access a
              join organization_members om on om.id=a.organization_member_id
              join complex_managements m on m.complex_id=a.complex_id and m.organization_id=om.organization_id and m.status='ACTIVE'
              where om.organization_id=? and om.member_id=? and a.status='ACTIVE' and a.access_scope='ADMIN'
              and not exists(select 1 from member_complex_access b join organization_members other on other.id=b.organization_member_id
                where b.complex_id=a.complex_id and b.status='ACTIVE' and b.access_scope='ADMIN'
                and other.organization_id=om.organization_id and other.member_id<>om.member_id and other.role='ADMIN' and other.status='ACTIVE'))
            """,Boolean.class,org,target)))throw conflict();
    }
}
