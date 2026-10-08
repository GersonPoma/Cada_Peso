## Context

Motivación y alcance: ver `proposal.md`; comportamiento exigido: `specs/reportes/spec.md`. Estado
actual relevante (verificado en el código):

- **Actividad del mes.** `ActividadMensualRepository` ya suma, por categoría y mes y acotado a
  cuentas `enPresupuesto` (abiertas y cerradas, tarjetas incluidas): `actividadSimple`
  (transacciones con categoría) y `actividadDividida` (subtransacciones con categoría). Ambas
  devuelven **todos los meses hasta una fecha**, agrupados; no tienen cota inferior.
- **La reserva de pago de tarjeta no está en esas consultas.** Se agrega después, en
  `CalculadoraMes.agregarPagosDeTarjetas`, a la actividad de la categoría `Pago: <tarjeta>`
  (`CalculoPagoTarjeta`). Por eso la actividad "cruda" de las dos consultas ya cuenta el gasto
  de tarjeta **solo** en su categoría real, y la categoría de pago no tiene gasto propio.
- **Ingreso.** `ingresosSinCategoria` suma, acumulado hasta una fecha, las entradas sin
  categoría y sin subtransacciones de cuentas del presupuesto que no son tarjeta, salvo la
  entrada de una transferencia cuya pata par está en el presupuesto. Los saldos iniciales
  positivos entran aparte (`saldosInicialesPositivos`), sin mes.
- **Cálculo mensual.** `CalculoMensual.calcular(mes, asignado, actividad, ingresos)` es puro y
  package-private; recorre desde el primer mes con datos hasta `mes` y solo devuelve las filas
  de `mes`. `CalculadoraMes` (package-private) arma sus entradas con consultas agregadas;
  `MesPresupuestoService.calcular(presupuestoId, mes)` es su única puerta pública, **por mes**.
  Llamarlo en un bucle sería una carga completa por mes (la prohibición de N+1 del encargo).
- **Metas.** `CalculoMeta.calcular(meta, mes, fila, pospuesta)` es puro, pero la clase y su
  `Resultado` son package-private en `meta.service`. `MetaPospuestaRepository` solo sabe
  consultar las pospuestas de **un** mes.
- **Saldos.** `SaldoCuentaService` suma `saldoInicial + sum(monto)` de todas las transacciones
  de la cuenta, de cualquier estado y fecha. Las cuentas no tienen fecha de apertura y no se
  borran, solo se cierran. `TipoCuenta` es `CORRIENTE`, `AHORRO`, `EFECTIVO`, `TARJETA_CREDITO`,
  `INVERSION`, `PRESTAMO`; las dos últimas de deuda (`admiteSaldoNegativo`) son la tarjeta y el
  préstamo.
- **Orden de errores y parámetros como texto.** `ConciliacionController` recibe `LocalDate` y
  `Long` tipados, así que un valor mal formado lo rechaza Spring con `400` **antes** del
  `404` del service. La importación recibe los parámetros como texto y los valida el service,
  que es lo que exige este encargo.
- Las propiedades propias se registran con `@EnableConfigurationProperties` en el service que
  las usa (precedente: `ImportacionProperties`). `MesParametro.interpretar` ya valida
  `yyyy-MM` con año 2000–2100 y lanza `DatosInvalidosException`.

## Goals / Non-Goals

**Goals:** cinco reportes de solo lectura, coherentes al peso con el presupuesto mensual, con un
número fijo de consultas por reporte y errores en el orden pedido; reglas de contabilización
explícitas.

**Non-Goals:** frontend, exportación, caché, histórico de metas (la meta vigente se aplica a
todos los meses), reportes por beneficiario, tablas o columnas nuevas, y reimplementar
disponible, arrastre o sobregasto.

## Decisions

### D1. Paquete `reporte` y dependencias

`reporte` puede importar `presupuesto`, `cuenta`, `categoria`, `transaccion`, `asignacion`,
`meta` (solo el reporte 5) y `comun`; **nadie importa `reporte`**. El paquete es singular como
`meta` o `cuenta`; la URL, `reportes`. Se agrega `reporte` a la alternancia del `grep` de
AGENTS.md (y `comun/` no lo importa).

