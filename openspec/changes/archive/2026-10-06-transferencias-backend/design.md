# Design

## Context

Hoy `Transaccion` (paquete `com.presupuesto.transaccion`) no sabe nada de transferencias, y
`TransaccionService`, `TransaccionLoteService` y `TransaccionReferencias` (package-private,
resuelve cuentas y categorías del presupuesto) concentran las reglas de las transacciones.
`ActividadMensualRepository` (`asignacion`) calcula `ingresosSinCategoria` con una consulta JPQL
sobre `Transaccion`, y `actividadSimple` ya suma por categoría solo las transacciones de cuentas
con `enPresupuesto` verdadero. Los saldos (`sumarPorCuenta`) suman todas las transacciones por
cuenta. Ver `proposal.md` para el motivo y `specs/` para el comportamiento.

## Goals / Non-Goals

**Goals:**
- Transferencia = dos filas de `transacciones` enlazadas, creadas, editadas y borradas siempre
  juntas en una sola transacción de base de datos.
- Ninguna ruta existente puede dejar una transferencia a medias.
- "Listo para asignar" y la actividad correctas sin tocar las demás reglas de `asignacion`.

**Non-Goals:**
- Frontend, monedas distintas, transferencias programadas, tarjetas de crédito, conciliación.
- Un paquete nuevo: todo vive en `transaccion` (`asignacion` solo cambia una consulta).

## Decisions

**1. Enlace por auto-referencia en `Transaccion`.** Campo `transaccionPar`
(`@ManyToOne(fetch = LAZY)`, columna `transaccion_par_id`, nullable), con `@Setter(AccessLevel.NONE)`
y los métodos `enlazarCon(Transaccion par)`, `desenlazar()` y `esTransferencia()`. Alternativa
descartada: tabla `transferencias` con dos FKs; añade una entidad y un join en cada consulta de
`asignacion` y de saldos, y obliga a tocar el listado. Con la auto-referencia el listado, los
saldos y las specs de transacciones siguen funcionando tal cual. El esquema lo crea Hibernate
(`ddl-auto=update`), la columna nueva es nullable y no requiere migración.

**2. Crear y borrar con FKs circulares.** Crear: se guardan las dos filas sin enlace
(`saveAllAndFlush`), luego `enlazarCon` en ambas y otro `saveAllAndFlush`, todo dentro del mismo
`@Transactional`. Borrar: `desenlazar()` en ambas, `flush`, y recién entonces `deleteAll`; si no,
la FK de una fila a la otra rompe el borrado. Cualquier excepción revierte todo (atomicidad).

**3. `TransferenciaService` en `transaccion/service`.** Reutiliza `TransaccionReferencias`
(cuentas y categorías del presupuesto, `exigirAbierta`), `PresupuestoService.obtenerDelUsuario` en
cada operación y `Clock` solo si hiciera falta (no se usa "hoy": la fecha viene en el request).
Crea las entidades con `Transaccion.builder()`; las respuestas se arman con
`TransferenciaResponse.desde(salida, entrada)`. Una pata se identifica por el signo: la salida es
la de `monto < 0`.

**4. Orden de validación (igual en crear y editar).** 400 por Bean Validation (campos, `monto > 0`)
→ 400 por origen igual a destino (`DatosInvalidosException` en el service, antes de consultar) →
404 por presupuesto, cuentas y categoría del presupuesto de la URL → 422 por pata `RECONCILIADA`
(solo editar y borrar), cuenta cerrada y regla de categoría. Así lo ajeno siempre da 404 antes
que cualquier 422.

**5. Regla de categoría centralizada.** Un método en `TransferenciaService` recibe las dos
cuentas y el `categoriaId` ya resuelto y decide: misma `enPresupuesto` en ambas → categoría debe
ser nula (422 si no); sale del presupuesto → obligatoria; entra → opcional. Devuelve la categoría
que se asigna a la pata de la cuenta del presupuesto; la otra pata recibe siempre `null`. Al
editar se aplica sobre las cuentas actuales de las patas (no cambian).

