## Context

Existen `presupuesto`, `cuenta`, `categoria` y `transaccion` (588 tests verdes). `Transaccion` ya
guarda monto con signo, fecha de negocio, categoría opcional (nula si está dividida) y
`SubTransaccion` con su categoría; `Cuenta` tiene `enPresupuesto`, `cerrada`, `tipo` y
`saldoInicial`. Falta asignar dinero a las categorías por mes y derivar disponible, sobregasto
y "listo para asignar". Convenciones heredadas: dinero en `long` milésimas, `Clock` inyectado,
mapeo manual con `desde(...)`, builders en el service, setters bloqueados, 404 para lo ajeno y
`PresupuestoService.obtenerDelUsuario` como primera llamada de cada operación.

## Goals / Non-Goals

**Goals:** asignar por categoría y mes, consultar el mes calculado, mover dinero entre
categorías, con cálculo acumulado por consultas agregadas en base de datos.

**Non-Goals:** frontend, metas y auto-asignar, tarjetas de crédito y su categoría de pago,
transferencias, programadas, conciliación, reportes, Age of Money, notas por mes, deshacer.

## Decisions

**Dependencias.** `asignacion` importa `presupuesto` (`PresupuestoService`), `cuenta`,
`categoria`, `transaccion` (entidades `Transaccion` y `SubTransaccion` solo para las consultas
agregadas) y `comun`. Ninguna de ellas importa `asignacion` (lo comprueba el `grep` de
`AGENTS.md`).

**Modelo.** `AsignacionMensual` (`asignaciones_mensuales`): `categoria` (LAZY, obligatoria),
`mes` (`LocalDate`, siempre día 1), `asignado` (`long`). Restricción
`uk_asignaciones_mensuales_categoria_mes (categoria_id, mes)`. Setters bloqueados; métodos
`fijarAsignado(long)` y `sumarAsignado(long)`. Un `@PrePersist/@PreUpdate` rechaza con
`IllegalStateException` un `mes` que no sea día 1 (defensa; el service siempre normaliza con
`YearMonth.atDay(1)`).

**Mes de la URL.** `mes` llega como `String` y el service lo interpreta *después* de
`obtenerDelUsuario` (un presupuesto ajeno es 404 aunque el mes sea inválido). Regex estricta
`^\d{4}-(0[1-9]|1[0-2])$` y año entre 2000 y 2100, si no `DatosInvalidosException` (400). El
rango acota el cálculo acumulado (máx. 1.200 meses) y evita iterar desde/hasta años absurdos.

**Reglas de cálculo.** Para un presupuesto y un mes `M` (y `mes(c)` = mes calendario de una
fecha):

1. `actividad(cat, m)` = Σ `monto` de transacciones con `categoria = cat` y `mes(fecha) = m`
   + Σ `monto` de subtransacciones con `categoria = cat` de transacciones con `mes(fecha) = m`,
   solo de cuentas con `enPresupuesto = true` (abiertas y cerradas).
2. `disponible(cat, m) = max(0, disponible(cat, m-1)) + asignado(cat, m) + actividad(cat, m)`,
   con `disponible(cat, primer mes - 1) = 0`. El "primer mes" es el menor mes con una
   asignación o con actividad (hasta `M`).
3. `listoParaAsignar(M) = ingresos(≤ fin de M) − Σ asignado(todas las categorías, meses ≤ M)
   − Σ_{m < M} Σ_cat max(0, −disponible(cat, m))`.
4. `ingresos(≤ fin de M)` = Σ `monto` de transacciones con `monto > 0`, sin categoría y sin
   subtransacciones, de cuentas con `enPresupuesto = true`, fecha ≤ fin de `M`; más Σ
   `saldoInicial > 0` de cuentas con `enPresupuesto = true` y `tipo ≠ TARJETA_CREDITO` (sin
   importar la fecha ni si están cerradas).
5. Las transacciones sin categoría con monto negativo, las de cuentas fuera del presupuesto, y
   las entradas con categoría (cuentan como actividad positiva, no como ingreso) no entran en
   `ingresos`.

Un sobregasto se cuenta una sola vez: en `m` el `disponible` negativo suma al descuento de los
meses posteriores, y en `m+1` la categoría arranca de 0, así que no vuelve a quedar negativa por
el mismo gasto.

*Ejemplo A (arrastre):* Comida enero asignado 100000, actividad −30000 → disponible 70000;
febrero asignado 50000, actividad −20000 → 70000 + 50000 − 20000 = 100000.

