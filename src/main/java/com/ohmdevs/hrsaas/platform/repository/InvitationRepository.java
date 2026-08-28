package com.ohmdevs.hrsaas.platform.repository;

import com.ohmdevs.hrsaas.platform.domain.Invitation;
import com.ohmdevs.hrsaas.platform.domain.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    /**
     * La búsqueda es por hash, nunca por el token en claro, y sigue filtrada por
     * tenant: aceptar una invitación exige saber a qué tenant pertenece, dato
     * que viaja en el enlace del correo.
     */
    Optional<Invitation> findByTokenHash(String tokenHash);

    List<Invitation> findByEmailAndStatus(String email, InvitationStatus status);

    List<Invitation> findByStatus(InvitationStatus status);
}
