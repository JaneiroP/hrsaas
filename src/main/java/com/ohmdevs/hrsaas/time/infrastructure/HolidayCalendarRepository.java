package com.ohmdevs.hrsaas.time.infrastructure;

import com.ohmdevs.hrsaas.time.domain.HolidayCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendar, Long> {

    List<HolidayCalendar> findByCompanyId(Long companyId);
}
