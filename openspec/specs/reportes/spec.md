# reportes Specification

## Purpose

Permite leer, agregados por rango de meses, el gasto por categoría, los ingresos contra los
gastos, el patrimonio neto, la evolución del saldo de una cuenta y el cumplimiento de las metas
de un presupuesto, con las mismas reglas de contabilización que el presupuesto mensual y sin
modificar ningún dato.

## Requirements

### Requirement: Autenticación, aislamiento y orden de errores
El sistema SHALL exigir autenticación en todas las rutas de
`/api/v1/presupuestos/{presupuestoId}/reportes/...`. En cada operación SHALL validar en este
orden: presupuesto (`404`), cuenta dentro del presupuesto (`404`, solo en la evolución del
saldo) y parámetros (`400`). Un presupuesto o una cuenta inexistente o ajena SHALL responder
`404` con código `RECURSO_NO_ENCONTRADO`, nunca `403`. Todos los errores SHALL ser
`ProblemDetail` con `codigo` y `timestamp`.

#### Scenario: Petición sin token
- **DADO** una petición sin token de acceso
- **CUANDO** se llama a cualquier ruta de reportes
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

#### Scenario: Presupuesto ajeno
- **DADO** un presupuesto de otra persona
- **CUANDO** se pide cualquiera de los cinco reportes, con parámetros válidos o no
- **ENTONCES** el sistema responde `404` y no revela nada del presupuesto

#### Scenario: Cuenta ajena o de otro presupuesto
- **DADO** una cuenta de otra persona, o de otro presupuesto propio
- **CUANDO** se pide la evolución de su saldo por la URL de mi presupuesto
- **ENTONCES** el sistema responde `404`, también si los parámetros son inválidos

#### Scenario: El 404 va antes que el 400
- **DADO** un presupuesto inexistente
- **CUANDO** se pide un reporte con `desde=2026-13`
- **ENTONCES** el sistema responde `404`, no `400`

#### Scenario: Aislamiento entre personas
- **DADO** dos personas con movimientos distintos en sus presupuestos
- **CUANDO** cada una pide un reporte
- **ENTONCES** las cifras de cada respuesta salen solo de su propio presupuesto

### Requirement: Rango de meses
Los reportes por rango SHALL recibir `desde` y `hasta` como mes `yyyy-MM` (año entre 2000 y
2100), ambos inclusivos. El sistema SHALL responder `400` con código `DATOS_INVALIDOS` si falta
un parámetro obligatorio, si un mes está mal formado, si `desde` es posterior a `hasta` o si el
rango tiene más meses que el máximo configurable (60 por defecto). El resultado SHALL traer un
elemento por cada mes del rango, sin omitir los meses sin datos.

#### Scenario: Mes mal formado
- **CUANDO** se pide un reporte con `desde=2026-13`, `desde=2026-1`, `desde=ayer` o `hasta=1999-12`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y un mensaje fijo, sin
  reflejar el valor recibido

#### Scenario: Rango invertido
- **CUANDO** se pide un reporte con `desde=2026-10` y `hasta=2026-09`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Rango más largo que el máximo
- **DADO** el máximo configurado en 60 meses
- **CUANDO** se pide un rango de 61 meses
- **ENTONCES** el sistema responde `400`
- **Y** un rango de exactamente 60 meses responde `200`

#### Scenario: Máximo configurable
- **DADO** el máximo configurado en 12 meses
- **CUANDO** se pide un rango de 13 meses
- **ENTONCES** el sistema responde `400`

#### Scenario: Meses sin datos
- **DADO** un presupuesto sin ninguna transacción
- **CUANDO** se pide un reporte mensual de `2026-01` a `2026-03`
- **ENTONCES** el sistema responde `200` con tres meses, todos con cifras en cero

#### Scenario: Un solo mes
- **CUANDO** se pide un reporte con `desde=2026-10` y `hasta=2026-10`
- **ENTONCES** el sistema responde `200` con un solo mes

### Requirement: Qué cuenta como gasto y como ingreso
El gasto de una categoría en un mes SHALL ser el negativo de su actividad del presupuesto
mensual: la suma de las transacciones y subtransacciones con esa categoría en cuentas del
presupuesto, abiertas o cerradas, tarjetas incluidas. Un reembolso con categoría reduce el
gasto. Las transferencias entre cuentas del presupuesto y las categorías de pago de tarjeta
SHALL quedar fuera. Las cuentas fuera del presupuesto SHALL quedar fuera del gasto y del ingreso.

