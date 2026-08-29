-- ============================================================================
-- costo laboral / resúmenes por centro de costo (blueprint §12 Fase 4):
-- captura el centro de costo del contrato al momento del cálculo, no una
-- referencia viva — si el contrato cambia de centro de costo después, los
-- volantes ya calculados no deben moverse de centro retroactivamente.
-- ============================================================================

ALTER TABLE payslips ADD COLUMN cost_center_id BIGINT REFERENCES cost_centers (id);
