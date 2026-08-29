-- ============================================================================
-- Row Level Security para payroll (AD-02) — mismo patrón que el resto de la
-- suite. A diferencia de taxconfig (V18, sin RLS por diseño), estas seis
-- tablas sí llevan tenant_id porque son datos de negocio del tenant.
-- ============================================================================

DO $$
    DECLARE
        t TEXT;
    BEGIN
        FOREACH t IN ARRAY ARRAY[
            'payroll_periods',
            'payroll_runs',
            'payroll_concepts',
            'payslips',
            'payslip_lines',
            'accruals'
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
