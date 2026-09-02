package com.ohmdevs.hrsaas.platform.web;

import com.ohmdevs.hrsaas.platform.service.AuthenticationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Único endpoint de autenticación de toda la aplicación: lo usa tanto el
 * personal de RRHH como el portal del empleado, porque ambos terminan en el
 * mismo {@link com.ohmdevs.hrsaas.platform.access.CurrentActor} — lo que
 * cambia es qué permisos trae cada uno, no cómo entra.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticationService.IssuedAccessToken issued = authenticationService.login(request.email(), request.password());
        return LoginResponse.bearer(issued.value(), issued.expiresInSeconds());
    }
}
