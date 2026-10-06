# Design

## Context

`PresupuestoService.obtenerDelUsuario(presupuestoId, usuarioId)` ya es la vía única de validación
de pertenencia, y `cuenta` fija el patrón de nombre único (`nombre` + `nombreNormalizado`,
consulta previa y traducción de `DataIntegrityViolationException`). `categoria` replica ese
patrón con dos entidades encadenadas (presupuesto → grupo → categoría) y añade el orden y el
movimiento. Motivo y alcance en `proposal.md`; comportamiento en la spec `categorias`.

Hoy no existe una excepción que produzca `400 DATOS_INVALIDOS` desde un service: solo lo emiten
Bean Validation y el cuerpo ilegible. La `posicion` máxima depende de cuántos elementos hay, así
que solo el service puede comprobarla.

## Goals / Non-Goals

**Goals:**
- Feature `categoria` conforme a la estructura obligatoria de AGENTS.md.
- Orden siempre consecutivo (`0..n-1`) tras crear y mover, sin depender de la base de datos.

**Non-Goals:**
- Categorías por defecto, asignación mensual, metas, transacciones, borrado, paginación, frontend.
- Bloqueo de concurrencia para el orden (ver Riesgos).

## Decisions

**1. Entidades.** `GrupoCategoria` (`@Table(name = "grupos_categoria")`, restricción
`uk_grupos_categoria_presupuesto_nombre` sobre `presupuesto_id, nombre_normalizado`) y
`Categoria` (`@Table(name = "categorias")`, `uk_categorias_grupo_nombre` sobre
`grupo_id, nombre_normalizado`). `presupuesto` y `grupo` son `@ManyToOne(LAZY, optional =
false)`. `nombre`, `nombreNormalizado`, `orden`, `oculto`/`oculta` (y `grupo` en `Categoria`)
llevan `@Setter(AccessLevel.NONE)`; `nota` también, porque solo se cambia con
`cambiarNota(String)`. Métodos de `GrupoCategoria`: `normalizar` (estático,
`toLowerCase(Locale.ROOT)`), `renombrar`, `asignarOrden(int)`, `ocultar`, `mostrar`. Métodos de
`Categoria`: los mismos más `cambiarNota` y `moverAGrupo(GrupoCategoria)`. `oculto`/`oculta` usan
`@Builder.Default` en `false`. No hay restricción única sobre `orden`: renumerar en una sola
transacción provocaría choques transitorios, y la consecutividad la garantiza el service.

**2. Requests como records** con normalización en el constructor compacto:
- `CrearGrupoCategoriaRequest(@NotBlank @Size(max = 100) nombre)` y
  `ActualizarGrupoCategoriaRequest(nombre)`.
- `CrearCategoriaRequest(@NotNull Long grupoId, nombre, @Size(max = 500) nota)` y
  `ActualizarCategoriaRequest(nombre, nota)`. La nota se recorta y pasa a `null` si queda vacía;
  el helper vive en una clase package-private `Normalizacion` de `dto/request`, como en `auth`.
  Los `PUT` no declaran `grupoId` ni `orden`, así que Jackson los descarta.
- `MoverGrupoCategoriaRequest(@NotNull @PositiveOrZero Integer posicion)` y
  `MoverCategoriaRequest(@NotNull Long grupoId, @NotNull @PositiveOrZero Integer posicion)`.
  Un `posicion` negativo o ausente lo rechaza Bean Validation; el máximo, el service.

**3. `DatosInvalidosException` (400).** Nueva subclase de `NegocioException` en
`comun/excepcion`, con `CodigoError.DATOS_INVALIDOS`, y un `@ExceptionHandler` más en
`ManejadorGlobalExcepciones` que responde `400` sin mapa `errores` (igual que el cuerpo
ilegible). Alternativa descartada: reutilizar `ReglaNegocioException` (422), que contradice el
requisito de 400, o validar con una anotación entre campos, que no conoce el tamaño de la lista.

**4. Orden al crear.** El `orden` nuevo es la cantidad actual de hermanos
(`countByPresupuestoId` / `countByGrupoId`), contando también los ocultos. Como mover renumera
sin huecos, la cantidad es siempre el siguiente `orden`.