| Clase | Paquete | Test (mismo paquete) |
|---|---|---|
| `ReporteController` (5 `GET`, parámetros como `String`) | `com.presupuesto.reporte.controller` | ver tests de integración abajo |
| `GastoReporteService` (reportes 1 y 2) | `com.presupuesto.reporte.service` | `GastoReporteServiceTest` (mocks: orden de errores y cantidad de consultas) |
| `PatrimonioReporteService` (reportes 3 y 4) | `...reporte.service` | `PatrimonioReporteServiceTest` |
| `MetasReporteService` (reporte 5) | `...reporte.service` | `MetasReporteServiceTest` |
| `ReporteProperties` (record `@ConfigurationProperties("reportes")`, `maxMeses`) | `...reporte.service` | `ReportePropertiesTest` (valor por defecto 60; `< 1` falla al arrancar) |
| `RangoMeses` (record `desde`/`hasta`; `interpretar(...)`; `meses()`; package-private) | `...reporte.service` | `RangoMesesTest` (puro, ver D2) |
| `Porcentaje` (`centesimas(parte, total)`; package-private) | `...reporte.service` | `PorcentajeTest` (puro) |
| `CalculoGasto` (neto por categoría y mes y filtro de rango; package-private) | `...reporte.service` | `CalculoGastoTest` (puro) |
| `CalculoSaldos` (saldo al cierre de cada mes desde `saldoInicial` + movimientos, incluidos los anteriores al rango; package-private) | `...reporte.service` | `CalculoSaldosTest` (puro) |
| `CalculoPatrimonio` (activos/pasivos por tipo de cuenta; package-private) | `...reporte.service` | `CalculoPatrimonioTest` (puro) |
| `CalculoCumplimiento` (porcentaje y totales de una meta en el rango; package-private) | `...reporte.service` | `CalculoCumplimientoTest` (puro) |
| `MovimientoMensualRepository` (`Repository<Transaccion, Long>`, 2 consultas + 1 proyección) | `...reporte.repository` | `MovimientoMensualRepositoryTest` |
| `GastoPorCategoriaResponse`, `GrupoGastoResponse`, `CategoriaGastoResponse`, `SinCategoriaGastoResponse` | `...reporte.dto.response` | `GastoPorCategoriaResponseTest` (solo si `desde(...)` tiene lógica) |
| `IngresosGastosResponse`, `MesIngresosGastosResponse` | `...reporte.dto.response` | `IngresosGastosResponseTest` (totales = suma de los meses) |
| `PatrimonioResponse`, `MesPatrimonioResponse` | `...reporte.dto.response` | cubierto por HTTP |
| `EvolucionSaldoResponse`, `MesSaldoResponse` | `...reporte.dto.response` | cubierto por HTTP |
| `CumplimientoMetasResponse`, `MetaCumplimientoResponse`, `MesMetaResponse` | `...reporte.dto.response` | cubierto por HTTP |
| Tests de integración HTTP (D10) | `com.presupuesto.reporte.controller` | `GastoReporteIntegracionTest`, `PatrimonioReporteIntegracionTest`, `MetasReporteIntegracionTest`, `RangoYErroresReporteIntegracionTest`, `CoincidenciaMesIntegracionTest`, `ConsultasReporteIntegracionTest` |
| `ActividadMensualRepository` + 2 consultas (D5) | `com.presupuesto.asignacion.repository` | `ActividadMensualRepositoryTest` (casos nuevos) |
| `CalculadoraMes.calcularFilas`, `MesPresupuestoService.calcularFilas` (D6) | `com.presupuesto.asignacion.service` | `CalculadoraMesTest`, `MesPresupuestoServiceTest` (casos nuevos con mocks) y `MesPresupuestoServiceRangoTest` (base de datos real: contra `calcular` mes a mes, ver D11) |
| `CalculoMeta` y `CalculoMeta.Resultado` pasan a `public` | `com.presupuesto.meta.service` | `CalculoMetaTest` (sin cambios) |
| `MetaPospuestaRepository.findPospuestasEnRango` (D6) | `com.presupuesto.meta.repository` | `MetaPospuestaRepositoryTest` (caso nuevo) |

Sin `entity` ni `evento`: no hay tablas ni eventos. Sin `dto/request`: los parámetros llegan
como texto y no hay cuerpo (ver D2). `Normalizacion` y similares no aplican.

