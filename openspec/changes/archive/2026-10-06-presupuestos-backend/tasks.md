# Tasks

Todas las rutas de paquete son bajo `com.presupuesto`; los tests viven en el mismo subpaquete
bajo `src/test`. No hacer commit.

## 1. Entidad, repositorio y código de error

- [x] 1.1 Agregar `PRESUPUESTO_YA_EXISTE` a `CodigoError` (`comun.excepcion`); verificar que
  `.\mvnw.cmd -q compile` pasa.
- [x] 1.2 Crear `Presupuesto` (`presupuesto.entity`; `@Table(name = "presupuestos")` con
  unique `(usuario_id, nombre_normalizado)`; `usuario` ManyToOne LAZY obligatorio, `nombre`
  length 100, `nombreNormalizado`, `moneda` length 3 no editable con `updatable = false`;
  método `renombrar`) y su test `PresupuestoTest` (`presupuesto.entity`) que verifica la
  normalización a minúsculas y que `renombrar` la actualiza.
- [x] 1.3 Crear `PresupuestoRepository` (`presupuesto.repository`) con `findByIdAndUsuarioId`,
  `findByUsuarioIdOrderByNombreNormalizado`, `existsByUsuarioIdAndNombreNormalizado` y
  `existsByUsuarioIdAndNombreNormalizadoAndIdNot`, y `PresupuestoRepositoryTest`
  (`presupuesto.repository`, `@Transactional`) que verifica aislamiento por usuario, orden, y
  que la restricción única rechaza el mismo nombre normalizado del mismo usuario (forzando el
  flush con `saveAndFlush` dentro de `assertThrows`) pero permite el de otro usuario.

## 2. DTO

- [x] 2.1 Crear `CrearPresupuestoRequest` (`presupuesto.dto.request`; record `nombre` con
  `strip()` en el constructor compacto, `@NotBlank @Size(max = 100)`; `moneda` con
  `@MonedaValida`, nula si no se envía) y `CrearPresupuestoRequestTest`
  (`presupuesto.dto.request`) que cubre recorte, vacío, 101 caracteres y moneda `ZZZ`/minúscula.
- [x] 2.2 Crear `ActualizarPresupuestoRequest` (`presupuesto.dto.request`; record solo con
  `nombre`, mismas reglas) y `ActualizarPresupuestoRequestTest` (`presupuesto.dto.request`).
- [x] 2.3 Crear `PresupuestoResponse` (`presupuesto.dto.response`; `id`, `nombre`, `moneda`,
  `fechaCreacion`, `fechaActualizacion`, con `desde(Presupuesto)`) y `PresupuestoResponseTest`
  (`presupuesto.dto.response`) que verifica el mapeo de cada campo.

## 3. Service

- [x] 3.1 Crear `PresupuestoService` (`presupuesto.service`) con `obtenerDelUsuario`, `crear`,
  `crearInicial(Usuario, String)`, `listar`, `obtener` y `renombrar`; usa builder, `desde(...)`,
  la moneda del perfil si falta y traduce `DataIntegrityViolationException` a
  `PRESUPUESTO_YA_EXISTE`. Verificar con `PresupuestoServiceTest` (`presupuesto.service`,
  JUnit 5 + Mockito): moneda por defecto del perfil, moneda explícita, duplicado → 409, renombrar
  al mismo nombre con otras mayúsculas permitido, renombrar a nombre de otro propio → 409,
  `obtenerDelUsuario` inexistente o ajeno → `RecursoNoEncontradoException`, que `renombrar` no
  toca la moneda, que crear sin moneda y sin perfil lanza `NoAutenticadoException`, y que
  `crearInicial` usa el nombre `Mi presupuesto` y la moneda recibida.

## 4. Controller

- [x] 4.1 Crear `PresupuestoController` (`presupuesto.controller`, `/api/v1/presupuestos`,
  `POST` 201, `GET`, `GET /{id}`, `PUT /{id}`, sin `DELETE`, `@Valid` y
  `@AuthenticationPrincipal UsuarioAutenticado`) y `PresupuestoIntegracionTest`
  (`presupuesto.controller`, `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Transactional`):
  aislamiento entre usuarios (GET/PUT ajeno → 404 `RECURSO_NO_ENCONTRADO`, nunca 403), duplicado
  sin distinguir mayúsculas → 409 `PRESUPUESTO_YA_EXISTE`, mismo nombre en usuarios distintos →
  201, moneda por defecto del perfil, moneda no editable en el `PUT`, lista ordenada por nombre
  sin paginación, 400 por datos inválidos y 401 sin token en cada ruta.

## 5. Presupuesto inicial en el registro

- [x] 5.1 Modificar `AuthService` (`auth.service`) para llamar a
  `PresupuestoService.crearInicial` tras guardar el perfil, en la misma transacción, con nombre
  `Mi presupuesto` y la moneda del perfil. Verificar ampliando `RegistroIntegracionTest`
  (`auth.controller`): con moneda `USD` y sin moneda (`BOB`) el único presupuesto es
  `Mi presupuesto`, y `RegistroAtomicidadTest` (`auth.controller`): si falla
  `PresupuestoRepository.save`, no queda usuario ni perfil con ese email.

## 6. Documentación e integración

- [x] 6.1 Actualizar `AGENTS.md`: agregar `presupuesto/` al árbol de paquetes, la sección
  "Recursos de negocio cuelgan de un presupuesto" (URLs
  `/api/v1/presupuestos/{presupuestoId}/<recurso>` y validación con `obtenerDelUsuario`) y
  `presupuesto` en la alternancia del `grep` de dependencias de `comun/`; además, documentar que
  los comandos de Maven usan el wrapper (`.\mvnw.cmd` en Windows, `./mvnw` en Linux o macOS),
  nunca `mvn` directamente. Verificar releyendo
  las secciones y que el `grep` documentado devuelve cero líneas desde `backend/src`.
- [x] 6.2 Verificación final: `.\mvnw.cmd test` en `backend/` pasa completo (los 94 tests existentes más
  los nuevos) y `openspec validate presupuestos-backend` no reporta errores.
