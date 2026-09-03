package com.ohmdevs.hrsaas.org.web;

import jakarta.validation.constraints.NotBlank;

public record CostCenterCreateRequest(@NotBlank String code, @NotBlank String name) {
}
