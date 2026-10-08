# Design

## Context

Estado observado en el código (ver proposal.md para la motivación):

- `CalculoMensual.calcular` es una función pura: recibe `asignado` y `actividad` por categoría y
  mes más los `ingresos`, y produce `disponible` (arrastre solo positivo) y `listoParaAsignar`.
  `CalculadoraMes` arma esas entradas con consultas agregadas de `ActividadMensualRepository`.
- Una compra con tarjeta ya es una transacción de una cuenta `enPresupuesto` con categoría, así que
  la actividad de la categoría normal ya baja; lo que falta es la reserva y el pago.
- `ingresosSinCategoria` hoy cuenta toda entrada positiva sin categoría de una cuenta del
  presupuesto cuya pata par no esté en una cuenta del presupuesto, **tarjetas incluidas**.
- `TransaccionReferencias.categoria(...)` valida que la categoría sea del presupuesto y lo usan
  crear, editar, subtransacciones, transferencias y el filtro del listado.
- Cadena de dependencias: `cuenta` es anterior a `categoria` (`categoria` puede importar `cuenta`,
  nunca al revés); `asignacion` puede importar `categoria` y `cuenta`; `meta` importa `asignacion`
  y nadie importa `meta`.
- El esquema lo gestiona Hibernate con `ddl-auto=update`, sin Flyway; el nombre de una cuenta y
  el de una categoría miden a lo sumo 100 caracteres.

## Goals / Non-Goals

**Goals:**
- Reservar automáticamente el pago de cada tarjeta en `Pago: <tarjeta>` sin tocar la regla de
  sobregasto ni la actividad de las categorías normales.
- Mantener el invariante: dinero en cuentas de presupuesto que no son tarjeta = `listoParaAsignar`
  + suma de `disponible` (categorías de pago incluidas).
- Cálculo con consultas agregadas y una función pura aislada y probable por separado.

**Non-Goals:**
- Frontend (el contrato solo gana campos aditivos).
- Metas sobre categorías de pago (no se bloquean ni se tratan distinto), intereses, fechas de
  corte o pago mínimo, y préstamos (`PRESTAMO`).
- Crear categorías de pago para tarjetas de seguimiento (`enPresupuesto` falso).

## Decisiones

### D1. Cómo se calcula la reserva (funciones puras + consultas agregadas)

Tres consultas nuevas en `ActividadMensualRepository`, todas acotadas por presupuesto, sobre
cuentas `TARJETA_CREDITO` con `enPresupuesto` verdadero (abiertas y cerradas), agrupadas por
`cuenta.id`, año y mes, sin cargar entidades:

1. `gastosTarjeta`: `sum(t.monto)` de transacciones con `categoria is not null`.
2. `gastosTarjetaDivididos`: `sum(s.monto)` de subtransacciones con `categoria is not null`.
3. `pagosATarjeta`: `sum(t.monto)` de las patas de la tarjeta con `transaccionPar` no nulo y
   `exists (select 1 from Transaccion p where p = t.transaccionPar and p.cuenta.enPresupuesto =
   true)` (mismo patrón `not exists`/`exists` que `ingresosSinCategoria`, para evitar el inner
   join implícito).

`monto` de un gasto es negativo y el de una pata que recibe un pago, positivo; la actividad de la
categoría de pago es `-(suma)` en los tres casos, lo que da gasto `+30000`, reembolso negativo y
pago `-30000` con una sola regla. Una pata de la tarjeta hacia una cuenta del presupuesto (avance
de efectivo) da `+M`, que compensa el efectivo que entra a la cuenta sin contar como ingreso.

