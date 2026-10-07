# Design

## Context

Ver `proposal.md` para la motivación. Estado actual relevante:

- `Transaccion.beneficiario` es un `String` (máx. 100) recortado por `Normalizacion.recortarONulo`
  en `CrearTransaccionRequest` y `ActualizarTransaccionRequest`. `TransaccionSpecifications.contiene`
  busca `q` sobre ese texto y sobre el memo; el contrato de la API depende de él.
- `cuenta` es el modelo a imitar: nombre + `nombreNormalizado` con setter bloqueado,
  `Cuenta.normalizar(...)` (`strip` + `toLowerCase(Locale.ROOT)`), restricción única
  `(presupuesto_id, nombre_normalizado)`, consulta previa más traducción de
  `DataIntegrityViolationException` a `ConflictoException`, y `404` por `findByIdAndPresupuestoId`.
- `TransaccionService.crear/actualizar` resuelven la categoría con
  `TransaccionReferencias.dividir(...)` (404 si es ajena; `null` si la transacción es dividida).
  `TransaccionLoteService` categoriza con `Transaccion.categorizar(...)`.
- Dependencias entre features (AGENTS.md): `transaccion` puede depender de `presupuesto`, `cuenta`,
  `categoria` y `comun`. `beneficiario` es nuevo y `transaccion` pasará a depender de él;
  `beneficiario` nunca importa `transaccion` ni `asignacion`.
- El esquema lo gestiona Hibernate (`ddl-auto=update`): una tabla y una columna nullable nuevas
  no necesitan migración.

## Goals / Non-Goals

**Goals:**
- Beneficiario como recurso del presupuesto, con unicidad sin distinguir mayúsculas y
  autocompletado por prefijo.
- Vincular las transacciones nuevas y editadas sin romper el campo de texto ni las respuestas
  actuales (solo se agrega `beneficiarioId`).
- Recordar la última categoría usada con cada beneficiario.

**Non-Goals:**
- Frontend, renombrado automático (renombrar un beneficiario no reescribe el texto de sus
  transacciones), fusión, borrado, importación, sugerencia por ubicación.
- Migración masiva de las transacciones existentes.

## Decisions

**1. El vínculo se llama `beneficiarioVinculado` y el texto se deriva de él.** `Transaccion` ya
tiene un campo `beneficiario` (texto, columna `beneficiario`); el vínculo nuevo es
`beneficiarioVinculado` (`@ManyToOne(LAZY)`, columna `beneficiario_id`, nula, `@Setter(NONE)`).
Mientras haya vínculo, el texto es un campo derivado (igual que `nombreNormalizado`): `editar(...)`
recibe el `Beneficiario` (o `null`) y fija ambos, y el builder de crear/duplicar los rellena
juntos. Alternativa descartada: cambiar el tipo de `beneficiario` a la entidad, que rompería el
contrato JSON, `TransaccionSpecifications.contiene` y los datos existentes.

**2. API pública de `beneficiario` para `transaccion`.** `BeneficiarioService` (`@Service`)
expone, además de las operaciones HTTP, `obtenerOCrear(Presupuesto, String nombre)`: busca por
`(presupuestoId, Beneficiario.normalizar(nombre))` y, si no existe, guarda uno nuevo sin
categoría. `transaccion` solo usa el service, nunca el repositorio. La
categoría recordada se fija con el método de la entidad `Beneficiario.recordarCategoria(Categoria)`
sobre una entidad ya gestionada: la transacción de base de datos la persiste por dirty checking,
sin `save` extra. Alternativa descartada: un evento de Spring; aquí el resultado (el
beneficiario) se necesita de vuelta para vincular, y el aviso en sentido contrario no existe.

**3. Orden en `crear` y `actualizar`.** Primero se validan cuenta, categoría y división (404/400/
422 como hoy) y solo después se llama a `obtenerOCrear`, de modo que una petición inválida no deja
beneficiarios nuevos. Luego se construye o edita la transacción y, si `division.categoria()` no es
nula (lo es solo cuando no hay subtransacciones y se envió `categoriaId`) y hay beneficiario,
`beneficiario.recordarCategoria(categoria)`. El presupuesto sale de `obtenerDelUsuario` (en
`crear`) y de `transaccion.getCuenta().getPresupuesto()` (en `actualizar`).

**4. Lote `CATEGORIZAR`.** Tras `transaccion.categorizar(categoria)`, si `getBeneficiarioVinculado()`
no es nulo se llama a `recordarCategoria(categoria)`. La validación previa (dividida, reconciliada,
transferencia) ya ocurre antes de cambiar nada, así que un lote rechazado tampoco toca los
beneficiarios. `APROBAR`, `BORRAR`, `mover-cuenta`, `aprobar` y `cambiarEstado` no tocan el vínculo.

**5. Duplicar copia texto y vínculo** pasando ambos al builder; no crea beneficiarios ni cambia
su categoría predeterminada. Las transferencias no se modifican: `TransferenciaService` no
asigna beneficiario y sus patas quedan con ambos campos nulos.

**6. Búsqueda por prefijo con `LIKE ... ESCAPE`.** El service recorta `q`, lo pasa por
`Beneficiario.normalizar`, escapa `!`, `%` y `_` con `!` (HQL no admite la barra invertida como
escape) y agrega `%` al final; el repositorio ejecuta
`... b.nombreNormalizado like :patron escape '!' order by b.nombreNormalizado` con
`PageRequest.of(0, limite)`. Como `nombreNormalizado` ya está en minúsculas no hace falta
`lower(...)` ni un índice funcional. Sin `q` (ausente, vacía o solo espacios) se devuelve la lista
completa ordenada por `nombreNormalizado`, sin límite. `limite` (`Integer`, por defecto 10) se
valida siempre que se envíe (1 a 50, si no `DatosInvalidosException`) pero solo se aplica con `q`.
Alternativa descartada: `Specification` como en transacciones; una sola consulta fija es más
simple y no tiene filtros combinables.

