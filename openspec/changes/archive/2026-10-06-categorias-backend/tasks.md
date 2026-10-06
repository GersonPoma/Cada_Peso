# Tasks

Todos los comandos de Maven usan el wrapper (`.\mvnw.cmd` desde `backend/`) con JDK 21. Los
tests de repositorio y de integración llevan `@Transactional`. No hacer commit.

## 1. Infraestructura común

- [x] 1.1 Agregar `GRUPO_CATEGORIA_YA_EXISTE` y `CATEGORIA_YA_EXISTE` a `CodigoError`
  (`com.presupuesto.comun.excepcion`). Verificar: compila y los usan las tareas 5.1 y 5.2.
- [x] 1.2 Crear `DatosInvalidosException` (`com.presupuesto.comun.excepcion`, extiende
  `NegocioException` con `CodigoError.DATOS_INVALIDOS`) y su test `DatosInvalidosExceptionTest`
  (`com.presupuesto.comun.excepcion`). Verificar: el test comprueba código y mensaje.
- [x] 1.3 Agregar a `ManejadorGlobalExcepciones` (`com.presupuesto.comun.excepcion`) un handler de
  `DatosInvalidosException` que responde `400` con `codigo` `DATOS_INVALIDOS`, sin `errores`, y
  un caso nuevo en `ManejadorGlobalExcepcionesTest` (`com.presupuesto.comun.excepcion`).
  Verificar: `.\mvnw.cmd test -Dtest=ManejadorGlobalExcepcionesTest` en verde, con aserción de
  estado `400` y de código `DATOS_INVALIDOS`.

## 2. Entidades

- [x] 2.1 Crear `GrupoCategoria` (`com.presupuesto.categoria.entity`, extiende `EntidadBase`,
  tabla `grupos_categoria`, restricción `uk_grupos_categoria_presupuesto_nombre`, setters
  bloqueados en nombre, nombreNormalizado, orden y oculto; métodos `normalizar`, `renombrar`,
  `asignarOrden`, `ocultar`, `mostrar`) y su test `GrupoCategoriaTest`
  (`com.presupuesto.categoria.entity`). Verificar: el test comprueba que `renombrar` mantiene
  `nombreNormalizado` (con `Locale.ROOT`), que `ocultar`/`mostrar` son idempotentes, que `oculto`
  nace en `false` y, por reflexión, que no existen `setNombre`, `setNombreNormalizado`,
  `setOrden` ni `setOculto`.
- [x] 2.2 Crear `Categoria` (`com.presupuesto.categoria.entity`, extiende `EntidadBase`, tabla
  `categorias`, restricción `uk_categorias_grupo_nombre`, setters bloqueados en grupo, nombre,
  nombreNormalizado, orden, oculta y nota; métodos `normalizar`, `renombrar`, `cambiarNota`,
  `asignarOrden`, `moverAGrupo`, `ocultar`, `mostrar`) y su test `CategoriaTest`
  (`com.presupuesto.categoria.entity`). Verificar: el test comprueba el mantenimiento de
  `nombreNormalizado`, `cambiarNota`, `moverAGrupo`, idempotencia de ocultar/mostrar y, por
  reflexión, que no existen `setNombre`, `setNombreNormalizado`, `setOrden`, `setOculta`,
  `setNota` ni `setGrupo`.

## 3. Repositorios

- [x] 3.1 Crear `GrupoCategoriaRepository` (`com.presupuesto.categoria.repository`) con
  `findByIdAndPresupuestoId`, los listados por presupuesto ordenados por `orden` (con y sin
  ocultos), `countByPresupuestoId` y `existsBy...` (con y sin `IdNot`), y su test
  `GrupoCategoriaRepositoryTest` (`com.presupuesto.categoria.repository`, `@Transactional`).
  Verificar: el test cubre el acotado por presupuesto, el filtro de ocultos, el orden y la
  restricción única con `saveAndFlush` dentro de
  `assertThrows(DataIntegrityViolationException.class, ...)`; el mismo nombre en otro
  presupuesto sí se guarda.
- [x] 3.2 Crear `CategoriaRepository` (`com.presupuesto.categoria.repository`) con
  `findByIdAndGrupoPresupuestoId`, el listado de un grupo ordenado por `orden`, el de todo un
  presupuesto ordenado por `orden` (con y sin ocultas), `countByGrupoId` y `existsBy...` (con y
  sin `IdNot`), y su test `CategoriaRepositoryTest` (`com.presupuesto.categoria.repository`,
  `@Transactional`). Verificar: el test cubre el acotado por presupuesto, el filtro de ocultas,
  la restricción única con `saveAndFlush` dentro de `assertThrows`, y que el mismo nombre en
  grupos distintos sí se guarda.

