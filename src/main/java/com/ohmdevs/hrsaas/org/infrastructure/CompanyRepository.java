package com.ohmdevs.hrsaas.org.infrastructure;

import com.ohmdevs.hrsaas.org.domain.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByRnc(String rnc);

    List<Company> findByActiveTrue();
}
