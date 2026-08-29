-- ============================================================================
-- Row Level Security para time (AD-02) — mismo patrón que el resto de la suite.
-- ============================================================================

DO $$
    DECLARE
        t TEXT;
    BEGIN
        FOREACH t IN ARRAY ARRAY[
            'holiday_calendars',
            'holidays',
            'leave_types',
            'leave_balances',
            'leave_requests',
            'work_schedules',
            'employee_work_schedules',
            'time_entries'
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
