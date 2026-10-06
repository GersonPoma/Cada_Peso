# Proposal

## Why

Hoy mover dinero entre dos cuentas obliga a crear a mano dos transacciones sueltas: nada las
vincula, se pueden editar o borrar por separado y la entrada sin categoría se cuenta como
ingreso en "listo para asignar". Hacen falta transferencias de primera clase antes de construir
su pantalla.

## What Changes

- Nueva capacidad **transferencias**: una transferencia son DOS transacciones enlazadas (salida
  negativa en la cuenta origen, entrada positiva en la destino, misma fecha, mismo valor absoluto
  y mismo memo), con endpoints `POST`, `GET /{transaccionId}`, `PUT /{transaccionId}` y
  `DELETE /{transaccionId}` bajo `/api/v1/presupuestos/{presupuestoId}/transferencias`.
- Regla de categoría según `enPresupuesto` de las dos cuentas: ambas iguales, sin categoría;
  del presupuesto a una externa, categoría obligatoria; de una externa al presupuesto,
  categoría opcional. La categoría se guarda solo en la pata de la cuenta del presupuesto.
- `Transaccion` gana un enlace opcional a su par y `TransaccionResponse` el campo
  `transaccionParId` (nulo si no es transferencia).
- Las patas siguen teniendo estado y `aprobada` propios: aprobar y cambiar estado funcionan por
  pata con los endpoints existentes.
- **BREAKING** (para quien use una pata como transacción normal): `PUT` y `DELETE` de
  `/transacciones/{id}`, `mover-cuenta`, `duplicar` y, en `/lote`, `BORRAR` y `CATEGORIZAR`
  responden `422 REGLA_NEGOCIO_VIOLADA` sobre una pata de transferencia. `APROBAR` en lote sigue
  permitido.
- "Listo para asignar" deja de contar como ingreso la pata de entrada de una transferencia cuya
  pata par está en una cuenta del presupuesto.
- Los saldos no cambian de código; se agrega un escenario que lo demuestra.
- `AGENTS.md` documenta las dos patas enlazadas y la regla de categoría.

## Capabilities

### New Capabilities
- `transferencias`: crear, consultar, editar y borrar transferencias entre dos cuentas del
  presupuesto como dos transacciones enlazadas, con su regla de categoría y de aislamiento.

### Modified Capabilities
- `transacciones`: `transaccionParId` en el detalle; operaciones bloqueadas sobre una pata de
  transferencia (editar, borrar, mover, duplicar, lote `BORRAR`/`CATEGORIZAR`); escenario de
  saldos con una transferencia.
- `asignacion`: el requisito "Listo para asignar" excluye de los ingresos las patas de
  transferencias entre cuentas del presupuesto y cuenta las que vienen de una cuenta externa.

## Impact

- Backend, paquete `com.presupuesto.transaccion`: entidad `Transaccion` (columna
  `transaccion_par_id`, creada por Hibernate `ddl-auto=update`), `TransaccionResponse`,
  `TransaccionService`, `TransaccionLoteService`, `TransaccionReferencias`, y clases nuevas
  `TransferenciaController`, `TransferenciaService` y sus DTO.
- Backend, paquete `com.presupuesto.asignacion`: consulta `ingresosSinCategoria` de
  `ActividadMensualRepository`.
- `AGENTS.md` y specs `transacciones` y `asignacion`.
- Fuera de alcance: frontend, monedas distintas, transferencias programadas, tarjetas de
  crédito y su categoría de pago, conciliación, importación, beneficiarios.
