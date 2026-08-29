-- ============================================================================
-- Envers (AD-07): historial de employment_contracts y salary_history.
--
-- Todo lo de este archivo esta verificado corriendo un spike real
-- (EnversSchemaSpikeTest) contra hibernate-envers:7.4.5.Final -- no
-- adivinado ni copiado del diseno original sin comprobar. El spike encontro
-- tres sorpresas reales frente a lo que se habia asumido:
--
--   1. @TenantId NO se audita automaticamente. Envers lo trata como parte
--      del contexto de conexion, no como estado persistente -- las tablas
--      _aud NO llevan columna tenant_id (una columna que Envers nunca
--      escribe seria peor que no tenerla: quedaria siempre NULL, invisible
--      bajo cualquier politica RLS de igualdad). El aislamiento de las
--      tablas _aud se resuelve en V11 via JOIN contra revinfo, que si lleva
--      tenant_id.
--
--   2. La clave primaria de revinfo NO se llama "rev": se llama "id" (el
--      nombre del campo Java heredado de RevisionMapping en Hibernate 7, sin
--      @Column que lo renombre). Las tablas _aud si tienen una columna "rev"
--      -- es su FK hacia revinfo.id, un nombre de columna distinto al de la
--      tabla padre.
--
--   3. revinfo.id NO tiene IDENTITY ni DEFAULT a nivel de Postgres: Hibernate
--      lo genera en la aplicacion via la secuencia "revinfo_seq" (incremento
--      50, el "hi/lo" por defecto de Hibernate para @GeneratedValue sin
--      estrategia explicita) e inserta el valor ya calculado -- no hace
--      falta, ni Hibernate lo espera, un DEFAULT en la columna.
-- ============================================================================

CREATE SEQUENCE revinfo_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE revinfo (
    id        INTEGER PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenants (id),
    timestamp BIGINT NOT NULL
);

CREATE TABLE employment_contracts_aud (
    id                     BIGINT      NOT NULL,
    rev                    INTEGER     NOT NULL REFERENCES revinfo (id),
    revtype                SMALLINT,
    employee_id            BIGINT,
    company_id             BIGINT,
    position_id            BIGINT,
    cost_center_id         BIGINT,
    supervisor_employee_id BIGINT,
    contract_type          VARCHAR(20),
    start_date             DATE,
    end_date               DATE,
    probation_ends_at      DATE,
    status                 VARCHAR(16),
    created_at             TIMESTAMPTZ,
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_employment_contracts_aud_id ON employment_contracts_aud (id);

CREATE TABLE salary_history_aud (
    id             BIGINT        NOT NULL,
    rev            INTEGER       NOT NULL REFERENCES revinfo (id),
    revtype        SMALLINT,
    contract_id    BIGINT,
    amount         NUMERIC(15,2),
    currency       VARCHAR(3),
    effective_from DATE,
    effective_to   DATE,
    reason         VARCHAR(20),
    PRIMARY KEY (id, rev)
);
CREATE INDEX ix_salary_history_aud_id ON salary_history_aud (id);
