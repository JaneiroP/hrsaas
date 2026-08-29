-- ============================================================================
-- Row Level Security para contracts (AD-02) — mismo patrón que V2/V5/V7.
-- ============================================================================

DO $$
    DECLARE
        t TEXT;
    BEGIN
        FOREACH t IN ARRAY ARRAY[
            'employment_contracts',
            'salary_history',
            'contract_amendments',
            'terminations'
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
