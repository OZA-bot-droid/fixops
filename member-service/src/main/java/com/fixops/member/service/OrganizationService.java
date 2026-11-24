package com.fixops.member.service;
import com.fixops.member.model.*;
import com.fixops.member.repo.*;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
@Service
public class OrganizationService {
 @Value("${fixops.demo.building-approval-bypass:false}") private boolean demoBuildingApproval;
 private final OrganizationRepository orgs;private final OrganizationMemberRepository memberships;private final MemberRepository members;private final AuditLogRepository audit; private final AuthService auth;
 public OrganizationService(OrganizationRepository o,OrganizationMemberRepository om,MemberRepository m,AuditLogRepository a,AuthService auth){orgs=o;memberships=om;members=m;audit=a;this.auth=auth;}
 @Transactional public Organization apply(Long memberId,String name,String kind){
  Member member=members.findById(memberId).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED));
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
  if(approve && org.kind.equals("BUILDING") && !demoBuildingApproval)throw new ResponseStatusException(HttpStatus.CONFLICT,"BUILDING_MANAGEMENT_VERIFICATION_REQUIRED");
  if(!approve && (reason==null||reason.isBlank()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"REJECTION_REASON_REQUIRED");
  org.status=approve?"ACTIVE":"REJECTED";org.reviewReason=reason;org.reviewedBy=platformAdminId;org.reviewedAt=Instant.now();
  if(approve && !memberships.existsByOrganizationIdAndMemberId(org.id,org.applicantId)) memberships.save(new OrganizationMember(org.id,org.applicantId,"ADMIN"));
  audit.save(new AuditLog(platformAdminId,approve?"ORGANIZATION_APPROVE":"ORGANIZATION_REJECT","organization",org.id.toString()));
  // Newly granted ADMIN scope must not be interpreted from an older JWT.
  if (approve) auth.logoutAll(org.applicantId);
  return orgs.save(org);
 }
 public List<Organization> pending(Long requesterId){if(!isAdmin(requesterId))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"PLATFORM_ADMIN_REQUIRED");return orgs.findByStatusOrderByCreatedAtAsc("PENDING_APPROVAL");}
 public List<Organization> mine(Long requesterId){return orgs.findByApplicantIdOrderByCreatedAtDesc(requesterId);}
 private boolean isAdmin(Long id){return members.findById(id).map(m->m.platformAdmin).orElse(false);}
}
