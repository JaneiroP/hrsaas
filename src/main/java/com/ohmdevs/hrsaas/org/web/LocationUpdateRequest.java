package com.ohmdevs.hrsaas.org.web;

import jakarta.validation.constraints.NotBlank;

public record LocationUpdateRequest(@NotBlank String name, String address, boolean active) {
}