### D2. Parámetros como texto, validados en el service

El controller recibe `desde`, `hasta` como `@RequestParam(required = false) String` y los pasa
sin tocar. Con tipos (`YearMonth`, `LocalDate`) o `@Valid`, un valor malo daría `400` antes de
que el service compruebe presupuesto y cuenta. Cada service hace, **en este orden**:
`presupuestoService.obtenerDelUsuario` (404) → `cuentaRepository.findByIdAndPresupuestoId` (404,
solo evolución) → `RangoMeses.interpretar(desde, hasta, props.maxMeses())` (400).

`RangoMeses.interpretar` usa `MesParametro.interpretar` para cada mes (mismo mensaje fijo y
mismo rango de años 2000–2100 que el presupuesto mensual), exige `desde` (y `hasta`, salvo en
metas, donde `hasta` ausente = `desde`), rechaza `desde > hasta` y
`mesesEntre(desde, hasta) + 1 > maxMeses`. Mensajes fijos en español, sin reflejar el valor.
Todo es `DatosInvalidosException` → `400 DATOS_INVALIDOS`.

`ReporteProperties.maxMeses` (por defecto 60) se declara en `application.properties` como
`reportes.max-meses=60`; un valor `< 1` falla el arranque (constructor compacto).

Alternativas descartadas: `@RequestParam YearMonth` + `@DateTimeFormat` (el `400` de Spring
iría antes que el `404`); un `HandlerInterceptor` (oculta la regla de negocio y obliga a
resolver el presupuesto dos veces).

### D3. Qué cuenta como gasto, ingreso y patrimonio

Las reglas son las del presupuesto mensual, **leídas** de las mismas consultas, no repetidas.
Todo en milésimas; ejemplos en `specs/reportes/spec.md`.

| Caso | Gasto | Ingreso | Patrimonio | Por qué |
|---|---|---|---|---|
| Gasto con categoría en cuenta del presupuesto | `-monto` en su categoría | — | baja el saldo | `actividadSimple` |
| Gasto con tarjeta (con categoría) | `-monto` en **su categoría** (Comida), no en "Pago: Visa" | — | baja el saldo de la tarjeta (más deuda) | la reserva de pago la agrega `CalculadoraMes`; las consultas crudas no la tienen |
| Pago de tarjeta (transferencia Corriente → Visa) | **no** cuenta | **no** cuenta | neutro (baja Corriente, sube Visa) | ambas patas en el presupuesto: sin categoría, y `not exists` par en el presupuesto |
| Categoría `Pago: <tarjeta>` / grupo de pagos | nunca tiene gasto propio | — | — | no admite transacciones (regla citada abajo) y la reserva no está en las consultas crudas: no hay doble conteo |
| Transferencia entre cuentas del presupuesto | no cuenta | no cuenta | neutro | ídem |
| Transferencia presupuesto → cuenta fuera (categoría obligatoria) | `-monto` en esa categoría | — | pasa de una cuenta a otra | igual que el mes (`actividadSimple`) |
| Entrada desde cuenta fuera del presupuesto sin categoría | — | sí | sube el saldo | igual que el mes (`ingresosSinCategoria`) |
| Transacción dividida | cada parte en su categoría | — | el total baja el saldo | `actividadDividida`; la transacción madre no tiene categoría ni cuenta como "sin categoría" |
| Parte de una división sin categoría | "Sin categoría" | — | — | subtransacción con `categoria` nula |
| Sin categoría negativa (no transferencia interna) | "Sin categoría" | — | baja el saldo | los gastos sin categoría no afectan ningún `disponible`, pero **sí** son gasto |
| Sin categoría positiva, cuenta no tarjeta | — | sí | sube el saldo | `ingresosSinCategoria` |
| Sin categoría positiva en tarjeta, o parte de división sin categoría positiva | no cuenta | no cuenta | — | como el mes: ni ingreso ni gasto; queda documentado |
| Reembolso con categoría | resta del gasto de su categoría (puede quedar negativo) | — | sube el saldo | actividad neta |
| Cuenta cerrada del presupuesto | cuenta | cuenta | cuenta | el mes ya las incluye |
| Cuenta fuera del presupuesto | **no** cuenta | **no** cuenta | **sí** cuenta (activo o pasivo según tipo) | seguimiento: no es plata del presupuesto, pero sí del patrimonio |
| Saldo inicial | — | **no** es ingreso de ningún mes del reporte | parte del saldo desde el primer mes | no tiene fecha |

