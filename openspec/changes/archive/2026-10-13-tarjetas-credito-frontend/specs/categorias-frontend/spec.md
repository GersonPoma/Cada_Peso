# Spec Delta

## ADDED Requirements

### Requirement: Grupo y categorías de pago de tarjeta como protegidos
El árbol SHALL distinguir el grupo de tipo `PAGOS_TARJETA` y las categorías con `esPagoTarjeta`
verdadero. Cada una SHALL mostrarse con la etiqueta de pago de tarjeta; la categoría SHALL
mostrar además la nota `Se crea y se renombra sola con la tarjeta`. Esta regla prevalece sobre
las demás de esta spec: sobre ellos SHALL NOT ofrecerse menú de acciones, asa de arrastre,
`Agregar categoría` ni `Mover a...`.

#### Scenario: Grupo de pagos
- **DADO** el grupo `Pagos de tarjetas de crédito` con tipo `PAGOS_TARJETA`
- **CUANDO** la persona ve el árbol
- **ENTONCES** el grupo lleva la etiqueta `Pago de tarjeta`, sin asa de arrastre, sin menú de
  acciones y sin `Agregar categoría`

#### Scenario: Categoría de pago
- **DADO** la categoría `Pago: Visa` con `esPagoTarjeta` verdadero
- **CUANDO** la persona ve el árbol
- **ENTONCES** lleva la etiqueta y la nota `Se crea y se renombra sola con la tarjeta`, sin asa de
  arrastre y sin menú de acciones

#### Scenario: Las demás conservan sus acciones
- **DADO** un grupo normal con la categoría `Luz`
- **CUANDO** la persona ve el árbol
- **ENTONCES** conservan su asa, su menú, `Agregar categoría` y `Mover a...` como antes

#### Scenario: Categoría de pago oculta con la tarjeta cerrada
- **DADO** `Mostrar ocultas` activo y la categoría oculta `Pago: Visa` de una tarjeta cerrada
- **CUANDO** la persona ve el árbol
- **ENTONCES** se ve con el ícono de oculta, la etiqueta y sin acciones

### Requirement: Los movimientos no tocan lo protegido
Una categoría normal SHALL NOT poder soltarse en el grupo de pagos ni elegirse el grupo de pagos
como destino de `Mover a...`, y una categoría de pago SHALL NOT poder arrastrarse. El grupo de
pagos SHALL NOT poder arrastrarse, y los demás grupos SHALL seguir reordenándose.

#### Scenario: Soltar en el grupo de pagos
- **DADO** la categoría `Comida` arrastrada sobre el grupo de pagos
- **CUANDO** la persona la suelta
- **ENTONCES** el grupo de pagos no la acepta y no se llama al backend

#### Scenario: Destinos de Mover a...
- **DADO** el diálogo `Mover a...` de la categoría `Comida`
- **CUANDO** la persona abre la lista de grupos
- **ENTONCES** no aparece el grupo de pagos

#### Scenario: Reordenar otros grupos
- **DADO** el grupo de pagos al final del árbol
- **CUANDO** la persona arrastra `Facturas` a otro lugar
- **ENTONCES** se llama a `.../mover` como antes

### Requirement: Errores `422` de lo protegido en Categorías
Si el backend responde `422` con `REGLA_NEGOCIO_VIOLADA` a cualquier operación de esta pantalla
(ocultar o mostrar, mover, arrastrar o los diálogos de grupo, categoría y `Mover a...`), el
sistema SHALL mostrar el mensaje fijo `Las categorías de pago de tarjeta se administran desde la
cuenta de la tarjeta` en lugar del aviso genérico, por `codigo`. Esta regla prevalece sobre las
demás de esta spec. Los diálogos SHALL quedar abiertos y un movimiento SHALL volver al orden
anterior y volver a pedir el árbol.

#### Scenario: Ocultar con el árbol desactualizado
- **DADO** una categoría que otra sesión convirtió en categoría de pago
- **CUANDO** la persona elige `Ocultar` y la API responde `422` con `REGLA_NEGOCIO_VIOLADA`
- **ENTONCES** se muestra el mensaje fijo, no el aviso genérico, y el árbol se vuelve a pedir

#### Scenario: Movimiento rechazado
- **DADO** un movimiento por arrastre
- **CUANDO** la API responde `422` con `REGLA_NEGOCIO_VIOLADA`
- **ENTONCES** la vista vuelve al orden anterior, se muestra el mensaje fijo y el árbol se vuelve
  a pedir

#### Scenario: Diálogo de categoría
- **DADO** el diálogo de editar una categoría
- **CUANDO** la API responde `422` con `REGLA_NEGOCIO_VIOLADA`
- **ENTONCES** se muestra el mensaje fijo y el diálogo sigue abierto con lo escrito

#### Scenario: Mover a... rechazado
- **DADO** el diálogo `Mover a...`
- **CUANDO** la API responde `422` con `REGLA_NEGOCIO_VIOLADA`
- **ENTONCES** el diálogo muestra el mensaje fijo y sigue abierto
