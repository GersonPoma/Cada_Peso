# categorias-frontend Specification

## Purpose
Permite ver y organizar los grupos de categorías y las categorías del presupuesto activo:
crearlos, renombrarlos, ocultarlos y mostrarlos, y reordenarlos arrastrando o con "Mover a...".

## Requirements

### Requirement: Sección Categorías en el menú del presupuesto
El sistema SHALL ofrecer la pantalla de categorías en `/presupuestos/:presupuestoId/categorias`,
con sesión, y SHALL mostrar en el menú lateral del presupuesto un enlace `Categorías` que lleva a
ella.

#### Scenario: Enlace en el menú
- **DADO** una persona en `/presupuestos/3`
- **CUANDO** pulsa `Categorías` en el menú lateral
- **ENTONCES** llega a `/presupuestos/3/categorias`

### Requirement: Árbol de grupos y categorías
La pantalla SHALL pedir el árbol del presupuesto activo y SHALL mostrar los grupos en el orden de
la API, cada uno con sus categorías en el orden de la API. Cada categoría SHALL mostrar su nombre
y, si tiene nota, la nota como texto secundario.

#### Scenario: Árbol inicial
- **DADO** un presupuesto nuevo con su árbol inicial
- **CUANDO** la persona entra a la pantalla de categorías
- **ENTONCES** ve `Facturas`, `Necesidades`, `Deseos` y `Ahorro`, en ese orden, cada uno con sus
  categorías

#### Scenario: Nota como texto secundario
- **DADO** la categoría `Luz` con la nota `Vence el 10`
- **CUANDO** se muestra el árbol
- **ENTONCES** `Vence el 10` aparece debajo de `Luz`

### Requirement: Ocultos a pedido
La pantalla SHALL pedir el árbol sin ocultos por defecto y SHALL ofrecer un interruptor
`Mostrar ocultas` que lo vuelve a pedir con `incluirOcultas=true`. Con el interruptor activo, los
grupos y las categorías ocultos SHALL verse atenuados y con un ícono de visibilidad apagada.

#### Scenario: Por defecto sin ocultos
- **DADO** la pantalla de categorías recién abierta
- **CUANDO** se pide el árbol
- **ENTONCES** la petición lleva `incluirOcultas=false`

#### Scenario: Mostrar ocultas
- **DADO** un grupo oculto `Viejo`
- **CUANDO** la persona activa `Mostrar ocultas`
- **ENTONCES** el árbol se pide con `incluirOcultas=true`
- **Y** `Viejo` se ve atenuado y con el ícono `visibility_off`

### Requirement: Estados de carga, error y vacío
La pantalla SHALL mostrar un indicador de carga mientras espera el árbol por primera vez. Si la
carga falla por un error distinto de `401`, SHALL mostrar el aviso genérico con `Reintentar`. Si
el árbol llega vacío, SHALL mostrar `Aún no tienes categorías` con un botón para agregar un grupo.

#### Scenario: Cargando
- **DADO** el árbol todavía pendiente
- **CUANDO** se observa la pantalla
- **ENTONCES** se ve un indicador de carga

#### Scenario: Error y reintento
- **DADO** que `GET .../categorias` responde `500`
- **CUANDO** la persona pulsa `Reintentar`
- **ENTONCES** el árbol se vuelve a pedir

#### Scenario: Árbol vacío
- **DADO** un presupuesto sin grupos
- **CUANDO** la persona entra a la pantalla
- **ENTONCES** ve `Aún no tienes categorías` y un botón para agregar un grupo

### Requirement: Crear y renombrar grupos
La pantalla SHALL ofrecer `Agregar grupo`, que abre un diálogo con el nombre, y en el menú de cada
grupo `Renombrar`, que abre el mismo diálogo con el nombre actual. La petición SHALL llevar el
nombre recortado. Al guardarse, el diálogo SHALL cerrarse y el árbol SHALL volver a pedirse.

#### Scenario: Crear un grupo
- **DADO** el diálogo de grupo abierto desde `Agregar grupo`
- **CUANDO** la persona escribe `"  Mascotas "` y confirma
- **ENTONCES** la petición es `POST .../grupos-categorias` con `{ nombre: 'Mascotas' }`
- **Y** el árbol se vuelve a pedir

#### Scenario: Renombrar un grupo
- **DADO** el grupo `Deseos` (id `3`)
- **CUANDO** la persona elige `Renombrar`, escribe `Gustos` y confirma
- **ENTONCES** la petición es `PUT .../grupos-categorias/3` con `{ nombre: 'Gustos' }`

### Requirement: Crear y editar categorías
Cada grupo SHALL ofrecer `Agregar categoría`, que abre un diálogo con nombre y nota, y el menú de
cada categoría SHALL ofrecer `Editar`, que abre el mismo diálogo con sus valores. La petición
SHALL llevar el nombre recortado y la nota recortada, o `null` si queda vacía. Al crear SHALL
incluir el `grupoId` del grupo. Al guardarse, el árbol SHALL volver a pedirse.

