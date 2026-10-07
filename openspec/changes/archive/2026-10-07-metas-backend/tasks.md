# Tasks

Comandos desde `backend/` con `.\mvnw.cmd` y JDK 21. Línea base: 884 tests pasando. Los tests
de integración usan `@Transactional`, crean sus propios datos y consultan solo dentro de su
presupuesto. No hacer commit.

## 1. Cambios internos en `asignacion` y `categoria`

- [x] 1.1 Hacer `public` `com.presupuesto.asignacion.service.MesParametro` (clase e
  `interpretar`); los tests de `MesParametroTest` (mismo paquete) siguen pasando.
- [x] 1.2 Hacer `public` `com.presupuesto.asignacion.service.CalculoMensual` y sus records
  `FilaMes` y `ResultadoMes`, y agregar `MesPresupuestoService.calcular(Long, YearMonth)`
  (público, delega en `CalculadoraMes`); test en
  `com.presupuesto.asignacion.service.MesPresupuestoServiceTest`.
- [x] 1.3 Agregar `AsignacionMensualRepository.findByCategoriaIdInAndMes` en
  `com.presupuesto.asignacion.repository` y `AsignacionService.fijarAsignados(Long presupuestoId,
  YearMonth mes, Map<Long, Long> asignados)` en `com.presupuesto.asignacion.service` (resuelve
  todas las categorías antes de escribir, upsert por lote, un `saveAllAndFlush`, sin
  recalcular); tests en `AsignacionServiceTest` (crea y actualiza, categoría ajena = 404 sin
  cambiar nada, mapa vacío) y `AsignacionMensualRepositoryTest`.
- [x] 1.4 Agregar a `com.presupuesto.categoria.repository.CategoriaRepository` las tres
  consultas ordenadas por grupo y orden del design; test en
  `com.presupuesto.categoria.repository.CategoriaRepositoryTest` (orden del árbol, ocultas, `In`).
  Verificar: `.\mvnw.cmd test` sigue en 884 + los tests nuevos de esta sección.

## 2. Entidades, repositorios y enums

- [x] 2.1 `com.presupuesto.meta.entity.TipoMeta` y `FrecuenciaMeta` (enums persistidos como
  texto).
- [x] 2.2 `com.presupuesto.meta.entity.Meta` (tabla `metas`, único por `categoria_id`, setters
  bloqueados, `reemplazar(...)`); test en `com.presupuesto.meta.entity.MetaTest`.
- [x] 2.3 `com.presupuesto.meta.entity.MetaPospuesta` (tabla `metas_pospuestas`, único
  `(meta, mes)`, `mes` siempre día 1); test en `com.presupuesto.meta.entity.MetaPospuestaTest`.
- [x] 2.4 `com.presupuesto.meta.repository.MetaRepository` (por categoría, lista ordenada por
  grupo y categoría con `join fetch`, por presupuesto) y `MetaPospuestaRepository` (existe,
  borrar por meta y mes, borrar por meta, ids de metas pospuestas de un presupuesto y mes);
  tests en `com.presupuesto.meta.repository.MetaRepositoryTest` (segunda meta de la misma
  categoría con `saveAndFlush` en `assertThrows`, orden, aislamiento) y
  `MetaPospuestaRepositoryTest` (única `(meta, mes)`).

## 3. Cálculo puro

- [x] 3.1 `com.presupuesto.meta.service.CalendarioMeta` (package-private, funciones puras:
  lunes de un mes, vencimientos personalizados, meses restantes, división hacia arriba); test
  en `com.presupuesto.meta.service.CalendarioMetaTest` sin base: ejemplos B y C, antes de
  `fechaInicio`, cada 30 días desde `2026-10-02` (febrero de 2027 = 0 porque el siguiente es el 1
  de marzo; diciembre de 2026 = 2, el 1 y el 31), cruce de año, objetivo pasado (1 mes),
  `ceil(100000 / 3) = 33334`.
- [x] 3.2 `com.presupuesto.meta.dto.response.EstadoMeta` y
  `com.presupuesto.meta.service.CalculoMeta` (necesidad, faltante y estado); test en
  `com.presupuesto.meta.service.CalculoMetaTest` con los ejemplos A, B, C, D, E, F y G con sus
  números exactos y la prioridad de estados.

## 4. DTO y validación

- [x] 4.1 `com.presupuesto.meta.validacion.MetaCoherente` y `MetaCoherenteValidator` (violaciones
  en el campo concreto); test en `com.presupuesto.meta.validacion.MetaCoherenteValidatorTest`.