#### Scenario: Ejemplo completo de octubre
- **DADO** un presupuesto con las cuentas Corriente y Visa (tarjeta), ambas del presupuesto, y
  Ahorro (fuera del presupuesto), y estos movimientos de octubre (milésimas):
  Corriente: sueldo `+500000` sin categoría; supermercado `-80000` en Comida; retiro `-5000`
  sin categoría; compra dividida `-60000` con partes `-40000` en Comida y `-20000` en Hogar;
  transferencia a Ahorro `-100000` con categoría Metas de ahorro.
  Visa: restaurante `-30000` en Comida; reembolso `+10000` en Comida.
  Transferencia de pago Corriente → Visa de `30000` (patas `-30000` y `+30000`, sin categoría).
  Ahorro: intereses `+2000` sin categoría y la pata `+100000` de la transferencia.
- **CUANDO** se piden el gasto por categoría y los ingresos contra gastos de octubre
- **ENTONCES** Comida gasta `140000` (`80000 + 40000 + 30000 - 10000`), Hogar `20000`, Metas de
  ahorro `100000` y "Sin categoría" `5000`; el gasto total es `265000`
- **Y** el ingreso es `500000`: el pago de la tarjeta no es ingreso ni gasto, la categoría
  "Pago: Visa" no aparece, y los intereses de Ahorro no cuentan por estar fuera del presupuesto

#### Scenario: Gasto con tarjeta
- **DADO** un gasto de `-30000` con categoría Comida hecho con una tarjeta del presupuesto
- **CUANDO** se pide el gasto por categoría del mes
- **ENTONCES** Comida suma `30000` y la categoría "Pago: <tarjeta>" no suma nada, aunque la
  reserva para pagar la tarjeta aumente en el presupuesto mensual

#### Scenario: Las categorías de pago no tienen gasto propio
- **DADO** que una categoría de pago de tarjeta no admite transacciones (se rechazan con `422`)
- **CUANDO** se pide el gasto por categoría de cualquier rango
- **ENTONCES** el grupo de pagos de tarjeta y sus categorías no aparecen en la respuesta

#### Scenario: Transferencia entre cuentas del presupuesto
- **DADO** una transferencia de `50000` entre dos cuentas del presupuesto
- **CUANDO** se piden los reportes del mes
- **ENTONCES** no suma al gasto ni al ingreso de ninguna categoría ni de "Sin categoría"

#### Scenario: Transferencia desde una cuenta fuera del presupuesto
- **DADO** una entrada de `20000` desde una cuenta fuera del presupuesto sin categoría
- **CUANDO** se pide ingresos contra gastos del mes
- **ENTONCES** el ingreso del mes incluye `20000`, igual que el presupuesto mensual

#### Scenario: Transacción dividida
- **DADO** una transacción de `-60000` dividida en `-40000` (Comida) y `-20000` (Hogar)
- **CUANDO** se pide el gasto por categoría
- **ENTONCES** Comida suma `40000` y Hogar `20000`, y la transacción no cuenta una vez más
  como un gasto sin categoría

#### Scenario: Parte de una división sin categoría
- **DADO** una transacción dividida con una parte `-10000` sin categoría
- **CUANDO** se pide el gasto por categoría
- **ENTONCES** "Sin categoría" suma `10000`

#### Scenario: Categoría oculta con actividad
- **DADO** una categoría oculta con gasto en el rango
- **CUANDO** se pide el gasto por categoría
- **ENTONCES** aparece en su grupo con su gasto

#### Scenario: Cuenta cerrada
- **DADO** una cuenta del presupuesto cerrada con gastos en octubre
- **CUANDO** se pide el gasto de octubre
- **ENTONCES** esos gastos cuentan, igual que en el presupuesto mensual

### Requirement: Gasto por categoría
El sistema SHALL ofrecer `GET .../reportes/gasto-por-categoria?desde&hasta` con el gasto neto
de cada categoría en el rango, agrupado por grupo en el orden del árbol, el gasto "Sin
categoría" como cubo aparte, el gasto total y, para cada categoría, grupo y cubo, su
porcentaje del total en centésimas de punto porcentual (`1234` = `12,34 %`) redondeado al
entero más cercano, con el medio alejándose de cero. Si el total es cero o negativo, todos los porcentajes SHALL ser `0`.
Solo aparecen las categorías con movimientos en el rango; el grupo de pagos de tarjeta nunca.

#### Scenario: Totales y porcentajes del ejemplo
- **DADO** el presupuesto del ejemplo de octubre
- **CUANDO** se pide el gasto por categoría de `2026-10` a `2026-10`
- **ENTONCES** el total es `265000` y los porcentajes son Comida `5283`, Metas de ahorro `3774`,
  Hogar `755` y "Sin categoría" `189`
