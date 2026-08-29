package com.ohmdevs.hrsaas.org.infrastructure;

import com.ohmdevs.hrsaas.org.domain.Location;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LocationRepository extends JpaRepository<Location, Long> {

    List<Location> findByCompanyId(Long companyId);
}
