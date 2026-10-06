## Context

Existen `presupuesto`, `cuenta` y `categoria` (395 tests verdes). Falta el recurso que mueve el
dinero. Reglas heredadas de `AGENTS.md`: dinero en `long` milésimas, `Clock` inyectado, DTO
como records con `desde(...)`, builders de Lombok en el service, setters bloqueados, 404 para
lo ajeno, y `PresupuestoService.obtenerDelUsuario` como primera llamada de cada operación.
Hoy ningún endpoint pagina y `ManejadorGlobalExcepciones` no maneja
`MethodArgumentTypeMismatchException` (un `estado=XYZ` en la URL daría 500).

## Goals / Non-Goals

**Goals:**
- Feature `transaccion` completa en backend con las reglas del proposal.
- Paginación reutilizable (`PaginaResponse`) y documentada.
- Lote atómico y saldos calculados en base de datos.

**Non-Goals:** frontend, `Beneficiario`, transferencias, conciliación (paso a `RECONCILIADA`),
programadas, importación, borrado de cuentas/categorías, "Ready to Assign".

## Decisions

**Dependencias.** `transaccion` importa `presupuesto` (`PresupuestoService`), `cuenta`
(`Cuenta`, `CuentaRepository`), `categoria` (`Categoria`, `CategoriaRepository`) y `comun`.
Ninguna de ellas importa `transaccion`; el `grep` de `AGENTS.md` lo comprueba.

**Modelo.**
- `Transaccion` (`transacciones`): `cuenta` (LAZY, obligatoria), `fecha` `LocalDate`, `monto`
  `long`, `categoria` (LAZY, opcional), `beneficiario` (100), `memo` (500), `estado`
  (`@Enumerated(STRING)`, length 20, defecto `NO_CONCILIADA`), `aprobada` (defecto `true`),
  `subtransacciones` (`@OneToMany(mappedBy = "transaccion", cascade = ALL, orphanRemoval = true)`,
  `@OrderBy("id")`, `@Builder.Default` lista vacía). Índices `(cuenta_id, fecha)`.
- `SubTransaccion` (`subtransacciones`): `transaccion` (LAZY), `categoria` (LAZY, opcional),
  `monto`, `memo` (500).
- Setters bloqueados (`@Setter(AccessLevel.NONE)`) en todos los campos de negocio. Métodos de
  la entidad: `editar(fecha, monto, categoria, beneficiario, memo)`,
  `reemplazarSubtransacciones(List<SubTransaccion>)` (limpia y agrega, para que
  `orphanRemoval` funcione sobre la misma colección), `moverA(Cuenta)`, `aprobar()`,
  `cambiarEstado(EstadoTransaccion)`, `categorizar(Categoria)`, `estaReconciliada()` y
  `tieneSubtransacciones()`. La entidad solo expone los cambios; las reglas de negocio con
  excepciones viven en el service.
- Sin Bean Validation en entidades; los límites los impone el request.

**Validación en capas.**
- Request (400 por Jakarta Validation, normalizando en el constructor compacto): `fecha`,
  `cuentaId`, `monto` obligatorios; `monto != 0` con la anotación propia `@MontoNoCero` (null-segura, mismo
  patrón para subtransacciones; así el error cae en el campo `monto`); beneficiario `@Size(max=100)`,
  memo `@Size(max=500)`; `aprobada` nulo → `true` al crear; `subtransacciones` `@Valid`.
- Service (`DatosInvalidosException`, 400): subtransacciones de tamaño distinto de 0 o entre 2 y
  20, y `categoriaId` junto a subtransacciones. Una lista vacía o nula equivale a "sin
  división". (Se decide en el service y no en `@Size` para que 1 sola, 21 y "categoría + split"
  den todas `DATOS_INVALIDOS` sin `errores` por campo.)
- Service (`ReglaNegocioException`, 422): suma ≠ monto, cuenta cerrada, `RECONCILIADA`, estado
  manual inválido, y en lote categorizar divididas.
- La suma usa `Math.addExact`; un desbordamiento se trata como suma ≠ monto (422).

**Orden de evaluación en cada operación:** `obtenerDelUsuario` → buscar la transacción por
`(id, presupuestoId)` (404) → resolver cuenta/categorías del presupuesto (404) → validar el
cuerpo que solo el service puede comprobar (400) → reglas de estado (422). Así lo ajeno siempre
es 404 antes que cualquier otro error.

**Cuenta cerrada.** Crear, editar, mover (destino) y duplicar exigen la cuenta (la de la
transacción o la destino) abierta. Mover *desde* una cerrada hacia una abierta se permite.
Aprobar, cambiar estado y borrar no miran la cuenta.

**Búsqueda de transacciones.** `TransaccionRepository extends JpaRepository,
JpaSpecificationExecutor<Transaccion>`; los filtros se arman en `TransaccionSpecifications`
(`repository`, package-private salvo lo que use el service), todos acotados por
`cuenta.presupuesto.id`:
- `categoriaId`: `categoria.id = :c OR EXISTS (subconsulta sobre SubTransaccion con
  transaccion = raíz y categoria.id = :c)`; sin `join` para no duplicar filas ni romper el
  conteo de la página.
