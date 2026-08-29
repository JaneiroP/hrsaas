package com.ohmdevs.hrsaas.org.infrastructure;

import com.ohmdevs.hrsaas.org.domain.CostCenter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CostCenterRepository extends JpaRepository<CostCenter, Long> {

    Optional<CostCenter> findByCompanyIdAndCode(Long companyId, String code);

    List<CostCenter> findByCompanyId(Long companyId);
}
