# Spec Delta

## MODIFIED Requirements

### Requirement: Listo para asignar
`listoParaAsignar` de un mes SHALL ser los ingresos hasta el fin de ese mes, menos todo lo
asignado hasta ese mes (incluido), menos la suma de los sobregastos (`disponible` negativo) al
cierre de los meses anteriores a ese mes. Los ingresos son las transacciones con monto positivo y
sin categoría (ni subtransacciones) en cuentas con `enPresupuesto` verdadero con fecha hasta el
fin del mes, más los saldos iniciales positivos de las cuentas con `enPresupuesto` verdadero que
no son de tipo `TARJETA_CREDITO`. De esos ingresos SHALL excluirse la entrada de una
transferencia cuya pata par está en una cuenta con `enPresupuesto` verdadero (mover dinero dentro
del presupuesto no es un ingreso); la entrada de una transferencia desde una cuenta externa sin
categoría SÍ es un ingreso. Puede ser negativo y se devuelve tal cual. Las transacciones sin
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
