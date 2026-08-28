-- ============================================================================
-- Catálogo global de permisos.
--
-- Convención del código: '<recurso>:<acción>'. Cubre los módulos del plan
-- aunque todavía no existan; el catálogo es barato y evita migraciones sueltas
-- cada vez que se levanta un módulo nuevo.
--
-- max_scope acota lo que un administrador de tenant puede conceder.
-- ============================================================================

INSERT INTO modules (code, name) VALUES
    ('platform',     'Plataforma'),
    ('org',          'Organización'),
    ('people',       'Personal'),
    ('contracts',    'Contratos'),
    ('time',         'Tiempo y ausencias'),
    ('payroll',      'Nómina'),
    ('loans',        'Préstamos'),
    ('assets',       'Activos'),
    ('banking',      'Banca'),
    ('compliance',   'Cumplimiento'),
    ('dataio',       'Importación y exportación'),
    ('integrations', 'Integraciones');

INSERT INTO permissions (code, module_code, description, max_scope) VALUES
    -- platform
    ('tenant:manage',        'platform',     'Configurar el tenant',                        'ALL'),
    ('role:manage',          'platform',     'Crear roles y conceder permisos',             'ALL'),
    ('user:invite',          'platform',     'Invitar usuarios al tenant',                  'ALL'),
    ('user:manage',          'platform',     'Activar, suspender y revocar usuarios',       'ALL'),
    ('audit:read',           'platform',     'Consultar el registro de auditoría',          'ALL'),

    -- org
    ('company:read',         'org',          'Ver razones sociales y estructura',           'ALL'),
    ('company:manage',       'org',          'Administrar razones sociales y estructura',   'ALL'),

    -- people
    ('employee:read',        'people',       'Ver expedientes de empleados',                'ALL'),
    ('employee:manage',      'people',       'Crear y editar expedientes',                  'ALL'),
    ('employee:read_bank',   'people',       'Ver cuentas bancarias sin enmascarar',        'ALL'),

    -- contracts
    ('contract:read',        'contracts',    'Ver contratos e histórico salarial',          'ALL'),
    ('contract:manage',      'contracts',    'Contratar, modificar y desvincular',          'ALL'),

    -- time
    ('leave:read',           'time',         'Ver ausencias y saldos',                      'ALL'),
    ('leave:request',        'time',         'Solicitar ausencias',                         'SELF'),
    ('leave:approve',        'time',         'Aprobar o rechazar solicitudes',              'COMPANY'),
    ('holiday:manage',       'time',         'Administrar calendarios de feriados',         'ALL'),

    -- payroll
    ('payroll:read',         'payroll',      'Consultar corridas de nómina',                'ALL'),
    ('payroll:run',          'payroll',      'Ejecutar el cálculo de una corrida',          'COMPANY'),
    ('payroll:approve',      'payroll',      'Aprobar una corrida calculada',               'COMPANY'),
    ('payroll:reverse',      'payroll',      'Revertir una corrida cerrada',                'COMPANY'),
    ('payslip:read',         'payroll',      'Ver volantes de pago',                        'ALL'),
    ('taxconfig:manage',     'payroll',      'Administrar tasas, escalas y topes',          'ALL'),

    -- loans
    ('loan:read',            'loans',        'Ver préstamos y avances',                     'ALL'),
    ('loan:manage',          'loans',        'Otorgar y modificar préstamos',               'COMPANY'),

    -- assets
    ('asset:read',           'assets',       'Ver activos asignados',                       'ALL'),
    ('asset:manage',         'assets',       'Asignar y recibir activos',                   'COMPANY'),

    -- banking
    ('bank_batch:read',      'banking',      'Ver lotes de pago',                           'ALL'),
    ('bank_batch:build',     'banking',      'Construir lotes de pago',                     'COMPANY'),
    ('bank_batch:approve',   'banking',      'Aprobar lotes (maker-checker)',               'COMPANY'),

    -- compliance
    ('report:generate',      'compliance',   'Generar reportes TSS, DGII e INFOTEP',        'COMPANY'),

    -- dataio
    ('data:import',          'dataio',       'Importar archivos',                           'COMPANY'),
    ('data:export',          'dataio',       'Exportar archivos',                           'COMPANY'),

    -- integrations
    ('integration:manage',   'integrations', 'Configurar conectores y API keys',            'ALL');
