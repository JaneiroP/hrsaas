package com.ohmdevs.hrsaas.org.infrastructure;

import com.ohmdevs.hrsaas.org.domain.PayGrade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayGradeRepository extends JpaRepository<PayGrade, Long> {

    Optional<PayGrade> findByCompanyIdAndCode(Long companyId, String code);

    List<PayGrade> findByCompanyId(Long companyId);
}
