# Spec Delta

## MODIFIED Requirements

### Requirement: Listo para asignar
`listoParaAsignar` de un mes SHALL ser los ingresos hasta el fin de ese mes, menos todo lo
asignado hasta ese mes (incluido), menos la suma de los sobregastos (`disponible` negativo) al
cierre de los meses anteriores a ese mes. Los ingresos son las transacciones con monto positivo y
sin categoría (ni subtransacciones) en cuentas con `enPresupuesto` verdadero con fecha hasta el
fin del mes, más los saldos iniciales positivos de las cuentas con `enPresupuesto` verdadero que
no son de tipo `TARJETA_CREDITO`. De esos ingresos SHALL excluirse toda entrada en una cuenta
`TARJETA_CREDITO` (no entra dinero a ninguna cuenta que no sea tarjeta) y la entrada de una
transferencia cuya pata par está en una cuenta con `enPresupuesto` verdadero (mover dinero dentro
del presupuesto no es un ingreso); la entrada de una transferencia desde una cuenta externa sin
categoría SÍ es un ingreso, salvo que su destino sea una tarjeta. Puede ser negativo y se devuelve tal cual. Las transacciones sin
categoría con monto negativo y las de cuentas fuera del presupuesto no lo afectan.

#### Scenario: Ingresos sin categoría
- **DADO** una entrada de `+500000` sin categoría el `2026-01-01`
- **CUANDO** se consulta enero sin asignaciones
- **ENTONCES** `listoParaAsignar` es `500000`

#### Scenario: Asignar reduce el listo para asignar
- **DADO** ese ingreso de `500000` y Comida con `asignado` `100000` y Ocio con `20000` en enero
- **CUANDO** se consulta enero
- **ENTONCES** `listoParaAsignar` es `380000`

#### Scenario: Saldo inicial de cuentas
- **DADO** una cuenta corriente con saldo inicial `100000`, una `TARJETA_CREDITO` con saldo
  inicial `50000`, una cuenta con `enPresupuesto` falso con `70000` y una cuenta con saldo
  inicial negativo de `-20000`
- **CUANDO** se consulta cualquier mes
- **ENTONCES** `listoParaAsignar` es `100000` (solo cuentan los saldos iniciales positivos de
  cuentas del presupuesto que no son tarjeta)

#### Scenario: Ingresos acumulados por fecha
- **DADO** una entrada de `+200000` el `2026-01-05` y otra de `+80000` el `2026-02-10`, sin
  categoría
- **CUANDO** se consulta enero y febrero sin asignaciones
- **ENTONCES** `listoParaAsignar` es `200000` en enero y `280000` en febrero

#### Scenario: Lo que no es ingreso
- **DADO** una salida de `-30000` sin categoría, una entrada de `+9000` con categoría, una
  entrada dividida con subtransacciones y una entrada de `+70000` en una cuenta con
  `enPresupuesto` falso
- **CUANDO** se consulta el mes
- **ENTONCES** ninguna de ellas cambia `listoParaAsignar`

#### Scenario: Sobregasto que reduce los meses siguientes
- **DADO** un ingreso de `+500000` el `2026-01-01`, Comida con `asignado` `20000` y `actividad`
  `-50000` en enero (sobregasto de `30000`) y `asignado` `10000` en febrero
- **CUANDO** se consulta enero y febrero
- **ENTONCES** `listoParaAsignar` es `480000` en enero (`500000 - 20000`) y `440000` en febrero
  (`500000 - 30000` asignados - `30000` de sobregasto de enero); en marzo, sin más datos, sigue
  siendo `440000` (el sobregasto de enero se descuenta una sola vez, no mes a mes)

#### Scenario: Listo para asignar negativo
- **DADO** ingresos de `100000` y `asignado` total de `150000`
- **CUANDO** se consulta el mes
- **ENTONCES** `listoParaAsignar` es `-50000`

#### Scenario: Mes futuro con asignación
- **DADO** un ingreso de `+300000` en enero y Comida con `asignado` `120000` en `2026-06`
- **CUANDO** se consulta `2026-06`
- **ENTONCES** `listoParaAsignar` es `180000`, y `2026-01` mantiene `300000`

#### Scenario: Transferencia entre dos cuentas del presupuesto
- **DADO** un `listoParaAsignar` de `500000` y una transferencia de `30000` entre dos cuentas con
  `enPresupuesto` verdadero
- **CUANDO** se consulta el mes de la transferencia
- **ENTONCES** `listoParaAsignar` sigue siendo `500000` y ninguna categoría cambia su actividad

#### Scenario: Transferencia desde una cuenta externa sin categoría
- **DADO** un `listoParaAsignar` de `500000` y una transferencia de `50000` desde una cuenta con
  `enPresupuesto` falso hacia una del presupuesto, sin categoría
