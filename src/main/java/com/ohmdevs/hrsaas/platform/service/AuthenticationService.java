package com.ohmdevs.hrsaas.platform.service;

import com.ohmdevs.hrsaas.platform.domain.User;
import com.ohmdevs.hrsaas.platform.repository.UserRepository;
import com.ohmdevs.hrsaas.platform.repository.UserTenantRepository;
import com.ohmdevs.hrsaas.platform.security.AccessTokenIssuer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Verifica credenciales y emite el access token de sesión.
 *
 * <p><strong>Decisión abierta sin resolver, dejada explícita en vez de
 * inventada:</strong> {@code POST /auth/login} recibe solo correo y
 * contraseña, sin discriminador de tenant — así lo definió el contrato ya
 * acordado con el frontend. Un usuario puede pertenecer a varios tenants
 * (contador externo de tres empresas, blueprint §03); esta primera pasada
 * toma el primero de {@link UserTenantRepository#findActiveTenantIdsForUser}
 * en cuanto a orden de fila, sin criterio de negocio detrás. Un usuario
 * multi-tenant real necesita, o bien un selector de tenant tras el login, o
 * bien un discriminador en la propia petición (subdominio, cabecera) — ninguno
 * de los dos está implementado todavía.
 */
@Service
public class AuthenticationService {

    /** Igual de arbitrario que en cualquier login clásico; configurable si el negocio lo pide. */
    private static final short MAX_FAILED_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final UserTenantRepository userTenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;

    public AuthenticationService(UserRepository userRepository,
                                  UserTenantRepository userTenantRepository,
                                  PasswordEncoder passwordEncoder,
                                  AccessTokenIssuer accessTokenIssuer) {
        this.userRepository = userRepository;
        this.userTenantRepository = userTenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
    }

    public record IssuedAccessToken(String value, long expiresInSeconds) {
    }

    /**
     * Contraseña incorrecta cuenta como intento fallido (bloquea tras
     * {@value #MAX_FAILED_ATTEMPTS}, usando el campo {@code failed_attempts}
     * que {@code User} ya reservaba para esto). Una cuenta que todavía no
     * puede autenticarse (deshabilitada, bloqueada, o invitada sin contraseña
     * fijada) se rechaza antes de intentar comparar la contraseña, para no
     * comparar contra un {@code passwordHash} nulo.
     */
    @Transactional
    public IssuedAccessToken login(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new InvalidCredentialsException("Correo no registrado: " + normalizedEmail));

        if (!user.canAuthenticate()) {
            throw new InvalidCredentialsException(
                    "Usuario " + user.getId() + " no puede autenticarse todavía (deshabilitado, bloqueado o sin contraseña)");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            registerFailedAttempt(user);
            throw new InvalidCredentialsException("Contraseña incorrecta para " + normalizedEmail);
        }

        List<Long> activeTenantIds = userTenantRepository.findActiveTenantIdsForUser(user.getId());
        if (activeTenantIds.isEmpty()) {
            throw new InvalidCredentialsException(
                    "El usuario " + user.getId() + " no tiene pertenencia activa a ningún tenant");
        }
        Long tenantId = activeTenantIds.get(0);

        user.setFailedAttempts((short) 0);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        String token = accessTokenIssuer.issueAccessToken(user.getId(), tenantId);
        return new IssuedAccessToken(token, AccessTokenIssuer.ACCESS_TOKEN_TTL_SECONDS);
    }

    private void registerFailedAttempt(User user) {
        short attempts = (short) (user.getFailedAttempts() + 1);
        user.setFailedAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLocked(true);
        }
        userRepository.save(user);
    }
}
