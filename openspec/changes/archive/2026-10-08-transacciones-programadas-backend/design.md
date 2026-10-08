# Design

## Context

Hoy `TransaccionService.crear` exige un `usuarioId` (valida el presupuesto con
`obtenerDelUsuario`), y sus reglas viven en `TransaccionReferencias` (package-private): cuenta y
categoría del presupuesto (404), `exigirAbierta` (422), categoría de pago de tarjeta (422) y
beneficiario auto-creado con `vincular`. El generador corre sin usuario (arranque y tarea diaria),
así que necesita una entrada pública que reutilice esa lógica sin duplicarla. Ya existe el patrón
de trabajo por lotes tolerante a fallos en `MigracionPagosTarjeta` (`ApplicationRunner` +
`TransactionTemplate` por presupuesto). No hay `@Scheduled` ni `@EnableScheduling` todavía.
Motivación y alcance: ver `proposal.md`; requisitos: ver `specs/`.

## Goals / Non-Goals

**Goals:**
- Generación correcta, idempotente y sin duplicados aunque haya reinicios o dos instancias.
- Reutilizar la creación de `transaccion` (sin tocar el comportamiento de `crear`).
- Cálculo de fechas como función pura y testeable.

**Non-Goals:**
- Transferencias programadas, frecuencias personalizadas, subtransacciones y UI (fuera de alcance).
- Cola, reintentos con backoff o notificaciones: el error queda en `ultimoError`.
- Cambiar la cuenta o `fechaInicio` de una plantilla existente (se crea otra).

## Decisions

### D1. Modelo `TransaccionProgramada` (`transacciones_programadas`)
Campos: `presupuesto`, `cuenta` (sin setter), `fechaInicio` (sin setter), `frecuencia` (enum
`FrecuenciaProgramada`), `fechaFin` (opcional), `monto` (long, no cero), `categoria` (opcional),
`beneficiario` (texto, 100), `memo` (500), `activa`, `proximaFecha` (**nullable**: `null` =
finalizada), `ultimaOcurrenciaGenerada` (nullable) y `ultimoError` (500, nullable). Los cambios
pasan por métodos de la entidad (`editar`, `pausar`, `reanudar`, `avanzarA`, `registrarError`),
con setters bloqueados como en `Transaccion`. `ultimaOcurrenciaGenerada` es lo que permite
recalcular `proximaFecha` al cambiar la frecuencia sin depender de las transacciones.
*Alternativa descartada:* derivar "la última generada" con una consulta a `transacciones`: se
rompe si la persona borra transacciones generadas.

### D2. `CalendarioProgramado`: funciones puras
Clase final sin estado en `service` (package-private salvo lo que use el generador):
- `ocurrencia(inicio, frecuencia, n)`: diarias/semanales con `plusDays(n*paso)`; mensuales,
  trimestrales y anuales con `inicio.plusMonths(n*meses)` / `plusYears(n)`. `LocalDate.plusMonths`
  parte siempre de `inicio` y recorta al último día del mes, que es exactamente la regla pedida
  (31-ene → 28/29-feb → 31-mar); se prueba explícitamente, no se asume.
- `primeraDesde(inicio, frecuencia, fecha)`: primera ocurrencia `>= fecha` (devuelve `inicio` si
  `fecha <= inicio`). Estima `n` con la diferencia de días o de meses entre `inicio` y `fecha` y
  ajusta ±1 comparando con `ocurrencia(n)` (sin bucles largos).
- `primeraDespuesDe(inicio, frecuencia, fecha)`: igual pero `> fecha`.
Todas validan `fechaFin`: el llamador compara el resultado con `fechaFin` y guarda `null` si lo
supera. *Alternativa descartada:* encadenar `proxima = anterior + periodo` (degrada el 31).

### D3. Entrada pública en `transaccion` para crear desde una plantilla
`Transaccion` gana `programadaId` (Long, columna `programada_id`, **sin FK ni relación JPA**) y
`fechaOcurrencia` (LocalDate), ambos con builder y sin setter; `@Table` suma
`uniqueConstraints (programada_id, fecha_ocurrencia)` (los nulos no colisionan en PostgreSQL, así
que las manuales no se ven afectadas). Se evita la relación JPA para que `transaccion` no importe
`transaccionprogramada` (la dependencia solo va en un sentido). `TransaccionService` extrae el
cuerpo de `crear` a un método privado y agrega:

`public Transaccion crearProgramada(Presupuesto presupuesto, CrearTransaccionRequest request, Long programadaId, LocalDate fechaOcurrencia)`