- **Y** cada categoría trae su grupo, y el total de cada grupo es la suma de sus categorías

#### Scenario: Rango de varios meses
- **DADO** gasto en Comida de `100000` en septiembre y `50000` en octubre
- **CUANDO** se pide el rango `2026-09` a `2026-10`
- **ENTONCES** Comida suma `150000`

#### Scenario: Sin movimientos
- **DADO** un presupuesto sin transacciones
- **CUANDO** se pide el gasto por categoría de un rango
- **ENTONCES** el total es `0`, no hay grupos, "Sin categoría" vale `0` y los porcentajes son `0`

#### Scenario: Reembolso mayor que el gasto
- **DADO** una categoría con gasto `10000` y un reembolso `+30000`
- **CUANDO** se pide el gasto por categoría
- **ENTONCES** su total es `-20000`, y el total general lo incluye con ese signo

### Requirement: Ingresos contra gastos
El sistema SHALL ofrecer `GET .../reportes/ingresos-gastos?desde&hasta` con, para cada mes del
rango, `ingresos`, `gastos` y `neto = ingresos - gastos`, y los mismos tres totales del rango.
`gastos` SHALL ser la suma del gasto de todas las categorías (sin las de pago de tarjeta) más
"Sin categoría". `ingresos` SHALL ser lo que el presupuesto mensual cuenta como ingreso: las
entradas sin categoría, sin división, de cuentas del presupuesto que no son tarjeta, salvo la
entrada de una transferencia cuya otra pata está en el presupuesto. El saldo inicial de las
cuentas no es un ingreso de ningún mes de este reporte.

#### Scenario: Mes a mes
- **DADO** ingresos `500000` y gastos `265000` en octubre, y nada en noviembre
- **CUANDO** se pide el rango `2026-10` a `2026-11`
- **ENTONCES** octubre trae `500000`, `265000` y `235000`, noviembre trae ceros, y los totales
  son `500000`, `265000` y `235000`

#### Scenario: Coincide con el gasto por categoría
- **DADO** cualquier presupuesto y rango
- **CUANDO** se piden el gasto por categoría y los ingresos contra gastos del mismo rango
- **ENTONCES** el gasto total del primero es igual a los gastos totales del segundo

#### Scenario: Entrada a una tarjeta
- **DADO** una entrada sin categoría de `+10000` en una tarjeta de crédito
- **CUANDO** se pide ingresos contra gastos
- **ENTONCES** no suma al ingreso ni al gasto

#### Scenario: Saldo inicial
- **DADO** una cuenta con saldo inicial `+1000000`
- **CUANDO** se pide ingresos contra gastos
- **ENTONCES** el saldo inicial no aparece como ingreso de ningún mes

### Requirement: Patrimonio neto
El sistema SHALL ofrecer `GET .../reportes/patrimonio?desde&hasta` con, al cierre de cada mes
del rango, `activos`, `pasivos` y `patrimonio = activos - pasivos`. El saldo de una cuenta al
cierre de un mes SHALL ser su saldo inicial más todas sus transacciones con fecha hasta el
último día de ese mes. SHALL incluir todas las cuentas del presupuesto: dentro y fuera de él,
abiertas y cerradas. Las cuentas corriente, de ahorro, de efectivo y de inversión suman a
`activos`; las tarjetas de crédito y los préstamos suman a `pasivos` con el signo cambiado, de
modo que `patrimonio` sea la suma de todos los saldos.

#### Scenario: Ejemplo numérico
- **DADO** al cierre de octubre los saldos Corriente `1200000`, Ahorro (fuera del presupuesto)
  `600000`, Visa `-150000` y Préstamo `-2900000`
- **CUANDO** se pide el patrimonio de `2026-10` a `2026-10`
- **ENTONCES** `activos` vale `1800000`, `pasivos` vale `3050000` y `patrimonio` vale `-1250000`

#### Scenario: El mes actual coincide con los saldos
- **DADO** un presupuesto con cuentas de todos los tipos, abiertas y cerradas, dentro y fuera
  del presupuesto, y sin movimientos futuros
- **CUANDO** se pide el patrimonio del mes actual
- **ENTONCES** `patrimonio` es igual a la suma de los saldos de todas las cuentas que informa
  el listado de saldos

#### Scenario: Cuenta cerrada con saldo
- **DADO** una cuenta cerrada que aún tiene saldo
- **CUANDO** se pide el patrimonio
- **ENTONCES** su saldo se incluye en los meses en que lo tenía