**6. Bloqueo de patas en las rutas existentes.** `TransaccionReferencias.exigirNoEsTransferencia`
(mensaje fijo "Es parte de una transferencia; usa /transferencias", `ReglaNegocioException`) se
llama en `TransaccionService.actualizar`, `borrar`, `moverCuenta` y `duplicar` justo después de
`buscar` (404 primero), y en `TransaccionLoteService.validar` para `BORRAR` y `CATEGORIZAR`
(antes de cambiar nada, por eso el lote sigue siendo todo o nada). `aprobar`, `cambiarEstado` y
`APROBAR` en lote no se tocan.

**7. Consulta de ingresos.** En `ingresosSinCategoria` se añade
`and not exists (select 1 from Transaccion p where p = t.transaccionPar and
p.cuenta.enPresupuesto = true)`. Se usa `not exists` y no `t.transaccionPar.cuenta...` porque la
navegación implícita genera un inner join y descartaría todas las transacciones sin par. Una
entrada desde una cuenta externa sin categoría tiene par en cuenta externa y sigue contando; la
entrada entre cuentas del presupuesto queda excluida; la salida nunca contaba (monto negativo). La
actividad no necesita cambios: la pata externa nunca lleva categoría y las cuentas externas ya
se ignoran.

**8. `transaccionParId` en `TransaccionResponse`.** Se agrega como componente del record
(después de `subtransacciones`), con `getTransaccionPar().getId()` sin inicializar el proxy
(mismo patrón que `categoriaId`). Los tests que construyen el record se ajustan.

### Clases nuevas o movidas (paquete completo)

| Clase | Paquete | Test (mismo paquete, en `src/test`) |
|-------|---------|--------------------------------------|
| `TransferenciaController` | `com.presupuesto.transaccion.controller` | `TransferenciaIntegracionTest` (`...controller`) |
| `TransferenciaService` | `com.presupuesto.transaccion.service` | `TransferenciaServiceTest` (`...service`) |
| `CrearTransferenciaRequest` | `com.presupuesto.transaccion.dto.request` | `CrearTransferenciaRequestTest` (`...dto.request`) |
| `ActualizarTransferenciaRequest` | `com.presupuesto.transaccion.dto.request` | `ActualizarTransferenciaRequestTest` (`...dto.request`) |
| `TransferenciaResponse` | `com.presupuesto.transaccion.dto.response` | `TransferenciaResponseTest` (`...dto.response`) |

Modificadas (sin moverse): `Transaccion` (`...transaccion.entity`, `TransaccionTest`),
`TransaccionResponse` (`...dto.response`, `TransaccionResponseTest`), `TransaccionService`,
`TransaccionLoteService`, `TransaccionReferencias` (`...service`, sus tests),
`TransaccionRepository` (`...repository`, `TransaccionRepositoryTest`, si se añade una consulta
para encontrar el par) y `ActividadMensualRepository` (`com.presupuesto.asignacion.repository`,
`ActividadMensualRepositoryTest`; los números de las specs, también en
`...asignacion.controller.AsignacionIntegracionTest`).

Dependencias: `transaccion` sigue sin importar `asignacion`; `asignacion` importa `transaccion`
(ya lo hace). `comun` no cambia.

## Risks / Trade-offs

- [Las patas dejan de ser editables por las rutas viejas: cambio incompatible] → Es deliberado y
  lo cubren specs y tests; el frontend usará `/transferencias`.
- [Estados distintos entre patas: una pata `RECONCILIADA` bloquea editar y borrar toda la
  transferencia] → Es la regla pedida; el mensaje del 422 lo indica.
- [Un cambio de moneda entre cuentas no está modelado] → Fuera de alcance; se rechazará cuando se
  aborde, y hoy se asume la moneda del presupuesto.
- [Pata huérfana si algo borra una sola fila fuera de la API] → Las rutas existentes la bloquean
  y el borrado de la transferencia siempre quita las dos filas.
- [Consulta `not exists` más cara en `ingresosSinCategoria`] → Usa la PK de la pata par; el
  cálculo ya agrega en base de datos sin cargar entidades.