**5. Algoritmo de mover.** Se cargan los hermanos ordenados por `orden`, se quita el elemento,
se inserta en `posicion` y se reasigna `asignarOrden(i)` a toda la lista, guardando solo los que
cambian de valor. Reglas de rango, sobre la lista final:
- Grupo: `posicion` en `[0, n-1]`.
- Categoría en su mismo grupo: `[0, n-1]`.
- Categoría a otro grupo: `[0, m]`, con `m` las categorías que ya tiene el destino (la lista
  final tiene `m+1`). Es la única lectura que permite mover a un grupo vacío o al final de otro;
  se recoge en la spec.
Fuera de rango → `DatosInvalidosException` antes de modificar nada. Al cambiar de grupo se
renumeran el origen (sin el elemento) y el destino (con él). Orden de comprobaciones: 404
(presupuesto, categoría, grupo destino), rango (400), nombre repetido en el destino (409).

**6. Doble defensa ante duplicados.** Consulta previa (`existsByPresupuestoIdAndNombreNormalizado`
y variante `...AndIdNot` en grupos; `existsByGrupoIdAndNombreNormalizado` y `...AndIdNot` en
categorías) y `saveAndFlush` con traducción de `DataIntegrityViolationException` a
`ConflictoException(GRUPO_CATEGORIA_YA_EXISTE | CATEGORIA_YA_EXISTE)`. El `AndIdNot` hace
válido conservar el propio nombre al renombrar, editar o mover dentro del mismo grupo. Se
agregan ambos valores a `CodigoError`.

**7. Búsqueda siempre acotada al presupuesto.** Grupos: `findByIdAndPresupuestoId`. Categorías:
`findByIdAndGrupoPresupuestoId`. El `grupoId` de crear y mover se resuelve con la misma consulta
de grupos, así que un grupo de otro presupuesto da `404`. Cada método de cada service llama
primero a `presupuestoService.obtenerDelUsuario(...)`. `CategoriaService` usa
`GrupoCategoriaRepository` (misma feature) y no `GrupoCategoriaService`.

**8. Árbol.** `CategoriaService.arbol(presupuestoId, usuarioId, incluirOcultas)` hace dos
consultas, sin N+1: los grupos del presupuesto ordenados por `orden` (con variante
`OcultoFalse`) y todas sus categorías ordenadas por `orden` (con variante `OcultaFalse`),
agrupadas en memoria por id de grupo. Con `incluirOcultas=false` también se omiten las
categorías ocultas de grupos visibles; las de un grupo oculto salen con él. Responde
`List<GrupoCategoriaConCategoriasResponse>`, sin paginar. El endpoint `GET /categorias` vive en
`CategoriaController`.

**9. Ocultar y mostrar idempotentes**: asignan el estado y guardan; repetir da el mismo `200`.
Ocultar un grupo no oculta ni altera sus categorías.

**10. Respuestas.** `GrupoCategoriaResponse(id, nombre, orden, oculto, fechaCreacion,
fechaActualizacion)`; `CategoriaResponse(id, grupoId, nombre, orden, oculta, nota,
fechaCreacion, fechaActualizacion)`; `GrupoCategoriaConCategoriasResponse(id, nombre, orden,
oculto, categorias)`. Todos con `desde(...)`; el del árbol recibe el grupo y su lista de
categorías ya filtrada. `grupoId` sale de `getGrupo().getId()`, sin cargar el grupo.

**11. Sin DELETE.** Los controllers no lo declaran; Spring responde `405` y los tests solo
comprueban el estado.

**12. AGENTS.md.** Se agrega `categoria/` al árbol de paquetes, la dependencia `categoria` →
`presupuesto` y `comun` (y que `presupuesto` y `cuenta` no importan `categoria`), `categoria` a
la alternancia del `grep` de `comun/` y `DatosInvalidosException` (400) a la jerarquía de
excepciones. Los `grep` se ejecutan sobre `main` y `test` de `comun/` y también sobre
`presupuesto/` y `cuenta/` buscando `com.presupuesto.categoria`; todos deben dar cero líneas.

### Clases nuevas o modificadas

