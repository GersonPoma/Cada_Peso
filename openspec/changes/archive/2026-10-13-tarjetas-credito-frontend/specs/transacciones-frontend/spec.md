# Spec Delta

## ADDED Requirements

### Requirement: Aviso de una entrada sin categoría en una tarjeta
El diálogo de transacción SHALL mostrar, con el ícono `info` y como texto, el aviso `Una entrada
sin categoría en una tarjeta de crédito no cuenta como ingreso ni como pago de la tarjeta. Para
pagarla, usa Pagar tarjeta en Cuentas` cuando la cuenta elegida sea de tipo `TARJETA_CREDITO` del
presupuesto, el tipo sea `Entrada`, la transacción no esté dividida y no tenga categoría. SHALL
NOT bloquear el guardado y SHALL desaparecer en cuanto falte alguna de esas condiciones.

#### Scenario: Entrada sin categoría en una tarjeta
- **DADO** el diálogo de crear con la tarjeta `Visa`, el tipo `Entrada` y `Sin categoría`
- **CUANDO** se observa el diálogo
- **ENTONCES** se ve el aviso y el botón de guardar sigue habilitado si el formulario es válido

#### Scenario: Reembolso con categoría
- **DADO** el diálogo de crear con `Visa`, `Entrada` y la categoría `Comida`
- **CUANDO** se observa el diálogo
- **ENTONCES** no se ve el aviso

#### Scenario: Otras cuentas y salidas
- **DADO** una `Entrada` sin categoría en la cuenta `Banco`, y una `Salida` sin categoría en
  `Visa`
- **CUANDO** se observa el diálogo en cada caso
- **ENTONCES** no se ve el aviso

### Requirement: Pagar una tarjeta abre el diálogo de transferencia
Si la URL de las transacciones trae `pagarTarjeta` con el id de una tarjeta abierta del
presupuesto, la pantalla SHALL abrir el diálogo de transferencia en modo de pago de esa tarjeta
una vez cargadas las cuentas y las categorías, y SHALL quitar `pagarTarjeta` de la URL sin
añadir una entrada al historial para que recargar no lo reabra. Un id que no sea una tarjeta
abierta del presupuesto SHALL ignorarse sin aviso y también quitarse de la URL.

#### Scenario: Abrir el pago
- **DADO** la URL `.../transacciones?cuentaId=4&pagarTarjeta=4` con `Visa` (id `4`) abierta
- **CUANDO** terminan de cargar las cuentas y las categorías
- **ENTONCES** se abre el diálogo de transferencia en modo de pago de `Visa`
- **Y** la URL queda `.../transacciones?cuentaId=4`

#### Scenario: Recargar no reabre
- **DADO** el diálogo de pago cerrado y la URL ya sin `pagarTarjeta`
- **CUANDO** la persona recarga la pantalla
- **ENTONCES** el diálogo no se abre

#### Scenario: Id que no es una tarjeta
- **DADO** la URL `.../transacciones?pagarTarjeta=5` donde `5` es una cuenta `Corriente`
- **CUANDO** terminan de cargar las listas
- **ENTONCES** no se abre ningún diálogo ni se muestra ningún error, y `pagarTarjeta` se quita de
  la URL

#### Scenario: Tarjeta cerrada o inexistente
- **DADO** `pagarTarjeta` con el id de una tarjeta cerrada o que no existe
- **CUANDO** terminan de cargar las listas
- **ENTONCES** no se abre ningún diálogo ni se muestra ningún error

### Requirement: El foco vuelve a Agregar transferencia al cerrar el pago de una tarjeta
El pago de una tarjeta se abre desde la URL, sin un botón de origen al que volver. Al cerrarse ese
diálogo, por cualquier vía (Escape, `Cancelar` o guardar), el foco SHALL volver al botón
`Agregar transferencia` de la pantalla. Los demás diálogos de la pantalla SHALL conservar su
manejo del foco.

#### Scenario: Cerrar el pago con Escape
- **DADO** el diálogo de pago de `Visa` abierto desde `?pagarTarjeta=4`
- **CUANDO** la persona pulsa Escape
- **ENTONCES** el diálogo se cierra y el foco queda en `Agregar transferencia`

#### Scenario: Cancelar el pago
- **DADO** el diálogo de pago abierto
- **CUANDO** la persona pulsa `Cancelar`
- **ENTONCES** el foco queda en `Agregar transferencia`

#### Scenario: Guardar el pago
- **DADO** el diálogo de pago con origen y monto válidos
- **CUANDO** la persona pulsa `Crear` y la API responde `201`
- **ENTONCES** el diálogo se cierra y el foco queda en `Agregar transferencia`

#### Scenario: Los demás diálogos no cambian
- **DADO** el diálogo de transferencia abierto con `Agregar transferencia`
- **CUANDO** se cierra
- **ENTONCES** el foco sigue el comportamiento de siempre, sin una opción de restauración propia