**7. Unicidad y `409`.** Restricción única `uk_beneficiarios_presupuesto_nombre`
`(presupuesto_id, nombre_normalizado)`. El service hace la consulta previa
(`existsByPresupuestoIdAndNombreNormalizado[AndIdNot]`) y traduce `DataIntegrityViolationException`
de `saveAndFlush` a `ConflictoException(CodigoError.BENEFICIARIO_YA_EXISTE, ...)`, igual que
cuentas. `CodigoError` gana `BENEFICIARIO_YA_EXISTE`.

**8. Categoría predeterminada.** `categoriaId` se resuelve con
`CategoriaRepository.findByIdAndGrupoPresupuestoId` (404 si no es del presupuesto; una categoría
oculta sirve). En `PUT`, `categoriaId` nulo la quita. `beneficiario` puede depender de
`categoria` (permitido); `categoria` no importa `beneficiario`.

**9. Rutas y seguridad.** `BeneficiarioController` bajo
`/api/v1/presupuestos/{presupuestoId}/beneficiarios`, con `@AuthenticationPrincipal
UsuarioAutenticado`; cada operación del service empieza con
`PresupuestoService.obtenerDelUsuario`. Sin `DELETE`: Spring responde `405` y
`ManejadorGlobalExcepciones` ya lo traduce (se verifica en el test de integración).

### Clases y tests

Todas las rutas son `com.presupuesto.<paquete>`; los tests están en `src/test/java` con el mismo
paquete.

| Clase | Paquete | Test (mismo paquete, clase) |
|---|---|---|
| `Beneficiario` (nueva) | `beneficiario.entity` | `BeneficiarioTest` |
| `BeneficiarioRepository` (nueva) | `beneficiario.repository` | `BeneficiarioRepositoryTest` |
| `BeneficiarioService` (nueva) | `beneficiario.service` | `BeneficiarioServiceTest` |
| `BeneficiarioController` (nueva) | `beneficiario.controller` | `BeneficiarioIntegracionTest` |
| `CrearBeneficiarioRequest` (nueva) | `beneficiario.dto.request` | `CrearBeneficiarioRequestTest` |
| `ActualizarBeneficiarioRequest` (nueva) | `beneficiario.dto.request` | `ActualizarBeneficiarioRequestTest` |
| `BeneficiarioResponse` (nueva) | `beneficiario.dto.response` | `BeneficiarioResponseTest` |
| `CodigoError` (modificada) | `comun.excepcion` | `ManejadorGlobalExcepcionesTest` (si enumera códigos) |
| `Transaccion` (modificada) | `transaccion.entity` | `TransaccionTest` |
| `TransaccionResponse` (modificada) | `transaccion.dto.response` | `TransaccionResponseTest` |
| `TransaccionService` (modificada) | `transaccion.service` | `TransaccionServiceTest` |
| `TransaccionLoteService` (modificada) | `transaccion.service` | `TransaccionLoteServiceTest` |
| `TransaccionController` (sin cambios de código) | `transaccion.controller` | `TransaccionIntegracionTest` (casos nuevos de beneficiario y lote) |

`Beneficiario` lleva `@Getter @Setter @SuperBuilder @NoArgsConstructor @AllArgsConstructor`,
`@Table(name = "beneficiarios", uniqueConstraints = ...)`, y `nombre`, `nombreNormalizado` y
`categoriaPredeterminada` con `@Setter(AccessLevel.NONE)`, cambiados solo por
`renombrar(String)`, `cambiarCategoriaPredeterminada(Categoria)` (acepta `null`) y
`recordarCategoria(Categoria)`. `Beneficiario.normalizar(String)` es estática. El
`BeneficiarioIntegracionTest` y los de `transaccion` reutilizan las utilidades de test de
`CuentaIntegracionTest`/`TransaccionIntegracionTest` y `RelojDePrueba`.

## Risks / Trade-offs

- [El texto guardado en cada transacción puede quedar desactualizado si se renombra el
  beneficiario] → es intencional (renombrado automático fuera de alcance); el texto se vuelve a
  derivar al editar la transacción y `beneficiarioId` es la fuente de verdad para quien muestre
  el nombre actual.
- [Dos creaciones simultáneas de transacciones con un beneficiario nuevo del mismo nombre] → una
  gana y la otra recibe `409 BENEFICIARIO_YA_EXISTE` por la restricción única; reintentar la
  petición la vincula al ya creado. Es raro y evita un `REQUIRES_NEW` que complicaría la
  transacción de crear.
- [Transacciones existentes sin vínculo] → conviven con las vinculadas; se vinculan al editarlas,
  y `beneficiarioId` nulo no implica texto nulo.
- [`CATEGORIZAR` en lote carga hasta 100 beneficiarios lazy] → como máximo un `select` por
  beneficiario distinto; se acepta por el tope de 100 ids (se puede añadir `@BatchSize` si se mide
  un problema).
- [Nueva dependencia `transaccion → beneficiario`] → se documenta en `AGENTS.md` y se comprueba
  con `grep` que `beneficiario` no importa `transaccion` ni `asignacion`, y que `presupuesto`,
  `cuenta`, `categoria` y `asignacion` no importan `beneficiario`.
- [Orden por `nombreNormalizado` depende de la collation de PostgreSQL] → igual que en cuentas;
  los tests usan nombres cuyo orden es el mismo en cualquier collation razonable.
