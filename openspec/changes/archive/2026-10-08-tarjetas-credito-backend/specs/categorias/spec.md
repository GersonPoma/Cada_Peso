# Spec Delta

## ADDED Requirements

### Requirement: Grupo de pagos de tarjetas de crédito
El sistema SHALL mantener en cada presupuesto que tenga al menos una cuenta `TARJETA_CREDITO`
con `enPresupuesto` verdadero un único grupo de tipo `PAGOS_TARJETA`, creado al final del orden
la primera vez que se crea una tarjeta. El grupo SHALL localizarse siempre por su tipo, nunca por
su nombre. Su nombre es `Pagos de tarjetas de crédito`; si el presupuesto ya tiene otro grupo
con ese nombre (comparado sin distinguir mayúsculas), el grupo de pagos SHALL llamarse `Pagos de
tarjetas de crédito (2)`, o el primer número libre a partir de 2, y crear la tarjeta SHALL
funcionar igual. Un presupuesto
nuevo SHALL conservar su árbol inicial de 13 categorías, sin ese grupo. Los grupos del árbol
SHALL incluir `tipo` (`NORMAL` o `PAGOS_TARJETA`).

#### Scenario: El árbol inicial no cambia
- **DADO** una persona que se registra
- **CUANDO** consulta el árbol de su presupuesto
- **ENTONCES** tiene el árbol inicial de 13 categorías, todos los grupos con `tipo` `NORMAL` y
  ningún grupo `Pagos de tarjetas de crédito`

#### Scenario: La primera tarjeta crea el grupo
- **DADO** un presupuesto sin tarjetas
- **CUANDO** se crea una cuenta `TARJETA_CREDITO` con `enPresupuesto` verdadero
- **ENTONCES** el árbol incluye, al final, el grupo `Pagos de tarjetas de crédito` con `tipo`
  `PAGOS_TARJETA`

#### Scenario: Una segunda tarjeta reutiliza el grupo
- **DADO** un presupuesto con una tarjeta y su grupo de pagos
- **CUANDO** se crea una segunda tarjeta
- **ENTONCES** existe un solo grupo de pagos con dos categorías

#### Scenario: Una tarjeta de seguimiento no crea nada
- **DADO** un presupuesto sin tarjetas
- **CUANDO** se crea una `TARJETA_CREDITO` con `enPresupuesto` falso
- **ENTONCES** no se crea el grupo ni ninguna categoría de pago

#### Scenario: Ya existe un grupo normal con el nombre del grupo de pagos
- **DADO** un presupuesto con un grupo normal llamado `Pagos de tarjetas de crédito`, con sus
  categorías, y sin tarjetas
- **CUANDO** se crea una cuenta `TARJETA_CREDITO` con `enPresupuesto` verdadero
- **ENTONCES** el sistema responde `201`, el grupo normal conserva su nombre, tipo y categorías, y
  existe un grupo `PAGOS_TARJETA` llamado `Pagos de tarjetas de crédito (2)` con `Pago: <nombre>`

#### Scenario: El nombre alterno también está ocupado
- **DADO** grupos normales llamados `Pagos de tarjetas de crédito` y `Pagos de tarjetas de
  crédito (2)`
- **CUANDO** se crea la primera tarjeta
- **ENTONCES** el grupo de pagos se llama `Pagos de tarjetas de crédito (3)`

#### Scenario: Una segunda tarjeta encuentra el grupo por tipo
- **DADO** un grupo de pagos con nombre alterno y un grupo normal con el nombre original
- **CUANDO** se crea una segunda tarjeta
- **ENTONCES** su categoría queda en el grupo `PAGOS_TARJETA` y no se crea otro grupo

#### Scenario: Otro tipo de cuenta no crea nada
- **DADO** un presupuesto sin tarjetas
- **CUANDO** se crea una cuenta `CORRIENTE`
- **ENTONCES** el árbol no cambia

