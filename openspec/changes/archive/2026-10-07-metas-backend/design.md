# Design

## Context

`asignacion` ya calcula, para un presupuesto y un mes, el `asignado`, la `actividad` y el
`disponible` de cada categoría y el `listoParaAsignar` (`CalculadoraMes` + `CalculoMensual`, hoy
package-private, igual que `MesParametro` y `MesPresupuestoService.construir`). `AsignacionService`
fija el asignado de una categoría y recalcula el mes completo. Las metas son una feature nueva
que lee ese cálculo y, en auto-asignar, escribe asignados por lote. Ver `proposal.md` para el
motivo y `specs/metas/spec.md` para el comportamiento.

## Goals / Non-Goals

**Goals:**
- Feature `meta` con la estructura obligatoria de AGENTS.md y dependencias solo hacia
  `presupuesto`, `categoria`, `asignacion` y `comun`.
- Lógica de calendario y de cálculo como funciones puras, probadas sin base de datos.
- Auto-asignar atómico, con simulación, sin recalcular el mes por cada categoría.

**Non-Goals:**
- Frontend, metas repetidas cada año, notificaciones, límite por `listoParaAsignar`, deshacer,
  categorías de pago de tarjeta y objetivos compartidos.
- Cambiar el contrato HTTP o las specs de `asignacion` y `categoria`.

## Decisions

**1. Modelo.** `Meta` (tabla `metas`): `categoria` (`@ManyToOne(LAZY, optional = false)`, columna
`categoria_id`, restricción única `uk_metas_categoria`), `tipo` (`@Enumerated(STRING)`), `monto`
(`long`), `frecuencia`, `diaSemana` (`Integer`), `intervaloDias` (`Integer`), `fechaInicio` y
`fechaObjetivo` (`LocalDate`), todos nulos según el tipo. Sin setters (`@Setter(AccessLevel.NONE)`);
solo cambia con `reemplazar(tipo, monto, frecuencia, diaSemana, intervaloDias, fechaInicio,
fechaObjetivo)`. `MetaPospuesta` (tabla `metas_pospuestas`): `meta` (LAZY) y `mes` (`LocalDate`,
siempre día 1, validado en `@PrePersist`/`@PreUpdate` como `AsignacionMensual`), restricción
única `uk_metas_pospuestas_meta_mes`. Borrar una meta borra antes sus pospuestas
(`deleteByMetaId`, `flush`, luego `delete(meta)`): no hay cascada para que el orden sea explícito.
Una categoría oculta puede tener meta porque la meta cuelga de la categoría, no de su visibilidad.

**2. Validación en el request, no solo en el service.** `GuardarMetaRequest` es un record con
`@NotNull tipo`, `@NotNull @Positive monto`, `@Min(1) @Max(7) diaSemana`,
`@Min(2) @Max(365) intervaloDias` y la anotación de clase `@MetaCoherente`
(`meta/validacion`), cuyo validador agrega cada violación en el campo concreto
(`addPropertyNode("frecuencia")`, etc.) para que `errores` lo nombre. Antes de validar, el
constructor compacto **descarta** los campos que no aplican al tipo (los pone en `null`), según
`Normalizacion` de `categoria`: así un `diaSemana: 9` sobre un `SALDO_OBJETIVO` no es error.
Las cadenas desconocidas de `tipo` o `frecuencia` las cubre el 400 de cuerpo ilegible. El
service usa `DatosInvalidosException` solo para lo que no ve Bean Validation (mes, lista vacía de
`categoriaIds`).

**3. Orden de errores.** Bean Validation del cuerpo (400) → `obtenerDelUsuario` (404) → mes
(`MesParametro.interpretar`, 400) → categoría del presupuesto (404) → meta existente (404) →
`categoriaIds` (404, antes de escribir). `MesParametro` pasa a `public` (clase y `interpretar`),
sin duplicar la regex; sus tests existentes siguen valiendo.

**4. Interfaz pública mínima de `asignacion`.** Cambios internos, sin tocar HTTP ni specs:
- `CalculoMensual` y sus records `FilaMes` y `ResultadoMes` pasan a `public`; `ResultadoMes`
  ya expone `listoParaAsignar()` y `fila(categoriaId)`.
- `MesPresupuestoService.calcular(Long presupuestoId, YearMonth mes)` (público, sin validar
  pertenencia: lo llama quien ya la validó) devuelve el `ResultadoMes` de `CalculadoraMes`.
- `AsignacionService.fijarAsignados(Long presupuestoId, YearMonth mes, Map<Long, Long> asignados)`
  (público, `@Transactional`): resuelve **todas** las categorías en el presupuesto
  (`RecursoNoEncontradoException` si alguna no está, antes de escribir), busca las filas del mes
  (`AsignacionMensualRepository.findByCategoriaIdInAndMes`), las fija o crea (`fijarAsignado`,
  builder) y hace un solo `saveAllAndFlush`; la carrera de dos creaciones sigue siendo
  `ConflictoException`. No calcula el mes. Es el mismo upsert de `asignar`, por lote.
