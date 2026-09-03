package com.ohmdevs.hrsaas.org.web;

import jakarta.validation.constraints.NotBlank;

public record LocationCreateRequest(@NotBlank String name, String address) {
}