Función pura nueva `CalculoPagoTarjeta.actividadPorCategoria(Map<cuentaId, Map<YearMonth,Long>>
sumas, Map<cuentaId, categoriaId> categoriaDePago)` → `Map<categoriaId, Map<YearMonth,Long>>`
(niega las sumas y las vuelca a la categoría de pago; ignora tarjetas sin categoría). El
resultado se **suma al mapa `actividad`** que ya recibe `CalculoMensual.calcular`, que no cambia:
el arrastre, el sobregasto y `listoParaAsignar` salen de la misma función de siempre.
`CalculadoraMes` obtiene `cuentaId → categoriaId` con una consulta nueva de
`CategoriaRepository` (`categorias con cuentaTarjeta no nula del presupuesto`).

*Alternativa descartada*: calcular la reserva en `CalculoMensual` con reglas propias. Habría
duplicado el arrastre y el sobregasto y habría arriesgado la regla de sobregasto existente.

### D2. Demostración del invariante (con números)

Sea `C` = dinero en cuentas de presupuesto que no son tarjeta, `L` = `listoParaAsignar` y `D` =
suma de `disponible` de todas las categorías en el mes pedido. `D` del mes pedido incluye el
`disponible` negativo de ese mes (el −10000 de Comida en enero cuenta en `D` de enero). Lo que no
se arrastra es el negativo hacia el mes siguiente: ese sobregasto deja de estar en `D` de febrero
y ya está descontado en `L` de febrero, de modo que `L + D` vale lo mismo en cualquier mes. Cada
operación mantiene `C = L + D`:

- **Ingreso `+I`** en cuenta no tarjeta: `C` y `L` suben `I`. ✓
- **Asignar `A`**: `L` baja `A`, `D` sube `A`. ✓
- **Gasto con tarjeta `S`** (categoría normal): `C` no cambia; la normal baja `S` en `D` y la de
  pago sube `S`: `D` no cambia. ✓
- **Reembolso `R`** con tarjeta: espejo del anterior. ✓
- **Pago `P`** desde cuenta de presupuesto: `C` baja `P`; la pata en la tarjeta es `+P`
  (`−monto` = `−P` en la categoría de pago): `D` baja `P`; no es ingreso (excluida por tener par
  en el presupuesto). ✓
- **Entrada sin categoría en la tarjeta**: no entra dinero a `C`; por eso deja de ser ingreso
  (ver D6). ✓

Con **sobregasto**: ingreso `100000`, Comida asignado `30000`, gasto `40000` con tarjeta en enero.

| Mes | `C` | `L` | Comida `disponible` | Pago `disponible` | `D` | `L + D` |
|-----|-----|-----|---------------------|-------------------|-----|---------|
| Enero | 100000 | 100000 − 30000 = 70000 | 30000 − 40000 = −10000 | 0 + 40000 = 40000 | 30000 | 100000 ✓ |
| Febrero | 100000 | 100000 − 30000 − 10000 (sobregasto de enero) = 60000 | max(0, −10000) = 0 | 40000 | 40000 | 100000 ✓ |

El sobregasto se trata como el normal: no se arrastra, baja `L` de febrero y la reserva completa
(`40000`, no `30000`) sigue en la categoría de pago. Si se pagara la tarjeta `40000` en febrero,
`C` = 60000, Pago = 0, `D` = 0, `L` = 60000 ✓.

Límite conocido: una cuenta `PRESTAMO` con saldo inicial negativo en el presupuesto resta de `C`
sin restar de los ingresos (comportamiento previo, ya en el spec "Listo para asignar"); el
invariante se declara y se prueba sin ellas.

### D3. Eventos de `cuenta` y oyente de `categoria`

`cuenta/evento` publica cuatro records (cada uno lleva la `Cuenta` ya guardada), con
`ApplicationEventPublisher` dentro de `CuentaService` tras `saveAndFlush`:
`CuentaCreadaEvento` (en `crear`), `CuentaRenombradaEvento` (en `actualizar`, solo si el nombre
cambió), `CuentaCerradaEvento` y `CuentaReabiertaEvento`. Igual que `PresupuestoCreadoEvento`:
`@EventListener` **síncrono**, nunca asíncrono ni `@TransactionalEventListener`; el oyente
`PagosTarjetaListener` (`categoria/service`) corre en la transacción del publicador y un fallo
revierte cuenta y categoría juntas. El oyente solo actúa si `tipo == TARJETA_CREDITO` y
`enPresupuesto`.