#### Scenario: Meses sin movimientos arrastran el saldo
- **DADO** movimientos solo en septiembre
- **CUANDO** se pide el patrimonio de `2026-09` a `2026-11`
- **ENTONCES** octubre y noviembre repiten las cifras de septiembre

#### Scenario: Meses anteriores a la cuenta
- **DADO** una cuenta con saldo inicial `100000` y su primera transacción en octubre
- **CUANDO** se pide el patrimonio de un mes anterior
- **ENTONCES** la cuenta aporta su saldo inicial (la cuenta no tiene fecha de apertura)

#### Scenario: Presupuesto sin cuentas
- **DADO** un presupuesto sin cuentas
- **CUANDO** se pide el patrimonio de un rango
- **ENTONCES** cada mes vale `0` en `activos`, `pasivos` y `patrimonio`

### Requirement: Evolución del saldo de una cuenta
El sistema SHALL ofrecer `GET .../reportes/cuentas/{cuentaId}/evolucion-saldo?desde&hasta` con
los datos de la cuenta y, para cada mes del rango, `entradas` (suma de los montos positivos),
`salidas` (suma de los montos negativos, con signo) y `saldo` al cierre del mes (saldo inicial
más todas las transacciones hasta el último día del mes). SHALL funcionar para cualquier
cuenta del presupuesto: abierta o cerrada, de cualquier tipo, dentro o fuera del presupuesto.

#### Scenario: Ejemplo numérico
- **DADO** una cuenta con saldo inicial `100000`, en septiembre `+50000` y `-20000`, nada en
  octubre y en noviembre `-30000`
- **CUANDO** se pide su evolución de `2026-09` a `2026-11`
- **ENTONCES** septiembre trae entradas `50000`, salidas `-20000` y saldo `130000`; octubre
  `0`, `0` y `130000`; noviembre `0`, `-30000` y `100000`

#### Scenario: El saldo final coincide con el saldo actual
- **DADO** una cuenta sin transacciones con fecha posterior al último mes pedido
- **CUANDO** se pide su evolución hasta el mes actual
- **ENTONCES** el `saldo` del último mes es igual al saldo de la cuenta en el listado de saldos

#### Scenario: Rango que empieza después de movimientos previos
- **DADO** una cuenta con saldo inicial `100000`, noviembre de 2025 `+40000`, diciembre de 2025
  `-10000`, enero de 2026 `+5000` y `-2000`, y febrero de 2026 sin movimientos
- **CUANDO** se pide su evolución de `2026-01` a `2026-02`
- **ENTONCES** enero trae entradas `5000`, salidas `-2000` y saldo `133000`
  (`100000 + 40000 - 10000 + 5000 - 2000`), no `103000`; febrero trae `0`, `0` y `133000`
- **Y** la respuesta no incluye noviembre ni diciembre, pero sus movimientos sí están en el saldo

#### Scenario: Patrimonio con un rango que empieza después de movimientos previos
- **DADO** una única cuenta corriente con los mismos movimientos del escenario anterior
- **CUANDO** se pide el patrimonio de `2026-01` a `2026-02`
- **ENTONCES** `patrimonio` vale `133000` en ambos meses

#### Scenario: Tarjeta de crédito
- **DADO** una tarjeta con saldo inicial `-200000` y un pago de `+50000`
- **CUANDO** se pide su evolución
- **ENTONCES** el saldo es `-150000` (la deuda se muestra como saldo negativo)

#### Scenario: Cuenta fuera del presupuesto
- **DADO** una cuenta de seguimiento con movimientos
- **CUANDO** se pide su evolución
- **ENTONCES** responde `200` con todos sus movimientos

#### Scenario: Pata de una transferencia
- **DADO** una transferencia entre dos cuentas
- **CUANDO** se pide la evolución de cada una
- **ENTONCES** la de origen trae la salida y la de destino la entrada

### Requirement: Cumplimiento de metas
El sistema SHALL ofrecer `GET .../reportes/metas?desde&hasta` (`hasta` opcional, por defecto
igual a `desde`) con, para cada meta del presupuesto (también de categorías ocultas) en el
orden del árbol y para cada mes del rango: `necesidad`, `asignado`, `gastado` (negativo de la
actividad; `0` en las categorías de pago de tarjeta, cuya actividad es una reserva),
`disponible`, `faltante`, `estado` y `porcentaje` (`asignado / necesidad` en centésimas de
punto porcentual, sin tope, `null` si la necesidad es `0`). También SHALL traer los totales del
rango por meta. `necesidad`, `faltante` y `estado` SHALL ser los mismos que informa el estado
de metas del mes. La meta vigente se aplica a todos los meses (no hay historial de metas).