- **CUANDO** se consulta el mes de la transferencia
- **ENTONCES** `listoParaAsignar` es `550000`

#### Scenario: Transferencia desde una cuenta externa con categoría
- **DADO** un `listoParaAsignar` de `500000` y una transferencia de `50000` desde una cuenta
  externa hacia una del presupuesto, con la categoría Comida
- **CUANDO** se consulta el mes de la transferencia
- **ENTONCES** `listoParaAsignar` sigue siendo `500000` y la `actividad` de Comida suma `50000`

#### Scenario: Transferencia del presupuesto a una cuenta externa
- **DADO** una transferencia de `20000` desde una cuenta del presupuesto hacia una externa, con
  la categoría Comida
- **CUANDO** se consulta el mes de la transferencia
- **ENTONCES** la `actividad` de Comida resta `20000` y `listoParaAsignar` no cambia

#### Scenario: Entrada sin categoría en una tarjeta
- **DADO** un `listoParaAsignar` de `500000` y una entrada de `+9000` sin categoría en una
  cuenta `TARJETA_CREDITO` del presupuesto
- **CUANDO** se consulta el mes de la entrada
- **ENTONCES** `listoParaAsignar` sigue siendo `500000`

#### Scenario: Transferencia desde una cuenta externa hacia una tarjeta
- **DADO** un `listoParaAsignar` de `500000` y una transferencia de `20000` desde una cuenta con
  `enPresupuesto` falso hacia una tarjeta del presupuesto, sin categoría
- **CUANDO** se consulta el mes de la transferencia
- **ENTONCES** `listoParaAsignar` sigue siendo `500000`

## ADDED Requirements

### Requirement: Actividad de una categoría de pago de tarjeta
La `actividad` de la categoría de pago de una tarjeta en un mes SHALL ser la suma, sobre las
transacciones de esa tarjeta en ese mes con categoría normal (subtransacciones incluidas), de
`-monto`, más la suma de `-monto` de las patas de transferencia hacia esa tarjeta cuya otra pata
está en una cuenta con `enPresupuesto` verdadero. Un gasto de `30000` suma `+30000`, un
reembolso resta y un pago de `30000` resta `30000`. Las transacciones sin categoría en la
tarjeta no cuentan. La actividad de las categorías normales no cambia.

#### Scenario: Gasto con tarjeta
- **DADO** Comida con `asignado` `100000` y un gasto de `-30000` en Comida con la tarjeta `Visa`
- **CUANDO** se consulta el mes
- **ENTONCES** Comida tiene `actividad` `-30000` y `disponible` `70000`, y `Pago: Visa` tiene
  `actividad` `30000` y `disponible` `30000`

#### Scenario: Gasto dividido con tarjeta
- **DADO** un gasto dividido de `-30000` con la tarjeta, `-10000` en Comida y `-20000` en Ocio
- **CUANDO** se consulta el mes
- **ENTONCES** la actividad de `Pago: Visa` es `30000`

#### Scenario: Reembolso con tarjeta
- **DADO** un gasto de `-30000` y un reembolso de `+5000`, ambos en Comida con la tarjeta
- **CUANDO** se consulta el mes
- **ENTONCES** la actividad de `Pago: Visa` es `25000`

#### Scenario: Pagar la tarjeta
- **DADO** `Pago: Visa` con `disponible` `30000` y una transferencia de `30000` desde una cuenta
  del presupuesto hacia la tarjeta
- **CUANDO** se consulta el mes del pago
- **ENTONCES** la actividad de `Pago: Visa` baja `30000` y su `disponible` es `0`

#### Scenario: Transacción sin categoría en la tarjeta
- **DADO** una salida de `-8000` sin categoría en la tarjeta
- **CUANDO** se consulta el mes
- **ENTONCES** la actividad de `Pago: Visa` es `0`

#### Scenario: Transferencia desde una cuenta externa hacia la tarjeta
- **DADO** una transferencia de `20000` desde una cuenta con `enPresupuesto` falso hacia la
  tarjeta
- **CUANDO** se consulta el mes
- **ENTONCES** la actividad de `Pago: Visa` es `0`

#### Scenario: Otra tarjeta y otro presupuesto
- **DADO** un gasto de `-30000` con `Visa`, otra tarjeta `Master` sin gastos y un gasto con una
  tarjeta de otro presupuesto
- **CUANDO** se consulta el mes
- **ENTONCES** solo `Pago: Visa` tiene actividad

### Requirement: Reserva completa y sobregasto con tarjeta
La reserva en la categoría de pago SHALL ser siempre el monto completo del gasto, aunque la
categoría normal no tenga disponible. El sobregasto causado con tarjeta SHALL tratarse igual que
el normal: el `disponible` negativo no se arrastra y reduce `listoParaAsignar` de los meses
siguientes. El `disponible` de una categoría de pago SHALL usar la misma regla de arrastre que
las demás (el saldo positivo pasa al mes siguiente, el negativo no).

