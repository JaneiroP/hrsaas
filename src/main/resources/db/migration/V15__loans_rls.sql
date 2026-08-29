-- ============================================================================
-- Row Level Security para loans (AD-02) — mismo patrón que el resto de la suite.
-- ============================================================================

DO $$
    DECLARE
        t TEXT;
    BEGIN
        FOREACH t IN ARRAY ARRAY[
            'loan_agreements',
            'loan_installments',
            'salary_advances',
            'garnishment_orders'
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