- `categoria` solo gana `CategoriaRepository.findByGrupoPresupuestoIdOrderByGrupoOrdenAscOrdenAsc`,
  `findByGrupoPresupuestoIdAndOcultaFalseOrderByGrupoOrdenAscOrdenAsc` y
  `findByGrupoPresupuestoIdAndIdInOrderByGrupoOrdenAscOrdenAsc` (orden del árbol: el orden de
  grupo y luego el de categoría; el existente `...OrderByOrden` ordena solo por categoría).

**5. Cálculo como funciones puras.** `CalendarioMeta` (package-private, sin Spring ni base):
`vencimientosSemanales(YearMonth, int diaSemana)` (cuenta los días del mes cuyo
`DayOfWeek.getValue()` coincide), `vencimientosPersonalizados(YearMonth, LocalDate inicio, int
intervalo)` (`primero` = el primer vencimiento en o después del primer día del mes: `inicio` si
`inicio >= primerDiaMes`, si no `inicio + ceil((primerDiaMes - inicio) / intervalo) × intervalo`.
Si `primero > ultimoDia` el resultado es 0 (antes de `inicio`, o un intervalo largo que salta el
mes); solo cuando `primero <= ultimoDia` se aplica `floor((ultimoDia - primero) / intervalo) + 1`;
ej. cada 30 días desde `2026-10-02`: febrero de 2027 → `primero` = 1 de marzo → 0, y diciembre de
2026 → 1 y 31 → 2), `mesesRestantes(YearMonth,
LocalDate fechaObjetivo)` (`max(1, ChronoUnit.MONTHS.between(mes, YearMonth.from(fechaObjetivo)) +
1)`) y `divisionHaciaArriba(long, long)` (solo con numerador positivo; `Math.ceilDiv`).
`CalculoMeta.calcular(Meta, YearMonth, FilaMes, boolean pospuesta)` (package-private, usa solo
`CalendarioMeta`) devuelve `Resultado(necesidad, faltante, estado)`, con
`inicial = disponible - asignado - actividad`. Fórmulas de la spec, comprobadas con los ejemplos:
B: lunes de octubre de 2026 = 5, 12, 19, 26 (4); C: `2026-10-02 + 14k` = 2, 16, 30 y noviembre 13
y 27; D: meses 2026-10 a 2026-12 = 3 → `ceil(600000 / 3) = 200000`, `ceil(100000 / 3) =
33334`, `2027-01` → 1 mes. Si `monto - inicial <= 0` la necesidad de `MONTO_PARA_FECHA` es 0.
`estado` por prioridad: `disponible < 0` → `SOBREGASTADA`; pospuesta → `POSPUESTA`; `faltante > 0`
→ `FALTA`; si no `FINANCIADA`. `inicial` nunca es negativo (es `max(0, disponible anterior)`).
`totalFaltante` suma el `faltante` de las filas incluidas (también las `SOBREGASTADA`).

**6. Servicios.** `MetaService` (guardar, obtener, borrar, listar): `PUT` hace
`findByCategoriaId(...).map(reemplazar).orElseGet(builder)` y `saveAndFlush`.
`MetaMesService` (estado del mes, posponer, reanudar): un `calcular` del mes, un `Set` de ids de
metas pospuestas del mes (consulta por presupuesto y mes) y `CalculoMeta` por meta; posponer crea
la fila solo si no existe y reanudar la borra solo si existe, ambos devuelven el elemento del mes
(`MetaMesResponse.desde(...)`). `AutoAsignarService.autoAsignar(...)`:
1. valida presupuesto y mes; resuelve las categorías objetivo en el orden del árbol (sin
   `categoriaIds`: visibles; con ellos, deduplicados, ocultas permitidas; alguna ajena → 404).
2. `calcular(M)` da el asignado actual y `listoParaAsignarAntes`; para las estrategias de
   historial, `calcular` de cada mes anterior necesario (1 o 3 cálculos; un mes anterior a
   `2000-01` cuenta 0 sin calcular).
3. nuevo asignado por categoría según la estrategia (`FALTANTE_META` usa `CalculoMeta` y las
   pospuestas del mes; `max(0, -actividad)` para los gastados; promedio con división entera
   hacia abajo de la suma de 3 meses, sin números negativos porque cada término es ≥ 0).
4. `cambios` = categorías cuyo nuevo asignado difiere del actual;
   `listoParaAsignarDespues = antes - suma(despues - antes)`.
5. si `simular` es falso y hay cambios, `AsignacionService.fijarAsignados` una sola vez, todo en
   un `@Transactional`; `aplicado = !simular`.

**7. Dependencias.** `meta` importa `presupuesto` (`PresupuestoService.obtenerDelUsuario`),
`categoria` (`Categoria`, repositorio), `asignacion` (`AsignacionService`,
`MesPresupuestoService`, `CalculoMensual`, `MesParametro`) y `comun`. Ninguna de esas features
importa `meta`. `EstadoMeta` (dto/response) y `EstrategiaAutoAsignar` (dto/request) son los
enums de la API; `TipoMeta` y `FrecuenciaMeta` persisten, por eso van en `entity`.