**Ejemplo de octubre** (el de la spec): ingresos `500000`; Comida `80000 + 40000 + 30000 - 10000
= 140000`, Hogar `20000`, Metas de ahorro `100000`, "Sin categoría" `5000`; gasto total `265000`; neto
`235000`. Porcentajes (centésimas de punto): Comida `5283` (`140000 × 10000 / 265000 =
5283,02`), Metas de ahorro `3774`, Hogar `755`, "Sin categoría" `189`; suman `10001`, no `10000`: el
redondeo por fila no se compensa y se documenta.

**Patrimonio** (`activos` = saldos de `CORRIENTE`, `AHORRO`, `EFECTIVO`, `INVERSION`; `pasivos` =
`-(saldos de TARJETA_CREDITO y PRESTAMO)`; `patrimonio = activos - pasivos` = suma de saldos):
Corriente `1200000`, Ahorro externa `600000`, Visa `-150000`, Préstamo `-2900000` → activos
`1800000`, pasivos `3050000`, patrimonio `-1250000`. Una cuenta de activo con saldo negativo
(sobregiro) o una tarjeta con saldo a favor siguen su tipo (no se reclasifican): `activos` o
`pasivos` pueden salir negativos y `patrimonio` siempre es la suma de saldos.

**Cuentas sin fecha de apertura:** el `saldoInicial` cuenta desde el primer mes del rango (y de
cualquier mes anterior). Es lo que hace que el último mes de la evolución iguale el saldo
actual; reconstruir una fecha de apertura desde `fechaCreacion` falla con cuentas creadas hoy
con movimientos de años atrás (importación). Se documenta como limitación.

### D4. Origen de los números de cada reporte

| Reporte | Cifra | Origen (se reutiliza) | Cálculo nuevo |
|---|---|---|---|
| 1 Gasto por categoría | actividad por categoría y mes | `ActividadMensualRepository.actividadSimple` + `actividadDividida` (hasta `hasta`; se descartan en Java los meses `< desde`) | `-actividad`, suma del rango, `Porcentaje` |
| 1 | "Sin categoría" | consultas nuevas de D5 (misma regla que `ingresosSinCategoria`) | `-salidas` |
| 1 | orden y nombres de grupos y categorías | `GrupoCategoriaRepository.findByPresupuestoIdOrderByOrden`, `CategoriaRepository.findByGrupoPresupuestoIdOrderByOrden` | agrupar |
| 2 Ingresos contra gastos | gastos del mes | lo mismo que el reporte 1 (mismo componente `CalculoGasto`) | suma por mes |
| 2 | ingresos del mes | consulta nueva `sinCategoriaPorMes` (D5), misma cláusula que `ingresosSinCategoria` | por mes |
| 3 Patrimonio | movimientos por cuenta y mes | `MovimientoMensualRepository.movimientoPorCuentaYMes` | `CalculoSaldos` + `CalculoPatrimonio` |
| 3 | saldo inicial y tipo | `CuentaRepository.findByPresupuestoIdOrderByNombreNormalizado` | — |
| 4 Evolución de saldo | movimientos de la cuenta por mes | `MovimientoMensualRepository.movimientoDeCuentaPorMes` | `CalculoSaldos` |
| 4 | cuenta | `CuentaRepository.findByIdAndPresupuestoId` | — |
| 5 Metas | asignado, actividad y disponible por categoría y mes | `MesPresupuestoService.calcularFilas` → `CalculoMensual.calcular` por mes **en memoria** (D6) | — |
| 5 | necesidad, faltante, estado | `CalculoMeta.calcular` (público) | — |
| 5 | metas y orden | `MetaRepository.findDelPresupuestoEnOrdenDelArbol` | — |
| 5 | pospuestas | `MetaPospuestaRepository.findPospuestasEnRango` (nueva) | — |
| 5 | porcentaje, totales | — | `CalculoCumplimiento`, `Porcentaje` |

### D5. Dos consultas nuevas en `ActividadMensualRepository`

