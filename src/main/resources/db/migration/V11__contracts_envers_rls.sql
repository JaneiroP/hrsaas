-- ============================================================================
-- Row Level Security para las tablas de Envers (AD-02 + AD-07).
--
-- revinfo sigue el patron normal de columna directa. employment_contracts_aud
-- y salary_history_aud NO tienen tenant_id (ver V10) -- su aislamiento se
-- resuelve via JOIN contra revinfo, que si lo tiene, en vez del predicado de
-- igualdad directo que usa el resto de la suite. Sin esto, el historico de
-- contratos y salarios de todos los tenants seria legible sin proteccion --
-- cierra la fuga que AD-07 busca evitar.
-- ============================================================================

ALTER TABLE revinfo ENABLE ROW LEVEL SECURITY;
ALTER TABLE revinfo FORCE ROW LEVEL SECURITY;
CREATE POLICY p_tenant_isolation_revinfo ON revinfo
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());

DO $$
    DECLARE
        t TEXT;
    BEGIN
        FOREACH t IN ARRAY ARRAY['employment_contracts_aud', 'salary_history_aud']
            LOOP
                EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
                EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
                EXECUTE format(
                        'CREATE POLICY %I ON %I '
                            || 'USING (rev IN (SELECT id FROM revinfo WHERE tenant_id = app_current_tenant())) '
                            || 'WITH CHECK (rev IN (SELECT id FROM revinfo WHERE tenant_id = app_current_tenant()))',
                        'p_tenant_isolation_' || t, t
                        );
            END LOOP;
    END
$$;
