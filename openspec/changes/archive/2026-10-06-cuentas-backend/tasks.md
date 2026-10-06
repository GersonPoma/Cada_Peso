## 1. Dominio y persistencia

- [x] 1.1 Crear `TipoCuenta` (`com.presupuesto.cuenta.entity`) con `admiteSaldoNegativo()`, y su
  test `TipoCuentaTest` (`com.presupuesto.cuenta.entity`). Verificar: `.\mvnw.cmd test
  -Dtest=TipoCuentaTest` en verde (solo `TARJETA_CREDITO` y `PRESTAMO` admiten negativo).
- [x] 1.2 Crear `Cuenta` (`com.presupuesto.cuenta.entity`, extiende `EntidadBase`, setters
  bloqueados con `@Setter(AccessLevel.NONE)` en nombre, nombreNormalizado, tipo, enPresupuesto,
  saldoInicial y cerrada; métodos `renombrar`, `cambiarTipo(TipoCuenta)`, `cerrar`, `reabrir` y
  `normalizar`) y su test `CuentaTest` (`com.presupuesto.cuenta.entity`). Verificar: el test
  comprueba que `renombrar` mantiene `nombreNormalizado`, que `cambiarTipo` cambia el tipo y que
  `cerrar`/`reabrir` son idempotentes; `Cuenta` no expone `setTipo` (comprobado por reflexión
  en el test).
- [x] 1.3 Crear `CuentaRepository` (`com.presupuesto.cuenta.repository`) con `findByIdAndPresupuestoId`,
  los dos listados ordenados por `nombreNormalizado` y `existsBy...` (con y sin `IdNot`), y su
  test `CuentaRepositoryTest` (`com.presupuesto.cuenta.repository`, `@Transactional`). Verificar:
  el test cubre el filtro de cerradas, el acotado por presupuesto y la restricción única con
  `saveAndFlush` dentro de `assertThrows(DataIntegrityViolationException.class, ...)`; mismo
  nombre en otro presupuesto sí se guarda.
- [x] 1.4 Agregar `CUENTA_YA_EXISTE` a `CodigoError` (`com.presupuesto.comun.excepcion`).
  Verificar: compila y lo usa la tarea 3.1.

## 2. DTO

- [x] 2.1 Crear `CrearCuentaRequest` (`com.presupuesto.cuenta.dto.request`, record con
  `strip()` y valores por defecto en el constructor compacto) y su test
  `CrearCuentaRequestTest` (`com.presupuesto.cuenta.dto.request`). Verificar: nombre recortado,
  vacío y de 101 caracteres inválidos, tipo nulo inválido, `enPresupuesto` por defecto `true` y
  `saldoInicial` por defecto `0`.
- [x] 2.2 Crear `ActualizarCuentaRequest` (`com.presupuesto.cuenta.dto.request`, record con
  `nombre` y `tipo`) y su test `ActualizarCuentaRequestTest`
  (`com.presupuesto.cuenta.dto.request`). Verificar: mismas validaciones de nombre y tipo.
- [x] 2.3 Crear `CuentaResponse` (`com.presupuesto.cuenta.dto.response`, record con
  `desde(Cuenta)`) y su test `CuentaResponseTest` (`com.presupuesto.cuenta.dto.response`).
  Verificar: todos los campos mapeados desde la entidad.

## 3. Service y controller

- [x] 3.1 Crear `CuentaService` (`com.presupuesto.cuenta.service`) con `crear`, `listar`,
  `obtener`, `actualizar`, `cerrar` y `reabrir`: cada método llama primero a
  `PresupuestoService.obtenerDelUsuario`, busca por `(id, presupuestoId)`, aplica la regla de
  saldo negativo (422), la doble defensa de duplicados (409) y construye con el builder. Test
  `CuentaServiceTest` (`com.presupuesto.cuenta.service`, Mockito). Verificar: el test cubre
  404 por presupuesto y por cuenta, duplicado por consulta previa y por
  `DataIntegrityViolationException`, saldo negativo permitido y rechazado en alta y en cambio de
  tipo, e ignorar `saldoInicial`/`enPresupuesto` al editar.
- [x] 3.2 Crear `CuentaController` (`com.presupuesto.cuenta.controller`) bajo
  `/api/v1/presupuestos/{presupuestoId}/cuentas` con `POST` (201), `GET` (con `incluirCerradas`),
  `GET /{id}`, `PUT /{id}`, `POST /{id}/cerrar` y `POST /{id}/reabrir`, con `@Valid` y
  `@AuthenticationPrincipal UsuarioAutenticado`, y sin `DELETE`. Test `CuentaIntegracionTest`
  (`com.presupuesto.cuenta.controller`, MockMvc, `@Transactional`). Verificar: cubre todos los
  escenarios de `specs/cuentas/spec.md`: 401 sin token; un tipo desconocido (`OTRO`) y un tipo
  ausente afirman `400` y además el código `DATOS_INVALIDOS`; aislamiento entre usuarios con una
  aserción de `404` propia (un test o una aserción separada, no un bucle) para cada una de las
  seis operaciones (crear, listar, consultar, editar, cerrar y reabrir) contra el presupuesto de
  otra persona; cuenta de otro presupuesto del mismo usuario, duplicado sin distinguir mayúsculas, mismo nombre en
  presupuestos distintos, cerrar/reabrir idempotentes, `incluirCerradas`, saldo negativo por
  tipo, campos no editables y `405` en `DELETE`.

## 4. Documentación y verificación final

- [x] 4.1 Actualizar `AGENTS.md`: agregar `cuenta/` al árbol de paquetes y `cuenta` a la
  alternancia del `grep`. Verificar: el diff muestra ambos cambios y el `grep` de `comun/`
  (`main` y `test`) devuelve cero líneas.
- [x] 4.2 Verificar que `presupuesto` no importa `cuenta`:
  `grep -rn "com.presupuesto.cuenta" backend/src/main/java/com/presupuesto/presupuesto` devuelve
  cero líneas.
- [x] 4.3 Ejecutar la suite completa: `.\mvnw.cmd test` desde `backend/`. Verificar: los 154
  tests existentes y los nuevos pasan. No hacer commit.
