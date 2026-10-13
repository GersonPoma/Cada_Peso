# Spec Delta

## MODIFIED Requirements

### Requirement: Errores del diálogo de cuenta según su código
El diálogo SHALL decidir cómo mostrar un error de la API por su `codigo`, nunca por `detail`:
`CUENTA_YA_EXISTE` SHALL mostrarse en el campo nombre como `Ya tienes una cuenta con ese nombre`;
`DATOS_INVALIDOS` con `errores` SHALL mostrar cada mensaje en su campo;
`REGLA_NEGOCIO_VIOLADA` SHALL mostrarse como mensaje dentro del diálogo, elegido por la operación
que se envió: al editar, si el tipo enviado difiere del que tenía la cuenta y alguno de los dos
es `TARJETA_CREDITO`, `No se puede cambiar el tipo de una cuenta desde ni hacia tarjeta de
crédito`; en cualquier otro caso,
`El saldo inicial negativo solo se permite en tarjetas de crédito y préstamos`; cualquier otro
error SHALL mostrar el aviso genérico. En todos los casos el diálogo SHALL quedar abierto con lo
escrito.

#### Scenario: Nombre repetido
- **DADO** un presupuesto con la cuenta `Banco`
- **CUANDO** se crea otra con nombre `banco` y la API responde `409` con `CUENTA_YA_EXISTE`
- **ENTONCES** el campo nombre muestra `Ya tienes una cuenta con ese nombre`
- **Y** el diálogo sigue abierto

#### Scenario: Error de campo del backend
- **DADO** el diálogo de crear
- **CUANDO** la API responde `400` con `DATOS_INVALIDOS` y `errores.saldoInicial`
- **ENTONCES** ese mensaje se muestra en el campo saldo inicial

#### Scenario: Regla de negocio
- **DADO** el diálogo de editar
- **CUANDO** la API responde `422` con `REGLA_NEGOCIO_VIOLADA`
- **ENTONCES** el diálogo muestra el mensaje del saldo inicial negativo y sigue abierto

#### Scenario: Regla de negocio por cambio de tipo con tarjeta
- **DADO** el diálogo de editar una cuenta cuyo tipo guardado es `TARJETA_CREDITO`, con la
  petición enviada con otro tipo (por ejemplo, tras una validación local desactualizada)
- **CUANDO** la API responde `422` con `REGLA_NEGOCIO_VIOLADA`
- **ENTONCES** el diálogo muestra `No se puede cambiar el tipo de una cuenta desde ni hacia
  tarjeta de crédito`, no el del saldo negativo, y sigue abierto

#### Scenario: Error desconocido
- **DADO** el diálogo abierto
- **CUANDO** la API responde `500`
- **ENTONCES** se muestra el aviso genérico y el diálogo sigue abierto

## ADDED Requirements

### Requirement: El tipo de una tarjeta de crédito no se cambia al editar
Al editar una cuenta, el diálogo SHALL marcar el campo tipo como inválido y deshabilitar
`Guardar` si el tipo elegido difiere del guardado y alguno de los dos es `TARJETA_CREDITO`,
porque el backend responde `422`. Desde una tarjeta SHALL verse `El tipo de una tarjeta de
crédito no se puede cambiar`; hacia una tarjeta, `Una cuenta existente no puede pasar a tarjeta
de crédito`. Volver al tipo guardado SHALL quitar el error. Crear una cuenta SHALL NOT verse
afectado.

#### Scenario: Cambiar el tipo de una tarjeta
- **DADO** el diálogo de editar la tarjeta `Visa`
- **CUANDO** la persona elige `Ahorro`
- **ENTONCES** el tipo muestra `El tipo de una tarjeta de crédito no se puede cambiar` y
  `Guardar` está deshabilitado

#### Scenario: Pasar una cuenta a tarjeta
- **DADO** el diálogo de editar la cuenta `Banco`, de tipo `Corriente`
- **CUANDO** la persona elige `Tarjeta de crédito`
- **ENTONCES** el tipo muestra `Una cuenta existente no puede pasar a tarjeta de crédito` y
  `Guardar` está deshabilitado

#### Scenario: Volver al tipo guardado
- **DADO** el tipo de `Visa` cambiado a `Ahorro` con el error visible
- **CUANDO** la persona vuelve a elegir `Tarjeta de crédito`
- **ENTONCES** el error desaparece y `Guardar` se habilita

#### Scenario: Renombrar una tarjeta
- **DADO** el diálogo de editar la tarjeta `Visa`
- **CUANDO** la persona cambia solo el nombre a `Visa Oro` y confirma
- **ENTONCES** se envía `PUT` con `{ nombre: 'Visa Oro', tipo: 'TARJETA_CREDITO' }`

#### Scenario: Crear una tarjeta
- **DADO** el diálogo de crear
- **CUANDO** la persona elige `Tarjeta de crédito`
- **ENTONCES** no aparece ningún error de tipo

### Requirement: Pagar tarjeta desde el menú de la cuenta
El menú de una cuenta abierta de tipo `TARJETA_CREDITO` con `enPresupuesto` verdadero SHALL
ofrecer `Pagar tarjeta`, que lleva a las transacciones de esa tarjeta e indica que se debe abrir
el pago. Las tarjetas cerradas, las de seguimiento y las cuentas de otro tipo SHALL NOT
ofrecerlo.

#### Scenario: Tarjeta del presupuesto
- **DADO** la tarjeta abierta `Visa` (id `4`) del presupuesto `3`
- **CUANDO** la persona abre su menú y elige `Pagar tarjeta`
- **ENTONCES** llega a `/presupuestos/3/transacciones` con `cuentaId=4` y `pagarTarjeta=4`

#### Scenario: Cuentas sin la acción
- **DADO** una tarjeta cerrada, una tarjeta de seguimiento y una cuenta `Corriente`
- **CUANDO** la persona abre el menú de cada una
- **ENTONCES** ninguno ofrece `Pagar tarjeta`