La lógica de crear/renombrar/ocultar/mostrar vive en un `@Component` `CategoriasPagoTarjeta`
(`categoria/service`), usado por el oyente **y** por la migración (una sola implementación).

**Largo del nombre**: `Cuenta.nombre` y `Categoria.nombre` admiten 100 caracteres
(`@Size(max = 100)` y `length = 100`), así que `"Pago: " + nombre` puede llegar a 106. Se recorta
por *code points* (sin partir pares sustitutos) hasta que el nombre completo mida a lo sumo 100.
El recorte puede hacer coincidir dos nombres largos y la unicidad `(grupo_id, nombre_normalizado)`
obligaría a fallar; por eso `CategoriasPagoTarjeta` busca antes si el nombre ya existe en el grupo
y, de ser así, recorta a `100 - len(" (" + id + ")")` y agrega ` (<id de la cuenta>)`. Esa
búsqueda **excluye la categoría de la misma cuenta** (`existsByGrupoIdAndNombreNormalizadoAndIdNot`
con el id de su propia categoría al renombrar; `existsByGrupoIdAndNombreNormalizado` al crear, que
aún no tiene categoría): sin la exclusión, renombrar `Visa` a `VISA` (mismo nombre normalizado)
chocaría consigo misma y agregaría un sufijo sin necesidad. Un nombre de cuenta de 100 caracteres
(al crear y al renombrar), dos nombres que coinciden tras el recorte y renombrar solo cambiando
mayúsculas (sin sufijo) tienen test en `CategoriasPagoTarjetaTest` y en
`PagosTarjetaIntegracionTest`.

*Alternativa descartada*: que `CuentaService` llame a `categoria` (invierte la cadena).

### D4. Modelo

- `GrupoCategoria.tipo` (`TipoGrupoCategoria`: `NORMAL`, `PAGOS_TARJETA`), columna
  `tipo varchar(20) not null default 'NORMAL'` con `@Column(columnDefinition = ...)` para que
  `ddl-auto=update` pueda añadirla a una tabla con filas existentes en PostgreSQL. Constructores:
  `@Builder.Default tipo = NORMAL`; sin setter.
- `Categoria.cuentaTarjeta`: `@OneToOne(fetch = LAZY)` opcional, `@JoinColumn(name =
  "cuenta_tarjeta_id", unique = true)`, sin setter (solo vía builder). `esPagoTarjeta()` =
  `cuentaTarjeta != null`. `Categoria` importa `Cuenta` (permitido: `cuenta` es anterior).
- Grupo de pagos: se **localiza siempre por `tipo = PAGOS_TARJETA`** (consulta de
  `GrupoCategoriaRepository` por `presupuestoId` y `tipo`), nunca por nombre. Si no existe, se
  crea con `orden` = último + 1 y nombre `Pagos de tarjetas de crédito`; como
  `(presupuesto_id, nombre_normalizado)` es único en `grupos_categoria`, si un grupo normal ya usa
  ese nombre (comparado con `GrupoCategoria.normalizar`) se prueba `... (2)`, `... (3)` hasta el
  primer nombre libre. Así crear la tarjeta nunca falla por el nombre y el grupo normal
  preexistente no se toca (ni se reutiliza ni se le cambia el tipo). La migración usa el mismo
  `CategoriasPagoTarjeta.asegurarGrupo`.
- Responses (siempre aditivos, mapeo manual con `desde(...)`): `CategoriaResponse` y
  `CategoriaMesResponse` ganan `esPagoTarjeta` y `cuentaId`; `GrupoCategoriaResponse` y
  `GrupoCategoriaConCategoriasResponse` ganan `tipo`. `CategoriaMesResponse` conserva un
  constructor de 7 argumentos (con `false`/`null`) para que los tests existentes que la
  construyen directamente compilen sin cambios; lo mismo no hace falta en los demás, que solo se
  construyen con `desde(...)`.