Las reglas de ingreso y de "sin categoría" viven en ese repositorio; las dos consultas nuevas
van **junto a `ingresosSinCategoria`**, con un comentario cruzado ("mantener en sincronía") y un
test de equivalencia, para que quien cambie la regla vea la copia. Ambas llevan cota inferior
y superior (`fecha between :desde and :hasta`) y agrupan por año y mes:

- `sinCategoriaPorMes(presupuestoId, desde, hasta)`: transacciones de cuentas del presupuesto
  con `categoria is null`, sin subtransacciones y que **no** sean la entrada de una
  transferencia con par en el presupuesto (idéntico `not exists` que `ingresosSinCategoria`);
  devuelve `ingresos = sum(monto > 0 y cuenta no tarjeta)` y `salidas = sum(monto < 0)`. Una
  transferencia con par en el presupuesto no aporta ninguna de las dos: se excluyen **ambas
  patas** (el `not exists` mira el par de cada fila, así que la pata negativa también cae).
- `salidasDivididasSinCategoria(presupuestoId, desde, hasta)`: subtransacciones con
  `categoria is null` y `monto < 0` de cuentas del presupuesto.

Test de equivalencia (en `ActividadMensualRepositoryTest`): para un conjunto con transferencias
internas, tarjetas, divisiones y cuentas fuera, la suma de `ingresos` de `sinCategoriaPorMes`
hasta M es igual a `ingresosSinCategoria(M)`.

Alternativa descartada: llamar a `ingresosSinCategoria` por cada mes (N consultas) o restar
acumulados (mes N − mes N−1: sigue siendo una consulta por mes).

### D6. Rango de meses sin N+1 para las metas

`CalculoMensual.calcular` ya filtra `<= mes` y solo necesita los mapas `asignado` y `actividad`
cargados una vez hasta `hasta`. Se refactoriza `CalculadoraMes` **sin cambiar su
comportamiento** en dos pasos:

1. `cargar(presupuestoId, finDeMes)` extrae del `calcular` actual la lectura de asignaciones,
   actividad simple y dividida y reserva de pagos de tarjeta, y devuelve un record
   `Entradas(asignado, actividad)`.
2. `calcular(presupuestoId, mes)` pasa a ser `cargar` + los ingresos + `CalculoMensual.calcular`
   (mismo resultado, mismas consultas, mismo orden).
3. Nuevo `calcularFilas(presupuestoId, desde, hasta)`: un `cargar` con `hasta`, y para cada mes
   `m` del rango `CalculoMensual.calcular(m, asignado, actividad, 0L).filas()`. Devuelve
   `Map<YearMonth, Map<Long, FilaMes>>` y **no** `ResultadoMes`, porque `listoParaAsignar` con
   ingresos en `0` sería un número falso que alguien podría usar. Como los mapas cargados
   incluyen meses posteriores a `m`, `CalculoMensual` devolvería también las categorías cuyo
   primer dato es posterior a `m` (con una fila en ceros); se descartan para que cada mes traiga
   **exactamente** las mismas filas que `calcular(m)`. Lo comprueba un test (hallazgo de la
   implementación: sin el filtro los mapas difieren aunque `fila(id)` dé lo mismo).

`MesPresupuestoService.calcularFilas` lo expone (`@Transactional(readOnly = true)`, sin validar
pertenencia, igual que `calcular`). Por eso la ecuación de `disponible` queda escrita **una
sola vez** y `disponible(m)` del reporte es idéntico al de la pantalla del mes.

Costo en memoria: `meses del rango × categorías × meses desde el primer dato`. Con 60 meses,
300 meses de historia y 200 categorías son ~3,6 millones de iteraciones de sumas: aceptable.
Si fuera un problema se extrae una pasada única de `CalculoMensual`, que es un refactor
posterior sin cambio de API (anotado en Riesgos).

`CalculoMeta` y `CalculoMeta.Resultado` pasan a `public` (con `calcular` público); es lo único
que cambia en esa clase. `MetaPospuestaRepository.findPospuestasEnRango(presupuestoId, desde,
hasta)` devuelve pares `(metaId, mes)`; el service arma un `Set<(metaId, YearMonth)>`.

### D7. Saldo por mes: una consulta y suma acumulada en Java

`MovimientoMensualRepository`:

