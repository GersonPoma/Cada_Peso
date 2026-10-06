# Tasks

Comandos desde `backend/` con `.\mvnw.cmd` y JDK 21. Línea base: 698 tests pasando.

## 1. Entidad y respuesta de transacción

- [x] 1.1 En `com.presupuesto.transaccion.entity.Transaccion` agregar `transaccionPar`
  (`@ManyToOne(fetch = LAZY)`, columna `transaccion_par_id`, `@Setter(AccessLevel.NONE)`) y los
  métodos `enlazarCon(Transaccion)`, `desenlazar()` y `esTransferencia()`; test en
  `com.presupuesto.transaccion.entity.TransaccionTest` (enlazar, desenlazar, nulo por defecto).
  Verificar: `.\mvnw.cmd test -Dtest=TransaccionTest`.
- [x] 1.2 Agregar `transaccionParId` a `com.presupuesto.transaccion.dto.response.TransaccionResponse`
  (nulo sin par, id del par con él) y ajustar los tests que construyen el record; test en
  `com.presupuesto.transaccion.dto.response.TransaccionResponseTest`. Verificar: los tests de
  `transaccion` y `asignacion` compilan y pasan con `.\mvnw.cmd test`.

## 2. Bloqueo de patas en las rutas existentes

- [x] 2.1 Agregar `exigirNoEsTransferencia(Transaccion)` a
  `com.presupuesto.transaccion.service.TransaccionReferencias` (422 con el mensaje "Es parte de
  una transferencia; usa /transferencias") y llamarlo tras `buscar` en `actualizar`, `borrar`,
  `moverCuenta` y `duplicar` de `TransaccionService`; tests en
  `com.presupuesto.transaccion.service.TransaccionReferenciasTest` y `TransaccionServiceTest`
  (cada operación bloqueada, y `aprobar` y `cambiarEstado` siguen permitidos).
- [x] 2.2 En `TransaccionLoteService.validar` rechazar `BORRAR` y `CATEGORIZAR` si alguna
  transacción es pata, antes de cambiar nada; `APROBAR` sigue permitido; test en
  `com.presupuesto.transaccion.service.TransaccionLoteServiceTest` (todo o nada: con una pata
  entre normales no cambia ninguna). Verificar: `.\mvnw.cmd test -Dtest=TransaccionLoteServiceTest`.

## 3. Transferencias: DTO y service

- [x] 3.1 Crear `com.presupuesto.transaccion.dto.request.CrearTransferenciaRequest` (record:
  `@NotNull cuentaOrigenId`, `@NotNull cuentaDestinoId`, `@NotNull fecha`, `@NotNull @Positive
  monto`, `categoriaId`, `@Size(max = 500) memo` recortado a nulo si vacío) y
  `ActualizarTransferenciaRequest` (`fecha`, `monto`, `categoriaId`, `memo`), ambos en el mismo
  paquete; tests `CrearTransferenciaRequestTest` y `ActualizarTransferenciaRequestTest` en
  `com.presupuesto.transaccion.dto.request` (monto 0 y negativo, nulos, normalización del memo).
- [x] 3.2 Crear `com.presupuesto.transaccion.dto.response.TransferenciaResponse` (record
  `salida`, `entrada` con `desde(Transaccion, Transaccion)`); test
  `com.presupuesto.transaccion.dto.response.TransferenciaResponseTest`.
- [x] 3.3 Crear `com.presupuesto.transaccion.service.TransferenciaService` con `crear`, `obtener`,
  `actualizar` y `borrar` (cada una llama primero a `PresupuestoService.obtenerDelUsuario`),
  creando con builders, enlazando en una sola transacción de BD, desenlazando antes de borrar, y
  aplicando la regla de categoría y el orden de validación del `design.md`; agregar al
  `TransaccionRepository` (`com.presupuesto.transaccion.repository`) lo que haga falta para
  buscar una pata por id y presupuesto; test Mockito
  `com.presupuesto.transaccion.service.TransferenciaServiceTest` (las cuatro combinaciones de
  `enPresupuesto`, categoría ajena y oculta, origen igual a destino, cuenta cerrada, pata
  `RECONCILIADA`, que no cambie nada ante un fallo). Verificar:
  `.\mvnw.cmd test -Dtest=TransferenciaServiceTest`.

## 4. Controller e integración HTTP

- [x] 4.1 Crear `com.presupuesto.transaccion.controller.TransferenciaController` bajo
  `/api/v1/presupuestos/{presupuestoId}/transferencias` (`POST` 201, `GET`, `PUT`, `DELETE` 204
  en `/{transaccionId}`, con `@Valid` y `@AuthenticationPrincipal UsuarioAutenticado`); test
  `com.presupuesto.transaccion.controller.TransferenciaIntegracionTest` (`@Transactional`,
  MockMvc, comprobando estado y `codigo`): 401 sin token, 404 entre usuarios y entre presupuestos
  de la misma persona en cada operación, crear, leer por cualquiera de las dos patas, editar
  (ambas patas), borrar (ambas), 400 por monto 0/negativo y origen igual al destino, 422 por
  cuenta cerrada al crear y al editar, 422 por `RECONCILIADA` al editar y borrar sin cambios
  parciales, aprobar y cambiar estado por pata, y las rutas bloqueadas por pata (`PUT`,
  `DELETE`, mover-cuenta, duplicar, lote `BORRAR` y `CATEGORIZAR`, con `APROBAR` permitido).
  Verificar: `.\mvnw.cmd test -Dtest=TransferenciaIntegracionTest`.
- [x] 4.2 Agregar a `com.presupuesto.transaccion.controller.TransaccionIntegracionTest` el
  escenario de saldos (transferencia de `30000`: una cuenta baja, la otra sube, la suma no
  cambia) y `transaccionParId` en el detalle y el listado. Verificar:
  `.\mvnw.cmd test -Dtest=TransaccionIntegracionTest`.

## 5. Asignación

- [x] 5.1 En `com.presupuesto.asignacion.repository.ActividadMensualRepository`, cambiar
  `ingresosSinCategoria` con el `not exists` del `design.md`; tests en
  `com.presupuesto.asignacion.repository.ActividadMensualRepositoryTest` (par en cuenta del
  presupuesto excluido, par en cuenta externa incluido, transacción sin par incluida) y los
  números de la spec en `com.presupuesto.asignacion.controller.AsignacionIntegracionTest`:
  transferencia de `30000` entre cuentas del presupuesto no cambia `listoParaAsignar`; `50000`
  desde una externa sin categoría lo sube `50000`; la misma con categoría no lo sube y suma
  `50000` de actividad; `20000` a una externa con categoría resta `20000` de actividad. Verificar:
  `.\mvnw.cmd test -Dtest=ActividadMensualRepositoryTest,AsignacionIntegracionTest`.

## 6. Documentación y verificación final

- [x] 6.1 En `AGENTS.md` documentar que la transferencia son dos patas enlazadas dentro de
  `transaccion`, la regla de categoría, el bloqueo de patas en `/transacciones` y que `asignacion`
  excluye las patas entre cuentas del presupuesto; verificar releyendo la sección y que respeta
  las 100 columnas.
- [x] 6.2 Correr desde `backend/` `.\mvnw.cmd test` y mostrar el total (los 698 existentes más los
  nuevos, 0 fallos). Mostrar el resultado de los `grep` de dependencias desde `backend/src` (cero
  líneas en cada uno): `grep -rnE "import com\.presupuesto\.asignacion"` sobre
  `main/java/com/presupuesto/{presupuesto,cuenta,categoria,transaccion}` y
  `test/java/com/presupuesto/{presupuesto,cuenta,categoria,transaccion}`, y
  `grep -rnE "import com\.presupuesto\.(usuario|auth|presupuesto|cuenta|categoria|transaccion|asignacion)"`
  sobre `main/java/com/presupuesto/comun` y `test/java/com/presupuesto/comun`.
- [x] 6.3 Correr `openspec validate transferencias-backend --strict` y verificar que no reporta
  errores. No hacer commit.
