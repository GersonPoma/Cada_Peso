# Tasks

Paquetes bajo `backend/src/main/java/com/presupuesto/`; los tests van en el mismo subpaquete bajo
`backend/src/test/java/com/presupuesto/`. Línea base: los 1198 tests existentes pasan sin
modificar ninguna aserción (`.\mvnw.cmd test` desde `backend/`).

## 1. Cambios aditivos en `transaccion`

- [x] 1.1 `Transaccion` (`com.presupuesto.transaccion.entity`): agregar `programadaId` (Long) y
  `fechaOcurrencia` (LocalDate) sin setter y `uniqueConstraints (programada_id, fecha_ocurrencia)`
  en `@Table`. Verificar: `.\mvnw.cmd test` sigue en verde y la app arranca creando las columnas.
- [x] 1.2 `TransaccionResponse` (`com.presupuesto.transaccion.dto.response`): agregar `programadaId`
  después de `transaccionParId`. Verificar: `grep "new TransaccionResponse("` solo en `desde(...)`;
  los tests existentes siguen en verde sin editar aserciones.
- [x] 1.3 `TransaccionRepository` (`com.presupuesto.transaccion.repository`):
  `existsByProgramadaIdAndFechaOcurrencia` y `@Modifying desvincularProgramada(Long)`. Verificar
  con un test de repositorio/integración en `…transaccion.repository` (borra el vínculo —el borrado de plantillas solo pasa por el service, sin FK— y libera la
  restricción).
- [x] 1.4 `TransaccionService` (`com.presupuesto.transaccion.service`): extraer el cuerpo de
  `crear` a un método privado, agregar `crearProgramada(Presupuesto, CrearTransaccionRequest,
  Long, LocalDate)` (nace `aprobada=false`, `NO_CONCILIADA`) y `exigirRegistrable(Cuenta,
  Categoria)`. Test nuevo `…transaccion.service.TransaccionServiceProgramadaTest`: beneficiario
  auto-creado, categoría recordada, cuenta cerrada 422, categoría de pago 422, `crear` manual
  intacto con `programadaId` nulo.

## 2. Calendario puro

- [x] 2.1 `FrecuenciaProgramada` (`com.presupuesto.transaccionprogramada.entity`): enum con las seis
  frecuencias. Verificar: compila y lo usa `CalendarioProgramado`.
- [x] 2.2 `CalendarioProgramado` (`com.presupuesto.transaccionprogramada.service`):
  `ocurrencia`, `primeraDesde`, `primeraDespuesDe` sin estado. Test
  `…service.CalendarioProgramadoTest`: cada frecuencia; día 31, 30 y 29 mensual; febrero normal y
  bisiesto (2028); `CADA_3_MESES` desde el 30-nov; `ANUAL` desde el 29-feb-2028 (28-feb-2029, 29-feb-2032); mensual desde el 31-ene (28/29-feb,
  31-mar, 30-abr); `CADA_3_MESES` desde el 31-ene (30-abr, 31-jul); `primeraDespuesDe` semanal
  desde 2026-01-15 con fecha 2026-09-15 da 2026-09-17; `primeraDesde` /
  `primeraDespuesDe` con fecha anterior al inicio, igual a una ocurrencia y entre dos; muchos años
  de distancia (sin bucles largos).

## 3. Entidad, repositorio y gestión (CRUD)

- [x] 3.1 `TransaccionProgramada` (`com.presupuesto.transaccionprogramada.entity`) con
  `@Table(name = "transacciones_programadas")`, setters bloqueados y métodos `editar`, `pausar`,
  `reanudar`, `avanzarA`, `registrarError`. Test `…entity.TransaccionProgramadaTest` de sus
  transiciones.
- [x] 3.2 `TransaccionProgramadaRepository` (`com.presupuesto.transaccionprogramada.repository`):
  búsqueda por id y presupuesto, listado (con `soloActivas`) y consulta de ids vencidos
  (`activa`, `proximaFecha <= hasta`), y búsqueda con `PESSIMISTIC_WRITE`. Verificar con los tests
  de 3.5 y 4.2.
- [x] 3.3 `CrearProgramadaRequest` y `ActualizarProgramadaRequest`
  (`com.presupuesto.transaccionprogramada.dto.request`) con Bean Validation (`@MontoNoCero` de
  `transaccion.validacion`) y recorte de texto; `TransaccionProgramadaResponse` y
  `GeneracionResponse` (`…dto.response`) con `desde(...)`. Tests unitarios en los mismos
  subpaquetes (recorte, límites 100/500, `desde`).
- [x] 3.4 `TransaccionProgramadaService` (`…service`): crear (404 presupuesto → cuenta → categoría,
  luego 422), listar, obtener, actualizar (recalculo de `proximaFecha`, 400 si `fechaFin <
  fechaInicio`), borrar (desvincula y borra), pausar y reanudar (D5). Test
  `…service.TransaccionProgramadaServiceTest`.
