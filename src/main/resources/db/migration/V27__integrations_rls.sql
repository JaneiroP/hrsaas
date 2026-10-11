-- ============================================================================
-- Row Level Security para integrations (AD-02) — mismo patrón que el resto de la suite.
-- ============================================================================

ALTER TABLE attendance_connectors ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_connectors FORCE ROW LEVEL SECURITY;
CREATE POLICY p_tenant_isolation_attendance_connectors ON attendance_connectors
    USING (tenant_id = app_current_tenant())
    WITH CHECK (tenant_id = app_current_tenant());
