package com.ohmdevs.hrsaas.org.web;

import jakarta.validation.constraints.NotBlank;

public record DepartmentUpdateRequest(@NotBlank String name, boolean active) {
}
