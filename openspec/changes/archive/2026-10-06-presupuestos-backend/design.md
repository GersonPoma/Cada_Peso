# Design

## Context

Hoy `com.presupuesto` tiene `usuario` (con `Perfil.monedaPredeterminada`), `auth` y `comun`. El
registro (`AuthService.registrar`) ya es `@Transactional` y crea `Usuario` y `Perfil`; la moneda
del request llega ya con `BOB` por defecto. Los errores 404/409 salen de `comun/excepcion`, y los
controllers reciben `@AuthenticationPrincipal UsuarioAutenticado`. Ver `proposal.md` para el
motivo y las specs `presupuestos` y `registro-usuarios` para el comportamiento.

## Goals / Non-Goals

**Goals:**
- Una feature `presupuesto` conforme a la estructura obligatoria de AGENTS.md.
- Una única vía (`obtenerDelUsuario`) para validar la pertenencia, reutilizable por las features
  que cuelguen de un presupuesto.

**Non-Goals:**
- Migrar usuarios existentes sin presupuesto, borrado, archivado, frontend.
- Un presupuesto "activo" o "predeterminado" por usuario.

## Decisions

**1. Unicidad sin distinguir mayúsculas con columna `nombre_normalizado`.** La entidad guarda
`nombre` (como lo escribió la persona, recortado) y `nombreNormalizado` (`nombre` en minúsculas
con `Locale.ROOT`), con `@UniqueConstraint(usuario_id, nombre_normalizado)`. Se mantiene en un
solo lugar, el método `Presupuesto.renombrar(String)`, que el builder del service también usa
para inicializar. Sirve además para ordenar por nombre sin distinguir mayúsculas. Alternativas:
índice funcional `lower(nombre)` (Hibernate `ddl-auto=update` no lo declara de forma portable) y
solo `existsBy...IgnoreCase` (sin garantía ante concurrencia).

**2. Doble defensa ante duplicados.** El service consulta antes
(`existsByUsuarioIdAndNombreNormalizado`, y en el renombre `...AndIdNot`) para dar el 409 normal;
además hace `saveAndFlush` y traduce `DataIntegrityViolationException` a la misma
`ConflictoException(PRESUPUESTO_YA_EXISTE)`, igual que `AuthService` con el email.

**3. `obtenerDelUsuario` busca por `(id, usuarioId)` en una sola consulta**
(`findByIdAndUsuarioId`). Así "no existe" y "es de otra persona" son indistinguibles y devuelven
el mismo `RecursoNoEncontradoException` (404, nunca 403). Es público y de lectura; las features
futuras lo inyectan y lo llaman antes de cualquier operación bajo
`/api/v1/presupuestos/{presupuestoId}/...`.

**4. Moneda por defecto desde el perfil.** Si el request no trae moneda, el service lee
`PerfilRepository.findByUsuarioId` (de `usuario`; `presupuesto` puede depender de `usuario`). Un
token válido sin perfil es un caso imposible salvo datos corruptos: se trata como
`NoAutenticadoException`, igual que `UsuarioService.obtenerActual`. La moneda no se normaliza ni
se pone por defecto en el record (a diferencia de `RegistroRequest`), porque el valor por defecto
depende del usuario.

**5. PUT solo renombra.** `ActualizarPresupuestoRequest` tiene únicamente `nombre`; Jackson ignora
propiedades desconocidas (comportamiento por defecto de Spring Boot), así que una `moneda` enviada
se descarta sin error. Se prefiere ignorar a rechazar para no romper clientes que reenvían el
objeto completo.

**6. Presupuesto inicial vía `PresupuestoService.crearInicial(Usuario, String moneda)`.**
`AuthService` lo llama tras guardar el perfil, dentro de su misma transacción (el service de
presupuesto usa `@Transactional` con propagación `REQUIRED`, se une a la existente). Va en
`PresupuestoService` y no en `AuthService` para que el nombre `Mi presupuesto` y la regla de
creación vivan en la feature dueña. Dependencia permitida: `auth` → `presupuesto` → `usuario`;
`comun` no importa ninguna. Se pasa la entidad `Usuario` ya persistida (no hace falta recargarla).

**7. Lista sin paginación**: `findByUsuarioIdOrderByNombreNormalizado`, devuelve `List`.

**8. Códigos**: se agrega `PRESUPUESTO_YA_EXISTE` a `CodigoError`; los 404 usan
`RecursoNoEncontradoException` con el mensaje fijo `Presupuesto no encontrado`.

**9. Convención en AGENTS.md** (nueva sección "Recursos de negocio cuelgan de un presupuesto"):
URLs `/api/v1/presupuestos/{presupuestoId}/<recurso>`, cada operación llama
`PresupuestoService.obtenerDelUsuario(presupuestoId, usuario.id())` antes de tocar datos, y el
árbol de paquetes actual incluye `presupuesto/`. También se amplía la alternancia del `grep` de
dependencias de `comun/` con `presupuesto`. Se documenta también que los comandos de Maven usan
el wrapper (`.\mvnw.cmd` en Windows, `./mvnw` en Linux o macOS), nunca `mvn` directamente.

### Clases nuevas o modificadas

| Clase | Paquete | Test (paquete) |
|---|---|---|
| `Presupuesto` | `com.presupuesto.presupuesto.entity` | `PresupuestoTest` (`...entity`) |
| `PresupuestoRepository` | `com.presupuesto.presupuesto.repository` | `PresupuestoRepositoryTest` (`...repository`) |
| `CrearPresupuestoRequest` | `com.presupuesto.presupuesto.dto.request` | `CrearPresupuestoRequestTest` (`...dto.request`) |
| `ActualizarPresupuestoRequest` | `com.presupuesto.presupuesto.dto.request` | `ActualizarPresupuestoRequestTest` (`...dto.request`) |
| `PresupuestoResponse` | `com.presupuesto.presupuesto.dto.response` | `PresupuestoResponseTest` (`...dto.response`) |
| `PresupuestoService` | `com.presupuesto.presupuesto.service` | `PresupuestoServiceTest` (`...service`, Mockito) |
| `PresupuestoController` | `com.presupuesto.presupuesto.controller` | `PresupuestoIntegracionTest` (`...controller`) |
| `AuthService` (modificada) | `com.presupuesto.auth.service` | `RegistroIntegracionTest`, `RegistroAtomicidadTest` (`com.presupuesto.auth.controller`, ampliados) |
| `CodigoError` (modificada) | `com.presupuesto.comun.excepcion` | cubierto por `PresupuestoIntegracionTest` |

`Normalizacion` (package-private en `auth.dto.request`) no se reutiliza: el recorte del nombre se
hace en el constructor compacto de cada request con `strip()`.

## Risks / Trade-offs

- [Un test o dato que borre un `Usuario` con presupuestos falla por la FK] → los tests que
  borran usuarios (`RegistroAtomicidadTest`) solo lo hacen tras un registro fallido (sin
  presupuesto, por el rollback); cualquier test nuevo no transaccional borra primero los
  presupuestos.
- [Usuarios ya existentes quedan sin presupuesto] → fuera de alcance; el `POST` les permite crear
  uno manualmente.
- [Minúsculas con `Locale.ROOT` no cubren todo el plegado Unicode] → aceptable para nombres de
  presupuesto; documentado en la entidad.
- [`registrar` ahora hace una inserción más] → despreciable.