### Requirement: Categoría de pago por tarjeta
El sistema SHALL mantener una categoría `Pago: <nombre de la tarjeta>` por cada tarjeta con `enPresupuesto` verdadero, dentro del grupo de pagos, con
`esPagoTarjeta` verdadero y `cuentaId` igual a la cuenta; las demás categorías SHALL tener
`esPagoTarjeta` falso y `cuentaId` nulo. Al renombrar la tarjeta SHALL renombrarse la categoría,
al cerrarla SHALL ocultarse y al reabrirla SHALL mostrarse, en la misma transacción que la
operación sobre la cuenta. Como el nombre de cuenta y el de categoría admiten hasta 100
caracteres y el prefijo `Pago: ` suma 6, el nombre de la categoría SHALL recortarse (por
caracteres completos) para medir como máximo 100; si dos categorías de pago del presupuesto
quedaran con el mismo nombre, la segunda SHALL terminar en ` (<id de la cuenta>)` sin pasar de 100.

#### Scenario: Tarjeta con el nombre del largo máximo
- **DADO** una cuenta `TARJETA_CREDITO` cuyo nombre mide 100 caracteres
- **CUANDO** se crea, y luego se renombra con otro nombre de 100 caracteres
- **ENTONCES** el sistema responde `201` y `200`, y la categoría de pago mide como máximo 100
  caracteres y empieza con `Pago: `

#### Scenario: Dos nombres largos que coinciden tras el recorte
- **DADO** dos tarjetas con nombres de 100 caracteres que difieren solo en los últimos 3
- **CUANDO** se crean ambas
- **ENTONCES** existen dos categorías de pago de nombres distintos, de a lo sumo 100 caracteres

#### Scenario: Crear una tarjeta
- **DADO** una cuenta nueva `Visa` tipo `TARJETA_CREDITO`
- **CUANDO** se consulta el árbol
- **ENTONCES** el grupo de pagos contiene `Pago: Visa` con `esPagoTarjeta` `true` y `cuentaId`
  igual al id de `Visa`

#### Scenario: Renombrar la tarjeta
- **DADO** la tarjeta `Visa` con su categoría `Pago: Visa`
- **CUANDO** se renombra la tarjeta a `Visa Oro`
- **ENTONCES** la categoría se llama `Pago: Visa Oro` y conserva su id

#### Scenario: Cerrar la tarjeta
- **DADO** la tarjeta `Visa` con su categoría visible
- **CUANDO** se cierra la tarjeta
- **ENTONCES** la categoría `Pago: Visa` queda oculta (solo aparece con `incluirOcultas`)

#### Scenario: Reabrir la tarjeta
- **DADO** la tarjeta `Visa` cerrada y su categoría oculta
- **CUANDO** se reabre la tarjeta
- **ENTONCES** la categoría `Pago: Visa` vuelve a estar visible

#### Scenario: Las categorías normales
- **DADO** el árbol inicial
- **CUANDO** se consulta
- **ENTONCES** todas las categorías tienen `esPagoTarjeta` `false` y `cuentaId` nulo

#### Scenario: Nombre de tarjeta repetido distinguiendo solo mayúsculas
- **DADO** la tarjeta `Visa` con su categoría `Pago: Visa`
- **CUANDO** se intenta crear otra tarjeta llamada `visa`
- **ENTONCES** el sistema responde `409` con código `CUENTA_YA_EXISTE` y no se crea ninguna
  categoría nueva

### Requirement: Grupo y categorías de pago protegidos
Esta regla prevalece sobre las demás operaciones de este spec. Sobre el grupo de tipo
`PAGOS_TARJETA`, el sistema SHALL responder `422` con `REGLA_NEGOCIO_VIOLADA` al renombrarlo,
ocultarlo, mostrarlo o moverlo. Sobre una categoría de pago SHALL responder `422` al
renombrarla o editar su nota, ocultarla, mostrarla o moverla (dentro del grupo o a otro). Crear
una categoría con `grupoId` del grupo de pagos, o mover una categoría normal a ese grupo,
SHALL responder `422`. En todos los casos no se cambia nada. Un recurso ajeno o inexistente
sigue respondiendo `404`.

#### Scenario: Renombrar la categoría de pago
- **DADO** la categoría `Pago: Visa`
- **CUANDO** se edita su nombre o su nota
- **ENTONCES** el sistema responde `422` con `REGLA_NEGOCIO_VIOLADA` y no cambia

#### Scenario: Ocultar o mostrar a mano
- **DADO** la categoría `Pago: Visa`
- **CUANDO** se oculta o se muestra con las operaciones de categoría
- **ENTONCES** el sistema responde `422`