- [x] 3.5 `TransaccionProgramadaController` (`com.presupuesto.transaccionprogramada.controller`)
  con las rutas de D7 salvo `/generar`. Test de integración
  `…controller.TransaccionProgramadaIntegracionTest`: creación mínima, fecha pasada sin generar,
  validaciones 400/404/422 y su orden, listado y `soloActivas`, PUT (cuenta ignorada, cambio de
  frecuencia, y en una pausada no recalcula `proximaFecha`), DELETE (204 y `programadaId` nulo en lo generado), pausar/reanudar idempotentes,
  401 sin token.

## 4. Generador

- [x] 4.1 `ResultadoGeneracion` y `GeneradorProgramadas` (`…transaccionprogramada.service`) según
  D4: una transacción por plantilla con `TransactionTemplate`, bloqueo pesimista, tope de 366,
  omisión de ocurrencias ya existentes, cada ocurrencia con `ocurrencia(inicio, frecuencia, n)` o
  `primeraDespuesDe` (nunca sumando un periodo a la fecha anterior), `proximaFecha` en la
  siguiente no generada al llegar al tope, `ultimoError` con un `TransactionTemplate`
  `REQUIRES_NEW` propio (no auto-invocación; si ese guardado falla: `log.error` con el id de la
  plantilla y se sigue con las demás), `Clock` inyectado. Verificar con 4.2.
- [x] 4.2 Test `…service.GeneradorProgramadasTest` con `RelojDePrueba`: una vencida, futura sin
  generar, perdidas con fecha original, tope de 366 con diaria y 400 días vencidos (la primera ejecución genera 366 y deja `proximaFecha`
  en la siguiente no generada; la segunda genera el resto), mensual desde el 31-ene hasta el
  30-abr (31-ene, 28/29-feb, 31-mar, 30-abr, sin degradarse), fallo al guardar `ultimoError` sin
  detener a las demás, fin
  de mes (31-ene→28-feb→31-mar), `fechaFin` y `proximaFecha` nula, pausa, idempotencia (dos
  corridas), ocurrencia ya existente, fallo de una plantilla (cuenta cerrada) sin afectar otra
  con `ultimoError`, recuperación al reabrir, la restricción única impide el duplicado (la carrera real queda como riesgo aceptado, sin test), y
  nacen `NO_CONCILIADA`/`aprobada=false`, y
  ocurrencia borrada: se genera una ocurrencia, se borra la transacción generada y se vuelve a
  correr el generador con la misma fecha y con una fecha posterior sin nueva ocurrencia; la
  ocurrencia borrada no reaparece y `proximaFecha` no retrocede. Los datos son propios y se cuenta solo por presupuesto.
- [x] 4.3 `POST /transacciones-programadas/generar` en el controller (200 con `GeneracionResponse`,
  solo ese presupuesto). Test de integración en `TransaccionProgramadaIntegracionTest` y test de
  aislamiento `…controller.TransaccionProgramadaAislamientoTest` (presupuesto/plantilla ajenos
  404, `generar` valida primero el presupuesto de la URL y, con dos presupuestos con plantillas
  vencidas, solo genera en el de la URL). Además, reanudar con ocurrencia hoy no crea la
  transacción hasta la siguiente ejecución del generador, y `PUT` ignora `fechaInicio`.

## 5. Ejecución automática y configuración

- [x] 5.1 `ProgramacionConfig` (`com.presupuesto.comun.config`) con `@EnableScheduling`;
  `GeneracionAlArrancar` y `GeneracionDiaria` (`…transaccionprogramada.service`) con
  `@ConditionalOnProperty(programadas.generacion.habilitada)` y cron
  `programadas.generacion.cron` (por defecto `0 0 3 * * *`, UTC). Documentar ambas propiedades en
  `application.properties`. Tests `…service.GeneracionAlArrancarTest` y
  `…service.GeneracionDiariaTest` (habilitado genera; el runner no tumba el arranque si falla), y
  un test de contexto `…service.GeneracionDeshabilitadaTest` que verifica que con
  `programadas.generacion.habilitada=false` no existen los beans `GeneracionAlArrancar` ni
  `GeneracionDiaria`.
- [x] 5.2 `pom.xml`: en Surefire, `systemPropertyVariables` con
  `programadas.generacion.habilitada=false`. Verificar: la suite completa no genera nada en datos
  ajenos y `.\mvnw.cmd test` conserva los 1198 tests existentes más los nuevos en verde.

## 6. Documentación y verificación final

- [x] 6.1 Actualizar `AGENTS.md`: árbol de paquetes y dependencias de `transaccionprogramada`,
  sección "Transacciones programadas" (reglas de generación, reanudar, propiedades, transferencias
  fuera de alcance) y el `grep` de `comun/` con `transaccionprogramada`. Verificar leyendo el
  archivo.
- [x] 6.2 Verificación de integración: desde `backend/src`,
  `grep -rnE "import com\.presupuesto\.(usuario|auth|presupuesto|cuenta|categoria|transaccion|transaccionprogramada|asignacion|beneficiario|meta)" main/java/com/presupuesto/comun test/java/com/presupuesto/comun`
  devuelve cero líneas; `grep -rn "transaccionprogramada" main/java --include=*.java` fuera de su
  paquete solo aparece en `comun/config` si hiciera falta (idealmente nada);
  `openspec validate transacciones-programadas-backend --strict` pasa; `git diff` no muestra
  aserciones existentes modificadas (solo archivos nuevos y adiciones).
