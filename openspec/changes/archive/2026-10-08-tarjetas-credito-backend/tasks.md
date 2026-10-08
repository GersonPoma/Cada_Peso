# Tasks

Rutas de paquete completas según AGENTS.md; cada test va en el mismo paquete que su clase. Todo
comando Maven usa `.\mvnw.cmd` desde `backend/`. Los tests de integración crean sus propios datos
y solo consultan dentro de su presupuesto.

## 1. Cálculo puro de la reserva (asignacion)

- [x] 1.1 Crear `CalculoPagoTarjeta` (`com.presupuesto.asignacion.service`, package-private o
  public según el uso desde `CalculadoraMes`) con `actividadPorCategoria(sumas, categoriaDePago)`
  que niega las sumas por cuenta y mes y las vuelca a la categoría de pago, ignorando tarjetas sin
  categoría. Test `CalculoPagoTarjetaTest` (`com.presupuesto.asignacion.service`): gasto `+`,
  reembolso `−`, pago `−`, varias tarjetas, tarjeta sin categoría, mapas vacíos; verificar con
  `.\mvnw.cmd test -Dtest=CalculoPagoTarjetaTest`.
- [x] 1.2 Añadir a `CalculoMensualTest` (`com.presupuesto.asignacion.service`) casos nuevos (sin
  tocar los existentes) que combinan la salida de 1.1 con `CalculoMensual.calcular` y comprueban
  el invariante `C = listoParaAsignar + suma(disponible)` con los números del design (asignado
  `30000`, gasto `40000` con tarjeta; enero y febrero; luego pago de `40000`); verificar con
  `.\mvnw.cmd test -Dtest=CalculoMensualTest`.

## 2. Consultas agregadas (asignacion)

- [x] 2.1 Añadir a `ActividadMensualRepository` (`com.presupuesto.asignacion.repository`)
  `gastosTarjeta`, `gastosTarjetaDivididos` y `pagosATarjeta` (por `cuenta.id`, año y mes,
  tarjetas `enPresupuesto`, abiertas y cerradas, acotadas por presupuesto). Tests en
  `ActividadMensualRepositoryTest` (`com.presupuesto.asignacion.repository`): categorías normales
  vs. sin categoría, subtransacciones, pago con par en presupuesto vs. externa, tarjeta cerrada,
  otra tarjeta y otro presupuesto; verificar con
  `.\mvnw.cmd test -Dtest=ActividadMensualRepositoryTest`.
- [x] 2.2 Añadir `and t.cuenta.tipo <> TARJETA_CREDITO` a `ingresosSinCategoria`. Tests nuevos en
  `ActividadMensualRepositoryTest`: entrada sin categoría en tarjeta no cuenta; transferencia
  externa → tarjeta no cuenta; las existentes siguen verdes sin cambios.

## 3. Modelo de categorías (categoria)

- [x] 3.1 Crear `TipoGrupoCategoria` (`com.presupuesto.categoria.entity`) con `NORMAL` y
  `PAGOS_TARJETA`; añadir `GrupoCategoria.tipo` (columna `not null default 'NORMAL'`, sin setter,
  `@Builder.Default`) y `Categoria.cuentaTarjeta` (`@OneToOne` LAZY, `cuenta_tarjeta_id` única,
  sin setter) con `esPagoTarjeta()`. Tests `TipoGrupoCategoriaTest`, `GrupoCategoriaTest`,
  `CategoriaTest` (`com.presupuesto.categoria.entity`) y
  `CategoriaRepositoryTest`/`GrupoCategoriaRepositoryTest` (`com.presupuesto.categoria.repository`):
  valor por defecto `NORMAL`, unicidad de `cuenta_tarjeta_id`; verificar con `.\mvnw.cmd test`
  de esas clases.
- [x] 3.2 Añadir consultas a `CategoriaRepository`/`GrupoCategoriaRepository`
  (`com.presupuesto.categoria.repository`): grupo de pagos del presupuesto, mapa
  `cuentaId → categoriaId` de pago, categoría de una cuenta y tarjetas `enPresupuesto` sin
  categoría de pago. Probar cada una en los `*RepositoryTest` correspondientes, incluyendo
  aislamiento entre presupuestos.
- [x] 3.3 Añadir `esPagoTarjeta`/`cuentaId` a `CategoriaResponse`
  (`com.presupuesto.categoria.dto.response`) y `tipo` a `GrupoCategoriaResponse` y
  `GrupoCategoriaConCategoriasResponse`; actualizar sus `desde(...)` y ampliar los
  `*ResponseTest` con aserciones nuevas (sin modificar las existentes); verificar con
  `.\mvnw.cmd test` de esas clases.