- `CodigoError` no cambia: todas las protecciones son `ReglaNegocioException` (422
  `REGLA_NEGOCIO_VIOLADA`).

### D5. Protecciones

- `GrupoCategoriaService.renombrar/ocultar/mostrar/mover` y `CategoriaService.actualizar/ocultar/
  mostrar/mover/crear` llaman a un helper que lanza `ReglaNegocioException` con mensaje fijo
  cuando el grupo es `PAGOS_TARJETA` o la categoría es de pago. El oyente y la migración usan los
  repositorios directamente, así que no chocan con la protección.
- Mover un grupo normal renumera todos los grupos (ocultos incluidos), grupo de pagos incluido;
  eso sí se permite (solo se bloquea mover el grupo de pagos mismo).
- **Todas las vías que asignan categoría a una transacción pasan por
  `TransaccionReferencias.categoriaParaRegistrar(categoriaId, presupuestoId)`**, que llama a
  `categoria(...)` y lanza `ReglaNegocioException` (422) si es de pago. Recorrido completo del
  código actual:

  | Vía | Clase y método | Cómo llega a `categoriaParaRegistrar` |
  |-----|----------------|----------------------------------------|
  | Crear | `TransaccionService.crear` | `referencias.dividir(...)` |
  | Editar | `TransaccionService.actualizar` | `referencias.dividir(...)` |
  | Subtransacciones (dividir) | `TransaccionReferencias.dividir` | cada parte con categoría |
  | Transferencia (crear y editar) | `TransferenciaService.crear/actualizar` | directo, en lugar de `categoria(...)` |
  | Lote `CATEGORIZAR` | `TransaccionLoteService` | directo, en lugar de `categoria(...)` |
  | Duplicar | `TransaccionService.duplicar` | no aplica: copia la categoría de una transacción ya válida |
  | Mover de cuenta | `TransaccionService.moverCuenta` | no aplica: no toca la categoría |

  `dividir` deja de llamar a `categoria(...)` y usa `categoriaParaRegistrar` tanto para la
  categoría de la transacción como para la de cada parte, así que `TransaccionService` no
  necesita código propio. El único uso que conserva `categoria(...)` es el filtro del listado
  (`TransaccionService.listar`), donde una categoría de pago es válida y devuelve una lista vacía.
  Al implementar, `grep -n "referencias.categoria(" ` en `transaccion/service` solo puede devolver
  el filtro del listado.
- `AsignacionService.asignar/moverDinero` no cambian (permiten categorías de pago).
- `BeneficiarioService.crear/actualizar` (`com.presupuesto.beneficiario.service`) lanzan
  `ReglaNegocioException` (422) si la categoría predeterminada es de pago; `beneficiario` ya
  puede importar `categoria`. Quitar la categoría (`categoriaId` nulo) no cambia.
- `CuentaService.actualizar`: 422 si `request.tipo()` difiere del tipo actual y alguno de los dos
  es `TARJETA_CREDITO` (se evalúa antes que `exigirSaldoValido`).

### D6. `listoParaAsignar`

`ingresosSinCategoria` añade `and t.cuenta.tipo <> TARJETA_CREDITO`. Sin esto, un reembolso sin
categoría o un pago desde una cuenta externa sumaría ingresos que nunca entraron a una cuenta que
no es tarjeta y rompería el invariante. Efecto: se revierte un caso extremo (entrada sin
categoría en tarjeta) que hoy cuenta como ingreso; no hay aserciones existentes sobre él
(verificado: los tests actuales con `TARJETA_CREDITO` solo prueban el saldo inicial).

### D7. Migración idempotente