que no valida usuario (el llamador ya validó el presupuesto), y crea con `aprobada=false`,
`NO_CONCILIADA`, `programadaId` y `fechaOcurrencia`. `crear` sigue haciendo
`obtenerDelUsuario` y delega en el mismo privado: comportamiento idéntico.
Además un `public void exigirRegistrable(Cuenta, Categoria)` (cuenta abierta, categoría no de
pago) para validar la plantilla al crearla/editarla con los mismos mensajes. `transaccionprogramada`
resuelve cuenta/categoría con `CuentaRepository.findByIdAndPresupuestoId` y
`CategoriaRepository.findByIdAndGrupoPresupuestoId` (404) antes de llamar a `exigirRegistrable`
(422), respetando el orden 404 → 422.
`TransaccionRepository` gana `existsByProgramadaIdAndFechaOcurrencia` y un
`@Modifying @Query` `desvincularProgramada(programadaId)` que pone `programada_id` y
`fecha_ocurrencia` en nulo (así borrar una plantilla conserva sus transacciones y libera la
restricción única). `TransaccionResponse` agrega `programadaId` **después de `transaccionParId`**.
Como no hay FK, **el único camino de borrado de una plantilla es `TransaccionProgramadaService`**
(que desvincula y borra en una transacción); ningún otro código ni consulta debe borrar filas de
`transacciones_programadas`, y un borrado directo en la base dejaría `programada_id` apuntando a
un id inexistente.
*Alternativa descartada:* FK `@ManyToOne` a la plantilla con `ON DELETE SET NULL`: Hibernate
`ddl-auto=update` no la expresa y `transaccion` importaría la feature nueva.

### D4. `GeneradorProgramadas`: una transacción de base por plantilla
Bean no transaccional con `generarVencidas(LocalDate hasta)` (todos los presupuestos) y
`generarVencidas(Long presupuestoId, LocalDate hasta)` (endpoint). Ambos obtienen los **ids** de
las plantillas candidatas (`activa`, `proximaFecha <= hasta`, ordenadas por id) y por cada una
ejecutan un `TransactionTemplate` (como `MigracionPagosTarjeta`):

1. Dentro de la transacción, carga la plantilla con **bloqueo pesimista**
   (`@Lock(PESSIMISTIC_WRITE)`): una segunda instancia espera y, al leer, ya ve `proximaFecha`
   avanzada, así que no hace nada. Revalida `activa` y `proximaFecha <= hasta`.
2. Recorre las ocurrencias desde `proximaFecha` hasta `hasta` o `fechaFin`, **máximo 366**. Cada
   ocurrencia se calcula con `primeraDespuesDe(inicio, frecuencia, ocurrenciaActual)` o con
   `ocurrencia(inicio, frecuencia, n)`, **nunca sumando un periodo a la fecha anterior** (así un
   31 no se degrada a 28 para siempre). Por cada una: si ya existe la transacción
   (`existsByProgramadaIdAndFechaOcurrencia`) la omite; si no, llama a `crearProgramada`.
3. Al terminar fija `ultimaOcurrenciaGenerada`, `proximaFecha` (siguiente ocurrencia, o `null`
   si supera `fechaFin`) y `ultimoError=null`. Si se llegó al tope de 366, `proximaFecha` queda
   en la **siguiente ocurrencia no generada** (aunque siga `<= hasta`) y la próxima ejecución
   continúa desde ahí.

Si algo lanza (negocio o `DataIntegrityViolationException` por la restricción única), la
transacción de esa plantilla se revierte por completo (todo o nada, sin ocurrencias a medias) y
una **segunda transacción independiente** guarda `ultimoError` (mensaje de la
`NegocioException`; para errores inesperados, un texto genérico "Error inesperado al generar") sin
tocar `proximaFecha`. Esa segunda transacción se abre con un `TransactionTemplate` propio
(`PROPAGATION_REQUIRES_NEW`) o desde un bean distinto, **nunca con una auto-invocación** de un
método `@Transactional(REQUIRES_NEW)` del mismo bean, porque el proxy no la interceptaría y
participaría de la transacción ya revertida. El error se registra con `log.error` y el bucle sigue con la siguiente.
Si **también** falla el guardado de `ultimoError` en esa transacción aparte (p. ej. base caída),
se captura, se registra con `log.error` incluyendo el **id de la plantilla** y el motivo original,
y el generador sigue con las demás plantillas; esa plantilla se reintentará en la próxima
ejecución, sin `ultimoError` visible hasta entonces.
Retorna `ResultadoGeneracion(generadas, plantillasConError)`.
*Por qué "todo o nada" por plantilla:* las validaciones dependen de la cuenta/categoría, que no
cambian entre ocurrencias de la misma ejecución, así que un fallo afecta a todas igual; revertir
evita estados parciales difíciles de explicar.
*Alternativa descartada:* una sola transacción para todo el lote: un fallo tumbaría las demás
plantillas.
La restricción única es el respaldo final si el bloqueo no basta (p. ej. otra base); el caso
"fecha ya existente" por borrado manual y reintento se resuelve con la omisión del paso 2.

