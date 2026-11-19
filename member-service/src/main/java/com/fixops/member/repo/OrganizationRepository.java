package com.fixops.member.repo;
import com.fixops.member.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OrganizationRepository extends JpaRepository<Organization,Long> { java.util.List<Organization> findByStatusOrderByCreatedAtAsc(String status);
 java.util.List<Organization> findByApplicantIdOrderByCreatedAtDesc(Long applicantId);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select o from Organization o where o.id = :id")
 java.util.Optional<Organization> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id); }