`MigracionPagosTarjeta` (`categoria/service`) es un `ApplicationRunner` que **no** es
transaccional en conjunto: obtiene los ids de los presupuestos con trabajo pendiente y procesa
cada uno en su propia transacción (`TransactionTemplate`), delegando en `CategoriasPagoTarjeta`,
la misma clase que usa el oyente de D3. Garantías:

**a) Solo lo que falta.** La consulta vive en `CategoriaRepository` (puede recorrer `Cuenta`, que
es anterior) y devuelve únicamente las tarjetas con `tipo = TARJETA_CREDITO`, `enPresupuesto`
verdadero y sin categoría que apunte a ellas (`cuentaTarjeta` ausente). Tras una corrida completa,
la consulta devuelve cero filas: la segunda corrida no encuentra presupuestos pendientes y no hace
nada, ni siquiera lee el árbol.

**b) Un solo grupo de pagos.** Antes de crear, `asegurarGrupo` localiza el grupo por `tipo =
PAGOS_TARJETA` en el presupuesto y lo reutiliza si existe; solo si no existe lo crea, con el nombre
original o el alterno `(2)`, `(3)`... de D4 cuando un grupo normal lo ocupa. Para que dos
transacciones sobre el mismo presupuesto no creen cada una su grupo, `asegurarGrupo` toma antes un
bloqueo pesimista de escritura sobre la fila del `Presupuesto` (`PresupuestoRepository`, método
nuevo con `@Lock(PESSIMISTIC_WRITE)`), de modo que la segunda transacción espera y luego ve el
grupo ya creado. Ese método es un cambio interno de consulta: no modifica ningún requisito de
`presupuestos` (no hay delta de esa capacidad) ni la API. **Cada transacción bloquea un solo
presupuesto, nunca dos**, para evitar interbloqueos (la migración procesa un presupuesto por
transacción y el oyente de D3 solo toca el de la cuenta que se está creando). Nunca se crea un segundo grupo de pagos en el mismo presupuesto; el grupo normal
con el nombre original no se toca.

**c) Última defensa en la base.** La restricción única de `categorias.cuenta_tarjeta_id` impide
que una tarjeta tenga dos categorías de pago aunque falle cualquier comprobación anterior.

**d) Dos instancias arrancando a la vez.** Aunque el bloqueo de (b) serializa por presupuesto, si
aun así una de las dos viola la restricción única, esa transacción falla y se deshace. El runner
captura el fallo (`DataIntegrityViolationException` o fallo de bloqueo) por presupuesto, lo
registra con el id del presupuesto y continúa con los demás: no tumba el arranque. Como cada
presupuesto va en su propia transacción, un fallo no deshace los ya procesados. La siguiente
corrida (el próximo arranque, o invocar el bean) vuelve a encontrar solo lo que faltó y lo
completa. El bloqueo se prueba con Mockito en `CategoriasPagoTarjetaTest`: `asegurarGrupo` consulta el
presupuesto con el método con bloqueo (y no con el `findById` normal) antes de buscar o crear el
grupo, y dos llamadas seguidas devuelven el mismo grupo (la segunda lo encuentra por tipo y no
crea otro). La garantía (d) se prueba sin concurrencia real: el test hace que `CategoriasPagoTarjeta`
lance `DataIntegrityViolationException` para un presupuesto y comprueba que el runner no lanza,
que el otro presupuesto sí queda migrado y que una segunda corrida sin el fallo completa el
pendiente. La carrera real entre dos JVM no se prueba (queda como riesgo documentado más abajo).

**e) Tarjeta cerrada.** Su categoría se crea oculta, con el nombre actual de la tarjeta.

Los tests invocan el bean directamente (sin reiniciar el contexto) y trabajan solo con los
presupuestos que crean.

### D8. Auto-asignar

`AutoAsignarService.categoriasObjetivo` con `categoriaIds == null` filtra
`!categoria.isPagoTarjeta()`; con ids explícitos no filtra (se pueden procesar, ocultas incluidas).

