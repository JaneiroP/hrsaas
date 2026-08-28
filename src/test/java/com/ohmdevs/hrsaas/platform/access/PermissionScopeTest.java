package com.ohmdevs.hrsaas.platform.access;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contención de alcances. Sin base de datos: es la lógica que decide quién ve
 * el sueldo de quién, y merece verificarse en milisegundos y en cada build.
 */
class PermissionScopeTest {

    @Test
    @DisplayName("un alcance se incluye a sí mismo y a todos los más estrechos")
    void includesNarrowerScopes() {
        assertThat(PermissionScope.ALL.includes(PermissionScope.COMPANY)).isTrue();
        assertThat(PermissionScope.ALL.includes(PermissionScope.SELF)).isTrue();
        assertThat(PermissionScope.COMPANY.includes(PermissionScope.TEAM)).isTrue();
        assertThat(PermissionScope.TEAM.includes(PermissionScope.TEAM)).isTrue();
    }

    @Test
    @DisplayName("un alcance estrecho no cubre a uno más amplio")
    void doesNotIncludeWiderScopes() {
        assertThat(PermissionScope.SELF.includes(PermissionScope.TEAM)).isFalse();
        assertThat(PermissionScope.TEAM.includes(PermissionScope.COMPANY)).isFalse();
        assertThat(PermissionScope.COMPANY.includes(PermissionScope.ALL)).isFalse();
    }

    @Test
    @DisplayName("al consolidar roles gana el alcance más amplio")
    void widestWins() {
        assertThat(PermissionScope.TEAM.widest(PermissionScope.COMPANY))
                .isEqualTo(PermissionScope.COMPANY);
        assertThat(PermissionScope.COMPANY.widest(PermissionScope.TEAM))
                .isEqualTo(PermissionScope.COMPANY);
        assertThat(PermissionScope.SELF.widest(PermissionScope.SELF))
                .isEqualTo(PermissionScope.SELF);
    }

    @Test
    @DisplayName("un supervisor aprueba ausencias de su equipo pero solo ve su propio volante")
    void supervisorHasDifferentScopesPerPermission() {
        CurrentActor supervisor = new CurrentActor(7L, 1L, Map.of(
                "employee:read", PermissionScope.TEAM,
                "leave:approve", PermissionScope.TEAM,
                "payslip:read", PermissionScope.SELF));

        // Éste es el caso que justifica poner el alcance en la concesión y no
        // en el rol: el mismo rol, dos alcances distintos.
        assertThat(supervisor.canAtLeast("leave:approve", PermissionScope.TEAM)).isTrue();
        assertThat(supervisor.canAtLeast("payslip:read", PermissionScope.SELF)).isTrue();
        assertThat(supervisor.canAtLeast("payslip:read", PermissionScope.TEAM)).isFalse();
    }

    @Test
    @DisplayName("un permiso ausente se deniega aunque se pida el alcance mínimo")
    void missingPermissionIsDenied() {
        CurrentActor employee = new CurrentActor(9L, 1L, Map.of("payslip:read", PermissionScope.SELF));

        assertThat(employee.can("payroll:approve")).isFalse();
        assertThat(employee.canAtLeast("payroll:approve", PermissionScope.SELF)).isFalse();
        assertThat(employee.scopeFor("payroll:approve")).isEmpty();
    }
}