#### Scenario: Mover la categoría de pago
- **DADO** la categoría `Pago: Visa`
- **CUANDO** se mueve dentro de su grupo o a un grupo normal
- **ENTONCES** el sistema responde `422`

#### Scenario: Crear una categoría en el grupo de pagos
- **DADO** el grupo de pagos
- **CUANDO** se crea una categoría con ese `grupoId`
- **ENTONCES** el sistema responde `422` y no se crea

#### Scenario: Mover una categoría normal al grupo de pagos
- **DADO** la categoría `Comida` y el grupo de pagos
- **CUANDO** se mueve `Comida` a ese grupo
- **ENTONCES** el sistema responde `422` y `Comida` conserva su grupo

#### Scenario: Renombrar, ocultar, mostrar o mover el grupo de pagos
- **DADO** el grupo de pagos
- **CUANDO** se renombra, oculta, muestra o mueve
- **ENTONCES** el sistema responde `422` en cada caso

#### Scenario: Los demás grupos se siguen moviendo
- **DADO** un presupuesto con el grupo de pagos al final
- **CUANDO** se mueve un grupo normal a otra posición
- **ENTONCES** el sistema responde `200` y los órdenes siguen consecutivos

### Requirement: Campos de tarjeta en el árbol
El árbol y la consulta de una categoría SHALL incluir `esPagoTarjeta` (booleano) y `cuentaId`
(nulo si no es de pago) en cada categoría, y `tipo` en cada grupo, sin cambiar ni quitar ningún
campo existente.

#### Scenario: Campos aditivos
- **DADO** un presupuesto con la tarjeta `Visa`
- **CUANDO** se consulta el árbol y la categoría `Pago: Visa`
- **ENTONCES** la respuesta conserva todos los campos previos y agrega `esPagoTarjeta` `true`,
  `cuentaId` y, en el grupo, `tipo` `PAGOS_TARJETA`

### Requirement: Migración de tarjetas y presupuestos existentes
Al arrancar la aplicación el sistema SHALL crear, para cada tarjeta con `enPresupuesto`
verdadero que no tenga categoría de pago, el grupo de pagos (si falta, localizado por tipo y con las
mismas reglas de nombre alterno) y la categoría; el nombre SHALL reflejar el nombre actual de la tarjeta y la categoría SHALL quedar oculta si la
tarjeta está cerrada. La operación SHALL ser idempotente: repetirla no crea ni cambia nada. Cada
presupuesto SHALL migrarse en su propia transacción: el fallo de uno se registra y no impide
migrar los demás ni el arranque, y la siguiente ejecución completa lo que faltó.

#### Scenario: Tarjeta existente sin categoría
- **DADO** una tarjeta creada antes de este cambio, sin categoría de pago
- **CUANDO** se ejecuta la migración
- **ENTONCES** existen el grupo y `Pago: <nombre>` con `cuentaId` de la tarjeta

#### Scenario: Idempotencia
- **DADO** una migración ya ejecutada
- **CUANDO** se ejecuta otra vez
- **ENTONCES** no se crea ninguna fila ni se altera ninguna existente

#### Scenario: Tarjeta cerrada
- **DADO** una tarjeta cerrada sin categoría de pago
- **CUANDO** se ejecuta la migración
- **ENTONCES** su categoría se crea oculta

#### Scenario: Grupo normal con el nombre del grupo de pagos
- **DADO** un presupuesto con un grupo normal llamado `Pagos de tarjetas de crédito` y una
  tarjeta sin categoría de pago
- **CUANDO** se ejecuta la migración, dos veces
- **ENTONCES** el grupo normal no cambia, existe un único grupo `PAGOS_TARJETA` con nombre
  alterno y la categoría de la tarjeta está en él

#### Scenario: El fallo de un presupuesto no afecta a los demás
- **DADO** dos presupuestos con tarjetas sin categoría de pago y un fallo de base de datos al
  migrar el primero
- **CUANDO** se ejecuta la migración
- **ENTONCES** la migración no lanza error, el segundo presupuesto queda migrado y el primero
  no queda con datos a medias; al ejecutarla otra vez sin el fallo, el primero se completa

#### Scenario: Presupuestos sin tarjetas
- **DADO** un presupuesto sin tarjetas
- **CUANDO** se ejecuta la migración
- **ENTONCES** su árbol no cambia