### Supuestos documentados (decisiones ya tomadas)

1. **Saldo inicial negativo de una tarjeta** (deuda anterior): no reserva nada; la persona lo
   financia asignando a la categoría de pago.
2. **Sobregasto con tarjeta**: igual al normal (D2); no se toca esa regla.
3. **Datos existentes**: migración idempotente al arrancar, sin Flyway (D7).
4. **Auto-asignar**: sin `categoriaIds` ignora las categorías de pago; con ids explícitos las procesa.
5. **Conflicto de línea base resuelto con la persona**: la regla "cambiar el tipo desde o hacia
   `TARJETA_CREDITO` responde 422" contradice los tests
   `CuentaIntegracionTest` (editar tarjeta → `PRESTAMO` espera 200) y `CuentaServiceTest`
   (`actualizar` tarjeta → `PRESTAMO` espera el nuevo tipo). Se aceptó modificar **solo esas dos
   aserciones** (pasan a esperar 422 y que el tipo no cambia); es el único cambio de aserciones
   permitido y el escenario homólogo del spec cambia igual.
6. **Las categorías de pago SÍ admiten meta**: `MetaService` no se modifica (ya acepta
   cualquier categoría del presupuesto, ocultas incluidas) y el cálculo de la meta usa las cifras
   de la categoría de pago como las de cualquier otra. Un escenario y un test lo dejan fijado; no
   se bloquea ni se trata distinto. **Nota**: la meta de una categoría de pago mide lo asignado
   en el mes y no cuenta la reserva automática; igual que en las categorías normales, la
   actividad del mes no entra en el inicial (`inicial = disponible - asignado - actividad`).
   Ejemplo fijado en el spec: meta `MONTO_MENSUAL` `MENSUAL` de `50000`, `asignado` `0` y gasto de
   `40000` con la tarjeta → `disponible` `40000`, `necesidad` `50000`, `faltante` `50000`,
   `estado` `FALTA`.
7. Las tarjetas con `enPresupuesto` falso no tienen categoría de pago (no hay reserva para
   dinero fuera del presupuesto).

## Paquetes de las clases nuevas o modificadas (checklist AGENTS.md)