- `movimientoPorCuentaYMes(presupuestoId, hasta)`: `cuenta, año, mes, sum(monto > 0),
  sum(monto < 0)` de **todas** las cuentas del presupuesto (cualquier tipo, abierta o cerrada,
  dentro o fuera), `fecha <= hasta`.
- `movimientoDeCuentaPorMes(cuentaId, hasta)`: lo mismo para una cuenta (usa el índice
  `ix_transacciones_cuenta_fecha`).

Se consulta **sin cota inferior**: el saldo de un mes necesita todo lo anterior. Cada fila
resume un mes de una cuenta, así que el volumen es `cuentas × meses con movimientos`, no
transacciones. `CalculoSaldos.alCierre(saldoInicial, movimientosPorMes, rango)` suma lo anterior
a `desde` en un saldo de apertura y recorre el rango acumulando; sin movimiento el mes repite
el saldo y devuelve `entradas = salidas = 0`. No se usa `SaldoCuentaService` (es total, no por
mes); el test de coincidencia compara contra él.

**Ejemplo con rango que empieza después de movimientos previos** (milésimas). Cuenta con
`saldoInicial = 100000`; noviembre de 2025 `+40000`; diciembre de 2025 `-10000`; enero de 2026
`+5000` y `-2000`; febrero de 2026 sin movimientos. Se pide `desde=2026-01`, `hasta=2026-02`:

| Mes | Cálculo | `entradas` | `salidas` | `saldo` |
|---|---|---|---|---|
| (apertura, no se devuelve) | `100000 + 40000 - 10000` | — | — | `130000` |
| 2026-01 | `130000 + 5000 - 2000` | `5000` | `-2000` | `133000` |
| 2026-02 | sin movimientos: repite | `0` | `0` | `133000` |

El saldo de apertura (`saldoInicial` más todo lo anterior a `desde`) es el punto de partida del
primer mes; si no se sumara, enero daría `103000`. Mismo criterio para cada cuenta en el
patrimonio.

Se cuentan **todos los estados** de transacción (también `NO_CONCILIADA`), como el `saldo` del
listado de saldos, no el `saldoConciliado`.

### D8. Porcentaje en enteros

`Porcentaje.centesimas(parte, total)` devuelve `round-half-up(parte × 10000 / total)` con
`BigInteger` internamente (sin `double`/`float`, sin desbordar `long`), `0` si `total <= 0`. El
signo de `parte` se respeta (una categoría con reembolso neto da porcentaje negativo). Se
redondea al entero más cercano, con el medio alejándose de cero: `1,5 → 2` y `-1,5 → -2`. En metas el porcentaje es `asignado / necesidad` con
`necesidad > 0` y sin tope (`>10000` si se asignó de más) y `null` si `necesidad == 0`; el
total del rango usa las sumas del rango, no el promedio de porcentajes mensuales.

### D9. Forma de las respuestas

Todos los montos `long` (milésimas) y los meses `String` `yyyy-MM` (como `MesPresupuestoResponse`);
`desde` y `hasta` se devuelven normalizados. Cada record de `dto/response` tiene `desde(...)`.

| Reporte | Respuesta |
|---|---|
| 1 | `{desde, hasta, total, grupos:[{grupoId, nombre, total, porcentaje, categorias:[{categoriaId, nombre, oculta, total, porcentaje}]}], sinCategoria:{total, porcentaje}}`; grupos y categorías en el orden del árbol; solo categorías con movimientos; sin el grupo de pagos |
| 2 | `{desde, hasta, ingresos, gastos, neto, meses:[{mes, ingresos, gastos, neto}]}` |
| 3 | `{desde, hasta, meses:[{mes, activos, pasivos, patrimonio}]}` |
| 4 | `{cuentaId, nombre, tipo, enPresupuesto, cerrada, saldoInicial, desde, hasta, meses:[{mes, entradas, salidas, saldo}]}` |
| 5 | `{desde, hasta, metas:[{categoriaId, nombre, oculta, tipo, monto, necesidad, asignado, gastado, porcentaje, meses:[{mes, necesidad, asignado, gastado, disponible, faltante, estado, porcentaje}]}]}` |

En el reporte 1, "con movimientos" significa que la categoría aparece en alguna consulta de
actividad del rango (aunque su neto sea `0`); el cubo "Sin categoría" siempre está.

