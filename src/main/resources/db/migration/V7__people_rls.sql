-- ============================================================================
-- Row Level Security para people (AD-02) — mismo patrón que V2/V5.
-- ============================================================================

DO $$
    DECLARE
        t TEXT;
    BEGIN
        FOREACH t IN ARRAY ARRAY[
            'employees',
            'employee_documents',
            'dependents',
            'emergency_contacts',
            'bank_accounts'
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