| Clase | Paquete completo | Test (mismo paquete) |
|-------|------------------|----------------------|
| `CuentaCreadaEvento`, `CuentaRenombradaEvento`, `CuentaCerradaEvento`, `CuentaReabiertaEvento` (nuevas) | `com.presupuesto.cuenta.evento` | `CuentaEventosTest` (mismo paquete) |
| `CuentaService` (modif.) | `com.presupuesto.cuenta.service` | `CuentaServiceTest` |
| `TipoGrupoCategoria` (nueva) | `com.presupuesto.categoria.entity` | `TipoGrupoCategoriaTest` |
| `GrupoCategoria`, `Categoria` (modif.) | `com.presupuesto.categoria.entity` | `GrupoCategoriaTest`, `CategoriaTest` |
| `CategoriasPagoTarjeta`, `PagosTarjetaListener`, `MigracionPagosTarjeta` (nuevas) | `com.presupuesto.categoria.service` | `CategoriasPagoTarjetaTest`, `PagosTarjetaListenerTest`, `MigracionPagosTarjetaTest` |
| `TransaccionService` (crear/editar; sin cambios de código: usa `dividir`) | `com.presupuesto.transaccion.service` | `TransaccionServiceTest` |
| `CategoriaService`, `GrupoCategoriaService` (modif.) | `com.presupuesto.categoria.service` | `CategoriaServiceTest`, `GrupoCategoriaServiceTest` |
| `PresupuestoRepository` (modif.: consulta con bloqueo de escritura por id) | `com.presupuesto.presupuesto.repository` | `PresupuestoRepositoryTest` |
| `CategoriaRepository`, `GrupoCategoriaRepository` (modif.) | `com.presupuesto.categoria.repository` | `CategoriaRepositoryTest`, `GrupoCategoriaRepositoryTest` |
| `CategoriaResponse`, `GrupoCategoriaResponse`, `GrupoCategoriaConCategoriasResponse` (modif.) | `com.presupuesto.categoria.dto.response` | sus `*ResponseTest` |
| `CalculoPagoTarjeta` (nueva, pura) | `com.presupuesto.asignacion.service` | `CalculoPagoTarjetaTest` |
| `CalculadoraMes` (modif.) | `com.presupuesto.asignacion.service` | `CalculadoraMesTest` |
| `ActividadMensualRepository` (modif.) | `com.presupuesto.asignacion.repository` | `ActividadMensualRepositoryTest` |
| `CategoriaMesResponse` (modif.) | `com.presupuesto.asignacion.dto.response` | `CategoriaMesResponseTest` |
| `TransaccionReferencias`, `TransferenciaService`, `TransaccionLoteService` (modif.) | `com.presupuesto.transaccion.service` | `TransaccionReferenciasTest`, `TransferenciaServiceTest`, `TransaccionLoteServiceTest` |
| `BeneficiarioService` (modif.) | `com.presupuesto.beneficiario.service` | `BeneficiarioServiceTest` |
| `BeneficiarioIntegracionTest` (casos nuevos) | `com.presupuesto.beneficiario.controller` | (es el test) |
| `MetaIntegracionTest` (caso nuevo: meta en categoría de pago) | `com.presupuesto.meta.controller` | (es el test) |
| `AutoAsignarService` (modif.) | `com.presupuesto.meta.service` | `AutoAsignarServiceTest` |
| Integración por flujo: `TarjetaCreditoIntegracionTest` (nueva) | `com.presupuesto.asignacion.controller` | (es el test) |
| Integración de categorías de pago: `PagosTarjetaIntegracionTest` (nueva) | `com.presupuesto.categoria.controller` | (es el test) |

Dependencias: `beneficiario` importa `categoria` (ya permitido); `cuenta` no importa `categoria` ni `asignacion`; `categoria` importa `cuenta`;
`asignacion` importa `categoria` y `cuenta`; `transaccion` importa `categoria`; nadie importa
`meta`. El `grep` de `comun/` (agregando las features) debe seguir en cero líneas.

## Riesgos / Trade-offs

- [Columna `tipo` NOT NULL sobre filas existentes con `ddl-auto=update`] → `columnDefinition`
  con `DEFAULT 'NORMAL'`; verificado en el test de migración con filas previas.
- [Migración al arrancar en cada test de contexto] → es idempotente y los tests de integración
  crean sus propios datos y solo consultan dentro de su presupuesto (la base tiene datos de
  ejemplo).
- [Cambio de ingresos (D6)] → caso extremo y cubierto por escenarios nuevos; sin aserciones
  previas afectadas.
- [Rendimiento] → tres consultas agregadas más por cálculo de mes, solo sobre tarjetas; el
  historial de `auto-asignar` multiplica el costo por mes calculado, igual que hoy.
- [Tipo de cuenta inmutable desde/hacia tarjeta] → rompe el flujo "corregir el tipo de una
  tarjeta"; mitigación: cerrar y crear otra cuenta.
- [Colisión de nombre por recorte] → sufijo con id (D3).
- [Dos instancias migrando a la vez] → bloqueo por presupuesto, restricción única y un fallo por
  presupuesto que no tumba el arranque (D7); la carrera real entre dos procesos no tiene test
  automático: solo se verifica con Mockito que se usa la consulta con bloqueo y la simulación de
  la violación de la restricción. Riesgo aceptado y documentado.
- [Grupo normal con el nombre del grupo de pagos] → localizar por tipo y nombre alterno (D4);
  quien intente crear después un grupo normal con el nombre del grupo de pagos recibe el 409
  habitual.
