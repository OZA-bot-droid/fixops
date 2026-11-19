package com.fixops.member.repo;
import com.fixops.member.model.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember,Long> { java.util.List<OrganizationMember> findByMemberIdAndStatus(Long memberId,String status);
 boolean existsByOrganizationIdAndMemberId(Long organizationId,Long memberId); }
