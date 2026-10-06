## Why

Un presupuesto base cero necesita saber dónde está el dinero. Hoy cada persona tiene
presupuestos, pero no cuentas (corriente, ahorro, efectivo, tarjeta...) que los alimenten. Las
cuentas son el siguiente recurso que cuelga de un presupuesto y la base de las transacciones.

## What Changes

- Nueva feature `cuenta` en el backend: entidad `Cuenta` (tabla `cuentas`) que pertenece a un
  presupuesto, con nombre único por presupuesto sin distinguir mayúsculas, tipo, `enPresupuesto`,
  `saldoInicial` en milésimas y estado `cerrada`.
- Endpoints bajo `/api/v1/presupuestos/{presupuestoId}/cuentas`: crear, listar (completa, con
  `incluirCerradas`), detalle, editar nombre y tipo, `cerrar` y `reabrir` (idempotentes). No hay
  borrado: las cuentas se cierran.
- Nuevo `CodigoError.CUENTA_YA_EXISTE` (409).
- Reglas: `enPresupuesto` y `saldoInicial` se fijan al crear y no se editan; el saldo inicial
  negativo solo se admite en `TARJETA_CREDITO` y `PRESTAMO`.
- `AGENTS.md`: `cuenta/` en el árbol de paquetes y en la alternancia del `grep` de `comun/`.
- Sin cambios incompatibles.

## Capabilities

### New Capabilities
- `cuentas`: gestión de las cuentas financieras de un presupuesto (alta, consulta, edición
  limitada, cierre y reapertura), con aislamiento entre personas y presupuestos.

### Modified Capabilities
<!-- Ninguna: los requisitos de `presupuestos` no cambian. -->

## Impact

- Código: `com.presupuesto.cuenta.*` (nuevo), `CodigoError` (un valor nuevo), `AGENTS.md`.
- Base de datos: tabla `cuentas` creada por Hibernate (`ddl-auto=update`), con restricción única
  `(presupuesto_id, nombre_normalizado)`.
- Dependencias: `cuenta` → `presupuesto` y `comun` (el `usuarioId` llega de `UsuarioAutenticado`);
  `presupuesto` no importa `cuenta`.
- Fuera de alcance: frontend, transacciones, saldo calculado, reconciliación, transferencias,
  paginación y borrado.