## 4. Eventos de cuenta y categorías de pago (cuenta + categoria)

- [x] 4.1 Crear `CuentaCreadaEvento`, `CuentaRenombradaEvento`, `CuentaCerradaEvento` y
  `CuentaReabiertaEvento` (records en `com.presupuesto.cuenta.evento`, cada uno con la `Cuenta`
  guardada) y publicarlos desde `CuentaService` (`com.presupuesto.cuenta.service`) tras guardar;
  el renombrado solo si el nombre cambió. Test `CuentaEventosTest`
  (`com.presupuesto.cuenta.evento`) y ampliar `CuentaServiceTest` con verificación de
  publicación (sin tocar las aserciones existentes salvo lo dicho en 4.2); verificar con
  `.\mvnw.cmd test -Dtest=CuentaServiceTest`.
- [x] 4.2 En `CuentaService.actualizar`, responder `ReglaNegocioException` (422) si el tipo
  cambia desde o hacia `TARJETA_CREDITO`, antes de `exigirSaldoValido`. **Modificar las dos
  aserciones en conflicto declaradas en el design**: `CuentaIntegracionTest` (línea ~305,
  tarjeta → `PRESTAMO` pasa a esperar 422) y `CuentaServiceTest` (línea ~230, pasa a esperar
  `ReglaNegocioException` y tipo sin cambio); añadir tests nuevos para `CORRIENTE` → tarjeta y
  renombrar conservando el tipo. Verificar con `.\mvnw.cmd test -Dtest="Cuenta*Test"`.
- [x] 4.3 Crear `CategoriasPagoTarjeta` (`com.presupuesto.categoria.service`): `asegurarGrupo`
  (localiza por `tipo`; si falta lo crea con el nombre original o `(2)`, `(3)`... si un grupo
  normal lo usa), `crear(Cuenta)`, `renombrar(Cuenta)`, `ocultar/mostrar(Cuenta)` con nombre
  `Pago: <nombre>` recortado por code points a 100 y sufijo ` (id)` ante colisión. Test
  `CategoriasPagoTarjetaTest` (`com.presupuesto.categoria.service`): primera tarjeta crea grupo al
  final, segunda lo reutiliza, tarjeta de seguimiento no crea nada, **tarjeta con nombre de 100
  caracteres (crear y renombrar) con categoría de a lo sumo 100**, colisión tras el recorte,
  **grupo normal con el nombre original (grupo de pagos con `(2)`, normal intacto)**, nombre
  alterno `(2)` ocupado → `(3)`, y segunda tarjeta que reutiliza el grupo de pagos aunque haya un
  normal con el nombre original; **renombrar la tarjeta cambiando solo mayúsculas (`Visa` → `VISA`) conserva
  `Pago: VISA` sin sufijo (la búsqueda de nombre excluye la categoría de la misma cuenta), mientras
  que crear otra tarjeta cuyo nombre normalizado coincide con una categoría ajena del grupo sí
  agrega el sufijo; con Mockito, `asegurarGrupo` obtiene el presupuesto con la
  consulta con bloqueo (nunca con `findById`) y dos llamadas seguidas devuelven el mismo grupo sin
  crear otro**. La carrera real entre procesos queda como riesgo documentado, sin test.
