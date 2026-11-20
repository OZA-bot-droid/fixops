package com.fixops.asset.location.repository;

import com.fixops.asset.location.entity.Location;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Location 영속화와 조직·단지 범위를 함께 제한하는 조회를 담당한다. */
public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findAllByOrganizationIdAndComplexIdOrderById(Long organizationId, Long complexId);
    List<Location> findAllByOrganizationIdAndComplexIdIsNullOrderById(Long organizationId);
    Optional<Location> findByIdAndOrganizationIdAndComplexId(Long id, Long organizationId, Long complexId);
    Optional<Location> findByIdAndOrganizationIdAndComplexIdIsNull(Long id, Long organizationId);
}