**Las categorías de pago no necesitan filtro.** La regla ya existe y está en la spec principal
`transacciones` (requisito *Una categoría de pago de tarjeta no admite transacciones*, que vino
de `tarjetas-credito-backend`): "El sistema SHALL responder `422` con `REGLA_NEGOCIO_VIOLADA` y
no guardar nada cuando una transacción, una subtransacción, una transferencia, una edición o
una operación en lote `CATEGORIZAR` use como categoría una categoría de pago de tarjeta". Las
plantillas programadas (`transacciones-programadas`) y los ajustes de conciliación
(`conciliacion`) la rechazan igual con `422`, y la importación no pone categoría. Por tanto
`actividadSimple` y `actividadDividida` nunca devuelven una categoría de pago, y el reporte no
filtra por grupo `PAGOS_TARJETA`: el grupo de pagos no aparece porque no tiene movimientos.

### D10. Consultas por reporte

Medido en tests con las estadísticas de Hibernate (`getPrepareStatementCount()`), con el JWT
sin consultas a base de datos. Los números no dependen de meses, categorías ni cuentas.

| Reporte | Consultas | Cuáles |
|---|---|---|
| 1 Gasto por categoría | 7 | presupuesto, `actividadSimple`, `actividadDividida`, `sinCategoriaPorMes`, `salidasDivididasSinCategoria`, grupos, categorías |
| 2 Ingresos contra gastos | 5 | presupuesto, `actividadSimple`, `actividadDividida`, `sinCategoriaPorMes`, `salidasDivididasSinCategoria` (no necesita grupos ni categorías: no filtra por nombre ni por grupo) |
| 3 Patrimonio | 3 | presupuesto, cuentas, `movimientoPorCuentaYMes` |
| 4 Evolución de saldo | 3 | presupuesto, cuenta, `movimientoDeCuentaPorMes` |
| 5 Metas | 7 (10 con alguna tarjeta) | presupuesto, metas con categoría y grupo, pospuestas del rango, asignaciones, `actividadSimple`, `actividadDividida`, pagos de tarjetas (+3 de sumas de tarjeta si hay) |

`@Transactional(readOnly = true)` en todos los services. Los nombres de categoría y grupo (solo
reporte 1) salen de las listas ya cargadas; el `getGrupo().getId()` de una categoría usa el
proxy y no consulta.

### D11. Tests

- **Puros** (sin Spring): `RangoMesesTest`, `PorcentajeTest`, `CalculoGastoTest`,
  `CalculoSaldosTest`, `CalculoPatrimonioTest`, `CalculoCumplimientoTest`. Cubren todas las
  filas de D3 y los ejemplos numéricos de la spec.
- **Servicios con mocks:** orden de errores (el repositorio de cuenta no se consulta si el
  presupuesto no es del usuario; los repositorios de datos no se consultan si el rango es
  inválido).
- **`calcularFilas` contra `calcular`, con base de datos real**
  (`MesPresupuestoServiceRangoTest`, `com.presupuesto.asignacion.service`): se arma un
  historial de 8 meses con asignaciones, gastos, un sobregasto, saldo positivo que se arrastra,
  una tarjeta con categoría de pago, una división y categorías sin movimientos en algunos
  meses; se pide el rango del mes 4 al mes 8 (**empieza a mitad del historial**) y, para cada
  mes `m` del rango, `calcularFilas(...).get(m)` debe ser igual, categoría por categoría
  (incluido el conjunto de categorías presentes), a `calcular(presupuestoId, m).filas()`. El
  primer mes del rango debe traer el `disponible` que arrastra lo ocurrido antes de `desde`.
  Además, un rango que termina antes del primer dato devuelve filas vacías, igual que `calcular`.
