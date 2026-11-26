package com.fixops.member.repo;

import com.fixops.member.model.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {
    java.util.List<OrganizationMember> findByMemberIdAndStatus(Long memberId, String status);

    @org.springframework.data.jpa.repository.Query("""
        select new map(m.organizationId as organizationId, m.id as membershipId, m.role as role,
            o.name as organizationName, o.kind as organizationKind)
        from OrganizationMember m, Organization o
        where m.organizationId=o.id and m.memberId=:memberId and m.status='ACTIVE' and o.status='ACTIVE'
        order by o.name, o.id
        """)
    java.util.List<java.util.Map<String,Object>> findActiveScopes(@org.springframework.data.repository.query.Param("memberId") Long memberId);

    boolean existsByOrganizationIdAndMemberId(Long organizationId, Long memberId);
}
