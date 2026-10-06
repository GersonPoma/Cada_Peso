## Why

Las cuentas ya existen, pero no guardan movimientos: sin transacciones no hay saldo real, ni
gasto por categoría, ni base para la conciliación. Las transacciones son el corazón de un
presupuesto base cero y el siguiente recurso que cuelga de un presupuesto.

## What Changes

- Nueva feature `transaccion` en el backend: entidades `Transaccion` (tabla `transacciones`) y
  `SubTransaccion` (tabla `subtransacciones`), enum `EstadoTransaccion` (`NO_CONCILIADA`,
  `CONCILIADA`, `RECONCILIADA`). Monto con signo en milésimas (entrada positiva, salida
  negativa, nunca 0), fecha de negocio (`LocalDate`, se permiten futuras), categoría opcional,
  beneficiario y memo como texto libre, `aprobada`, y división (split) en subtransacciones.
- Endpoints bajo `/api/v1/presupuestos/{presupuestoId}/transacciones`: crear, listar paginado
  con filtros (`cuentaId`, `categoriaId`, `desde`, `hasta`, `estado`, `soloSinAprobar`, `q`),
  detalle, editar, borrar, `aprobar`, cambiar `estado`, `mover-cuenta`, `duplicar`, operación
  en `lote` (CATEGORIZAR, APROBAR, BORRAR; atómica) y `saldos` por cuenta.
- Reglas: cuenta y categorías del presupuesto de la URL (si no, 404; una categoría oculta sí se
  puede usar); cuenta cerrada no admite crear ni editar (422); split de 2 a 20 partes cuya suma
  es exactamente el monto y sin categoría propia; una `RECONCILIADA` no se edita, mueve, borra
  ni cambia de estado; el estado manual solo alterna `NO_CONCILIADA` y `CONCILIADA`.
- `comun`: nuevo record `PaginaResponse<T>` con `desde(Page, mapeo)`, y un manejador en
  `ManejadorGlobalExcepciones` para parámetros de consulta con tipo inválido
  (`MethodArgumentTypeMismatchException`, p. ej. `estado=XYZ`) que responde 400
  `DATOS_INVALIDOS` en lugar de un 500.
- `AGENTS.md`: `transaccion/` en el árbol, en la alternancia del `grep` de `comun/`, en la regla
  de dependencias, y la convención de paginación (`page` desde 0, `size` 20 por defecto, máx.
  100, nunca devolver `Page` de Spring).
- Sin cambios incompatibles en endpoints existentes.

## Capabilities

### New Capabilities
- `transacciones`: movimientos de dinero de una cuenta (alta, consulta paginada y filtrada,
  edición, borrado, división, aprobación, estado, cambio de cuenta, duplicado, operaciones en
  lote y saldos por cuenta), con aislamiento entre personas y presupuestos.

### Modified Capabilities
<!-- Ninguna: los requisitos de cuentas, categorias y presupuestos no cambian. -->

## Impact

- Código: `com.presupuesto.transaccion.*` (nuevo), `com.presupuesto.comun.paginacion.PaginaResponse`
  (nuevo), `ManejadorGlobalExcepciones` (un handler nuevo), `AGENTS.md`.
- Base de datos: tablas `transacciones` y `subtransacciones` creadas por Hibernate
  (`ddl-auto=update`), con índices por presupuesto/fecha.
- Dependencias: `transaccion` → `presupuesto`, `cuenta`, `categoria` y `comun`; ninguna de
  ellas importa `transaccion`.
- Fuera de alcance: frontend, entidad `Beneficiario` y autocompletado, transferencias entre
  cuentas, conciliación (el paso a `RECONCILIADA`), transacciones programadas, importación,
  borrado de cuentas o categorías con reasignación, "Ready to Assign" y asignación mensual.
