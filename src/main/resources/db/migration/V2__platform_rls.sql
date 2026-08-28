-- ============================================================================
-- Row Level Security — segunda capa de aislamiento (AD-02).
--
-- @TenantId de Hibernate filtra las consultas de JPA. Esto cubre lo que aquella
-- capa no ve: consultas nativas, reportes, scripts de mantenimiento y cualquier
-- repositorio nuevo donde alguien olvide el filtro.
--
-- DOS ADVERTENCIAS QUE HACEN LA DIFERENCIA ENTRE PROTEGER Y APARENTAR PROTEGER:
--
--   1. RLS NO se aplica al dueño de la tabla salvo que se declare FORCE.
--      Por eso cada tabla lleva FORCE ROW LEVEL SECURITY abajo.
--
--   2. RLS NO se aplica NUNCA a un superusuario, ni con FORCE. Si la aplicación
--      se conecta como 'postgres', estas políticas no hacen absolutamente nada
--      y los tests pasan en verde sin probar nada. El usuario de runtime debe
--      ser un rol normal — ver PlatformIsolationIT, que crea uno para verificarlo.
-- ============================================================================

-- Devuelve NULL cuando no hay tenant en la sesión: 'tenant_id = NULL' es NULL,
-- que no es TRUE, así que no pasa ninguna fila. Falla cerrado por construcción.
CREATE OR REPLACE FUNCTION app_current_tenant() RETURNS BIGINT
    LANGUAGE sql
    STABLE
AS $$
SELECT NULLIF(current_setting('app.tenant_id', true), '')::BIGINT
$$;

DO $$
    DECLARE
        t TEXT;
    BEGIN
        FOREACH t IN ARRAY ARRAY[
            'user_tenants',
            'roles',
            'role_permissions',
            'user_roles',
            'invitations',
            'audit_events'
        ]
            LOOP
                EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
                EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
                EXECUTE format(
                        'CREATE POLICY %I ON %I USING (tenant_id = app_current_tenant()) '
                            || 'WITH CHECK (tenant_id = app_current_tenant())',
                        'p_tenant_isolation_' || t, t
                        );
            END LOOP;
    END
$$;
