package com.ohmdevs.hrsaas.org.web;

import jakarta.validation.constraints.NotBlank;

public record DepartmentCreateRequest(@NotBlank String name) {
}