#### Scenario: Ejemplo de un mes
- **DADO** una meta mensual de `100000` en Comida, con asignado `80000` y actividad `-60000`
- **CUANDO** se pide `desde=2026-10`
- **ENTONCES** trae `necesidad 100000`, `asignado 80000`, `gastado 60000`, `faltante 20000`,
  `estado FALTA` y `porcentaje 8000`

#### Scenario: Rango de dos meses
- **DADO** la meta anterior con asignado `100000` en septiembre y `80000` en octubre
- **CUANDO** se pide de `2026-09` a `2026-10`
- **ENTONCES** los totales son `necesidad 200000`, `asignado 180000` y `porcentaje 9000`

#### Scenario: Coincide con el estado de metas del mes
- **DADO** cualquier presupuesto con metas de los tres tipos, una pospuesta y una sobregastada
- **CUANDO** se pide cada mes del rango con este reporte y con el estado de metas de ese mes
- **ENTONCES** `necesidad`, `asignado`, `disponible`, `faltante` y `estado` son iguales

#### Scenario: Meta pospuesta o sin necesidad
- **DADO** una meta pospuesta en un mes
- **CUANDO** se pide ese mes
- **ENTONCES** la necesidad es `0`, el porcentaje es `null` y el estado es `POSPUESTA`

#### Scenario: Meta en categoría de pago de tarjeta
- **DADO** una meta en la categoría de pago de una tarjeta
- **CUANDO** se pide su mes
- **ENTONCES** `gastado` vale `0`

#### Scenario: Presupuesto sin metas
- **DADO** un presupuesto sin metas
- **CUANDO** se pide el reporte
- **ENTONCES** responde `200` con la lista de metas vacía

#### Scenario: `hasta` omitido
- **CUANDO** se pide `desde=2026-10` sin `hasta`
- **ENTONCES** el reporte trae solo octubre

#### Scenario: Meses anteriores a la creación de la meta
- **DADO** una meta mensual de `100000` creada en octubre de 2026 (no hay historial de metas)
- **CUANDO** se pide el reporte de `2026-01` a `2026-03`
- **ENTONCES** la meta se evalúa también en enero, febrero y marzo con su definición vigente,
  con la misma `necesidad` de `100000` en cada mes y su `asignado` real de cada uno

### Requirement: Coincidencia con el presupuesto mensual
Los reportes SHALL reutilizar las reglas del presupuesto mensual y no reimplementarlas, de modo
que las cifras de un mes coincidan con las de las demás pantallas.

#### Scenario: El gasto por categoría de un mes es la actividad del mes
- **DADO** cualquier presupuesto con gastos, tarjetas, divisiones y transferencias
- **CUANDO** se pide el gasto por categoría de un mes y el presupuesto de ese mes
- **ENTONCES** el gasto de cada categoría que no es de pago de tarjeta es el negativo de su
  actividad, y la suma de todas más "Sin categoría" es el gasto total

#### Scenario: Los ingresos acumulados coinciden con los del mes
- **DADO** un presupuesto con ingresos en varios meses y saldos iniciales positivos
- **CUANDO** se suman los ingresos de todos los meses hasta M y los saldos iniciales positivos
- **ENTONCES** el resultado es igual a los ingresos con que el presupuesto de M calcula su
  "listo para asignar"

### Requirement: Solo lectura, montos y fechas
Los reportes SHALL ser de solo lectura: no crean ni modifican datos ni exigen cambios de
esquema, y los métodos distintos de `GET` SHALL responder `405`. Todos los montos SHALL ser
enteros en milésimas, con signo, y los meses `yyyy-MM`; los meses y las fechas de corte se
calculan en UTC, sin depender de la zona horaria del servidor.

#### Scenario: Método no permitido
- **CUANDO** se llama con `POST` a un reporte
- **ENTONCES** el sistema responde `405` y no cambia nada

#### Scenario: Frontera de mes
- **DADO** una transacción del `2026-10-31` y otra del `2026-11-01`
- **CUANDO** se piden los reportes mensuales
- **ENTONCES** la primera cuenta en octubre y la segunda en noviembre

### Requirement: Costo constante por rango
Cada reporte SHALL resolverse con un número fijo de consultas, que no crece con la cantidad de
meses del rango, de categorías, de cuentas ni de transacciones: nunca una consulta por mes ni
por categoría.

#### Scenario: El costo no depende del rango
- **DADO** un presupuesto con datos en 24 meses
- **CUANDO** se pide un reporte de 1 mes y el mismo reporte de 24 meses
- **ENTONCES** ambas peticiones ejecutan la misma cantidad de consultas
