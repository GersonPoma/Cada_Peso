## 1. Infraestructura en `comun`

- [x] 1.1 Crear `PaginaResponse<T>` (`com.presupuesto.comun.paginacion`, record
  `contenido, pagina, tamano, totalElementos, totalPaginas` con
  `desde(Page<E>, Function<E, T>)`) y su test `PaginaResponseTest`
  (`com.presupuesto.comun.paginacion`). Verificar: mapea el contenido, copia número, tamaño y
  totales, y una página vacía da `totalPaginas` 0.
- [x] 1.2 Agregar a `ManejadorGlobalExcepciones` (`com.presupuesto.comun.excepcion`) un handler
  de `MethodArgumentTypeMismatchException` → 400 `DATOS_INVALIDOS` con mensaje fijo, sin
  exponer valor ni causa; ampliar `ManejadorGlobalExcepcionesTest` y `ControladorDePrueba`
  (`com.presupuesto.comun.excepcion`, en `src/test`). Verificar: un parámetro `Long` con texto
  y un enum desconocido dan 400 `DATOS_INVALIDOS` sin `errores`.

## 2. Dominio y persistencia

- [x] 2.1 Crear `EstadoTransaccion` (`com.presupuesto.transaccion.entity`: `NO_CONCILIADA`,
  `CONCILIADA`, `RECONCILIADA`) y `EstadoTransaccionTest` (mismo paquete). Verificar: valores y
  orden.
- [x] 2.2 Crear `SubTransaccion` (`com.presupuesto.transaccion.entity`, extiende `EntidadBase`,
  `@Table(name = "subtransacciones")`, setters bloqueados) y `SubTransaccionTest` (mismo
  paquete). Verificar: sin setters por reflexión.
- [x] 2.3 Crear `Transaccion` (`com.presupuesto.transaccion.entity`, `@Table(name =
  "transacciones")`, `@SuperBuilder`, setters bloqueados, `@Builder.Default` en estado
  `NO_CONCILIADA`, `aprobada` `true` y lista de subtransacciones; métodos `editar`,
  `reemplazarSubtransacciones`, `moverA`, `aprobar`, `cambiarEstado`, `categorizar`,
  `estaReconciliada`, `tieneSubtransacciones`) y `TransaccionTest` (mismo paquete). Verificar:
  valores por defecto, cada método, `aprobar` idempotente, `reemplazarSubtransacciones`
  deja la colección con solo las nuevas y enlaza cada una a la transacción, y ningún setter
  expuesto (reflexión).
- [x] 2.4 Crear `TransaccionRepository` (`com.presupuesto.transaccion.repository`, con
  `JpaSpecificationExecutor`, `findByIdAndCuentaPresupuestoId`,
  `findByIdInAndCuentaPresupuestoId` y la consulta agregada de saldos por cuenta) y
  `TransaccionRepositoryTest` (mismo paquete, `@Transactional`). Verificar: acotado por
  presupuesto, `orphanRemoval` borra subtransacciones, borrar la transacción borra las
  subtransacciones, agregados con y sin transacciones y por estado.