#### Scenario: Sobregasto con tarjeta
- **DADO** un ingreso de `+100000`, Comida con `asignado` `30000` y un gasto de `-40000` con la
  tarjeta en enero, sin más datos en febrero
- **CUANDO** se consulta enero y febrero
- **ENTONCES** en enero Comida tiene `disponible` `-10000` (sobregastada), `Pago: Visa` tiene
  `disponible` `40000` y `listoParaAsignar` es `70000`; en febrero Comida tiene `disponible` `0`,
  `Pago: Visa` tiene `disponible` `40000` y `listoParaAsignar` es `60000`

#### Scenario: El disponible positivo de la categoría de pago se arrastra
- **DADO** `Pago: Visa` con `disponible` `30000` en enero y sin movimientos en febrero
- **CUANDO** se consulta febrero
- **ENTONCES** su `disponible` es `30000` con `actividad` `0`

#### Scenario: Pagar más de lo reservado
- **DADO** `Pago: Visa` con `disponible` `30000` y un pago de `50000` en enero
- **CUANDO** se consulta enero y febrero
- **ENTONCES** en enero el `disponible` es `-20000`; en febrero el `disponible` es `0` y
  `listoParaAsignar` baja `20000` por el sobregasto de enero

### Requirement: Asignar y mover dinero con categorías de pago
El sistema SHALL permitir asignar dinero a una categoría de pago y mover dinero hacia o desde
ella con las mismas reglas que a cualquier categoría (por ejemplo, para financiar deuda
anterior), incluido el límite de `disponible` del origen al mover. Un saldo inicial negativo de
una tarjeta no reserva nada por sí mismo.

#### Scenario: Financiar deuda previa
- **DADO** una tarjeta con `saldoInicial` `-80000`
- **CUANDO** se consulta el mes
- **ENTONCES** `Pago: Visa` tiene `disponible` `0`; al asignarle `80000` su `disponible` es
  `80000` y `listoParaAsignar` baja `80000`

#### Scenario: Mover dinero desde la categoría de pago
- **DADO** `Pago: Visa` con `disponible` `30000`
- **CUANDO** se mueven `10000` hacia Comida
- **ENTONCES** `Pago: Visa` queda en `20000` y Comida sube `10000`

#### Scenario: Mover más que el disponible de la categoría de pago
- **DADO** `Pago: Visa` con `disponible` `30000`
- **CUANDO** se mueven `40000` hacia Comida
- **ENTONCES** el sistema responde `422` con `REGLA_NEGOCIO_VIOLADA` y nada cambia

### Requirement: Campos de tarjeta en las filas del mes
Cada categoría de la respuesta del mes SHALL incluir `esPagoTarjeta` y `cuentaId` (nulo si no es
de pago), sin cambiar ni quitar ningún campo existente. La categoría de pago de una tarjeta
cerrada SHALL omitirse con `incluirOcultas` en `false`, como cualquier categoría oculta.

#### Scenario: Campos aditivos
- **DADO** la tarjeta `Visa`
- **CUANDO** se consulta el mes
- **ENTONCES** `Pago: Visa` trae `esPagoTarjeta` `true` y `cuentaId` de la tarjeta, y las demás
  categorías `false` y nulo

#### Scenario: Tarjeta cerrada
- **DADO** la tarjeta `Visa` cerrada con `Pago: Visa` oculta
- **CUANDO** se consulta el mes con y sin `incluirOcultas`
- **ENTONCES** solo con `incluirOcultas` aparece, y su `disponible` sigue contando en
  `totalDisponible` de esa respuesta y en el invariante del dinero

### Requirement: Invariante del dinero con tarjetas
En cualquier mes, el dinero en las cuentas con `enPresupuesto` verdadero que no son
`TARJETA_CREDITO` (saldo inicial más transacciones hasta el fin del mes) SHALL ser igual a
`listoParaAsignar` más la suma de los `disponible` de todas las categorías, incluidas las de
pago y las ocultas, en presupuestos sin cuentas `PRESTAMO` con saldo inicial negativo.

#### Scenario: Gasto, sobregasto, reembolso y pago
- **DADO** una cuenta corriente con saldo inicial `200000`, Comida con `asignado` `30000`, un
  gasto de `-40000` con la tarjeta, un reembolso de `+5000` y un pago de `20000` desde la cuenta
  corriente
- **CUANDO** se consulta el mes del gasto y el siguiente
- **ENTONCES** en ambos meses el dinero en la cuenta corriente es igual a `listoParaAsignar` más
  la suma de los `disponible`
