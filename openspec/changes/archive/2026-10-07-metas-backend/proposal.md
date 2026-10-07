# Proposal

## Why

El presupuesto base cero ya permite asignar dinero mes a mes, pero no hay forma de decir cuánto
debería tener una categoría ni de saber si el mes está financiado. Hacen falta metas por
categoría, su estado de financiamiento por mes y una asignación automática antes de construir la
pantalla.

## What Changes

- Nueva capacidad **metas** (paquete `com.presupuesto.meta`): una meta por categoría, de tipo
  `MONTO_MENSUAL` (semanal, mensual o personalizada), `MONTO_PARA_FECHA` o `SALDO_OBJETIVO`.
  Una categoría oculta también puede tener meta.
- Endpoints bajo `/api/v1/presupuestos/{presupuestoId}`: `PUT|GET|DELETE
  /categorias/{categoriaId}/meta`, `GET /metas`, `GET /meses/{mes}/metas` (necesidad, asignado,
  disponible, faltante y estado por categoría, con `totalFaltante`), `POST
  /meses/{mes}/metas/{categoriaId}/posponer|reanudar` y `POST /meses/{mes}/auto-asignar` con
  cinco estrategias y modo simulación.
- Estado de cada meta en un mes: `SOBREGASTADA`, `POSPUESTA`, `FALTA` o `FINANCIADA`, con reglas
  de cálculo documentadas en `design.md` y en la spec.
- `asignacion` gana, sin cambiar su contrato HTTP ni sus specs: `AsignacionService.fijarAsignados`
  (upsert por lote), y pasan a ser públicos `MesParametro` y el cálculo del mes
  (`CalculoMensual`/`MesPresupuestoService.calcular`) para que `meta` los use.
- `categoria` gana solo consultas de repositorio ordenadas por grupo y orden.
- `AGENTS.md`: `meta/` en el árbol, en el `grep` de `comun/` y en la regla de dependencias.

## Capabilities

### New Capabilities
- `metas`: metas por categoría, estado de financiamiento por mes, posponer/reanudar y
  auto-asignar.

### Modified Capabilities

Ninguna: los cambios en `asignacion` y `categoria` son internos (visibilidad y consultas) y no
alteran requisitos.

## Impact

- Backend: paquete nuevo `com.presupuesto.meta` y tablas nuevas `metas` y `metas_pospuestas`
  (las crea Hibernate con `ddl-auto=update`).
- `asignacion` (visibilidad y un método nuevo) y `categoria` (consultas); `meta` depende de
  `presupuesto`, `categoria`, `asignacion` y `comun`; ninguna de ellas importa `meta`.
- Sin frontend. Línea base: 884 tests existentes deben seguir pasando.
- Fuera de alcance: frontend, metas repetidas cada año, notificaciones, límite de auto-asignar
  por `listoParaAsignar`, deshacer, categorías de pago de tarjeta, objetivos compartidos.