#### Scenario: Crear una categoría
- **DADO** el grupo `Deseos` (id `3`)
- **CUANDO** la persona pulsa `Agregar categoría` en él, escribe `Libros` sin nota y confirma
- **ENTONCES** la petición es `POST .../categorias` con
  `{ grupoId: 3, nombre: 'Libros', nota: null }`

#### Scenario: Editar nombre y nota
- **DADO** la categoría `Luz` (id `7`)
- **CUANDO** la persona elige `Editar`, escribe la nota `"  Vence el 10 "` y confirma
- **ENTONCES** la petición es `PUT .../categorias/7` con `{ nombre: 'Luz', nota: 'Vence el 10' }`

### Requirement: Validación de los diálogos de grupo y categoría
Los diálogos SHALL exigir un nombre de 1 a 100 caracteres medidos sin los espacios de los extremos
y SHALL limitar la nota a 500 caracteres medidos igual, mostrando un contador de caracteres. El
botón de confirmar SHALL estar deshabilitado mientras el formulario sea inválido o se esté
enviando. Los mensajes SHALL ser `El nombre es obligatorio`,
`El nombre no puede superar los 100 caracteres` y `La nota no puede superar los 500 caracteres`.

#### Scenario: Nombre vacío
- **DADO** un diálogo de grupo o de categoría
- **CUANDO** la persona deja el nombre en `"   "` y sale del campo
- **ENTONCES** se muestra `El nombre es obligatorio` y el botón está deshabilitado

#### Scenario: Nota demasiado larga
- **DADO** el diálogo de categoría
- **CUANDO** la persona escribe una nota de 501 caracteres
- **ENTONCES** se muestra `La nota no puede superar los 500 caracteres`
- **Y** el contador muestra `501/500`

### Requirement: Errores de los diálogos según su código
Los diálogos SHALL decidir cómo mostrar un error de la API por su `codigo`, nunca por `detail`:
`GRUPO_CATEGORIA_YA_EXISTE` SHALL mostrarse en el nombre como `Ya tienes un grupo con ese nombre`;
`CATEGORIA_YA_EXISTE` como `Ya hay una categoría con ese nombre en este grupo`;
`DATOS_INVALIDOS` con `errores` en sus campos; cualquier otro error con el aviso genérico. En
todos los casos el diálogo SHALL quedar abierto con lo escrito.

#### Scenario: Grupo repetido
- **DADO** el grupo `Deseos`
- **CUANDO** se crea otro grupo `deseos` y la API responde `409` con `GRUPO_CATEGORIA_YA_EXISTE`
- **ENTONCES** el nombre muestra `Ya tienes un grupo con ese nombre` y el diálogo sigue abierto

#### Scenario: Categoría repetida en el grupo
- **DADO** la categoría `Luz` en `Facturas`
- **CUANDO** se crea otra `luz` en `Facturas` y la API responde `409` con `CATEGORIA_YA_EXISTE`
- **ENTONCES** el nombre muestra `Ya hay una categoría con ese nombre en este grupo`

### Requirement: Ocultar y mostrar
El menú de cada grupo y de cada categoría SHALL ofrecer `Ocultar` si está visible y `Mostrar` si
está oculto, llamando a `POST .../ocultar` o `.../mostrar` del grupo o la categoría. Al responder,
el árbol SHALL volver a pedirse; un error distinto de `401` SHALL mostrar el aviso genérico.
Ocultar un grupo SHALL NOT ocultar sus categorías.

#### Scenario: Ocultar una categoría
- **DADO** la categoría visible `Ropa` (id `12`)
- **CUANDO** la persona elige `Ocultar` en su menú
- **ENTONCES** se llama a `POST .../categorias/12/ocultar` y el árbol se vuelve a pedir

#### Scenario: Mostrar un grupo
- **DADO** `Mostrar ocultas` activo y el grupo oculto `Viejo` (id `9`)
- **CUANDO** la persona elige `Mostrar` en su menú
- **ENTONCES** se llama a `POST .../grupos-categorias/9/mostrar`

### Requirement: Reordenar arrastrando
La pantalla SHALL permitir arrastrar un grupo desde su asa (ícono `drag_indicator`) para cambiar
su lugar entre los grupos, y arrastrar una categoría para cambiar su lugar dentro de su grupo o
llevarla a otro grupo, incluido uno sin categorías visibles, que SHALL mostrar una zona donde
soltarla. Al soltar, la vista SHALL actualizarse de inmediato y SHALL llamarse a `.../mover`.
Soltar en el mismo lugar SHALL NOT llamar al backend. Si el backend responde con error, la vista
SHALL volver al orden anterior, SHALL mostrarse un aviso (`Ya hay una categoría con ese nombre en
el grupo destino` para `CATEGORIA_YA_EXISTE`; el aviso genérico en otro caso) y el árbol SHALL
volver a pedirse. Mientras un movimiento está en curso, los arrastres nuevos SHALL ignorarse.

