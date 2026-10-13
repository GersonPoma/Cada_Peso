# Spec Delta

## ADDED Requirements

### Requirement: Fila de una categoría de pago de tarjeta
La fila de una categoría con `esPagoTarjeta` verdadero SHALL mostrar junto a su nombre la etiqueta
de pago de tarjeta, el texto `Reserva para pagar la tarjeta` y un enlace `Ver movimientos` a las
transacciones filtradas por su `cuentaId`. Asignar, `Mover dinero...` y las metas SHALL seguir
ofrecidos en esas filas, porque el backend los permite. Las filas de las demás categorías SHALL
quedar igual.

#### Scenario: Fila de pago
- **DADO** la categoría `Pago: Visa` con `esPagoTarjeta` verdadero y `cuentaId` `4`
- **CUANDO** se muestra el mes
- **ENTONCES** su fila lleva la etiqueta `Pago de tarjeta`, el texto `Reserva para pagar la
  tarjeta` y el enlace `Ver movimientos`

#### Scenario: Enlace a la tarjeta
- **DADO** la fila de `Pago: Visa` con `cuentaId` `4` en el presupuesto `3`
- **CUANDO** la persona pulsa `Ver movimientos`
- **ENTONCES** llega a `/presupuestos/3/transacciones?cuentaId=4`

#### Scenario: Acciones que siguen permitidas
- **DADO** la fila de `Pago: Visa`
- **CUANDO** la persona abre su menú
- **ENTONCES** ofrece `Mover dinero...` y `Agregar meta`, y puede editar su asignado

#### Scenario: Pantalla estrecha
- **DADO** una pantalla estrecha
- **CUANDO** se muestra la fila de `Pago: Visa`
- **ENTONCES** la etiqueta, la aclaración y el enlace se apilan bajo el nombre sin desbordar

#### Scenario: Las demás filas
- **DADO** la categoría `Comida` con `esPagoTarjeta` falso
- **CUANDO** se muestra el mes
- **ENTONCES** su fila no tiene etiqueta, aclaración ni enlace
