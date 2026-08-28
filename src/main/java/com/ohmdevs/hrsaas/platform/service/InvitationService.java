package com.ohmdevs.hrsaas.platform.service;

import com.ohmdevs.hrsaas.platform.domain.Invitation;
import com.ohmdevs.hrsaas.platform.domain.InvitationStatus;
import com.ohmdevs.hrsaas.platform.domain.MembershipStatus;
import com.ohmdevs.hrsaas.platform.domain.Role;
import com.ohmdevs.hrsaas.platform.domain.Tenant;
import com.ohmdevs.hrsaas.platform.domain.TenantStatus;
import com.ohmdevs.hrsaas.platform.domain.User;
import com.ohmdevs.hrsaas.platform.domain.UserRole;
import com.ohmdevs.hrsaas.platform.domain.UserTenant;
import com.ohmdevs.hrsaas.platform.repository.InvitationRepository;
import com.ohmdevs.hrsaas.platform.repository.RoleRepository;
import com.ohmdevs.hrsaas.platform.repository.TenantRepository;
import com.ohmdevs.hrsaas.platform.repository.UserRepository;
import com.ohmdevs.hrsaas.platform.repository.UserRoleRepository;
import com.ohmdevs.hrsaas.platform.repository.UserTenantRepository;
import com.ohmdevs.hrsaas.platform.tenancy.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Alta de usuarios en un tenant por invitación.
 *
 * <p>No hay auto-registro: a un tenant de RRHH se entra porque alguien con
 * {@code user:invite} te metió, con un rol decidido de antemano.
 */
@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final UserTenantRepository userTenantRepository;
    private final UserRoleRepository userRoleRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final Duration invitationTtl;

    public InvitationService(InvitationRepository invitationRepository,
                             RoleRepository roleRepository,
                             UserRepository userRepository,
                             UserTenantRepository userTenantRepository,
                             UserRoleRepository userRoleRepository,
                             TenantRepository tenantRepository,
                             PasswordEncoder passwordEncoder,
                             @Value("${hrsaas.platform.invitation-ttl-days:7}") int invitationTtlDays) {
        this.invitationRepository = invitationRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.userTenantRepository = userTenantRepository;
        this.userRoleRepository = userRoleRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.invitationTtl = Duration.ofDays(invitationTtlDays);
    }

    /** Token en claro devuelto una sola vez, para armar el enlace del correo. */
    public record IssuedInvitation(Invitation invitation, String rawToken) {
    }

    /**
     * Emite una invitación para el tenant activo.
     *
     * <p>Revoca cualquier pendiente previa al mismo correo. La base de datos lo
     * impediría de todos modos por índice parcial único, pero fallar con una
     * violación de restricción cuando alguien reenvía una invitación es una
     * mala respuesta a una intención perfectamente razonable.
     */
    @Transactional
    public IssuedInvitation invite(String email, Long roleId, Long invitedByUserId) {
        TenantContext.require();
        String normalizedEmail = normalize(email);

        Role role = roleRepository.findById(roleId).orElseThrow(
                () -> new IllegalArgumentException("Rol " + roleId + " no existe en este tenant"));

        invitationRepository.findByEmailAndStatus(normalizedEmail, InvitationStatus.PENDING)
                .forEach(previous -> previous.setStatus(InvitationStatus.REVOKED));
        invitationRepository.flush();

        String rawToken = Tokens.generate();
        Invitation invitation = new Invitation();
        invitation.setEmail(normalizedEmail);
        invitation.setRole(role);
        invitation.setTokenHash(Tokens.sha256Hex(rawToken));
        invitation.setExpiresAt(Instant.now().plus(invitationTtl));
        if (invitedByUserId != null) {
            invitation.setInvitedBy(userRepository.getReferenceById(invitedByUserId));
        }
        return new IssuedInvitation(invitationRepository.save(invitation), rawToken);
    }

    /**
     * Canjea una invitación y deja al usuario dentro del tenant con su rol.
     *
     * <p><strong>Exige {@link TenantContext} ya establecido.</strong> Quien llama
     * resuelve el tenant desde el enlace (que lleva el slug) y abre el contexto
     * antes de invocar. Tiene que ser así: el contexto fija {@code app.tenant_id}
     * en la conexión al empezar la transacción, y esta transacción empieza aquí.
     * Resolver el tenant dentro ya sería tarde.
     *
     * <p>Si el correo ya tenía cuenta en otro tenant, se reutiliza y se le
     * respeta la contraseña. La contraseña propuesta solo se aplica a cuentas
     * nuevas — si no, invitar a alguien a tu tenant sería una forma de
     * cambiarle la contraseña en los demás.
     */
    @Transactional
    public User accept(String rawToken, String fullName, String rawPassword) {
        Long tenantId = TenantContext.require();
        Instant now = Instant.now();

        Invitation invitation = invitationRepository.findByTokenHash(Tokens.sha256Hex(rawToken))
                .orElseThrow(() -> new InvitationNotRedeemableException("Token de invitación desconocido"));

        if (!invitation.isRedeemable(now)) {
            if (invitation.getStatus() == InvitationStatus.PENDING) {
                invitation.setStatus(InvitationStatus.EXPIRED);
            }
            throw new InvitationNotRedeemableException(
                    "Invitación %d en estado %s, vencía %s"
                            .formatted(invitation.getId(), invitation.getStatus(), invitation.getExpiresAt()));
        }

        User user = userRepository.findByEmail(invitation.getEmail()).orElseGet(() -> {
            User fresh = new User();
            fresh.setEmail(invitation.getEmail());
            fresh.setFullName(fullName);
            return fresh;
        });
        if (user.getPasswordHash() == null && rawPassword != null) {
            user.setPasswordHash(passwordEncoder.encode(rawPassword));
        }
        user = userRepository.save(user);

        if (!userTenantRepository.existsByUserId(user.getId())) {
            UserTenant membership = new UserTenant();
            membership.setUser(user);
            membership.setStatus(MembershipStatus.ACTIVE);
            userTenantRepository.save(membership);
        }

        if (!userRoleRepository.existsByUserIdAndRoleId(user.getId(), invitation.getRole().getId())) {
            UserRole assignment = new UserRole();
            assignment.setUser(user);
            assignment.setRole(invitation.getRole());
            userRoleRepository.save(assignment);
        }

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(now);

        activateIfPending(tenantId);
        return user;
    }

    @Transactional
    public void revoke(Long invitationId) {
        Invitation invitation = invitationRepository.findById(invitationId).orElseThrow(
                () -> new IllegalArgumentException("Invitación " + invitationId + " no existe en este tenant"));
        if (invitation.getStatus() == InvitationStatus.PENDING) {
            invitation.setStatus(InvitationStatus.REVOKED);
        }
    }

    /** El tenant nace PENDING y se activa cuando su primer usuario entra. */
    private void activateIfPending(Long tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
        if (tenant.getStatus() == TenantStatus.PENDING
                && tenant.getStatus().canTransitionTo(TenantStatus.ACTIVE)) {
            tenant.setStatus(TenantStatus.ACTIVE);
        }
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