### D5. Ocurrencias perdidas, pausa y tope
Al correr genera todas las vencidas hasta `hasta` con su fecha original, hasta 366 por plantilla y
ejecución; lo restante se genera en la siguiente (la tarea diaria o el endpoint). **Reanudar**
fija `proximaFecha = primeraDesde(inicio, frecuencia, max(hoy, día siguiente a
ultimaOcurrenciaGenerada))` (o `null` si supera `fechaFin`): no se generan las ocurrencias del
periodo pausado, decisión de producto para no inundar de transacciones pasadas; la persona las
puede crear a mano. Pausar solo pone `activa=false` (no toca `proximaFecha`).

### D6. Cuándo corre
- Con `habilitada=false` los beans `GeneracionAlArrancar` y `GeneracionDiaria` **no existen** en el
  contexto (se prueba).
- `GeneracionAlArrancar` (`ApplicationRunner`, package-private) llama a `generarVencidas(hoy)` con
  `LocalDate.now(clock)`; un fallo no tumba el arranque (el generador ya captura por plantilla;
  el runner además envuelve todo en try/catch con `log.error`).
- `GeneracionDiaria` con `@Scheduled(cron = "${programadas.generacion.cron:0 0 3 * * *}",
  zone = "UTC")`. La "hora configurable" es esa propiedad (cron de Spring). `@EnableScheduling`
  va en `comun/config/ProgramacionConfig` (no importa ninguna feature).
- Ambos llevan `@ConditionalOnProperty(name = "programadas.generacion.habilitada",
  havingValue = "true", matchIfMissing = true)`. El `pom.xml` agrega a Surefire
  `<systemPropertyVariables><programadas.generacion.habilitada>false</...>`, de modo que **ningún
  test arrancado con el contexto completo toca datos de otros presupuestos**; los tests del
  runner/tarea lo activan con `@SpringBootTest(properties = ...)` y reloj fijo.
- "Hoy" es UTC (regla del proyecto). A las 03:00 UTC ya es "hoy" para Bolivia (UTC-4), por lo que
  la tarea diaria genera el día correcto; y por la noche el endpoint manual puede adelantar en
  UTC: es aceptable porque las generadas nacen sin aprobar.
- Con varias instancias, todas ejecutan la tarea; el bloqueo pesimista y la restricción única
  evitan duplicados. Sin ShedLock (fuera de alcance).

### D7. API y DTO
`TransaccionProgramadaController` bajo
`/api/v1/presupuestos/{presupuestoId}/transacciones-programadas`: `POST` (201), `GET`
(`?soloActivas`, lista sin paginar ordenada por `proximaFecha` asc con nulos al final, luego id),
`GET/PUT/DELETE /{id}` (204), `POST /{id}/pausar`, `POST /{id}/reanudar`, `POST /generar`
(200, `GeneracionResponse(generadas, plantillasConError)`). Cada operación valida el presupuesto con
`obtenerDelUsuario` primero. `CrearProgramadaRequest` (record con Bean Validation y
`@MontoNoCero` reutilizado de `transaccion.validacion`, que es público; recorta texto) y
`ActualizarProgramadaRequest` (monto, categoriaId, memo, beneficiario, frecuencia, fechaFin; un
`cuentaId` o `fechaInicio` extra se ignora por Jackson). `fechaFin >= fechaInicio` se valida en el
service con `DatosInvalidosException` (400) porque en PUT depende de la `fechaInicio` guardada.
`PUT` recalcula `proximaFecha` solo si la plantilla está activa y cambia la frecuencia, o si estaba
finalizada y se amplía `fechaFin`; usa `primeraDespuesDe(inicio, freq, ultimaOcurrenciaGenerada)`
(o `inicio` si no hay ninguna). El service nunca lee la cuenta de la request en `PUT`.
`DELETE` llama a `desvincularProgramada` y luego borra la plantilla, en una transacción.

### D8. Paquetes y dependencias
`transaccionprogramada` importa `transaccion`, `cuenta`, `categoria`, `presupuesto` y `comun`; nadie
la importa (se agrega `transaccionprogramada` a la alternancia del grep de `comun/`).
`transaccion` no importa `transaccionprogramada`.

#### Paquete de cada clase nueva o modificada