**8. Supuestos.** `categoriaIds: []` responde 400 (`@Size(min = 1)`), porque "todas" se pide
omitiendo el campo. `MetaResponse` no incluye los campos de auditoría. Los controllers son dos
(`MetaController` bajo `/categorias/...` y `/metas`, y `MetaMesController` bajo `/meses/{mes}`) para
no mezclar rutas de dos raíces; ambos validan primero con el service.

## Paquetes y clases

| Clase | Paquete | Test |
|---|---|---|
| `MetaController` | `com.presupuesto.meta.controller` | `...meta.controller.MetaIntegracionTest` |
| `MetaMesController` | `com.presupuesto.meta.controller` | `...meta.controller.MetaMesIntegracionTest` |
| `MetaService` | `com.presupuesto.meta.service` | `...meta.service.MetaServiceTest` |
| `MetaMesService` | `com.presupuesto.meta.service` | `...meta.service.MetaMesServiceTest` |
| `AutoAsignarService` | `com.presupuesto.meta.service` | `...meta.service.AutoAsignarServiceTest` |
| `CalendarioMeta` (puro) | `com.presupuesto.meta.service` | `...meta.service.CalendarioMetaTest` |
| `CalculoMeta` (puro) | `com.presupuesto.meta.service` | `...meta.service.CalculoMetaTest` |
| `MetaRepository` | `com.presupuesto.meta.repository` | `...meta.repository.MetaRepositoryTest` |
| `MetaPospuestaRepository` | `com.presupuesto.meta.repository` | `...meta.repository.MetaPospuestaRepositoryTest` |
| `Meta` | `com.presupuesto.meta.entity` | `...meta.entity.MetaTest` |
| `MetaPospuesta` | `com.presupuesto.meta.entity` | `...meta.entity.MetaPospuestaTest` |
| `TipoMeta`, `FrecuenciaMeta` | `com.presupuesto.meta.entity` | cubiertos por `MetaTest` |
| `GuardarMetaRequest` | `com.presupuesto.meta.dto.request` | `...dto.request.GuardarMetaRequestTest` |
| `AutoAsignarRequest` | `com.presupuesto.meta.dto.request` | `...dto.request.AutoAsignarRequestTest` |
| `EstrategiaAutoAsignar` | `com.presupuesto.meta.dto.request` | cubierto por `AutoAsignarRequestTest` |
| `MetaResponse` | `com.presupuesto.meta.dto.response` | `...dto.response.MetaResponseTest` |
| `MetaMesResponse` | `com.presupuesto.meta.dto.response` | `...dto.response.MetaMesResponseTest` |
| `MetasMesResponse` | `com.presupuesto.meta.dto.response` | `...dto.response.MetasMesResponseTest` |
| `AutoAsignarResponse` | `com.presupuesto.meta.dto.response` | `...dto.response.AutoAsignarResponseTest` |
| `CambioAsignacionResponse` | `com.presupuesto.meta.dto.response` | cubierto por `AutoAsignarResponseTest` |
| `EstadoMeta` | `com.presupuesto.meta.dto.response` | cubierto por `CalculoMetaTest` |
| `MetaCoherente`, `MetaCoherenteValidator` | `com.presupuesto.meta.validacion` | `...meta.validacion.MetaCoherenteValidatorTest` |
| `MesParametro` (visibilidad) | `com.presupuesto.asignacion.service` | `MesParametroTest` (existente) |
| `CalculoMensual` (visibilidad) | `com.presupuesto.asignacion.service` | `CalculoMensualTest` (existente) |
| `MesPresupuestoService.calcular` | `com.presupuesto.asignacion.service` | `MesPresupuestoServiceTest` |
| `AsignacionService.fijarAsignados` | `com.presupuesto.asignacion.service` | `AsignacionServiceTest` |
| `AsignacionMensualRepository.findByCategoriaIdInAndMes` | `com.presupuesto.asignacion.repository` | `AsignacionMensualRepositoryTest` |
| `CategoriaRepository` (3 consultas) | `com.presupuesto.categoria.repository` | `CategoriaRepositoryTest` |

Los tests viven en el mismo subpaquete que la clase, bajo `src/test/java`. Las pruebas de
integración crean sus propios usuarios, presupuestos y categorías y solo cuentan y consultan
dentro de su presupuesto, para que pasen con datos de ejemplo en la base.

## Risks / Trade-offs

- **Costo de los cálculos de historial**: promedio llama hasta 4 veces a `calcular`, cada vez con
  tres consultas agregadas; aceptable para una acción puntual. Alternativa descartada: una
  consulta propia de `meta` sobre las tablas de `asignacion`, que duplicaría reglas.
- **Hacer públicos `CalculoMensual` y `MesParametro`** ensancha la API interna de `asignacion`;
  se acota al cálculo ya existente y no se agrega ninguna regla nueva.
- **`listoParaAsignar` negativo tras auto-asignar** es intencional (misma regla que asignar a
  mano); la respuesta lo muestra.
- **Carrera al crear la meta o una pospuesta**: la restricción única la rechaza y el service
  responde 409 `ConflictoException`; el cliente puede reintentar.