*Ejemplo B (sobregasto):* ingreso +500000 el 2026-01-01. Comida enero asignado 20000, actividad
−50000 → disponible −30000; febrero asignado 10000 → disponible 10000 (no 10000 − 30000).
`listoParaAsignar` enero = 500000 − 20000 = 480000; febrero = 500000 − 30000 − 30000 = 440000;
marzo (sin datos) = 440000. Comprobación: dinero real en cuentas = 500000 − 50000 = 450000 =
listo 440000 + disponible 10000.

*Ejemplo C (saldos iniciales):* corriente 100000, tarjeta 50000, cuenta fuera del presupuesto
70000, cuenta de −20000 → ingresos por saldo inicial = 100000. Con una entrada sin categoría de
+200000 el 2026-01-05 y otra de +80000 el 2026-02-10: listo enero = 300000, febrero = 380000
(sin asignaciones).

*Ejemplo D (división):* transacción de −30000 el 2026-01-10 con partes −10000 (Comida) y −20000
(Ocio): actividad Comida −10000, Ocio −20000; no es ingreso ni gasto sin categoría.

*Ejemplo E (mover dinero):* Comida asignado 100000, actividad −30000 (disponible 70000), Ocio en
0. Mover 30000: Comida asignado 70000 / disponible 40000; Ocio asignado 30000 / disponible
30000; `listoParaAsignar` no cambia.

**Consultas agregadas (no se cargan transacciones).** Una interfaz
`ActividadMensualRepository extends Repository<Transaccion, Long>` (`asignacion/repository`)
con `@Query` JPQL, todas acotadas por `cuenta.presupuesto.id`, `cuenta.enPresupuesto` y
`fecha ≤ :hasta`:
- `actividadSimple`: `group by categoria.id, extract(year ...), extract(month ...)` con
  `sum(monto)` sobre transacciones con categoría.
- `actividadDividida`: lo mismo sobre `SubTransaccion s join s.transaccion t` con `s.categoria`.
- `ingresosSinCategoria`: `coalesce(sum(t.monto), 0)` con `monto > 0`, `categoria is null` y
  `not exists (select 1 from SubTransaccion s where s.transaccion = t)`.
- `saldosInicialesPositivos`: `coalesce(sum(c.saldoInicial), 0)` sobre `Cuenta`.
Las asignaciones hasta `M` se leen de `AsignacionMensualRepository` como proyecciones
`(categoriaId, mes, asignado)`. El volumen en memoria es categorías × meses, no transacciones.

**Cálculo.** `CalculoMensual` (package-private, funciones puras y sin Spring) recibe los mapas
`asignado[cat][mes]` y `actividad[cat][mes]`, los `ingresos` y el mes pedido, y devuelve
`ResultadoMes(listoParaAsignar, Map<categoriaId, FilaMes(asignado, actividad, disponible)>)`
(solo para categorías con datos; el resto es 0). `CalculadoraMes` (`@Component`
package-private) arma esas entradas con las consultas y llama a `CalculoMensual`.

**Totales del GET.** `totalAsignado`, `totalActividad` y `totalDisponible` son la suma de las
categorías incluidas en la respuesta (con `incluirOcultas = false`, sin las ocultas, para que
cuadren con las filas); `listoParaAsignar` siempre considera todo el presupuesto, incluidas las
ocultas.

**Orden y ocultas.** El GET lee grupos y categorías con los mismos métodos de
`GrupoCategoriaRepository`/`CategoriaRepository` que usa el árbol de categorías
(`findBy…OrderByOrden`, con y sin ocultas), sin importar `CategoriaService`.

**Asignar.** `AsignacionService.asignar`: `obtenerDelUsuario` → mes (400) → categoría del
presupuesto (404, la oculta sirve) → buscar fila `(categoria, mes)`; si existe
`fijarAsignado`, si no crear con el builder → `saveAndFlush` → calcular el mes y devolver
`AsignacionActualizadaResponse(categoria: CategoriaMesResponse, listoParaAsignar)`. Una
violación de la restricción única por una petición concurrente (`DataIntegrityViolationException`)
se traduce a `ConflictoException` (409), igual que las demás features.

**Mover dinero.** `AsignacionService.moverDinero`: `obtenerDelUsuario` → mes (400) →
`origenId = destinoId` (400) → origen y destino del presupuesto (404) → calcular el mes →
`disponible(origen) < monto` → 422 → fijar `asignado(origen) − monto` y `asignado(destino) +
monto` (crear la fila que falte) → `saveAll` → devolver el mes recalculado. Todo en una sola
`@Transactional`: cualquier excepción revierte ambos cambios. `monto > 0` y los ids
obligatorios los valida el request (`@NotNull`, `@Positive`) con 400 y `errores`.

