# Tasks

Comandos desde `backend/` con `.\mvnw.cmd` (JDK 21). Los paquetes son `com.presupuesto.<...>`.

## 1. Feature `beneficiario`: entidad, repositorio y código de error

- [x] 1.1 Agregar `BENEFICIARIO_YA_EXISTE` a `CodigoError` (`comun.excepcion`); verificar que
  `ManejadorGlobalExcepcionesTest` (`comun.excepcion`) sigue pasando con `.\mvnw.cmd test
  -Dtest=ManejadorGlobalExcepcionesTest`.
- [x] 1.2 Crear `Beneficiario` en `beneficiario.entity` (tabla `beneficiarios`, extiende
  `EntidadBase`, `presupuesto` LAZY obligatorio, `nombre`, `nombreNormalizado` y
  `categoriaPredeterminada` con `@Setter(AccessLevel.NONE)`, `Beneficiario.normalizar`,
  `renombrar`, `cambiarCategoriaPredeterminada`, `recordarCategoria`, restricción única
  `uk_beneficiarios_presupuesto_nombre`). Test `BeneficiarioTest` en `beneficiario.entity`:
  normalización con `Locale.ROOT`, renombrar mantiene sincronizado el campo derivado, quitar y
  recordar categoría; pasa con `.\mvnw.cmd test -Dtest=BeneficiarioTest`.
- [x] 1.3 Crear `BeneficiarioRepository` en `beneficiario.repository`:
  `findByIdAndPresupuestoId`, `findByPresupuestoIdOrderByNombreNormalizado`,
  `existsByPresupuestoIdAndNombreNormalizado[AndIdNot]`, `findByPresupuestoIdAndNombreNormalizado`
  y la consulta por prefijo con `escape '\'`, orden por `nombreNormalizado` y `Pageable`. Test
  `BeneficiarioRepositoryTest` en `beneficiario.repository` (`@Transactional`): restricción
  única con `saveAndFlush` dentro de `assertThrows`, mismo nombre en presupuestos distintos,
  prefijo sin distinguir mayúsculas, `%` y `_` literales, límite y orden; pasa con
  `.\mvnw.cmd test -Dtest=BeneficiarioRepositoryTest`.

## 2. Feature `beneficiario`: DTOs, service y controller

- [x] 2.1 Crear los records `CrearBeneficiarioRequest` y `ActualizarBeneficiarioRequest` en
  `beneficiario.dto.request` (`nombre` `@NotBlank @Size(max = 100)` con `strip()` en el
  constructor compacto, `categoriaId` opcional) y `BeneficiarioResponse` en
  `beneficiario.dto.response` (`id`, `nombre`, `categoriaPredeterminadaId`, método
  `desde(Beneficiario)`). Tests `CrearBeneficiarioRequestTest` y
  `ActualizarBeneficiarioRequestTest` (`beneficiario.dto.request`) y `BeneficiarioResponseTest`
  (`beneficiario.dto.response`); pasan con `.\mvnw.cmd test -Dtest="*Beneficiario*Test"`.
- [x] 2.2 Crear `BeneficiarioService` en `beneficiario.service` con `crear`, `listar(q,
  limite)`, `obtener`, `actualizar` y `obtenerOCrear(Presupuesto, String)`; cada operación HTTP
  empieza con `PresupuestoService.obtenerDelUsuario`, usa builder, traduce
  `DataIntegrityViolationException` a `ConflictoException(BENEFICIARIO_YA_EXISTE)`, valida
  `limite` entre 1 y 50 (`DatosInvalidosException`) y resuelve la categoría con
  `CategoriaRepository.findByIdAndGrupoPresupuestoId` (404 si es ajena). Test
  `BeneficiarioServiceTest` en `beneficiario.service` (Mockito): duplicado sin distinguir
  mayúsculas, conservar el propio nombre al editar, categoría ajena, quitar categoría, `q` vacío
  como ausente, escape de comodines, `limite` fuera de rango, `obtenerOCrear` que reutiliza o crea,
  y la carrera de unicidad; pasa con `.\mvnw.cmd test -Dtest=BeneficiarioServiceTest`.
- [x] 2.3 Crear `BeneficiarioController` en `beneficiario.controller` (`GET`, `POST` con `201`,
  `GET /{id}`, `PUT /{id}`; `@Valid`, `@AuthenticationPrincipal UsuarioAutenticado`, parámetros
  `q` y `limite`; sin `DELETE`). Test `BeneficiarioIntegracionTest` en `beneficiario.controller`
  (MockMvc, `@Transactional`): `401` sin token, aislamiento entre usuarios y entre presupuestos
  del mismo usuario con su `404` por operación, `409 BENEFICIARIO_YA_EXISTE` verificando el
  `codigo`, mismo nombre en otro presupuesto, categoría ajena, oculta y quitada, búsqueda por
  prefijo (mayúsculas, `%`, `_`, `limite`, orden, sin `q`), `400` por `limite` fuera de rango o
  no numérico y `405` en `DELETE`; pasa con `.\mvnw.cmd test -Dtest=BeneficiarioIntegracionTest`.