- [x] 2.5 Crear `TransaccionSpecifications` (`com.presupuesto.transaccion.repository`: un método
  por filtro y `escaparComodines`) y `TransaccionSpecificationsTest` (mismo paquete,
  `@Transactional`). Verificar: cada filtro por separado y combinados, `categoriaId` coincide
  con subtransacciones sin duplicar filas, `desde`/`hasta` inclusivos, `q` sin distinguir
  mayúsculas y con `%`, `_` y `\` literales, orden fecha desc e id desc.

## 3. DTO

- [x] 3.0 Crear `MontoNoCero` y `MontoNoCeroValidator` (`com.presupuesto.transaccion.validacion`)
  y `MontoNoCeroValidatorTest` (mismo paquete). Verificar: 0 inválido, nulo válido.
- [x] 3.1 Crear `Normalizacion` (package-private) y `SubTransaccionRequest`
  (`com.presupuesto.transaccion.dto.request`: `categoriaId`, `monto` distinto de 0, `memo`
  máx. 500, strip y vacío a `null`) con `NormalizacionTest` y `SubTransaccionRequestTest`
  (mismo paquete). Verificar: monto 0 y nulo inválidos, memo de 501 inválido.
- [x] 3.2 Crear `CrearTransaccionRequest` (`com.presupuesto.transaccion.dto.request`:
  `cuentaId`, `fecha`, `monto`, `categoriaId`, `beneficiario`, `memo`, `aprobada`,
  `subtransacciones` con `@Valid`; `aprobada` nulo → `true`) y `CrearTransaccionRequestTest`
  (mismo paquete). Verificar: obligatorios, monto 0, longitudes 100/101 y 500/501, normalización
  y validación en cascada de las subtransacciones.
- [x] 3.3 Crear `ActualizarTransaccionRequest` (`com.presupuesto.transaccion.dto.request`:
  igual sin `cuentaId` ni `aprobada`) y `ActualizarTransaccionRequestTest` (mismo paquete).
  Verificar: mismas validaciones.
- [x] 3.4 Crear `CambiarEstadoRequest` y `MoverCuentaRequest`
  (`com.presupuesto.transaccion.dto.request`, `@NotNull`) con `CambiarEstadoRequestTest` y
  `MoverCuentaRequestTest` (mismo paquete). Verificar: nulos inválidos.
- [x] 3.5 Crear `OperacionLote` y `LoteRequest` (`com.presupuesto.transaccion.dto.request`:
  `ids` `@NotEmpty @Size(max = 100)` con elementos no nulos, `operacion` `@NotNull`,
  `categoriaId`) y `LoteRequestTest` (mismo paquete). Verificar: 0, 1, 100 y 101 ids,
  elemento nulo, operación nula.
- [x] 3.6 Crear `FiltroTransacciones` (`com.presupuesto.transaccion.dto.request`, record con
  `cuentaId`, `categoriaId`, `desde`, `hasta`, `estado`, `soloSinAprobar`, `q`; `q` con strip y
  vacío a `null`) y `FiltroTransaccionesTest` (mismo paquete). Verificar: normalización de `q`.
- [x] 3.7 Crear `SubTransaccionResponse`, `TransaccionResponse`, `LoteResponse` y
  `SaldoCuentaResponse` (`com.presupuesto.transaccion.dto.response`, cada uno con
  `desde(...)` salvo `LoteResponse` que es un valor simple) y sus tests
  (`SubTransaccionResponseTest`, `TransaccionResponseTest`, `LoteResponseTest`,
  `SaldoCuentaResponseTest`, mismo paquete). Verificar: todos los campos mapeados, incluidas
  subtransacciones y `categoriaId` nulo.

## 4. Services

- [x] 4.1 Crear `TransaccionReferencias` (`com.presupuesto.transaccion.service`,
  package-private: resuelve cuenta y categorías por `(id, presupuestoId)` con 404, exige cuenta
  abierta con 422, valida las subtransacciones —2 a 20 y sin categoría propia con 400, suma
  exacta con `Math.addExact` y 422— y construye las `SubTransaccion` con builder) y
  `TransaccionReferenciasTest` (mismo paquete, Mockito). Verificar: cada rama, categoría oculta
  permitida y desbordamiento tratado como suma incorrecta.
- [x] 4.2 Crear `TransaccionService` (`com.presupuesto.transaccion.service`: `crear`, `listar`
  paginado con validación de `page`, `size` y `desde > hasta` en 400 y filtros con 404 para
  cuenta/categoría ajena, `obtener`, `actualizar`, `borrar`, `aprobar`, `cambiarEstado`,
  `moverCuenta`, `duplicar` con `Clock`; cada método llama primero a
  `PresupuestoService.obtenerDelUsuario`, busca por `(id, presupuestoId)` y sigue el orden de
  evaluación del design) y `TransaccionServiceTest` (mismo paquete, Mockito con
  `RelojDePrueba`). Verificar: 404 por presupuesto y por transacción en cada método, `RECONCILIADA`
  bloqueada en editar, mover, borrar y estado, estados permitidos y rechazados, aprobar
  idempotente, duplicar con fecha del reloj y subtransacciones copiadas, cuenta cerrada, y
  que `PaginaResponse` refleje los totales.
- [x] 4.3 Crear `TransaccionLoteService` (`com.presupuesto.transaccion.service`: `ejecutar`
  atómico, con deduplicación, 404/400/422 en el orden del design y mutación solo tras validar)
  y `TransaccionLoteServiceTest` (mismo paquete, Mockito). Verificar: cada operación, id ajeno
  → 404 sin mutar, reconciliada → 422 sin mutar en `CATEGORIZAR` y `BORRAR` y aceptada en
  `APROBAR`, dividida en `CATEGORIZAR` → 422 sin mutar,
  ids repetidos cuentan una vez, `CATEGORIZAR` sin categoría → 400.
- [x] 4.4 Crear `SaldoCuentaService` (`com.presupuesto.transaccion.service`: `listar`) y
  `SaldoCuentaServiceTest` (mismo paquete, Mockito). Verificar: cuenta sin transacciones, con
  mezcla de estados, cerrada y presupuesto ajeno (404).

## 5. Controller y pruebas de integración

- [x] 5.1 Crear `TransaccionController` (`com.presupuesto.transaccion.controller`, bajo
  `/api/v1/presupuestos/{presupuestoId}/transacciones`: POST, GET paginado con `page`, `size` y
  filtros, `GET /{id}`, `PUT /{id}`, `DELETE /{id}`, `POST /{id}/aprobar`,
  `PUT /{id}/estado`, `POST /{id}/mover-cuenta`, `POST /{id}/duplicar`, `POST /lote`,
  `GET /saldos`) con `@AuthenticationPrincipal UsuarioAutenticado` y `@Valid`.
- [x] 5.2 Crear `TransaccionIntegracionTest` (`com.presupuesto.transaccion.controller`,
  `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Transactional`, `RelojDePrueba`). Verificar,
  comprobando estado HTTP **y** `codigo`: 401 sin token en todas las rutas; aislamiento entre
  usuarios y entre presupuestos del mismo usuario con una aserción de 404 propia por cada
  operación (crear, listar, detalle, editar, borrar, aprobar, estado, mover, duplicar, lote,
  saldos); cuenta o categoría de otro presupuesto; categoría oculta; cuenta cerrada; monto 0;
  split (suma correcta e incorrecta, 1, 21, 20, categoría junto a subtransacciones);
  `RECONCILIADA` creada por repositorio bloqueada en editar, mover, borrar y estado; estados
  permitidos y rechazados; aprobar idempotente; mover; duplicar; lote (cada operación y
  todo-o-nada ante ajeno y ante reconciliado en `CATEGORIZAR` y `BORRAR`; `APROBAR` en lote
  acepta una reconciliada y la cuenta); saldos con y sin transacciones y con cuenta cerrada;
  paginación (primera, última, fuera de rango, `size` 0/101, `page` -1, totales); cada filtro y
  su combinación; `q` con `%` y `_`; orden; `estado=XYZ` y `desde=ayer` → 400
  `DATOS_INVALIDOS`.

## 6. Documentación y cierre

- [x] 6.1 Actualizar `AGENTS.md` (raíz): agregar `transaccion/` al árbol, a la alternancia del
  `grep` de `comun/` (`usuario|auth|presupuesto|cuenta|categoria|transaccion`) y a la regla de
  dependencias; agregar `comun/paginacion` y la sección "Paginación" descrita en el design.
- [x] 6.2 Verificar dependencias desde `backend/src`, ambos con cero líneas:
  `grep -rnE "import com\.presupuesto\.(usuario|auth|presupuesto|cuenta|categoria|transaccion)"
  main/java/com/presupuesto/comun test/java/com/presupuesto/comun`, y
  `grep -rn "import com\.presupuesto\.transaccion" main/java/com/presupuesto/presupuesto
  main/java/com/presupuesto/cuenta main/java/com/presupuesto/categoria
  test/java/com/presupuesto/presupuesto test/java/com/presupuesto/cuenta
  test/java/com/presupuesto/categoria`. Mostrar la salida de ambos.
- [x] 6.3 Ejecutar la suite completa con `.\mvnw.cmd test` desde `backend/` (JDK 21): los 395
  tests existentes siguen pasando; mostrar el total final (395 + los nuevos), sin fallos.
- [x] 6.4 Revisar el límite de 100 columnas en los archivos nuevos y modificados. No hacer
  commit.
