## Why

Ya hay cuentas, categorías y transacciones, pero todavía no existe lo que hace "base cero" a un
presupuesto: darle un trabajo a cada peso. Falta asignar dinero a las categorías mes a mes y
calcular cuánto queda disponible en cada una y cuánto falta por asignar.

## What Changes

- Nueva feature `asignacion` en el backend: entidad `AsignacionMensual` (tabla
  `asignaciones_mensuales`) con categoría, mes (siempre el primer día) y `asignado` en milésimas
  (puede ser 0 o negativo), única por `(categoria, mes)` también en base de datos.
- Endpoints bajo `/api/v1/presupuestos/{presupuestoId}/meses/{mes}` (`mes` = `yyyy-MM`):
  - `PUT /categorias/{categoriaId}`: fija el asignado de una categoría en ese mes (crea o
    actualiza) y devuelve la fila calculada y el `listoParaAsignar` actualizado.
  - `GET`: el presupuesto del mes (`listoParaAsignar`, totales y grupos con sus categorías, cada
    una con `asignado`, `actividad`, `disponible` y `sobregastada`), con `incluirOcultas`.
  - `POST /mover-dinero`: mueve asignado entre dos categorías del mismo presupuesto, atómico.
  - No hay `DELETE`: asignar 0 equivale a quitar.
- Reglas de cálculo (actividad, disponible con arrastre del saldo positivo, sobregasto que no se
  arrastra y se descuenta del `listoParaAsignar` de los meses siguientes, e ingresos) calculadas
  con consultas agregadas en base de datos, documentadas con ejemplos numéricos en la spec y en el
  design.
- Un `mes` con formato inválido o fuera del rango admitido responde 400 `DATOS_INVALIDOS`.
- `AGENTS.md`: `asignacion/` en el árbol, en la alternancia del `grep` de `comun/` y en la regla
  de dependencias.
- Sin cambios incompatibles en endpoints existentes.

## Capabilities

### New Capabilities
- `asignacion`: asignación mensual de dinero a categorías y cálculo del presupuesto del mes
  (actividad, disponible, sobregasto y listo para asignar), con aislamiento entre personas y
  presupuestos.

### Modified Capabilities
<!-- Ninguna: los requisitos de cuentas, categorias, transacciones y presupuestos no cambian. -->

## Impact

- Código: `com.presupuesto.asignacion.*` (nuevo) y `AGENTS.md`. No se modifican clases de otras
  features.
- Base de datos: tabla `asignaciones_mensuales` creada por Hibernate (`ddl-auto=update`) con
  restricción única `(categoria_id, mes)`.
- Dependencias: `asignacion` → `presupuesto`, `cuenta`, `categoria`, `transaccion` y `comun`;
  ninguna de ellas importa `asignacion`.
- Fuera de alcance: frontend, metas (targets) y auto-asignar, tarjetas de crédito y su categoría
  de pago, transferencias, transacciones programadas, conciliación, reportes, Age of Money,
  notas por mes y deshacer.