## 3. Vínculo en `transaccion`

- [x] 3.1 Modificar `Transaccion` (`transaccion.entity`): campo `beneficiarioVinculado`
  (`@ManyToOne(LAZY)`, `@JoinColumn(name = "beneficiario_id")`, `@Setter(AccessLevel.NONE)`) y
  `editar(...)` que recibe el `Beneficiario` y deriva el texto `beneficiario`; modificar
  `TransaccionResponse` (`transaccion.dto.response`) con `beneficiarioId` (nulo si no hay).
  Ajustar `TransaccionTest` y `TransaccionResponseTest` y verificar con `.\mvnw.cmd test
  -Dtest="TransaccionTest,TransaccionResponseTest"`.
- [x] 3.2 Modificar `TransaccionService` (`transaccion.service`): en `crear` y `actualizar`,
  después de validar cuenta, categoría y división, resolver el beneficiario con
  `BeneficiarioService.obtenerOCrear` (texto no vacío), vincularlo y recordar la categoría cuando
  `division.categoria()` no es nula; sin beneficiario, dejar la transacción sin vínculo;
  `duplicar` copia texto y vínculo; `moverCuenta`, `aprobar` y `cambiarEstado` no cambian. Casos
  nuevos en `TransaccionServiceTest` (`transaccion.service`): beneficiario nuevo se crea y se
  vincula, existente con otras mayúsculas se reutiliza, categoría recordada al crear y al editar,
  dividida y sin categoría no la cambian, sin beneficiario queda sin vínculo, categoría ajena no
  crea beneficiario, duplicar copia el vínculo, y transacciones sin vínculo siguen funcionando;
  pasa con `.\mvnw.cmd test -Dtest=TransaccionServiceTest`.
- [x] 3.3 Modificar `TransaccionLoteService` (`transaccion.service`): `CATEGORIZAR` llama a
  `recordarCategoria` en el beneficiario vinculado de cada transacción, solo después de las
  validaciones. Casos nuevos en `TransaccionLoteServiceTest` (`transaccion.service`): actualiza
  los beneficiarios vinculados, ignora las transacciones sin beneficiario y un lote rechazado no
  toca ninguno; pasa con `.\mvnw.cmd test -Dtest=TransaccionLoteServiceTest`.
- [x] 3.4 Casos nuevos HTTP en `TransaccionIntegracionTest` (`transaccion.controller`):
  `beneficiarioId` en detalle y listado, crear con beneficiario nuevo y existente con otras
  mayúsculas, vínculo y categoría recordada al editar, quitar el beneficiario, transacción
  preexistente sin vínculo, y lote `CATEGORIZAR`; pasa con
  `.\mvnw.cmd test -Dtest=TransaccionIntegracionTest`.
- [x] 3.5 Caso nuevo en `TransferenciaIntegracionTest` (`transaccion.controller`), escenario
  "Una pata de transferencia no tiene beneficiario": crear una transferencia, consultar cada
  pata por `GET /transacciones/{id}` y en el listado, y comprobar que `beneficiario` y
  `beneficiarioId` son nulos en ambas y que `GET /beneficiarios` sigue vacío; pasa con
  `.\mvnw.cmd test -Dtest=TransferenciaIntegracionTest`.

## 4. Documentación y verificación final

- [x] 4.1 Actualizar `AGENTS.md`: agregar `beneficiario/` al árbol, a la regla de dependencias
  (`beneficiario` depende de `presupuesto`, `categoria` y `comun`; `transaccion` también de
  `beneficiario`; ninguna otra feature lo importa) y a la alternancia del `grep` de `comun/`;
  verificar releyendo la sección y que el `grep` sigue siendo ejecutable.
- [x] 4.2 Ejecutar y mostrar, desde `backend/src`, los `grep -rnE "import com\.presupuesto\.X"`
  sobre `main/java/com/presupuesto/<feature>` y `test/java/com/presupuesto/<feature>` para
  comprobar con cero líneas que `presupuesto`, `cuenta`, `categoria` y `asignacion` no importan
  `beneficiario`, que `beneficiario` no importa `transaccion` ni `asignacion`, y que `comun` no
  importa ninguna feature (incluida `beneficiario`).
- [x] 4.3 Ejecutar la suite completa con `.\mvnw.cmd test` y mostrar el total: pasan los 773
  tests existentes (solo se ajustaron los que dependen de `TransaccionResponse` o de
  `Transaccion.editar`) más los nuevos, sin fallos; `openspec validate beneficiarios-backend`
  sin errores. No se hace commit.
