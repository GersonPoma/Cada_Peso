# Spec Delta

## ADDED Requirements

### Requirement: Diálogo de transferencia en modo de pago de tarjeta
En modo de pago de una tarjeta el diálogo de transferencia SHALL titularse `Pagar tarjeta`, fijar
la tarjeta como `Cuenta destino` (no editable) y SHALL ocultar `Invertir`. `Cuenta origen` SHALL
ofrecer solo cuentas abiertas con `enPresupuesto` verdadero que no sean de tipo
`TARJETA_CREDITO`, y SHALL verse la nota `Este pago rebaja lo reservado para pagar {nombre de la
tarjeta}`. El resto SHALL ser el diálogo existente, con la categoría oculta por ser entre
cuentas del presupuesto, y enviar `POST .../transferencias` igual que una transferencia normal.

#### Scenario: Campos del modo de pago
- **DADO** el pago de la tarjeta `Visa` y las cuentas `Banco` (corriente, del presupuesto),
  `Efectivo` (del presupuesto), `Hipoteca` (seguimiento) y `Master` (otra tarjeta)
- **CUANDO** se abre el diálogo
- **ENTONCES** el título es `Pagar tarjeta`, el destino es `Visa` y no se puede cambiar, y no hay
  botón `Invertir`
- **Y** el origen ofrece `Banco` y `Efectivo`, y no `Hipoteca` ni `Master`
- **Y** se lee `Este pago rebaja lo reservado para pagar Visa`

#### Scenario: Enviar el pago
- **DADO** el pago de `Visa` (id `4`), el origen `Banco` (id `1`), hoy el 8 de octubre de 2026 y
  un monto de `30`
- **CUANDO** la persona confirma
- **ENTONCES** se envía `{ cuentaOrigenId: 1, cuentaDestinoId: 4, fecha: '2026-10-08',
  monto: 30000, memo: null }` sin `categoriaId`

#### Scenario: Sin cuentas desde las que pagar
- **DADO** el pago de `Visa` y ninguna otra cuenta abierta del presupuesto que no sea tarjeta
- **CUANDO** se abre el diálogo
- **ENTONCES** se lee `No tienes otra cuenta del presupuesto desde la cual pagar` y el botón está
  deshabilitado

#### Scenario: La transferencia normal no cambia
- **DADO** el diálogo abierto con `Agregar transferencia`
- **CUANDO** se observa
- **ENTONCES** se comporta como antes: título, `Invertir`, y origen y destino sin restricción
  nueva