- **Integración HTTP por flujo**, cada una con presupuestos aislados de personas distintas
  (con un helper de test propio en `reporte/controller`, como `ApoyoHttpMeta` en `meta`) y
  `RelojDePrueba` fijado:
  - `GastoReporteIntegracionTest`: el ejemplo de octubre completo (tarjeta, pago de tarjeta,
    transferencia a cuenta fuera, división, reembolso, sin categoría, cuenta cerrada, cuenta
    fuera del presupuesto), categoría oculta, rango de varios meses, mes sin datos.
  - `PatrimonioReporteIntegracionTest`: cuentas de los seis tipos, cerrada con saldo, fuera del
    presupuesto, meses sin movimientos, evolución de una cuenta (ejemplo numérico, tarjeta,
    pata de transferencia, movimientos anteriores al rango, cuenta ajena `404`).
  - `MetasReporteIntegracionTest`: meta mensual, para fecha y saldo objetivo, pospuesta,
    sobregastada, categoría de pago, sin metas, `hasta` omitido.
  - `RangoYErroresReporteIntegracionTest`: `401`, aislamiento entre usuarios, `404` antes que
    `400` en los cinco, mes mal formado, invertido, 60 y 61 meses, máximo configurable
    (`@TestPropertySource("reportes.max-meses=12")` en una clase aparte si hace falta),
    `405`, rango vacío.
  - `CoincidenciaMesIntegracionTest` (**las tres igualdades obligatorias**, contra las otras
    pantallas por HTTP): gasto por categoría de un mes = `-actividad` del presupuesto mensual;
    saldo final de la evolución = `saldo` del listado de saldos; patrimonio del mes actual =
    suma con signo de los saldos de todas las cuentas; ingresos acumulados + saldos iniciales
    positivos = ingresos de `listoParaAsignar`; metas = `/metas?mes=` mes a mes.
  - `ConsultasReporteIntegracionTest`: el recuento de D10 con 1 y con 24 meses, y la frontera
    `2026-10-31` / `2026-11-01`.

## Risks / Trade-offs

- **Regla de ingreso copiada en JPQL** → comentario cruzado y test de equivalencia contra
  `ingresosSinCategoria` (D5). Si la regla cambia en un lado, el test falla.
- **`calcularFilas` repite `CalculoMensual.calcular` por mes en memoria** → coste
  `O(meses × categorías × historia)`; acotado por `reportes.max-meses`. Si molesta, una pasada
  única dentro de `CalculoMensual` es un refactor interno, protegido por el test que compara
  `calcularFilas` con `calcular` mes a mes.
- **Refactor de `CalculadoraMes`** toca el núcleo del presupuesto → solo extrae `cargar`; los
  tests existentes (`CalculadoraMesTest`, `AsignacionIntegracionTest`, tarjetas, metas) deben
  pasar sin tocar una aserción. Es el único riesgo para código existente; por eso las metas
  son el último bloque de tareas (se puede cortar, ver abajo).
- **Meta vigente aplicada a meses pasados** → una meta creada en octubre se evalúa también para
  enero; el reporte lo documenta y no hay historial de metas (cambiaría el esquema).
- **Saldo inicial sin fecha** → antes de la primera transacción la cuenta aporta su saldo
  inicial (D3). Documentado en la spec.
- **Porcentajes que no suman 100 %** → redondeo por fila (D3); aceptado y documentado.
- **Los gastos sin categoría negativos de tarjeta cuentan, pero el mes no los reserva** → el
  reporte muestra más gasto que lo que el presupuesto de ese mes reservó en "Pago: tarjeta"
  (solo reserva gasto *con* categoría). Es el comportamiento actual del mes; se explica en la
  spec y no se corrige aquí.
- **Rango grande × historial grande** → las consultas de actividad devuelven todos los meses
  hasta `hasta` (no tienen cota inferior); el volumen es `categorías × meses`, no
  transacciones, y el recuento de consultas es constante.

## Partir en dos changes

Se recomienda **un solo change**: los cinco reportes comparten parámetros, orden de errores,
propiedad de rango, controller, estructura de paquete y la línea de AGENTS.md; partirlo duplicaría
esa base. El punto de corte natural está al final de `tasks.md`:

- **`reportes-backend`** (reportes 1–4, grupos 1 a 5 de `tasks.md`): solo agrega código nuevo y
  dos consultas junto a `ingresosSinCategoria`; no cambia el comportamiento de nada existente.
- **`reportes-metas-backend`** (reporte 5, grupo 6): el único que refactoriza `CalculadoraMes`,
  expone `CalculoMeta` y toca `MetaPospuestaRepository`.

El grupo de metas es el **último** y la suite completa se corre justo después. **Condición de
separación:** si para que pase la suite hay que modificar la aserción de algún test existente,
no se modifica: se revierte el grupo 6, el change se cierra con los reportes 1–4 y las metas
pasan a su propio change.