## 4. DTO

- [x] 4.1 Crear `Normalizacion` (`com.presupuesto.categoria.dto.request`, package-private, con
  `strip` de nombre y de nota nula si queda vacía) y su test `NormalizacionTest`
  (`com.presupuesto.categoria.dto.request`). Verificar: `null`, vacío y solo espacios dan `null`;
  el texto se recorta.
- [x] 4.2 Crear `CrearGrupoCategoriaRequest` y `ActualizarGrupoCategoriaRequest`
  (`com.presupuesto.categoria.dto.request`, records con `nombre` recortado) y sus tests
  `CrearGrupoCategoriaRequestTest` y `ActualizarGrupoCategoriaRequestTest`
  (`com.presupuesto.categoria.dto.request`). Verificar: nombre recortado; nulo, vacío y de 101
  caracteres inválidos; de 100 válido.
- [x] 4.3 Crear `CrearCategoriaRequest` y `ActualizarCategoriaRequest`
  (`com.presupuesto.categoria.dto.request`, records con nombre y nota normalizados) y sus tests
  `CrearCategoriaRequestTest` y `ActualizarCategoriaRequestTest`
  (`com.presupuesto.categoria.dto.request`). Verificar: nombre como en 4.2; nota recortada, vacía
  a `null`, de 500 válida y de 501 inválida; `grupoId` nulo inválido en el de crear.
- [x] 4.4 Crear `MoverGrupoCategoriaRequest` y `MoverCategoriaRequest`
  (`com.presupuesto.categoria.dto.request`, records con `posicion` y, el segundo, `grupoId`) y
  sus tests `MoverGrupoCategoriaRequestTest` y `MoverCategoriaRequestTest`
  (`com.presupuesto.categoria.dto.request`). Verificar: `posicion` nula o negativa inválida y
  `0` válida; `grupoId` nulo inválido.
- [x] 4.5 Crear `GrupoCategoriaResponse`, `CategoriaResponse` y
  `GrupoCategoriaConCategoriasResponse` (`com.presupuesto.categoria.dto.response`, records con
  `desde(...)`) y sus tests `GrupoCategoriaResponseTest`, `CategoriaResponseTest` y
  `GrupoCategoriaConCategoriasResponseTest` (`com.presupuesto.categoria.dto.response`).
  Verificar: todos los campos mapeados desde la entidad, `grupoId` incluido, y la lista de
  categorías conserva el orden recibido.

## 5. Services

- [x] 5.1 Crear `GrupoCategoriaService` (`com.presupuesto.categoria.service`) con `crear`,
  `renombrar`, `ocultar`, `mostrar` y `mover`: cada método llama primero a
  `PresupuestoService.obtenerDelUsuario`, busca por `(id, presupuestoId)`, aplica la doble
  defensa de duplicados (409 `GRUPO_CATEGORIA_YA_EXISTE`), el orden al final al crear y la
  renumeración sin huecos al mover (`DatosInvalidosException` si `posicion` está fuera de
  `[0, n-1]`), y construye con el builder. Test `GrupoCategoriaServiceTest`
  (`com.presupuesto.categoria.service`, Mockito). Verificar: cubre 404 por presupuesto y por
  grupo en cada operación, duplicado por consulta previa y por `DataIntegrityViolationException`,
  conservar el propio nombre, orden consecutivo al crear, mover adelante, atrás y a la misma
  posición, grupos ocultos incluidos en la renumeración y `posicion` fuera de rango.
- [x] 5.2 Crear `CategoriaService` (`com.presupuesto.categoria.service`) con `crear`, `obtener`,
  `actualizar`, `ocultar`, `mostrar`, `mover` y `arbol`: valida el presupuesto, resuelve el grupo
  con `GrupoCategoriaRepository.findByIdAndPresupuestoId`, aplica la doble defensa de duplicados
  (409 `CATEGORIA_YA_EXISTE`, también al mover al grupo destino), el orden al final del grupo y
  la renumeración de origen y destino (rango `[0, n-1]` en el mismo grupo y `[0, m]` en otro), y
  arma el árbol con dos consultas y el filtro `incluirOcultas`. Test `CategoriaServiceTest`
  (`com.presupuesto.categoria.service`, Mockito). Verificar: cubre 404 por presupuesto, categoría
  y grupo (en crear y en mover), duplicado por consulta previa y por excepción de integridad,
  mismo nombre en otro grupo, orden consecutivo, mover dentro del grupo y a otro grupo (incluido
  vacío y al final) sin huecos ni repetidos, `posicion` fuera de rango sin modificar nada,
  nombre repetido en el destino y el filtro del árbol.

