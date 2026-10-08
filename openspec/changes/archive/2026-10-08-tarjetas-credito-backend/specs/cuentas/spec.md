# Spec Delta

## MODIFIED Requirements

### Requirement: Editar nombre y tipo
El sistema SHALL permitir con `PUT /{id}` cambiar únicamente el nombre y el tipo, con las mismas
validaciones que al crear, y SHALL responder `200`. `enPresupuesto` y `saldoInicial` SHALL
ignorarse si llegan en la petición. Un nombre ya usado por otra cuenta del presupuesto SHALL
responder `409` con `CUENTA_YA_EXISTE`; conservar el propio nombre, aunque cambie su
capitalización, SHALL ser válido. Con `saldoInicial` negativo, un tipo que no lo admite SHALL
responder `422` con `REGLA_NEGOCIO_VIOLADA`. Cambiar el tipo desde `TARJETA_CREDITO` hacia otro,
o desde otro hacia `TARJETA_CREDITO`, SHALL responder `422` con `REGLA_NEGOCIO_VIOLADA` y no
cambiar nada; conservar el tipo `TARJETA_CREDITO` al renombrar SHALL ser válido.

#### Scenario: Cambiar nombre y tipo
- **DADO** una cuenta `Banco` tipo `CORRIENTE`
- **CUANDO** se edita con nombre `Ahorros` y tipo `AHORRO`
- **ENTONCES** el sistema responde `200` con los nuevos valores

#### Scenario: Cambiar solo la capitalización
- **DADO** una cuenta `banco`
- **CUANDO** se edita con nombre `Banco`
- **ENTONCES** el sistema responde `200` con nombre `Banco`

#### Scenario: Nombre de otra cuenta
- **DADO** dos cuentas `Banco` y `Efectivo` en el presupuesto
- **CUANDO** se edita `Efectivo` con nombre `banco`
- **ENTONCES** el sistema responde `409` con código `CUENTA_YA_EXISTE`

#### Scenario: saldoInicial y enPresupuesto no se editan
- **DADO** una cuenta con `enPresupuesto` `true` y `saldoInicial` `1000`
- **CUANDO** se edita enviando además `enPresupuesto` `false` y `saldoInicial` `9999`
- **ENTONCES** el sistema responde `200` y la cuenta conserva `true` y `1000`

#### Scenario: Tipo que no admite el saldo negativo existente
- **DADO** una cuenta `TARJETA_CREDITO` con `saldoInicial` negativo
- **CUANDO** se edita cambiando el tipo a `AHORRO`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y la cuenta no cambia

#### Scenario: Cambio a un tipo que sí admite saldo negativo
- **DADO** una cuenta `TARJETA_CREDITO` con `saldoInicial` negativo
- **CUANDO** se edita cambiando el tipo a `PRESTAMO`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y la cuenta conserva
  el tipo `TARJETA_CREDITO`

#### Scenario: Cambiar una cuenta a tarjeta de crédito
- **DADO** una cuenta `CORRIENTE`
- **CUANDO** se edita cambiando el tipo a `TARJETA_CREDITO`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y la cuenta conserva
  su tipo

#### Scenario: Renombrar una tarjeta conservando el tipo
- **DADO** una cuenta `Visa` tipo `TARJETA_CREDITO`
- **CUANDO** se edita con nombre `Visa Oro` y tipo `TARJETA_CREDITO`
- **ENTONCES** el sistema responde `200` con el nuevo nombre

#### Scenario: Datos inválidos al editar
- **DADO** una cuenta del presupuesto
- **CUANDO** se edita con nombre vacío
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`