- [x] 4.4 Crear `PagosTarjetaListener` (`com.presupuesto.categoria.service`, package-private,
  `@EventListener` síncrono, solo `TARJETA_CREDITO` con `enPresupuesto`). Test
  `PagosTarjetaListenerTest` (`com.presupuesto.categoria.service`) y flujo HTTP en
  `PagosTarjetaIntegracionTest` (`com.presupuesto.categoria.controller`): crear, renombrar,
  cerrar, reabrir tarjeta y comprobar el árbol; un fallo del oyente revierte la cuenta; crear una
  tarjeta por HTTP con un grupo normal `Pagos de tarjetas de crédito` ya existente responde 201 y
  el árbol conserva ambos grupos; una tarjeta con nombre de 100 caracteres responde 201 y 200 al
  renombrarla, y renombrar `Visa` a `VISA` por HTTP deja la categoría en `Pago: VISA`; con la
  tarjeta `Visa` y su categoría `Pago: Visa`, crear una tarjeta llamada `visa` responde 409
  `CUENTA_YA_EXISTE` y después el árbol sigue teniendo una sola categoría de pago (`Pago: Visa`)
  y un solo grupo de pagos (escenario "Nombre de tarjeta repetido distinguiendo solo
  mayúsculas").
- [x] 4.5 Crear `MigracionPagosTarjeta` (`com.presupuesto.categoria.service`, `ApplicationRunner`
  no transaccional que procesa cada presupuesto pendiente en su propia transacción con
  `TransactionTemplate`, captura y registra el fallo por presupuesto y delega en
  `CategoriasPagoTarjeta`) y añadir a `PresupuestoRepository`
  (`com.presupuesto.presupuesto.repository`) la consulta por id con `@Lock(PESSIMISTIC_WRITE)` que
  usa `asegurarGrupo`; es un cambio interno de consulta, sin requisitos modificados en
  `presupuestos` (test en `PresupuestoRepositoryTest`: devuelve el presupuesto por id). Cada
  transacción bloquea un solo presupuesto. Test `MigracionPagosTarjetaTest`
  (`com.presupuesto.categoria.service`): tarjeta existente sin categoría, tarjeta cerrada
  (categoría oculta), presupuesto sin tarjetas sin cambios, segunda ejecución idempotente (conteo
  de filas y ids sin cambio), fila previa con `tipo` por defecto, **presupuesto con un grupo
  normal llamado `Pagos de tarjetas de crédito` y una tarjeta (grupo normal intacto, un solo
  grupo de pagos con nombre alterno, estable en una segunda ejecución)**, tarjeta con nombre de
  100 caracteres, **garantía (d) simulada: `CategoriasPagoTarjeta` falla con
  `DataIntegrityViolationException` para un presupuesto, el runner no lanza, el otro presupuesto
  queda migrado, el fallido no queda a medias y una segunda corrida sin el fallo lo completa**; verificar con
  `.\mvnw.cmd test -Dtest=MigracionPagosTarjetaTest`.

## 5. Protecciones (categoria + transaccion)

- [x] 5.1 Proteger con `ReglaNegocioException` el grupo de pagos
  (`GrupoCategoriaService.renombrar/ocultar/mostrar/mover`) y las categorías de pago
  (`CategoriaService.actualizar/ocultar/mostrar/mover/crear`, incluido mover una categoría normal
  al grupo de pagos). Tests en `GrupoCategoriaServiceTest` y `CategoriaServiceTest`
  (`com.presupuesto.categoria.service`) y casos HTTP en `PagosTarjetaIntegracionTest`; mover un
  grupo normal sigue en 200 con órdenes consecutivos.
- [x] 5.2 Añadir `TransaccionReferencias.categoriaParaRegistrar`
  (`com.presupuesto.transaccion.service`) y hacer que **todas** las vías de la tabla de D5 pasen
  por ella: `dividir` (que usa `TransaccionService.crear` y `actualizar`, y valida cada parte),
  `TransferenciaService.crear/actualizar` y `TransaccionLoteService` (`CATEGORIZAR`); duplicar y
  mover-cuenta no necesitan cambio. El filtro del listado sigue con `categoria(...)`; verificar
  con `grep -n "referencias.categoria(" backend/src/main/java/com/presupuesto/transaccion/service`
  (solo el filtro). Tests unitarios en `TransaccionReferenciasTest`, `TransferenciaServiceTest` y
  `TransaccionLoteServiceTest` (`com.presupuesto.transaccion.service`). Tests de integración,
  **uno por vía y cada uno espera 422 `REGLA_NEGOCIO_VIOLADA` con una categoría de pago y que no
  se guarde ni cambie nada**: crear, editar y dividir (parte con categoría de pago) en
  `TransaccionIntegracionTest`, lote `CATEGORIZAR` en `TransaccionIntegracionTest` (o en el
  archivo de lote si existe) y transferencia (crear y editar) en `TransferenciaIntegracionTest`,
  todos en `com.presupuesto.transaccion.controller`; además filtro vacío por la categoría de pago
  y 404 con la categoría de pago de otro presupuesto.

- [x] 5.3 Hacer que `BeneficiarioService.crear/actualizar`
  (`com.presupuesto.beneficiario.service`) lancen `ReglaNegocioException` (422) si el
  `categoriaId` es una categoría de pago; `categoriaId` nulo sigue válido y una categoría ajena
  sigue en 404. Tests en `BeneficiarioServiceTest` (`com.presupuesto.beneficiario.service`) y casos
  nuevos en `BeneficiarioIntegracionTest` (`com.presupuesto.beneficiario.controller`): crear,
  editar (el beneficiario conserva nombre y categoría), quitar categoría, categoría de pago ajena;
  verificar con `.\mvnw.cmd test -Dtest="Beneficiario*Test"`.

## 6. Integración en el cálculo del mes (asignacion)

- [x] 6.1 Integrar en `CalculadoraMes` (`com.presupuesto.asignacion.service`) las consultas de 2.1
  y el mapa `cuentaId → categoriaId`, sumando el resultado de `CalculoPagoTarjeta` al mapa
  `actividad` antes de `CalculoMensual.calcular`. Ampliar `CalculadoraMesTest` con casos nuevos
  (sin tocar los existentes); verificar con `.\mvnw.cmd test -Dtest=CalculadoraMesTest`.
- [x] 6.2 Añadir `esPagoTarjeta`/`cuentaId` a `CategoriaMesResponse`
  (`com.presupuesto.asignacion.dto.response`) con constructor de 7 argumentos conservado, y
  poblarlos en `MesPresupuestoService.fila`. Ampliar `CategoriaMesResponseTest` y
  `MesPresupuestoServiceTest` con aserciones nuevas.
- [x] 6.3 Crear `TarjetaCreditoIntegracionTest` (`com.presupuesto.asignacion.controller`) con un
  presupuesto propio por test: gasto con tarjeta (`100000` asignado, `30000` gastado → Comida
  `70000`, Pago `30000`), gasto dividido, reembolso, pago (transferencia), sobregasto
  (`30000` asignado, `40000` gastado; enero y febrero), deuda previa (asignar a la categoría de
  pago), tarjeta cerrada (oculta, sigue contando), renombrar, mover dinero desde/hacia la
  categoría de pago, aislamiento entre dos presupuestos y entre dos tarjetas, y el invariante
  `C = listoParaAsignar + suma(disponible)` en cada escenario. Verificar con
  `.\mvnw.cmd test -Dtest=TarjetaCreditoIntegracionTest`.

## 7. Auto-asignar (meta)

- [x] 7.1 En `AutoAsignarService.categoriasObjetivo` (`com.presupuesto.meta.service`) filtrar las
  categorías de pago cuando `categoriaIds` es `null`. Tests en `AutoAsignarServiceTest`
  (`com.presupuesto.meta.service`): sin ids las omite, con ids explícitos las procesa (ocultas
  incluidas); verificar con `.\mvnw.cmd test -Dtest=AutoAsignarServiceTest`.

- [x] 7.2 Fijar el supuesto 6 del design: caso nuevo en `MetaIntegracionTest`
  (`com.presupuesto.meta.controller`) que guarda, consulta, pospone y borra una meta en
  `Pago: <tarjeta>` y comprueba, con los números del escenario (meta `MONTO_MENSUAL` `MENSUAL` de `50000`,
  `asignado` `0`, gasto de `40000` con la tarjeta), `disponible` `40000`, `necesidad` `50000`,
  `faltante` `50000` y `estado` `FALTA`; no se modifica `MetaService`. Verificar con
  `.\mvnw.cmd test -Dtest=MetaIntegracionTest`.

## 8. Verificación final de integración

- [x] 8.1 Ejecutar `.\mvnw.cmd test` completo y comprobar que los 1053 tests de la línea base
  siguen pasando más los nuevos.
- [x] 8.2 Desde `backend/src`, ejecutar el `grep` de dependencias de `comun/`
  (`grep -rnE "import com\.presupuesto\.(usuario|auth|presupuesto|cuenta|categoria|transaccion|asignacion|beneficiario|meta)" main/java/com/presupuesto/comun test/java/com/presupuesto/comun`)
  y comprobar cero líneas; comprobar que `cuenta` no importa `categoria` ni `asignacion`
  (`grep -rnE "import com\.presupuesto\.(categoria|asignacion)" main/java/com/presupuesto/cuenta`)
  y que nadie importa `meta` fuera de `meta`
  (`grep -rln "import com\.presupuesto\.meta" main test` solo devuelve archivos de `meta`).
- [x] 8.3 Comprobar con `git diff` sobre `src/test` que no se modificó ninguna aserción
  existente salvo las dos declaradas en 4.2, y ejecutar
  `openspec validate tarjetas-credito-backend --strict` sin errores.