| Clase | Paquete | Test (paquete) |
|---|---|---|
| `GrupoCategoria` | `com.presupuesto.categoria.entity` | `GrupoCategoriaTest` (`...entity`) |
| `Categoria` | `com.presupuesto.categoria.entity` | `CategoriaTest` (`...entity`) |
| `GrupoCategoriaRepository` | `com.presupuesto.categoria.repository` | `GrupoCategoriaRepositoryTest` (`...repository`) |
| `CategoriaRepository` | `com.presupuesto.categoria.repository` | `CategoriaRepositoryTest` (`...repository`) |
| `CrearGrupoCategoriaRequest` | `com.presupuesto.categoria.dto.request` | `CrearGrupoCategoriaRequestTest` (`...dto.request`) |
| `ActualizarGrupoCategoriaRequest` | `com.presupuesto.categoria.dto.request` | `ActualizarGrupoCategoriaRequestTest` (`...dto.request`) |
| `MoverGrupoCategoriaRequest` | `com.presupuesto.categoria.dto.request` | `MoverGrupoCategoriaRequestTest` (`...dto.request`) |
| `CrearCategoriaRequest` | `com.presupuesto.categoria.dto.request` | `CrearCategoriaRequestTest` (`...dto.request`) |
| `ActualizarCategoriaRequest` | `com.presupuesto.categoria.dto.request` | `ActualizarCategoriaRequestTest` (`...dto.request`) |
| `MoverCategoriaRequest` | `com.presupuesto.categoria.dto.request` | `MoverCategoriaRequestTest` (`...dto.request`) |
| `Normalizacion` (package-private) | `com.presupuesto.categoria.dto.request` | `NormalizacionTest` (`...dto.request`) |
| `GrupoCategoriaResponse` | `com.presupuesto.categoria.dto.response` | `GrupoCategoriaResponseTest` (`...dto.response`) |
| `CategoriaResponse` | `com.presupuesto.categoria.dto.response` | `CategoriaResponseTest` (`...dto.response`) |
| `GrupoCategoriaConCategoriasResponse` | `com.presupuesto.categoria.dto.response` | `GrupoCategoriaConCategoriasResponseTest` (`...dto.response`) |
| `GrupoCategoriaService` | `com.presupuesto.categoria.service` | `GrupoCategoriaServiceTest` (`...service`, Mockito) |
| `CategoriaService` | `com.presupuesto.categoria.service` | `CategoriaServiceTest` (`...service`, Mockito) |
| `GrupoCategoriaController` | `com.presupuesto.categoria.controller` | `GrupoCategoriaIntegracionTest` (`...controller`) |
| `CategoriaController` | `com.presupuesto.categoria.controller` | `CategoriaIntegracionTest` (`...controller`) |
| `DatosInvalidosException` | `com.presupuesto.comun.excepcion` | `DatosInvalidosExceptionTest` (`...comun.excepcion`) |
| `CodigoError` (modificada) | `com.presupuesto.comun.excepcion` | cubierto por los tests de integración |
| `ManejadorGlobalExcepciones` (modificada) | `com.presupuesto.comun.excepcion` | `ManejadorGlobalExcepcionesTest` (`...comun.excepcion`, caso nuevo) |

## Risks / Trade-offs

- [Dos peticiones simultáneas pueden dar el mismo `orden` o dejar huecos] → no hay restricción
  única sobre `orden`; la unicidad de nombre sí está protegida por la base de datos. Un bloqueo
  de la fila del grupo o del presupuesto queda como mejora si aparece la concurrencia real.
- [Renumerar actualiza varias filas por cada mover] → aceptable: un presupuesto tiene decenas de
  grupos y categorías, no miles.
- [Un test no transaccional deja datos que impiden borrar un presupuesto por la FK] → los tests
  de integración y de repositorio usan `@Transactional`.
- [`Locale.ROOT` no cubre todo el plegado Unicode] → igual que en presupuestos y cuentas.
- [Rango `[0, m]` al cambiar de grupo difiere de `[0, n-1]` del enunciado] → es el rango de la
  lista final; con `[0, m-1]` no se podría mover a un grupo vacío ni al final.