- `desde`/`hasta` inclusivos sobre `fecha`; `estado`; `soloSinAprobar` → `aprobada = false`.
- `q`: `lower(beneficiario) like :p escape '\' or lower(memo) like :p escape '\'`, con
  `p = "%" + escapar(q.strip().toLowerCase(Locale.ROOT)) + "%"`; el escape antepone `\` a `\`,
  `%` y `_`. `q` en blanco se ignora.
- Orden `Sort.by(DESC, "fecha").and(DESC, "id")` y `PageRequest.of(page, size, orden)`.
- `cuentaId` y `categoriaId` de filtro se resuelven contra el presupuesto antes (404 si ajenos).
- Para evitar N+1 al mapear (`cuentaId`, `categoriaId` leen solo la clave de la relación LAZY,
  que Hibernate resuelve sin cargar el proxy) y para cargar las subtransacciones se usa
  `@BatchSize` en la colección y se accede a `getId()` del proxy.

**Paginación.** `PaginaResponse<T>(List<T> contenido, int pagina, int tamano, long
totalElementos, int totalPaginas)` en `com.presupuesto.comun.paginacion`, con
`static <E, T> PaginaResponse<T> desde(Page<E> pagina, Function<E, T> mapeo)`. No depende de
ninguna feature. El controller recibe `page` (defecto 0) y `size` (defecto 20) como `int`; el
service rechaza `page < 0` o `size` fuera de 1–100 con `DatosInvalidosException` (400), y
`desde > hasta` también. No se usa `Pageable` en la firma pública para controlar el error.

**Parámetros inválidos.** Nuevo `@ExceptionHandler(MethodArgumentTypeMismatchException)` en
`ManejadorGlobalExcepciones` → 400 `DATOS_INVALIDOS` con mensaje fijo ("Un parámetro de la
petición no es válido"), sin exponer el valor ni la causa, con su test en `comun/excepcion`.

**Estado.** `PUT /{id}/estado`: si es `RECONCILIADA` → 422; si destino es `RECONCILIADA` → 422;
si destino igual al actual → 200 sin cambios. Un valor desconocido en el cuerpo ya lo cubre el
manejador de cuerpo ilegible (400).

**Aprobar.** Idempotente; no exige cuenta abierta ni estado distinto de `RECONCILIADA`.

**Duplicar.** Construye con el builder una `Transaccion` nueva (fecha `LocalDate.now(clock)`,
`NO_CONCILIADA`, `aprobada = true`, misma cuenta/monto/categoría/beneficiario/memo) y copia cada
subtransacción con builder. Exige cuenta abierta (422). Se permite duplicar una `RECONCILIADA`.

**Lote.** `LoteRequest(List<Long> ids, OperacionLote operacion, Long categoriaId)` con `ids`
`@NotEmpty @Size(max=100)`, elementos `@NotNull`, `operacion` `@NotNull`. Se deduplican los ids
(`LinkedHashSet`) antes de contar. `TransaccionLoteService`, una sola `@Transactional`:
1. `obtenerDelUsuario`; 2. cargar con `findByIdInAndCuentaPresupuestoId`; si `size` ≠ ids
únicos → 404; 3. `CATEGORIZAR` sin `categoriaId` → 400, resolver categoría (404);
4. en `CATEGORIZAR` y `BORRAR`, cualquier `RECONCILIADA` → 422 (`APROBAR` acepta las
reconciliadas, igual que el aprobar individual), y en `CATEGORIZAR` cualquiera con
subtransacciones → 422;
5. aplicar. Todas las validaciones preceden a toda mutación, y cualquier excepción revierte la
transacción. Respuesta `LoteResponse(int afectadas)` (cuenta los ids únicos). No se exige
cuenta abierta en lote (APROBAR/BORRAR/CATEGORIZAR no crean ni editan montos); es una decisión
registrada: categorizar una transacción de cuenta cerrada se permite.

**Saldos.** `SaldoCuentaService` carga las cuentas del presupuesto
(`CuentaRepository.findByPresupuestoIdOrderByNombreNormalizado`) y una consulta agregada en
`TransaccionRepository` (`group by cuenta.id`, `sum(monto)` y suma condicional de estados
`CONCILIADA`/`RECONCILIADA`, acotada por presupuesto) devuelve las sumas; las cuentas sin
filas suman 0. `SaldoCuentaResponse(cuentaId, saldo, saldoConciliado)`; la respuesta es una
lista, sin paginar (acotada por el número de cuentas).

**Rutas y controller.** `TransaccionController` en
`/api/v1/presupuestos/{presupuestoId}/transacciones`. Las rutas literales `/lote` y `/saldos`
no chocan con `/{id}` porque `id` es `Long` y los literales tienen prioridad en Spring MVC.
`@AuthenticationPrincipal UsuarioAutenticado` y `@Valid` en los cuerpos.

**Tiempo.** Solo `duplicar` necesita "hoy", y lo obtiene de `Clock`; los tests usan
`RelojDePrueba`.

**AGENTS.md.** Se agrega `transaccion/` (con `controller`, `dto/request`, `dto/response`,
`entity`, `repository`, `service`) al árbol; la regla de dependencias pasa a "`transaccion`
puede depender de `presupuesto`, `cuenta`, `categoria` y `comun`; ninguna de ellas importa
`transaccion`"; la alternancia del `grep` de `comun/` suma `transaccion`; se añade
`comun/paginacion` a la lista de `comun/` y una sección "Paginación" (se paginan las listas que
pueden crecer sin límite, con `page` desde 0 y `size` 20 por defecto y máximo 100, respuesta
`PaginaResponse<T>`, nunca `Page` de Spring); y el `design.md` de este change documenta el
manejador de tipos inválidos.

## Paquetes (clases nuevas o movidas y sus tests)

Raíz `com.presupuesto`; tests en `src/test/java` con el mismo paquete.

| Clase | Paquete | Test (mismo paquete) |
|-------|---------|----------------------|
| `EstadoTransaccion` | `transaccion.entity` | `EstadoTransaccionTest` |
| `Transaccion` | `transaccion.entity` | `TransaccionTest` |
| `SubTransaccion` | `transaccion.entity` | `SubTransaccionTest` |
| `TransaccionRepository` | `transaccion.repository` | `TransaccionRepositoryTest` |
| `TransaccionSpecifications` | `transaccion.repository` | `TransaccionSpecificationsTest` |
| `CrearTransaccionRequest` | `transaccion.dto.request` | `CrearTransaccionRequestTest` |
| `ActualizarTransaccionRequest` | `transaccion.dto.request` | `ActualizarTransaccionRequestTest` |
| `SubTransaccionRequest` | `transaccion.dto.request` | `SubTransaccionRequestTest` |
| `CambiarEstadoRequest` | `transaccion.dto.request` | `CambiarEstadoRequestTest` |
| `MoverCuentaRequest` | `transaccion.dto.request` | `MoverCuentaRequestTest` |
| `LoteRequest` | `transaccion.dto.request` | `LoteRequestTest` |
| `OperacionLote` | `transaccion.dto.request` | (cubierto por `LoteRequestTest`) |
| `FiltroTransacciones` | `transaccion.dto.request` | `FiltroTransaccionesTest` |
| `MontoNoCero` | `transaccion.validacion` | `MontoNoCeroValidatorTest` |
| `MontoNoCeroValidator` | `transaccion.validacion` | `MontoNoCeroValidatorTest` |
| `Normalizacion` (package-private) | `transaccion.dto.request` | `NormalizacionTest` |
| `TransaccionResponse` | `transaccion.dto.response` | `TransaccionResponseTest` |
| `SubTransaccionResponse` | `transaccion.dto.response` | `SubTransaccionResponseTest` |
| `LoteResponse` | `transaccion.dto.response` | `LoteResponseTest` |
| `SaldoCuentaResponse` | `transaccion.dto.response` | `SaldoCuentaResponseTest` |
| `TransaccionReferencias` (package-private) | `transaccion.service` | `TransaccionReferenciasTest` |
| `TransaccionService` | `transaccion.service` | `TransaccionServiceTest` |
| `TransaccionLoteService` | `transaccion.service` | `TransaccionLoteServiceTest` |
| `SaldoCuentaService` | `transaccion.service` | `SaldoCuentaServiceTest` |
| `TransaccionController` | `transaccion.controller` | `TransaccionIntegracionTest` |
| `PaginaResponse` | `comun.paginacion` | `PaginaResponseTest` |
| `ManejadorGlobalExcepciones` (modificado) | `comun.excepcion` | `ManejadorGlobalExcepcionesTest` (existente, se amplía) |

`TransaccionIntegracionTest` (MockMvc, `@SpringBootTest`, `@Transactional`) cubre HTTP de todos
los endpoints, 401 y el código de error de cada rechazo; los repositorios y la creación de
`RECONCILIADA` en tests se hacen directamente por `TransaccionRepository`.

## Risks / Trade-offs

- **Specifications con `LIKE` sobre dos columnas** no usa índices; aceptable en esta etapa
  (presupuesto personal). Se paginan siempre los resultados.
- **`ddl-auto=update`** crea las tablas nuevas sin migraciones; un cambio futuro de columnas no
  se aplica solo (decisión vigente del proyecto).
- **Lote sin exigir cuenta abierta** es una decisión explícita que puede revisarse; está
  registrada en la spec implícitamente (no se prueba un 422 por cuenta cerrada en lote).
- **Mover desde cuenta cerrada permitido**: coherente con "sacar" un movimiento de una cuenta
  que ya no se usa; si se quiere lo contrario, es un cambio de una línea.
- **`categoriaId` filtrado por subconsulta** es más lento que un join, pero evita duplicados.
