# Tasks

## 1. Soporte en transaccion y cuenta (aditivo)

- [x] 1.1 En `com.presupuesto.transaccion.repository.TransaccionRepository` agregar
  `sumaConciliadaDeCuenta(cuentaId, hasta)` (criterio `estado <> NO_CONCILIADA`, fecha `<=`),
  `reconciliarConciliadasHasta(cuentaId, hasta, ahora)` (`@Modifying`, fija `fechaActualizacion`)
  y consultas de `NO_CONCILIADA` por cuenta (listar acotado y contar). Verificación: tests en
  `com.presupuesto.transaccion.service.SaldoCuentaServiceConciliadoTest` y
  `TransaccionServiceConciliacionTest`.
- [x] 1.2 En `com.presupuesto.transaccion.service.SaldoCuentaService` agregar
  `saldoConciliadoAl(Cuenta, LocalDate)`. Test: `SaldoCuentaServiceConciliadoTest` (mismo
  paquete); con `9999-12-31` coincide con `listar(...).saldoConciliado`, y con la transacción
  posterior del design (D1) da 300.000.
- [x] 1.3 En `com.presupuesto.transaccion.service.TransaccionService` agregar `crearAjuste`,
  `reconciliarHasta`, `listarNoConciliadas` y `contarNoConciliadas` (design D8). Test:
  `TransaccionServiceConciliacionTest` (mismo paquete): ajuste `CONCILIADA`/aprobado/beneficiario
  fijo, categoría 404/422, el lote deja las posteriores y las `NO_CONCILIADA` intactas.
- [x] 1.4 En `com.presupuesto.cuenta.repository.CuentaRepository` agregar
  `findByIdAndPresupuestoIdParaActualizar` con `PESSIMISTIC_WRITE`. Verificación: lo ejercita
  `ConciliacionIntegracionTest`; `.\mvnw.cmd test` sigue con 1315 tests en verde.

## 2. Feature conciliacion: modelo y lógica pura

- [x] 2.1 Crear `com.presupuesto.conciliacion.entity.Conciliacion` y
  `com.presupuesto.conciliacion.repository.ConciliacionRepository` (historial máx. 50, orden
  fecha desc, id desc; sin setters). Verificación: arranca y crea `conciliaciones`; test en
  `ConciliacionIntegracionTest`.
- [x] 2.2 Crear `com.presupuesto.conciliacion.service.CalculoConciliacion` (diferencia y regla de
  categoría del ajuste de D3). Test `com.presupuesto.conciliacion.service.CalculoConciliacionTest`
  con números: 0, +10.000, -10.000, extracto negativo, tarjeta, cuenta fuera del presupuesto.
- [x] 2.3 Crear `com.presupuesto.conciliacion.dto.request.CrearConciliacionRequest`
  (`saldoExtracto`, `fecha`, `crearAjuste`, `categoriaId`) y
  `com.presupuesto.conciliacion.dto.response.ConciliacionResponse` /
  `EstadoConciliacionResponse` con `desde(...)`. Tests en los mismos paquetes
  (`CrearConciliacionRequestTest`, `ConciliacionResponseTest`, `EstadoConciliacionResponseTest`).

## 3. Servicio y controller

- [x] 3.1 Crear `com.presupuesto.conciliacion.service.ConciliacionService` con `estado`,
  `crear` (un bloqueo de cuenta, orden de errores de D7, una sola transacción) e `historial`.
  Test `com.presupuesto.conciliacion.service.ConciliacionServiceTest` (Mockito): orden de
  errores, diferencia cero, ajuste, 422 sin ajuste sin efectos, hoy desde `Clock`.
- [x] 3.2 Crear `com.presupuesto.conciliacion.controller.ConciliacionController` bajo
  `/api/v1/presupuestos/{presupuestoId}/cuentas/{cuentaId}/conciliacion` (`GET /estado` con
  parámetros opcionales validados en el service, `POST` → 201, `GET`). Sin `PUT`/`DELETE`.
  Test de integración `com.presupuesto.conciliacion.controller.ConciliacionIntegracionTest`
  con datos propios por test: diferencia cero, con ajuste, sin ajuste 422, transacción
  posterior, cuenta cerrada, tarjeta, cuenta fuera del presupuesto, aislamiento por presupuesto
  y por persona, historial, 401, y el invariante del design D3 (tabla de números).
- [x] 3.3 Test en `ConciliacionIntegracionTest` de "Transacción reconciliada de fecha
  posterior": cuenta con una `RECONCILIADA` de fecha posterior al extracto. Tanto `GET /estado`
  como `POST` dejan esa transacción fuera de `saldoConciliadoAlCorte` y de la `diferencia`; el
  `POST` no la cuenta en `cantidadReconciliadas` y la transacción sigue `RECONCILIADA` sin cambios
  (mismo estado y `fechaActualizacion`).
- [x] 3.4 Test de secuencia de idempotencia (segunda conciliación idéntica → diferencia 0, sin
  segundo ajuste) en `ConciliacionIntegracionTest`; la carrera real no tiene test automático
  (documentado en design R3).
- [x] 3.5 Test en `ConciliacionIntegracionTest`: tras crear una conciliación con ajuste, `GET
  /estado` con el mismo `saldoExtracto` y `fecha` devuelve `diferencia` 0. Otro test comprueba
  que "Ajuste de conciliación" aparece en `GET /beneficiarios` del presupuesto (design R0) y que
  una `fecha` de mañana UTC responde 400 con el reloj fijado por `RelojDePrueba`.

- [x] 3.6 Tests de ajuste en `ConciliacionIntegracionTest`, cada uno comprobando también que no
  cambia nada cuando falla (sin ajuste, estados intactos, historial vacío): categoría de pago de
  tarjeta → 422; categoría de otro presupuesto e inexistente → 404; ajuste positivo en tarjeta sin
  categoría → 422; ajuste positivo con categoría en cuenta del presupuesto → 201 y el disponible
  de la categoría sube 10.000 (vía `GET` del mes) con `listoParaAsignar` igual.
- [x] 3.7 Tests de rutas en `ConciliacionIntegracionTest`: `PUT` y `DELETE` sobre `.../conciliacion`
  → 405; sobre `.../conciliacion/{id}` → 404. Si el 404 real difiere (la app no tiene manejador
  propio para rutas inexistentes), ajustar la spec con el código que devuelve y no el test.

## 4. Verificación final

- [x] 4.1 Desde `backend/`: `.\mvnw.cmd test` en verde (los 1315 existentes más los nuevos).
- [x] 4.2 Desde `backend/src`: `grep -rnE "import com\.presupuesto\.(usuario|auth|presupuesto|cuenta|categoria|transaccion|transaccionprogramada|asignacion|beneficiario|meta|conciliacion)"`
  sobre `main/java/com/presupuesto/comun` y `test/java/com/presupuesto/comun` devuelve 0 líneas,
  y ninguna feature fuera de `conciliacion` importa `com.presupuesto.conciliacion`. Actualizar
  AGENTS.md (árbol y dependencias) con la feature nueva.
- [x] 4.3 `openspec validate conciliacion-backend --strict` sin errores y `git diff` no muestra
  aserciones existentes modificadas (solo líneas añadidas en tests y código existentes).
