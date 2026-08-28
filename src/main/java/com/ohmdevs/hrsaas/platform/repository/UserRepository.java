package com.ohmdevs.hrsaas.platform.repository;

import com.ohmdevs.hrsaas.platform.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Los usuarios son globales: el mismo correo puede pertenecer a varios tenants. */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
}