La `posicion` enviada SHALL ser la del backend, que cuenta también los elementos ocultos: SHALL
tomarse del `orden` del elemento visible que ocupa el lugar de destino; al soltar al final de
otro grupo, SHALL ser el `orden` de su última categoría visible más uno, o `0` si no tiene
ninguna.

#### Scenario: Reordenar grupos
- **DADO** los grupos `Facturas` (orden 0), `Necesidades` (1), `Deseos` (2)
- **CUANDO** la persona arrastra `Facturas` al último lugar
- **ENTONCES** la vista muestra `Necesidades`, `Deseos`, `Facturas` antes de la respuesta
- **Y** se llama a `POST .../grupos-categorias/{id de Facturas}/mover` con `{ posicion: 2 }`

#### Scenario: Mover una categoría dentro de su grupo
- **DADO** el grupo `Deseos` (id `3`) con `Restaurantes` (0), `Ocio` (1), `Ropa` (2)
- **CUANDO** la persona arrastra `Ropa` al primer lugar
- **ENTONCES** se llama a `POST .../categorias/{id de Ropa}/mover` con
  `{ grupoId: 3, posicion: 0 }`

#### Scenario: Mover a otro grupo
- **DADO** `Ocio` en `Deseos` y el grupo `Ahorro` (id `4`) con `Fondo de emergencia` (0) y
  `Vacaciones` (1)
- **CUANDO** la persona suelta `Ocio` entre las dos categorías de `Ahorro`
- **ENTONCES** se llama a `.../mover` con `{ grupoId: 4, posicion: 1 }`

#### Scenario: Mover a un grupo vacío
- **DADO** el grupo `Mascotas` (id `5`) sin categorías
- **CUANDO** la persona suelta una categoría en su zona de destino
- **ENTONCES** se llama a `.../mover` con `{ grupoId: 5, posicion: 0 }`

#### Scenario: Posición con una categoría oculta en medio
- **DADO** `Mostrar ocultas` apagado y un grupo con `A` (0), `H` (1, oculta), `B` (2), `C` (3), de
  los que se ven `A`, `B` y `C`
- **CUANDO** la persona arrastra `A` al último lugar visible
- **ENTONCES** se llama a `.../mover` con `posicion` `3`

#### Scenario: Soltar en el mismo lugar
- **DADO** una categoría
- **CUANDO** la persona la arrastra y la suelta en su mismo lugar
- **ENTONCES** no se llama al backend

#### Scenario: El backend rechaza el movimiento
- **DADO** `Ocio` en `Deseos` y una categoría `Ocio` en `Ahorro`
- **CUANDO** la persona suelta `Ocio` en `Ahorro` y la API responde `409` con
  `CATEGORIA_YA_EXISTE`
- **ENTONCES** `Ocio` vuelve a `Deseos` en la vista
- **Y** se muestra `Ya hay una categoría con ese nombre en el grupo destino`
- **Y** el árbol se vuelve a pedir

#### Scenario: Movimiento en curso
- **DADO** un movimiento enviado al backend y todavía sin respuesta
- **CUANDO** la persona intenta otro arrastre
- **ENTONCES** se ignora y no se llama al backend

### Requirement: Mover a... sin arrastrar
El menú de cada categoría SHALL ofrecer `Mover a...`, que abre un diálogo para elegir el grupo
destino y el lugar dentro de él (`Al principio` o `Después de {categoría}` entre las categorías
visibles del destino, sin la propia). Al confirmar SHALL llamar a `.../mover` con la misma regla
de `posicion` que al arrastrar y, al responder, SHALL cerrar el diálogo y volver a pedir el árbol.
Un `409` con `CATEGORIA_YA_EXISTE` SHALL mostrarse en el diálogo como
`Ya hay una categoría con ese nombre en el grupo destino`.

#### Scenario: Mover al principio de otro grupo
- **DADO** `Ocio` en `Deseos` y el grupo `Ahorro` (id `4`)
- **CUANDO** la persona elige `Mover a...`, el grupo `Ahorro`, `Al principio` y confirma
- **ENTONCES** se llama a `.../mover` con `{ grupoId: 4, posicion: 0 }`

#### Scenario: Mover después de una categoría
- **DADO** `Ocio` en `Deseos` y `Ahorro` con `Fondo de emergencia` (0) y `Vacaciones` (1)
- **CUANDO** la persona elige `Ahorro` y `Después de Vacaciones`
- **ENTONCES** se llama a `.../mover` con `{ grupoId: 4, posicion: 2 }`

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
