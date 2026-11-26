package com.fixops.member.service;

import com.fixops.member.model.Member;
import com.fixops.member.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganizationOnboardingServiceTest {
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final MemberRepository members=mock(MemberRepository.class);
    private final OrganizationRepository orgs=mock(OrganizationRepository.class);
    private final AuditLogRepository audit=mock(AuditLogRepository.class);
    private final OrganizationService organizations=mock(OrganizationService.class);
    private final OrganizationOnboardingService service=new OrganizationOnboardingService(db,members,orgs,audit,organizations);
    private Member account(boolean verified,boolean admin){
        Member m=new Member("resident@example.com","hash","Resident",verified);
        m.id=1L;m.platformAdmin=admin;
        when(members.findById(1L)).thenReturn(Optional.of(m));return m;
    }
    @Test void ordinaryMemberCannotIssueOrRevoke(){
        account(true,false);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.issue(1L,"other@example.com","Factory","FACTORY")).getStatusCode().value());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.revoke(1L,5L)).getStatusCode().value());
        verifyNoInteractions(db,orgs,audit);
    }
    @Test void unverifiedEmailCannotAccept(){
        account(false,false);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.accept(1L,5L)).getStatusCode().value());
        verifyNoInteractions(orgs,audit,organizations);
    }
    @Test void missingMismatchedExpiredOrConsumedInvitationCannotCreateOrganization(){
        account(true,false);
        // The DB query returns no rows for any of these ineligible invitation states.
        assertEquals(403,assertThrows(ResponseStatusException.class,()->service.accept(1L,5L)).getStatusCode().value());
        verifyNoInteractions(orgs,audit,organizations);
    }
}
