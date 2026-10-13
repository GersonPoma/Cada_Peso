# tarjetas-credito-frontend Specification

## Purpose
Reúne lo que el frontend hace con las tarjetas de crédito y comparten varias pantallas: cómo se
distingue una categoría de pago de tarjeta y qué selectores de categoría no la ofrecen, porque el
backend no admite transacciones en ella.

## Requirements

### Requirement: Etiqueta de pago de tarjeta
Dondequiera que se muestre una categoría de pago de tarjeta o el grupo de pagos de tarjetas, el
sistema SHALL acompañar su nombre con el ícono `credit_card` y el texto `Pago de tarjeta`, de
modo que el estado no dependa solo del color. La etiqueta SHALL ser un solo componente
reutilizado por todas las pantallas que la muestran, y su texto SHALL leerse con lectores de
pantalla.

#### Scenario: Ícono y texto
- **DADO** la categoría `Pago: Visa`, que es de pago de tarjeta
- **CUANDO** se muestra con su etiqueta
- **ENTONCES** se ven el ícono `credit_card` y el texto `Pago de tarjeta`

#### Scenario: Sin etiqueta en las demás categorías
- **DADO** la categoría `Comida`, que no es de pago
- **CUANDO** se muestra
- **ENTONCES** no tiene etiqueta de pago de tarjeta

### Requirement: Los selectores de categoría de Transacciones excluyen las de pago
El diálogo de transacción, cada parte de una transacción dividida, el selector de `Categorizar`
de la barra de lote y el diálogo de transferencia SHALL NOT ofrecer las categorías de pago de
tarjeta, porque el backend responde `422` si se usan. Un grupo que quede sin categorías por esa
exclusión SHALL NOT mostrarse. Las demás reglas de cada selector (ocultas, `Sin categoría`) SHALL
quedar igual.

#### Scenario: Diálogo de transacción
- **DADO** un presupuesto con `Comida` y `Pago: Visa`
- **CUANDO** la persona abre el diálogo de crear una transacción
- **ENTONCES** el selector de categoría ofrece `Comida` y no `Pago: Visa`

#### Scenario: Partes de una división
- **DADO** el interruptor `Dividir` activo en el diálogo de transacción
- **CUANDO** la persona abre el selector de categoría de una parte
- **ENTONCES** no aparece ninguna categoría de pago de tarjeta

#### Scenario: Categorizar en lote
- **DADO** una o más filas seleccionadas
- **CUANDO** la persona abre el selector de `Categorizar` de la barra de lote
- **ENTONCES** no aparece ninguna categoría de pago de tarjeta

#### Scenario: Diálogo de transferencia
- **DADO** una transferencia del presupuesto a una cuenta de seguimiento, que exige categoría
- **CUANDO** la persona abre el selector de categoría
- **ENTONCES** no aparece ninguna categoría de pago de tarjeta

#### Scenario: El grupo de pagos desaparece del selector
- **DADO** el grupo `Pagos de tarjetas de crédito`, que solo tiene categorías de pago
- **CUANDO** se abre cualquiera de esos selectores
- **ENTONCES** el grupo no aparece

### Requirement: El filtro por categoría de la lista oculta las de pago
El filtro `categoriaId` de la lista de transacciones SHALL NOT ofrecer las categorías de pago de
tarjeta, porque filtrar por una devuelve siempre una lista vacía. Si la URL ya trae como
`categoriaId` una categoría de pago, el filtro SHALL seguir mostrándola como elegida y la lista
SHALL mostrar lo que responda la API, sin error.

#### Scenario: Opciones del filtro
- **DADO** un presupuesto con `Comida` y `Pago: Visa`
- **CUANDO** la persona abre el filtro de categoría
- **ENTONCES** ofrece `Comida` y no `Pago: Visa`

#### Scenario: Enlace antiguo con una categoría de pago
- **DADO** la URL `.../transacciones?categoriaId=20`, donde `20` es `Pago: Visa`
- **CUANDO** se abre la pantalla
- **ENTONCES** el filtro muestra `Pago: Visa` como elegida y la lista aparece vacía sin error

### Requirement: Un `422` sobre lo protegido se explica con un mensaje fijo
Cuando una operación sobre el grupo o las categorías de pago responda `422` con
`REGLA_NEGOCIO_VIOLADA`, el sistema SHALL mostrar el mensaje fijo `Las categorías de pago de
tarjeta se administran desde la cuenta de la tarjeta` en lugar del aviso genérico, decidiéndolo
por el `codigo` y nunca por el `detail` de la respuesta.

#### Scenario: Mensaje por código
- **DADO** una respuesta `422` con `REGLA_NEGOCIO_VIOLADA` y un `detail` con texto interno
- **CUANDO** se muestra el error
- **ENTONCES** se ve el mensaje fijo y no el texto del `detail`
