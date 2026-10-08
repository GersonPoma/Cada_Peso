## 1. Configuración y reglas del proyecto

- [x] 1.1 Agregar a `application.properties` `reportes.max-meses=60` con un comentario ("rango
  máximo de los reportes, en meses; un rango mayor responde 400"); verificar con
  `.\mvnw.cmd test -Dtest=ReportePropertiesTest` (tarea 2.1) que el valor llega.
- [x] 1.2 En `AGENTS.md`: agregar `reporte` al árbol de paquetes (`controller`, `dto/response`,
  `repository`, `service`), a la regla de dependencias (`reporte` puede depender de
  `presupuesto`, `cuenta`, `categoria`, `transaccion`, `asignacion` y `comun`; ninguna feature
  importa `reporte`; la dependencia sobre `meta` se agrega en la tarea 6.9) y a la alternancia
  del `grep` de `comun/`; verificar ejecutando el `grep` desde `backend/src` sobre
  `main/java/com/presupuesto/comun` y `test/java/com/presupuesto/comun`: cero líneas.
- [x] 1.3 En `AGENTS.md`: crear la sección "Reportes" con la URL base, el orden de errores
  (presupuesto 404, cuenta 404, parámetros 400), los parámetros como texto validados en el
  service, el rango (`reportes.max-meses`, meses sin datos en cero) y la tabla de qué cuenta
  como gasto, ingreso y patrimonio de `design.md` D3 (con la cita de la regla de las
  categorías de pago); verificar releyendo que cada afirmación coincide con
  `specs/reportes/spec.md`.

## 2. Núcleo común: propiedades, rango y porcentaje

- [x] 2.1 Crear `ReporteProperties` (record `@ConfigurationProperties("reportes")`,
  `@DefaultValue("60") int maxMeses`, constructor compacto que rechaza `< 1`) en
  `com.presupuesto.reporte.service`; test `ReportePropertiesTest` en el mismo paquete (por
  defecto 60, `0` falla, valor explícito).
- [x] 2.2 Crear `RangoMeses` (record package-private, `interpretar(desde, hasta, maximo,
  hastaOpcional)` apoyado en `MesParametro.interpretar`, `meses()` en orden) en
  `com.presupuesto.reporte.service`; `RangoMesesTest` en el mismo paquete cubre: `2026-13`,
  `2026-1`, `ayer`, vacío, nulo, `1999-12`, `2101-01`, `desde > hasta`, 60 y 61 meses, máximo
  configurado en 12, un solo mes, `hasta` ausente cuando es opcional, y que ningún mensaje
  contiene el valor recibido.
- [x] 2.3 Crear `Porcentaje.centesimas(parte, total)` (package-private, `BigInteger`, al entero
  más cercano con el medio alejándose de cero, `0` si `total <= 0`) en `com.presupuesto.reporte.service`;
  `PorcentajeTest` en el mismo paquete con `140000/265000 → 5283`, `100000 → 3774`,
  `20000 → 755`, `5000 → 189`, el medio exacto (`3/20000 → 2` es `1,5 → 2` y
  `-3/20000 → -2` es `-1,5 → -2`), parte negativa, total cero, total negativo, `parte > total` y
  montos cercanos a `Long.MAX_VALUE` sin desbordar.

## 3. Gasto por categoría e ingresos contra gastos (reportes 1 y 2)

- [x] 3.1 Agregar a `ActividadMensualRepository` (`com.presupuesto.asignacion.repository`)
  `sinCategoriaPorMes(presupuestoId, desde, hasta)` y `salidasDivididasSinCategoria(...)` con
  sus proyecciones y un comentario cruzado con `ingresosSinCategoria`; casos nuevos en
  `ActividadMensualRepositoryTest` (mismo paquete): transferencia interna excluida en ambas
  patas, tarjeta (salida sí, entrada no), cuenta fuera del presupuesto, cuenta cerrada,
  división con parte sin categoría, otro presupuesto, y **equivalencia**: la suma de `ingresos`
  de `sinCategoriaPorMes` hasta M es igual a `ingresosSinCategoria(M)`.
- [x] 3.2 Crear `CalculoGasto` (package-private, `com.presupuesto.reporte.service`): pasa la
  actividad de `actividadSimple` y `actividadDividida` a gasto neto (`-actividad`) por
  categoría y mes, descarta los meses anteriores a `desde` y arma el total por mes. No filtra
  categorías de pago: una categoría de pago no admite transacciones (`422`, requisito *Una
  categoría de pago de tarjeta no admite transacciones* de la spec `transacciones`), dejarlo
  anotado en el comentario de la clase. `CalculoGastoTest` en el mismo paquete con el ejemplo de
  octubre (Comida `140000`, Hogar `20000`, Metas de ahorro `100000`), reembolso mayor que el gasto
  (negativo), categoría con neto `0` que sigue apareciendo, meses anteriores a `desde`
  descartados y mes sin datos en cero.
- [x] 3.3 Crear los DTO de `com.presupuesto.reporte.dto.response`:
  `GastoPorCategoriaResponse`, `GrupoGastoResponse`, `CategoriaGastoResponse`,
  `SinCategoriaGastoResponse`, `IngresosGastosResponse`, `MesIngresosGastosResponse`, cada uno
  con `desde(...)`; `IngresosGastosResponseTest` (mismo paquete) comprueba que los totales
  son la suma de los meses y `neto = ingresos - gastos` y `GastoPorCategoriaResponseTest` que el
  total de un grupo es la suma de sus categorías.
- [x] 3.4 Crear `GastoReporteService` (`com.presupuesto.reporte.service`,
  `@EnableConfigurationProperties(ReporteProperties.class)`, `@Transactional(readOnly = true)`)
  con `gastoPorCategoria` (7 consultas) e `ingresosGastos` (5 consultas), según `design.md`
  D10: presupuesto 404 → rango 400 → consultas; `GastoReporteServiceTest` (mocks) verifica el
  orden de errores (un rango inválido no consulta datos), el número de llamadas a cada
  repositorio y que no hay consultas por mes ni por categoría.
- [x] 3.5 Crear `ReporteController` (`com.presupuesto.reporte.controller`,
  `/api/v1/presupuestos/{presupuestoId}/reportes`) con `GET gasto-por-categoria` y
  `GET ingresos-gastos`, parámetros `desde` y `hasta` como `String` sin `@Valid`, y
  `@AuthenticationPrincipal UsuarioAutenticado`; `GastoReporteIntegracionTest` en el mismo
  paquete con presupuestos aislados y `RelojDePrueba`: el ejemplo de octubre de la spec
  (ingresos `500000`, Comida `140000`, Hogar `20000`, Metas de ahorro `100000`, sin categoría
  `5000`,
  total `265000`, porcentajes `5283`/`3774`/`755`/`189`), gasto con tarjeta sin categoría
  "Pago: Visa" y sin grupo de pagos en la respuesta, pago de tarjeta neutro, transferencia
  interna neutra, entrada desde cuenta fuera, división (también con parte sin categoría),
  categoría oculta, cuenta cerrada, cuenta fuera del presupuesto, rango de varios meses, rango
  sin datos en ceros.
- [x] 3.6 Crear `CoincidenciaMesIntegracionTest` (`com.presupuesto.reporte.controller`) con los
  casos de los reportes 1 y 2: el gasto de cada categoría, en un mes con tarjetas, divisiones y
  transferencias, es el negativo de su `actividad` en el presupuesto mensual (las categorías de
  pago se comparan aparte: no figuran en el reporte); el gasto total de `gasto-por-categoria`
  es igual al `gastos` de `ingresos-gastos`; ingresos acumulados + saldos iniciales positivos =
  ingresos de `listoParaAsignar`; ejecutar `.\mvnw.cmd test -Dtest=CoincidenciaMesIntegracionTest`
  y ver que pasa.
- [x] 3.7 Agregar a la sección "Reportes" de `AGENTS.md` las dos rutas, la forma de las
  respuestas (porcentaje en centésimas, "Sin categoría" aparte) y el ejemplo de octubre;
  verificar que los números coinciden con los de `GastoReporteIntegracionTest`.

## 4. Patrimonio neto y evolución del saldo (reportes 3 y 4)

- [x] 4.1 Crear `MovimientoMensualRepository` (`Repository<Transaccion, Long>`, en
  `com.presupuesto.reporte.repository`) con `movimientoPorCuentaYMes(presupuestoId, hasta)` y
  `movimientoDeCuentaPorMes(cuentaId, hasta)` y la proyección `MovimientoPorMes`;
  `MovimientoMensualRepositoryTest` (mismo paquete) con cuentas de todos los tipos, cerrada,
  fuera del presupuesto, otro presupuesto, todos los estados de transacción, frontera
  `2026-10-31`/`2026-11-01` y suma por mes igual a `sumarPorCuenta` de `TransaccionRepository`.
- [x] 4.2 Crear `CalculoSaldos` (package-private, `com.presupuesto.reporte.service`):
  `alCierre(saldoInicial, movimientosPorMes, rango)` con saldo de apertura de lo anterior al
  rango, arrastre en meses sin movimiento y `entradas`/`salidas`; `CalculoSaldosTest` en el
  mismo paquete con el ejemplo (`100000`; septiembre `+50000 -20000`; noviembre `-30000` →
  `130000`, `130000`, `100000`), **el ejemplo de rango posterior a movimientos previos** (saldo
  inicial `100000`, `+40000` y `-10000` anteriores al rango, enero `+5000 -2000` → apertura
  `130000`, enero `133000`, febrero `133000`; y que no da `103000`), sin movimientos y cuenta
  con saldo inicial negativo.
- [x] 4.3 Crear `CalculoPatrimonio` (package-private, mismo paquete): clasifica por
  `TipoCuenta` (`TARJETA_CREDITO` y `PRESTAMO` son pasivos con el signo cambiado) y suma por
  mes; `CalculoPatrimonioTest` con el ejemplo (`1200000`, `600000`, `-150000`, `-2900000` →
  `1800000`, `3050000`, `-1250000`), sobregiro en una cuenta de activo, tarjeta con saldo a
  favor y que `patrimonio` es siempre la suma de saldos.
- [x] 4.4 Crear los DTO `PatrimonioResponse`, `MesPatrimonioResponse`, `EvolucionSaldoResponse`
  y `MesSaldoResponse` en `com.presupuesto.reporte.dto.response`, con `desde(...)`.
- [x] 4.5 Crear `PatrimonioReporteService` (`com.presupuesto.reporte.service`) con `patrimonio`
  (3 consultas) y `evolucionSaldo` (presupuesto 404 → cuenta 404 → rango 400, 3 consultas);
  `PatrimonioReporteServiceTest` (mocks): orden de errores y que la cuenta no se consulta si el
  presupuesto no es del usuario.
- [x] 4.6 Agregar `GET patrimonio` y `GET cuentas/{cuentaId}/evolucion-saldo` a
  `ReporteController`; `PatrimonioReporteIntegracionTest` (`...reporte.controller`) con
  cuentas de los seis tipos, cerrada con saldo, fuera del presupuesto, meses sin movimientos
  que arrastran, el ejemplo de patrimonio de la spec, la evolución del ejemplo, **el rango que
  empieza después de movimientos previos (apertura `130000`, enero `133000`)** en evolución y en
  patrimonio, tarjeta, pata de transferencia, cuenta fuera del presupuesto, cuenta ajena y de
  otro presupuesto `404`.
- [x] 4.7 Agregar a `CoincidenciaMesIntegracionTest` los casos de los reportes 3 y 4: el
  `saldo` del último mes de la evolución de cada cuenta es igual a su `saldo` en el listado de
  saldos, y el `patrimonio` del mes actual es igual a la suma con signo de los saldos de todas
  las cuentas; ejecutar la clase y ver que pasa.
- [x] 4.8 Agregar a la sección "Reportes" de `AGENTS.md` las dos rutas, la definición de
  `activos`/`pasivos`/`patrimonio`, el saldo inicial sin fecha, el saldo de apertura de un rango
  y los ejemplos numéricos; verificar que coinciden con `PatrimonioReporteIntegracionTest`.

## 5. Integración de los reportes 1 a 4

- [x] 5.1 `RangoYErroresReporteIntegracionTest` (`com.presupuesto.reporte.controller`): para
  los endpoints de los reportes 1 a 4, `401` sin token, aislamiento entre dos usuarios con
  movimientos distintos, `404` antes que `400` (presupuesto inexistente con `desde=2026-13`;
  cuenta ajena con rango inválido), mes mal formado, rango invertido, exactamente 60 meses
  `200` y 61 `400`, `405` con `POST`, presupuesto sin datos con ceros; y una clase aparte con
  `reportes.max-meses=12` que rechaza 13 meses; ejecutarla y ver que pasa.
- [x] 5.2 `ConsultasReporteIntegracionTest` (mismo paquete, con `generate_statistics=true`):
  para los reportes 1 a 4 comprobar el número exacto de consultas de `design.md` D10 (7, 5, 3 y
  3) con 1 mes y con 24 meses (iguales), y la frontera de mes `2026-10-31`/`2026-11-01`; si
  algún número difiere del diseño, corregir el código o la tabla de D10 y de la spec, no el
  test.
- [x] 5.3 Verificación del bloque: `.\mvnw.cmd test` completo (la línea base de tests anterior
  pasa sin tocar aserciones, más los nuevos), el `grep` de dependencias de `comun/` en cero
  líneas incluyendo `reporte`, `grep -rn "import com.presupuesto.reporte" backend/src/main`
  fuera de `reporte/` en cero líneas, y `openspec validate reportes-backend --strict` sin
  errores. Hasta aquí el change está completo sin tocar código existente salvo
  `ActividadMensualRepository`.

## 6. Cumplimiento de metas (reporte 5) — último grupo, separable

- [x] 6.1 Refactorizar `CalculadoraMes` (`com.presupuesto.asignacion.service`): extraer
  `cargar(presupuestoId, finDeMes)` → `Entradas(asignado, actividad)`, dejar `calcular` como
  `cargar` + ingresos + `CalculoMensual.calcular`, y agregar `calcularFilas(presupuestoId,
  desde, hasta)` que carga una vez y llama a `CalculoMensual.calcular` por mes en memoria;
  agregar `MesPresupuestoService.calcularFilas`. Casos nuevos con mocks en `CalculadoraMesTest`
  y `MesPresupuestoServiceTest` (mismo paquete): `calcularFilas(m, m)` igual a
  `calcular(m).filas()`, rango de varios meses igual a llamar a `calcular` mes a mes, mes
  anterior al primer dato, y mismo número de consultas al repositorio con 1 y con 12 meses.
  No tocar ninguna aserción de los tests existentes.
- [x] 6.2 Crear `MesPresupuestoServiceRangoTest` (`com.presupuesto.asignacion.service`, base de
  datos real) que compare `MesPresupuestoService.calcularFilas` con
  `MesPresupuestoService.calcular(presupuestoId, m).filas()` mes a mes con un rango que
  **empieza a mitad del historial**: historial de 8 meses con asignaciones, gastos, un
  sobregasto, saldo positivo que se arrastra, una tarjeta con categoría de pago, una división
  y categorías sin movimientos en algunos meses; rango del mes 4 al 8; para cada mes, mismas
  categorías y mismas `FilaMes` (`asignado`, `actividad`, `disponible`); el `disponible` del
  mes 4 incluye el arrastre de los meses 1 a 3; un rango anterior al primer dato devuelve
  filas vacías como `calcular`. Ejecutar la clase y ver que pasa.
- [x] 6.3 Hacer públicos `CalculoMeta`, `CalculoMeta.calcular` y `CalculoMeta.Resultado`
  (`com.presupuesto.meta.service`) sin tocar su lógica; verificar que `CalculoMetaTest` pasa sin
  cambios.
- [x] 6.4 Agregar `MetaPospuestaRepository.findPospuestasEnRango(presupuestoId, desde, hasta)`
  (`com.presupuesto.meta.repository`, devuelve `(metaId, mes)`); caso nuevo en
  `MetaPospuestaRepositoryTest` (mismo paquete): dentro y fuera del rango, otro presupuesto, y
  el resultado de un mes igual a `findMetaIdsPospuestas` de ese mes.
- [x] 6.5 Crear `CalculoCumplimiento` (package-private, `com.presupuesto.reporte.service`):
  porcentaje de un mes y totales del rango (`asignado / necesidad` con las sumas, `null` si la
  necesidad es `0`, sin tope); `CalculoCumplimientoTest` con `80000/100000 → 8000`, el rango de
  `200000`/`180000 → 9000`, pospuesta (`null`), asignado de más (`> 10000`) y gastado en
  categoría de pago igual a `0`.
- [x] 6.6 Crear los DTO `CumplimientoMetasResponse`, `MetaCumplimientoResponse` y
  `MesMetaResponse` en `com.presupuesto.reporte.dto.response`, con `desde(...)`, y
  `MetasReporteService` (`com.presupuesto.reporte.service`): presupuesto 404 → rango 400
  (`hasta` opcional) → `calcularFilas` + metas + pospuestas, y por cada meta y mes
  `CalculoMeta.calcular` igual que `MetaMesService`; `MetasReporteServiceTest` (mocks)
  verifica el orden de errores, que no hay consultas por mes ni por meta, y `gastado = 0` en
  categorías de pago.
- [x] 6.7 Agregar `GET metas` a `ReporteController`; `MetasReporteIntegracionTest`
  (`...reporte.controller`) con los tres tipos de meta, el ejemplo de la spec
  (`necesidad 100000`, `asignado 80000`, `gastado 60000`, `faltante 20000`, `FALTA`, `8000`),
  rango de dos meses (`9000`), pospuesta, sobregastada, categoría de pago, categoría oculta,
  sin metas, `hasta` omitido y presupuesto ajeno `404`. Extender `CoincidenciaMesIntegracionTest`
  (para cada mes de un rango, `necesidad`, `asignado`, `disponible`, `faltante` y `estado` son
  iguales a los de `GET /metas?mes=`), `RangoYErroresReporteIntegracionTest` y
  `ConsultasReporteIntegracionTest` (7 consultas, 10 con tarjetas, con 1 y con 24 meses) con el
  endpoint de metas.
- [x] 6.8 **Correr la suite completa justo después** (`.\mvnw.cmd test` desde `backend/`):
  deben pasar todos los tests de la línea base sin modificar ninguna aserción, más los nuevos.
  **Condición de separación:** si para que pase hay que modificar la aserción de algún test
  existente (asignación, tarjetas, metas, cuentas, transacciones), no se modifica: se revierten
  las tareas 6.1 a 6.7, se cierra este change con los reportes 1 a 4 (grupos 1 a 5) y las metas
  pasan a un change propio, `reportes-metas-backend`, quitando de este change el requisito
  *Cumplimiento de metas* de la spec, la sección de metas del design y este grupo.
- [x] 6.9 Agregar a `AGENTS.md` la dependencia `reporte → meta` en la regla de dependencias y a
  la sección "Reportes" la ruta de metas, que reutiliza `CalculoMensual` y `CalculoMeta`, y la
  meta vigente aplicada a todos los meses; repetir el `grep` de `comun/` y
  `openspec validate reportes-backend --strict`: sin errores.