- [x] 4.2 `com.presupuesto.meta.dto.request.GuardarMetaRequest` (record, descarta los campos que
  no aplican en el constructor compacto), `AutoAsignarRequest` y `EstrategiaAutoAsignar`; tests en
  `com.presupuesto.meta.dto.request.GuardarMetaRequestTest` (cada tipo, faltantes, rangos,
  descarte) y `AutoAsignarRequestTest` (`simular` por defecto falso, `categoriaIds: []` inválida).
- [x] 4.3 `com.presupuesto.meta.dto.response.MetaResponse`, `MetaMesResponse`, `MetasMesResponse`,
  `AutoAsignarResponse` y `CambioAsignacionResponse`, cada uno con `desde(...)`; tests en
  `com.presupuesto.meta.dto.response` (`MetaResponseTest`, `MetaMesResponseTest`,
  `MetasMesResponseTest`, `AutoAsignarResponseTest`).

## 5. Services

- [x] 5.1 `com.presupuesto.meta.service.MetaService` (guardar sin duplicar, obtener, borrar con
  sus pospuestas, listar; siempre `obtenerDelUsuario` primero, categoría oculta permitida); test
  en `com.presupuesto.meta.service.MetaServiceTest` (aislamiento entre usuarios y entre
  presupuestos del mismo usuario, categoría oculta, reemplazar no duplica, borrar con pospuestas).
- [x] 5.2 `com.presupuesto.meta.service.MetaMesService` (estado del mes con `incluirOcultas` y
  `totalFaltante`, posponer y reanudar idempotentes y solo en ese mes; mes interpretado después
  de validar el presupuesto); test en `com.presupuesto.meta.service.MetaMesServiceTest`.
- [x] 5.3 `com.presupuesto.meta.service.AutoAsignarService` (cinco estrategias, `simular`,
  `categoriaIds`, `listoParaAsignarDespues`, un solo `fijarAsignados`); test en
  `com.presupuesto.meta.service.AutoAsignarServiceTest` (cada estrategia con los números de la
  spec, simular no guarda, oculta explícita, ajena = 404 sin aplicar nada, sin cambios,
  `listoParaAsignarDespues` negativo, mes anterior a 2000-01, atomicidad).

## 6. Controllers

- [x] 6.1 `com.presupuesto.meta.controller.MetaController` (`PUT|GET|DELETE
  /categorias/{categoriaId}/meta` y `GET /metas`, con `@Valid` y `@AuthenticationPrincipal
  UsuarioAutenticado`); test en `com.presupuesto.meta.controller.MetaIntegracionTest` (401 con
  `NO_AUTENTICADO`, 404 con `RECURSO_NO_ENCONTRADO` en cada operación ajena o de otro
  presupuesto, 400 `DATOS_INVALIDOS` con el campo en `errores`, 204, orden de la lista, reemplazar conserva las pospuestas: pospone
  `2026-10`,
  reemplaza la meta y comprueba que `GET /meses/2026-10/metas` sigue en `POSPUESTA`).
- [x] 6.2 `com.presupuesto.meta.controller.MetaMesController` (`GET /meses/{mes}/metas`, `POST
  .../metas/{categoriaId}/posponer|reanudar`, `POST /meses/{mes}/auto-asignar`); test en
  `com.presupuesto.meta.controller.MetaMesIntegracionTest` (401, 404 con el mes inválido sobre
  un presupuesto ajeno, 400 de mes y de estrategia con el código, ejemplos de la spec de punta a
  punta, simular sin cambios guardados, `categoriaIds: []` responde 400 `DATOS_INVALIDOS` y
  los asignados no cambian).

## 7. AGENTS.md y verificación

- [x] 7.1 En `AGENTS.md`: agregar `meta/` (con `controller`, `dto/request`, `dto/response`,
  `entity`, `repository`, `service`, `validacion`) al árbol de paquetes; `meta` a la regla de
  dependencias (depende de `presupuesto`, `categoria`, `asignacion` y `comun`; esas no importan
  `meta`) y a la alternancia del `grep` de `comun/` (`...|beneficiario|meta)`).
- [x] 7.2 Comprobar desde `backend/src`, y mostrar la salida (debe ser cero líneas en todas):
  `grep -rnE "import com\.presupuesto\.meta" main/java/com/presupuesto/{presupuesto,categoria,asignacion}`
  y lo mismo sobre `test/java/com/presupuesto/{presupuesto,categoria,asignacion}`, y el `grep`
  de `comun/` con `meta` en la alternancia sobre `main` y `test`.
- [x] 7.3 Correr `.\mvnw.cmd test` completo y mostrar el total: los 884 existentes más los
  nuevos, todos en verde.
- [x] 7.4 Revisar que ninguna línea pase de 100 columnas ni haya tildes o `ñ` en nombres de
  código, y dejar el trabajo sin commit.
