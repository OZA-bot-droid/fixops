package com.fixops.member.service;

import com.fixops.member.model.*;
import com.fixops.member.repo.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class OrganizationOnboardingService {
    private final JdbcTemplate db;
    private final MemberRepository members;
    private final OrganizationRepository orgs;
    private final AuditLogRepository audit;
    private final OrganizationService organizations;
    public OrganizationOnboardingService(JdbcTemplate db,MemberRepository members,OrganizationRepository orgs,AuditLogRepository audit,OrganizationService organizations){
        this.db=db;this.members=members;this.orgs=orgs;this.audit=audit;this.organizations=organizations;
    }
    private ResponseStatusException denied(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"ORG_ACCESS_DENIED");}
    private ResponseStatusException conflict(){return new ResponseStatusException(HttpStatus.CONFLICT,"INVALID_STATE_TRANSITION");}
    private Member member(long actor){return members.findById(actor).orElseThrow(this::denied);}
    private void platform(long actor){if(!member(actor).platformAdmin)throw denied();}
    private void audit(long actor,long id,String action){audit.save(new AuditLog(actor,action,"organization_onboarding_invitation",Long.toString(id)));}
    private boolean exists(String sql,Object...args){return Boolean.TRUE.equals(db.queryForObject(sql,Boolean.class,args));}
    @Transactional
    public Map<String,Object> issue(long actor,String email,String name,String kind){
        platform(actor);
        email=email.trim().toLowerCase(Locale.ROOT);name=name.trim();
        if(name.isBlank()||!Set.of("BUILDING","FACTORY").contains(kind))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_INPUT");
        db.queryForList("select pg_advisory_xact_lock(hashtextextended(?, 31))",email);
        db.update("update organization_onboarding_invitations set revoked_at=now() where invitee_email=? and organization_name=? and organization_kind=? and accepted_at is null and revoked_at is null and expires_at<=now()",email,name,kind);
        if(exists("select exists(select 1 from organization_onboarding_invitations where invitee_email=? and organization_name=? and organization_kind=? and accepted_at is null and revoked_at is null)",email,name,kind))throw conflict();
        Long id=db.queryForObject("insert into organization_onboarding_invitations(invitee_email,organization_name,organization_kind,created_by,expires_at) values (?,?,?,?,now()+interval '72 hours') returning id",Long.class,email,name,kind,actor);
        audit(actor,id,"ORGANIZATION_ONBOARDING_INVITE");
        return Map.of("id",id,"expiresInSeconds",259200);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> list(long actor,int page,int size){
        platform(actor);
        return db.queryForList("select id,invitee_email,organization_name,organization_kind,organization_id,created_at,expires_at,case when accepted_at is not null then 'ACCEPTED' when revoked_at is not null then 'REVOKED' when expires_at<=now() then 'EXPIRED' else 'PENDING' end as status from organization_onboarding_invitations order by id desc limit ? offset ?",size,(long)page*size);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> mine(long actor){
        Member m=member(actor);if(!m.emailVerified)throw denied();
        return db.queryForList("select id,organization_name,organization_kind,expires_at from organization_onboarding_invitations where invitee_email=lower(?) and accepted_at is null and revoked_at is null and expires_at>now() order by id desc limit 100",m.email);
    }
    @Transactional
    public void revoke(long actor,long id){
        platform(actor);
        if(db.update("update organization_onboarding_invitations set revoked_at=now() where id=? and accepted_at is null and revoked_at is null and expires_at>now()",id)!=1)throw conflict();
        audit(actor,id,"ORGANIZATION_ONBOARDING_REVOKE");
    }
    @Transactional
    public Organization accept(long actor,long id){
        db.queryForList("select id from members where id=? for update",actor);
        Member m=member(actor);
        if(!m.emailVerified||m.platformAdmin)throw denied();
        var rows=db.queryForList("select * from organization_onboarding_invitations where id=? and invitee_email=lower(?) and accepted_at is null and revoked_at is null and expires_at>now() for update",id,m.email);
        if(rows.isEmpty())throw denied();
        var row=rows.getFirst();
        long issuer=((Number)row.get("created_by")).longValue();
        platform(issuer);
        if(exists("select exists(select 1 from organizations where applicant_id=? and status='PENDING_APPROVAL')",actor))throw conflict();
        Organization org=orgs.saveAndFlush(new Organization(actor,(String)row.get("organization_name"),(String)row.get("organization_kind")));
        db.update("update organization_onboarding_invitations set accepted_at=now(),accepted_by=?,organization_id=? where id=?",actor,org.id,id);
        audit(actor,id,"ORGANIZATION_ONBOARDING_ACCEPT");
        if("FACTORY".equals(org.kind))return organizations.decide(issuer,org.id,true,"Platform invitation accepted");
        return org;
    }
}