**Respuestas (records con `desde`).** `CategoriaMesResponse(categoriaId, nombre, oculta,
asignado, actividad, disponible, sobregastada)`, `GrupoMesResponse(id, nombre, oculto, orden,
categorias)`, `MesPresupuestoResponse(mes, listoParaAsignar, totalAsignado, totalActividad,
totalDisponible, grupos)` y `AsignacionActualizadaResponse(categoria, listoParaAsignar)`.
`mes` se responde como `yyyy-MM`.

**Rutas y controller.** `AsignacionController` en
`/api/v1/presupuestos/{presupuestoId}/meses/{mes}` con `@AuthenticationPrincipal
UsuarioAutenticado`, `@Valid` en los cuerpos, `incluirOcultas` (por defecto `false`) en el GET.
Sin `DELETE`.

**Tiempo.** Ninguna regla usa "hoy": no hay `Clock` en esta feature (el mes viene de la URL).

**AGENTS.md.** Se agrega `asignacion/` (con `controller`, `dto/request`, `dto/response`,
`entity`, `repository`, `service`) al árbol; la regla de dependencias suma "`asignacion` puede
depender de `presupuesto`, `cuenta`, `categoria`, `transaccion` y `comun`; ninguna de ellas
importa `asignacion`"; y la alternancia del `grep` de `comun/` suma `asignacion`.

## Paquetes (clases nuevas y sus tests)

Raíz `com.presupuesto`; tests en `src/test/java` con el mismo paquete.

| Clase | Paquete | Test (mismo paquete) |
|-------|---------|----------------------|
| `AsignacionMensual` | `asignacion.entity` | `AsignacionMensualTest` |
| `AsignacionMensualRepository` | `asignacion.repository` | `AsignacionMensualRepositoryTest` |
| `ActividadMensualRepository` | `asignacion.repository` | `ActividadMensualRepositoryTest` |
| `AsignarRequest` | `asignacion.dto.request` | `AsignarRequestTest` |
| `MoverDineroRequest` | `asignacion.dto.request` | `MoverDineroRequestTest` |
| `CategoriaMesResponse` | `asignacion.dto.response` | `CategoriaMesResponseTest` |
| `GrupoMesResponse` | `asignacion.dto.response` | `GrupoMesResponseTest` |
| `MesPresupuestoResponse` | `asignacion.dto.response` | `MesPresupuestoResponseTest` |
| `AsignacionActualizadaResponse` | `asignacion.dto.response` | `AsignacionActualizadaResponseTest` |
| `CalculoMensual` (package-private) | `asignacion.service` | `CalculoMensualTest` |
| `CalculadoraMes` (package-private) | `asignacion.service` | `CalculadoraMesTest` |
| `MesParametro` (package-private) | `asignacion.service` | `MesParametroTest` |
| `MesPresupuestoService` | `asignacion.service` | `MesPresupuestoServiceTest` |
| `AsignacionService` | `asignacion.service` | `AsignacionServiceTest` |
| `AsignacionController` | `asignacion.controller` | `AsignacionIntegracionTest` |

`AsignacionIntegracionTest` (MockMvc, `@SpringBootTest`, `@Transactional`) cubre HTTP y los
números concretos de los ejemplos A–E de punta a punta (creando cuentas, categorías y
transacciones por la API existente) y 401, y el código de error de cada rechazo.

## Risks / Trade-offs

- **Rango de meses 2000–2100**: es una decisión mía para acotar el cálculo acumulado; un mes
  fuera de rango responde 400. Ampliarlo es cambiar dos constantes.
- **Cálculo acumulado en cada petición**: O(categorías × meses) en memoria más 4 consultas
  agregadas. Aceptable para un presupuesto personal; si crece, se cachearía por mes.
- **Totales sin ocultas**: con `incluirOcultas = false` los totales no incluyen el dinero de
  categorías ocultas, pero `listoParaAsignar` sí lo descuenta; es consistente con "las filas
  cuadran con los totales". Decisión registrada y revisable.
- **Saldo inicial sin fecha**: cuenta siempre en `ingresos`, aun para meses anteriores a la
  creación de la cuenta; es lo que pide la regla y se documenta en la spec.
- **`ddl-auto=update`** crea la tabla y la restricción única sin migraciones (decisión vigente).
