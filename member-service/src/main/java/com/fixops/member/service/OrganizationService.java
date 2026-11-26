package com.fixops.member.service;
import com.fixops.member.model.*;
import com.fixops.member.repo.*;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
@Service
public class OrganizationService {
 private final OrganizationRepository orgs;private final OrganizationMemberRepository memberships;private final MemberRepository members;private final AuditLogRepository audit; private final AuthService auth; private final org.springframework.jdbc.core.JdbcTemplate db;
 public OrganizationService(OrganizationRepository o,OrganizationMemberRepository om,MemberRepository m,AuditLogRepository a,AuthService auth,org.springframework.jdbc.core.JdbcTemplate db){orgs=o;memberships=om;members=m;audit=a;this.auth=auth;this.db=db;}
 @Transactional public Organization apply(Long memberId,String name,String kind){
  Member member=members.findById(memberId).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  db.queryForList("select id from members where id=? for update",memberId);
  if(member.platformAdmin || Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from organization_members where member_id=? and status='ACTIVE' and role in ('USER','TECHNICIAN'))",Boolean.class,memberId)) && !Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from organization_members where member_id=? and status='ACTIVE' and role='ADMIN')",Boolean.class,memberId)))
   throw new ResponseStatusException(HttpStatus.FORBIDDEN,"ORG_ACCESS_DENIED");
  if(Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from organizations where applicant_id=? and status='PENDING_APPROVAL')",Boolean.class,memberId)))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"INVALID_STATE_TRANSITION");
  if(!member.emailVerified) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"EMAIL_VERIFICATION_REQUIRED");
  if(!Set.of("BUILDING","FACTORY").contains(kind)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"INVALID_ORGANIZATION_KIND");
  Organization org=orgs.save(new Organization(memberId,name.trim(),kind));
  audit.save(new AuditLog(memberId,"ORGANIZATION_APPLY","organization",org.id.toString()));return org;
 }
 @Transactional public Organization decide(Long platformAdminId,Long orgId,boolean approve,String reason){
  Member actor=members.findById(platformAdminId).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
  if(!actor.platformAdmin) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"PLATFORM_ADMIN_REQUIRED");
  Organization org=orgs.findByIdForUpdate(orgId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND"));
  if(!org.status.equals("PENDING_APPROVAL"))throw new ResponseStatusException(HttpStatus.CONFLICT,"INVALID_STATE_TRANSITION");
  if(approve && org.kind.equals("BUILDING") && !Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from complex_managements m join apartment_complexes c on c.id=m.complex_id where m.organization_id=? and m.status=\'ACTIVE\' and c.verification_status=\'VERIFIED\')",Boolean.class,orgId)))throw new ResponseStatusException(HttpStatus.CONFLICT,"BUILDING_MANAGEMENT_VERIFICATION_REQUIRED");
  if(!approve && (reason==null||reason.isBlank()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"REJECTION_REASON_REQUIRED");
  org.status=approve?"ACTIVE":"REJECTED";org.reviewReason=reason;org.reviewedBy=platformAdminId;org.reviewedAt=Instant.now();
  if(approve && !memberships.existsByOrganizationIdAndMemberId(org.id,org.applicantId)) memberships.saveAndFlush(new OrganizationMember(org.id,org.applicantId,"ADMIN"));
  if(approve && org.kind.equals("BUILDING")) {
   db.update("insert into member_complex_access(organization_member_id,complex_id,access_scope,status) select om.id,m.complex_id,'ADMIN','ACTIVE' from organization_members om join complex_managements m on m.organization_id=om.organization_id and m.status='ACTIVE' where om.organization_id=? and om.member_id=? and om.role='ADMIN' and om.status='ACTIVE' on conflict(organization_member_id,complex_id) do update set access_scope='ADMIN',status='ACTIVE'",orgId,org.applicantId);
  }
  audit.save(new AuditLog(platformAdminId,approve?"ORGANIZATION_APPROVE":"ORGANIZATION_REJECT","organization",org.id.toString()));
  // Newly granted ADMIN scope must not be interpreted from an older JWT.
  if (approve) auth.revokeMemberSessions(org.applicantId);
  if(!approve) {
   db.update("update complex_managements set status='REJECTED',reviewed_by=?,review_reason=?,reviewed_at=now() where organization_id=? and status='PENDING_APPROVAL'",platformAdminId,reason,orgId);
   db.update("update complex_managements set status='ENDED',ended_at=now(),reviewed_by=?,review_reason=?,reviewed_at=now() where organization_id=? and status='ACTIVE'",platformAdminId,reason,orgId);
  }
  return orgs.save(org);
 }
 @Transactional public void activateInvitedBuilding(Long actor,Long orgId){
  if(Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from organization_onboarding_invitations where organization_id=? and accepted_at is not null)",Boolean.class,orgId))){
   var org=orgs.findByIdForUpdate(orgId).orElseThrow();
   if("PENDING_APPROVAL".equals(org.status)) decide(actor,orgId,true,"Invited organization: management verified");
  }
 }
 public List<Organization> pending(Long requesterId){if(!isAdmin(requesterId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"PLATFORM_ADMIN_REQUIRED");return orgs.findByStatusOrderByCreatedAtAsc("PENDING_APPROVAL");}
 public List<Organization> mine(Long requesterId){return orgs.findByApplicantIdOrderByCreatedAtDesc(requesterId);}
 private boolean isAdmin(Long id){return members.findById(id).map(m->m.platformAdmin).orElse(false);}
}
