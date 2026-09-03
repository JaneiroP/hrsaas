package com.ohmdevs.hrsaas.org.web;

public record PositionResponse(
        Long id, Long companyId, Long departmentId, String departmentName,
        String title, Long payGradeId, boolean active) {
}
