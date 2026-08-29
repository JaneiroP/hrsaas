-- ============================================================================
-- Row Level Security para org (AD-02) — mismo patrón que V2__platform_rls.sql,
-- reutilizando app_current_tenant() en vez de redefinirla.
-- ============================================================================

DO $$
    DECLARE
        t TEXT;
    BEGIN
        FOREACH t IN ARRAY ARRAY[
            'companies',
            'locations',
            'departments',
            'pay_grades',
            'positions',
            'cost_centers'
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