## 6. Controllers

- [x] 6.1 Crear `GrupoCategoriaController` (`com.presupuesto.categoria.controller`) bajo
  `/api/v1/presupuestos/{presupuestoId}/grupos-categorias` con `POST` (201), `PUT /{id}`,
  `POST /{id}/ocultar`, `POST /{id}/mostrar` y `POST /{id}/mover`, con `@Valid` y
  `@AuthenticationPrincipal UsuarioAutenticado`, y sin `DELETE`. Test
  `GrupoCategoriaIntegracionTest` (`com.presupuesto.categoria.controller`, MockMvc,
  `@Transactional`). Verificar: cubre todos los escenarios de grupos de
  `specs/categorias/spec.md`: 401 sin token; `400` con código `DATOS_INVALIDOS` (no solo el
  estado) para nombre inválido, `posicion` negativa, ausente o fuera de rango; aislamiento entre
  usuarios con una aserción de `404` propia para cada operación (crear, renombrar, ocultar,
  mostrar y mover); grupo de otro presupuesto del mismo usuario con su propio `404` por
  operación; duplicado sin distinguir mayúsculas con código `GRUPO_CATEGORIA_YA_EXISTE`; mismo
  nombre en otro presupuesto; orden consecutivo; mover sin huecos; ocultar/mostrar idempotentes;
  `orden` y `oculto` ignorados en el `PUT`; `405` en `DELETE`.
- [x] 6.2 Crear `CategoriaController` (`com.presupuesto.categoria.controller`) bajo
  `/api/v1/presupuestos/{presupuestoId}/categorias` con `POST` (201), `GET` (árbol, con
  `incluirOcultas`), `GET /{id}`, `PUT /{id}`, `POST /{id}/ocultar`, `POST /{id}/mostrar` y
  `POST /{id}/mover`, con `@Valid` y `@AuthenticationPrincipal UsuarioAutenticado`, y sin
  `DELETE`. Test `CategoriaIntegracionTest` (`com.presupuesto.categoria.controller`, MockMvc,
  `@Transactional`). Verificar: cubre todos los escenarios de categorías y del árbol de
  `specs/categorias/spec.md`: 401 sin token; `400` con código `DATOS_INVALIDOS` para nombre
  inválido, nota de 501, `grupoId` ausente y `posicion` inválida o fuera de rango; aislamiento
  entre usuarios con una aserción de `404` propia para cada una de las siete operaciones (crear,
  árbol, detalle, editar, ocultar, mostrar y mover); categoría o grupo de otro presupuesto del
  mismo usuario con su propio `404` (detalle, editar, ocultar, mostrar, mover, crear con ese
  `grupoId` y mover a ese grupo); duplicado sin distinguir mayúsculas con código
  `CATEGORIA_YA_EXISTE`; mismo nombre en grupos distintos; orden consecutivo; nota recortada,
  vacía a `null` y de 500; mover dentro del grupo y a otro grupo (con grupo vacío) sin huecos ni
  repetidos y `409` por nombre en el destino; ocultar/mostrar idempotentes; `grupoId` y `orden`
  ignorados en el `PUT`; árbol ordenado con y sin `incluirOcultas`; `405` en `DELETE`.

## 7. Documentación y verificación final

- [x] 7.1 Actualizar `AGENTS.md`: agregar `categoria/` al árbol de paquetes actual, la
  dependencia `categoria` → `presupuesto` y `comun` (con `presupuesto` y `cuenta` sin importar
  `categoria`), `categoria` a la alternancia del `grep` de `comun/` y `DatosInvalidosException`
  (400 `DATOS_INVALIDOS`) a la jerarquía de excepciones. Verificar: el diff muestra esos
  cambios.
- [x] 7.2 Ejecutar desde `backend/src` los `grep` de dependencias y mostrar su salida:
  `grep -rnE "import com\.presupuesto\.(usuario|auth|presupuesto|cuenta|categoria)" <dir>` para
  `main/java/com/presupuesto/comun` y `test/java/com/presupuesto/comun`, y
  `grep -rn "com.presupuesto.categoria" main/java/com/presupuesto/presupuesto
  main/java/com/presupuesto/cuenta`. Verificar: los tres devuelven cero líneas.
- [x] 7.3 Ejecutar la suite completa: `.\mvnw.cmd test` desde `backend/` con JDK 21. Verificar:
  los 227 tests existentes y los nuevos pasan. No hacer commit.