| Clase | Paquete | Test (mismo paquete, en `src/test`) |
|-------|---------|-------------------------------------|
| `TransaccionProgramada` (entidad) | `com.presupuesto.transaccionprogramada.entity` | `…entity.TransaccionProgramadaTest` |
| `FrecuenciaProgramada` (enum) | `com.presupuesto.transaccionprogramada.entity` | cubierta por `CalendarioProgramadoTest` |
| `TransaccionProgramadaRepository` | `com.presupuesto.transaccionprogramada.repository` | cubierta por integración |
| `CalendarioProgramado` | `com.presupuesto.transaccionprogramada.service` | `…service.CalendarioProgramadoTest` |
| `GeneradorProgramadas` | `com.presupuesto.transaccionprogramada.service` | `…service.GeneradorProgramadasTest` |
| `TransaccionProgramadaService` | `com.presupuesto.transaccionprogramada.service` | `…service.TransaccionProgramadaServiceTest` |
| `GeneracionAlArrancar` | `com.presupuesto.transaccionprogramada.service` | `…service.GeneracionAlArrancarTest` |
| `GeneracionDiaria` | `com.presupuesto.transaccionprogramada.service` | `…service.GeneracionDiariaTest` |
| (test de contexto sin generador) | `com.presupuesto.transaccionprogramada.service` | `…service.GeneracionDeshabilitadaTest` |
| `ResultadoGeneracion` (record) | `com.presupuesto.transaccionprogramada.service` | cubierta por `GeneradorProgramadasTest` |
| `CrearProgramadaRequest` | `com.presupuesto.transaccionprogramada.dto.request` | `…dto.request.CrearProgramadaRequestTest` |
| `ActualizarProgramadaRequest` | `com.presupuesto.transaccionprogramada.dto.request` | `…dto.request.ActualizarProgramadaRequestTest` |
| `TransaccionProgramadaResponse` | `com.presupuesto.transaccionprogramada.dto.response` | `…dto.response.TransaccionProgramadaResponseTest` |
| `GeneracionResponse` | `com.presupuesto.transaccionprogramada.dto.response` | `…dto.response.GeneracionResponseTest` |
| `TransaccionProgramadaController` | `com.presupuesto.transaccionprogramada.controller` | `…controller.TransaccionProgramadaIntegracionTest` (flujos) y `…controller.TransaccionProgramadaAislamientoTest` |
| `ProgramacionConfig` (`@EnableScheduling`) | `com.presupuesto.comun.config` | cubierta por `GeneracionDiariaTest` |
| `Transaccion` (modifica) | `com.presupuesto.transaccion.entity` | `…entity` existente, sin tocar aserciones |
| `TransaccionService` (modifica) | `com.presupuesto.transaccion.service` | tests nuevos en `…service.TransaccionServiceProgramadaTest` |
| `TransaccionRepository` (modifica) | `com.presupuesto.transaccion.repository` | cubierto por `GeneradorProgramadasTest` |
| `TransaccionResponse` (modifica) | `com.presupuesto.transaccion.dto.response` | tests nuevos en `…controller.TransaccionProgramadaIntegracionTest` |

(Los tests de HTTP van en `controller`; los de `src/test` usan `RelojDePrueba` vía
`RelojDePruebaConfig`.)

## Risks / Trade-offs

- [Dos instancias simultáneas] → bloqueo pesimista por plantilla + restricción única; el perdedor
  se revierte y registra el error sin dejar datos.
- [`ddl-auto=update` y la restricción única en una tabla con datos] → todas las filas existentes
  tienen `programada_id` nulo, que PostgreSQL no considera duplicado; es seguro.
- [Plantilla con error permanente reintentada todos los días] → se registra solo un `log.error`
  por ejecución y el motivo queda visible; la persona la corrige, pausa o borra.
- [Reloj en UTC vs. fecha local del usuario] → ver D6; las generadas nacen sin aprobar.
- [Tope de 366: backlog grande tarda varios días] → se completa con el endpoint manual.
- [Aislamiento en tests] → generador deshabilitado en Surefire; los tests crean sus datos y sus
  conteos son siempre por presupuesto.
- [`TransaccionResponse` cambia de aridad] → solo se construye con `desde(...)` (verificado con
  `grep "new TransaccionResponse("`); el JSON gana un campo aditivo.

## Migration Plan

Sin datos que migrar. Al desplegar, Hibernate crea `transacciones_programadas` y agrega las dos
columnas nulables y la restricción única a `transacciones`. Rollback: el código anterior ignora
las columnas nuevas; la tabla puede quedar sin uso.

## Supuestos documentados

- Crear con `fechaInicio` pasada no genera dentro del `POST`.
- Reanudar salta las ocurrencias del periodo pausado (D5).
- `PUT` ignora `fechaInicio` y `cuentaId` si llegan en la petición (no se editan; para cambiar el
  día de pago se crea otra plantilla). El recálculo de `proximaFecha` aplica a la frecuencia.
- Una plantilla finalizada (`proximaFecha` nula) sigue visible y activa; solo deja de generar.
