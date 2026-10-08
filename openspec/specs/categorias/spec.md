# categorias Specification

## Purpose

Permite organizar el gasto de un presupuesto en grupos de categorías y categorías: crearlos,
renombrarlos, ocultarlos, reordenarlos y moverlos, y consultarlos como árbol, sin que nadie más
pueda verlos ni tocarlos.

## Requirements

### Requirement: Autenticación obligatoria
El sistema SHALL exigir autenticación en todas las rutas de
`/api/v1/presupuestos/{presupuestoId}/grupos-categorias` y
`/api/v1/presupuestos/{presupuestoId}/categorias`.

#### Scenario: Petición sin token
- **DADO** una petición sin token de acceso
- **CUANDO** se llama a cualquier ruta de grupos de categorías o de categorías
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

### Requirement: Aislamiento por presupuesto y por persona
El sistema SHALL validar, en cada operación, que el presupuesto de la URL pertenece a la persona
autenticada, y SHALL buscar todo grupo y toda categoría dentro de ese presupuesto. Un
presupuesto, grupo o categoría inexistente o ajeno SHALL responder `404` con código
`RECURSO_NO_ENCONTRADO`, nunca `403`.

#### Scenario: Presupuesto de otra persona en grupos
- **DADO** una persona B autenticada y un presupuesto de la persona A con grupos
- **CUANDO** B crea, renombra, oculta, muestra o mueve un grupo en el presupuesto de A
- **ENTONCES** el sistema responde `404` en cada operación y no modifica nada

#### Scenario: Presupuesto de otra persona en categorías
- **DADO** una persona B autenticada y un presupuesto de la persona A con categorías
- **CUANDO** B crea, consulta, edita, oculta, muestra, mueve o pide el árbol de categorías en el
  presupuesto de A
- **ENTONCES** el sistema responde `404` en cada operación y no modifica nada

#### Scenario: Grupo de otro presupuesto de la misma persona
- **DADO** una persona con dos presupuestos y un grupo en el primero
- **CUANDO** renombra, oculta, muestra o mueve ese grupo por la URL del segundo presupuesto
- **ENTONCES** el sistema responde `404` en cada operación

#### Scenario: Categoría de otro presupuesto de la misma persona
- **DADO** una persona con dos presupuestos y una categoría en el primero
- **CUANDO** la consulta, edita, oculta, muestra o mueve por la URL del segundo presupuesto
- **ENTONCES** el sistema responde `404` en cada operación

#### Scenario: Crear una categoría en un grupo de otro presupuesto
- **DADO** una persona con dos presupuestos y un grupo en el primero
- **CUANDO** crea una categoría en el segundo presupuesto con el `grupoId` de ese grupo
- **ENTONCES** el sistema responde `404` y no crea la categoría

#### Scenario: Mover una categoría a un grupo de otro presupuesto
- **DADO** una categoría del primer presupuesto y un grupo del segundo, ambos de la misma persona
- **CUANDO** mueve la categoría con el `grupoId` del grupo del segundo presupuesto
- **ENTONCES** el sistema responde `404` y la categoría no cambia

#### Scenario: Presupuesto, grupo o categoría inexistente
- **DADO** una persona autenticada
- **CUANDO** usa un `presupuestoId`, un id de grupo o un id de categoría que no existe
- **ENTONCES** el sistema responde `404`

### Requirement: Crear un grupo de categorías
El sistema SHALL permitir crear un grupo enviando su nombre y SHALL responder `201` con el grupo
(id, nombre, orden, `oculto` y fechas de auditoría). El grupo nuevo SHALL quedar al final del
presupuesto (`orden` igual a la cantidad de grupos existentes, desde 0) y nacer visible.

#### Scenario: Primer grupo
- **DADO** un presupuesto sin grupos
- **CUANDO** se crea el grupo `Vivienda`
- **ENTONCES** el sistema responde `201` con `orden` `0` y `oculto` `false`

#### Scenario: Orden consecutivo
- **DADO** un presupuesto con dos grupos
- **CUANDO** se crea un tercer grupo
- **ENTONCES** el sistema responde `201` con `orden` `2`

#### Scenario: Nombre recortado
- **DADO** una persona con un presupuesto
- **CUANDO** crea un grupo con nombre `  Comida  `
- **ENTONCES** el grupo se guarda y se devuelve con nombre `Comida`

### Requirement: Nombre único de grupo por presupuesto
El sistema SHALL rechazar con `409` y código `GRUPO_CATEGORIA_YA_EXISTE` un grupo cuyo nombre,
sin distinguir mayúsculas ni minúsculas, ya existe en el mismo presupuesto, tanto al crear como
al renombrar. Un grupo SHALL poder conservar su propio nombre aunque cambie la capitalización.
El mismo nombre SHALL poder usarse en otro presupuesto.

#### Scenario: Duplicado al crear
- **DADO** un presupuesto con el grupo `Vivienda`
- **CUANDO** se crea un grupo `vivienda`
- **ENTONCES** el sistema responde `409` con código `GRUPO_CATEGORIA_YA_EXISTE`

#### Scenario: Duplicado al renombrar
- **DADO** un presupuesto con los grupos `Vivienda` y `Comida`
- **CUANDO** se renombra `Comida` como `VIVIENDA`
- **ENTONCES** el sistema responde `409` con código `GRUPO_CATEGORIA_YA_EXISTE`

#### Scenario: Cambiar solo la capitalización
- **DADO** un grupo llamado `vivienda`
- **CUANDO** se renombra como `Vivienda`
- **ENTONCES** el sistema responde `200` con nombre `Vivienda`

#### Scenario: Mismo nombre en otro presupuesto
- **DADO** el grupo `Vivienda` en el primer presupuesto de una persona
- **CUANDO** crea un grupo `Vivienda` en su segundo presupuesto
- **ENTONCES** el sistema responde `201`

#### Scenario: Restricción en base de datos
- **DADO** un grupo guardado en un presupuesto
- **CUANDO** se intenta guardar otro con el mismo nombre normalizado en ese presupuesto
- **ENTONCES** la base de datos rechaza el segundo por la restricción única

### Requirement: Validación de los datos del grupo
El sistema SHALL recortar los espacios del nombre y SHALL rechazar con `400` y código
`DATOS_INVALIDOS` un nombre vacío o de más de 100 caracteres.

#### Scenario: Nombre vacío o en blanco
- **DADO** una persona con un presupuesto
- **CUANDO** crea o renombra un grupo con nombre ausente, vacío o solo espacios
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Nombre demasiado largo
- **DADO** una persona con un presupuesto
- **CUANDO** crea un grupo con un nombre de 101 caracteres
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Nombre de 100 caracteres
- **DADO** una persona con un presupuesto
- **CUANDO** crea un grupo con un nombre de 100 caracteres
- **ENTONCES** el sistema responde `201`

### Requirement: Renombrar un grupo
El sistema SHALL permitir renombrar un grupo con `PUT` y SHALL responder `200` con el grupo
actualizado. Cualquier otra propiedad del cuerpo, como `orden` u `oculto`, SHALL ignorarse.

#### Scenario: Renombrado exitoso
- **DADO** un grupo `Vivienda`
- **CUANDO** se renombra como `Hogar`
- **ENTONCES** el sistema responde `200` con nombre `Hogar` y el mismo `orden`

#### Scenario: Propiedades ajenas ignoradas
- **DADO** un grupo con `orden` `1` y `oculto` `false`
- **CUANDO** se envía un `PUT` con nombre válido, `orden` `5` y `oculto` `true`
- **ENTONCES** el sistema responde `200` y el grupo conserva `orden` `1` y `oculto` `false`

### Requirement: Ocultar y mostrar un grupo
El sistema SHALL permitir ocultar y mostrar un grupo con `POST .../{id}/ocultar` y
`POST .../{id}/mostrar`, ambas operaciones idempotentes, y SHALL responder `200` con el grupo.
Ocultar un grupo no cambia su `orden` ni el de ningún otro grupo.

#### Scenario: Ocultar un grupo
- **DADO** un grupo visible
- **CUANDO** se oculta
- **ENTONCES** el sistema responde `200` con `oculto` `true`

#### Scenario: Ocultar dos veces
- **DADO** un grupo ya oculto
- **CUANDO** se oculta de nuevo
- **ENTONCES** el sistema responde `200` con `oculto` `true`

#### Scenario: Mostrar un grupo
- **DADO** un grupo oculto
- **CUANDO** se muestra
- **ENTONCES** el sistema responde `200` con `oculto` `false`

#### Scenario: Mostrar dos veces
- **DADO** un grupo ya visible
- **CUANDO** se muestra
- **ENTONCES** el sistema responde `200` con `oculto` `false`

### Requirement: Mover un grupo
El sistema SHALL permitir mover un grupo con `POST .../{id}/mover` y un cuerpo con `posicion`
(base 0), SHALL reubicarlo en esa posición y renumerar todos los grupos del presupuesto, ocultos
incluidos, de `0` a `n-1` sin huecos ni repetidos, y SHALL responder `200` con el grupo.

#### Scenario: Mover hacia adelante
- **DADO** los grupos `A` (0), `B` (1), `C` (2)
- **CUANDO** se mueve `A` a la posición `2`
- **ENTONCES** el orden queda `B` (0), `C` (1), `A` (2)

#### Scenario: Mover hacia atrás
- **DADO** los grupos `A` (0), `B` (1), `C` (2)
- **CUANDO** se mueve `C` a la posición `0`
- **ENTONCES** el orden queda `C` (0), `A` (1), `B` (2)

#### Scenario: Mover a su propia posición
- **DADO** los grupos `A` (0), `B` (1), `C` (2)
- **CUANDO** se mueve `B` a la posición `1`
- **ENTONCES** el sistema responde `200` y el orden no cambia

#### Scenario: Grupos ocultos incluidos en la renumeración
- **DADO** los grupos `A` (0), `B` (1, oculto), `C` (2)
- **CUANDO** se mueve `C` a la posición `0`
- **ENTONCES** el orden queda `C` (0), `A` (1), `B` (2), sin huecos ni repetidos

#### Scenario: Posición fuera de rango en grupos
- **DADO** un presupuesto con 3 grupos
- **CUANDO** se mueve un grupo a la posición `3` o a la posición `-1`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y el orden no cambia

#### Scenario: Posición ausente en grupos
- **DADO** un grupo
- **CUANDO** se llama a mover sin `posicion`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Crear una categoría
El sistema SHALL permitir crear una categoría enviando `grupoId`, nombre y, opcionalmente,
`nota`, y SHALL responder `201` con la categoría (id, `grupoId`, nombre, orden, `oculta`, nota y
fechas de auditoría). La categoría nueva SHALL quedar al final de su grupo (`orden` igual a la
cantidad de categorías del grupo, desde 0) y nacer visible.

#### Scenario: Primera categoría del grupo
- **DADO** un grupo sin categorías
- **CUANDO** se crea la categoría `Alquiler`
- **ENTONCES** el sistema responde `201` con `orden` `0`, `oculta` `false` y nota `null`

#### Scenario: Orden consecutivo dentro del grupo
- **DADO** un grupo con dos categorías y otro grupo con una
- **CUANDO** se crea una categoría en el primer grupo
- **ENTONCES** el sistema responde `201` con `orden` `2`

#### Scenario: Categoría con nota
- **DADO** un grupo
- **CUANDO** se crea una categoría con nota `Pago mensual`
- **ENTONCES** el sistema responde `201` con esa nota

#### Scenario: Grupo ausente
- **DADO** una persona con un presupuesto
- **CUANDO** crea una categoría sin `grupoId`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Nombre único de categoría por grupo
El sistema SHALL rechazar con `409` y código `CATEGORIA_YA_EXISTE` una categoría cuyo nombre,
sin distinguir mayúsculas ni minúsculas, ya existe en el mismo grupo, al crear, al editar y al
mover. Una categoría SHALL poder conservar su propio nombre aunque cambie la capitalización. El
mismo nombre SHALL poder usarse en grupos distintos.

#### Scenario: Duplicado al crear
- **DADO** un grupo con la categoría `Alquiler`
- **CUANDO** se crea una categoría `ALQUILER` en ese grupo
- **ENTONCES** el sistema responde `409` con código `CATEGORIA_YA_EXISTE`

#### Scenario: Duplicado al editar
- **DADO** un grupo con las categorías `Alquiler` y `Luz`
- **CUANDO** se edita `Luz` con nombre `alquiler`
- **ENTONCES** el sistema responde `409` con código `CATEGORIA_YA_EXISTE`

#### Scenario: Cambiar solo la capitalización
- **DADO** una categoría llamada `alquiler`
- **CUANDO** se edita con nombre `Alquiler`
- **ENTONCES** el sistema responde `200` con nombre `Alquiler`

#### Scenario: Mismo nombre en grupos distintos
- **DADO** la categoría `Otros` en el grupo `Vivienda`
- **CUANDO** se crea una categoría `Otros` en el grupo `Comida`
- **ENTONCES** el sistema responde `201`

#### Scenario: Restricción en base de datos
- **DADO** una categoría guardada en un grupo
- **CUANDO** se intenta guardar otra con el mismo nombre normalizado en ese grupo
- **ENTONCES** la base de datos rechaza la segunda por la restricción única

### Requirement: Validación de los datos de la categoría
El sistema SHALL recortar los espacios del nombre y de la nota, SHALL rechazar con `400` y
código `DATOS_INVALIDOS` un nombre vacío o de más de 100 caracteres o una nota de más de 500
caracteres, y SHALL guardar como `null` una nota vacía o solo de espacios.

#### Scenario: Nombre vacío o en blanco
- **DADO** un grupo
- **CUANDO** se crea o edita una categoría con nombre ausente, vacío o solo espacios
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Nombre demasiado largo
- **DADO** un grupo
- **CUANDO** se crea una categoría con un nombre de 101 caracteres
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Nota recortada
- **DADO** un grupo
- **CUANDO** se crea una categoría con nota `  Pago mensual  `
- **ENTONCES** la categoría se devuelve con nota `Pago mensual`

#### Scenario: Nota vacía
- **DADO** un grupo
- **CUANDO** se crea o edita una categoría con nota vacía o solo de espacios
- **ENTONCES** la categoría se devuelve con nota `null`

#### Scenario: Nota de 500 caracteres
- **DADO** un grupo
- **CUANDO** se crea una categoría con una nota de 500 caracteres
- **ENTONCES** el sistema responde `201`

#### Scenario: Nota demasiado larga
- **DADO** un grupo
- **CUANDO** se crea o edita una categoría con una nota de 501 caracteres
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Editar nombre y nota de una categoría
El sistema SHALL permitir editar una categoría con `PUT`, cambiando solo nombre y nota, y SHALL
responder `200` con la categoría. Una nota ausente o vacía SHALL borrar la nota. Cualquier otra
propiedad del cuerpo, como `grupoId` u `orden`, SHALL ignorarse.

#### Scenario: Edición exitosa
- **DADO** una categoría `Luz` con nota `Vieja`
- **CUANDO** se edita con nombre `Electricidad` y nota `Nueva`
- **ENTONCES** el sistema responde `200` con esos valores y el mismo grupo y `orden`

#### Scenario: Borrar la nota
- **DADO** una categoría con nota
- **CUANDO** se edita sin enviar nota
- **ENTONCES** el sistema responde `200` con nota `null`

#### Scenario: Propiedades ajenas ignoradas
- **DADO** una categoría en el grupo `A` con `orden` `1`
- **CUANDO** se envía un `PUT` con nombre válido, otro `grupoId` y `orden` `7`
- **ENTONCES** el sistema responde `200` y la categoría conserva su grupo y `orden`

### Requirement: Consultar una categoría
El sistema SHALL devolver una categoría de un presupuesto con `GET .../categorias/{id}`.

#### Scenario: Consulta exitosa
- **DADO** una categoría del presupuesto de la persona
- **CUANDO** la pide por su id
- **ENTONCES** el sistema responde `200` con id, `grupoId`, nombre, orden, `oculta`, nota y
  fechas de auditoría

### Requirement: Ocultar y mostrar una categoría
El sistema SHALL permitir ocultar y mostrar una categoría con `POST .../{id}/ocultar` y
`POST .../{id}/mostrar`, ambas operaciones idempotentes, y SHALL responder `200` con la
categoría. Ocultar no cambia el `orden` de ninguna categoría.

#### Scenario: Ocultar una categoría
- **DADO** una categoría visible
- **CUANDO** se oculta
- **ENTONCES** el sistema responde `200` con `oculta` `true`

#### Scenario: Ocultar dos veces
- **DADO** una categoría ya oculta
- **CUANDO** se oculta de nuevo
- **ENTONCES** el sistema responde `200` con `oculta` `true`

#### Scenario: Mostrar una categoría
- **DADO** una categoría oculta
- **CUANDO** se muestra
- **ENTONCES** el sistema responde `200` con `oculta` `false`

#### Scenario: Mostrar dos veces
- **DADO** una categoría ya visible
- **CUANDO** se muestra
- **ENTONCES** el sistema responde `200` con `oculta` `false`

### Requirement: Mover una categoría dentro de su grupo
El sistema SHALL permitir mover una categoría con `POST .../{id}/mover` y un cuerpo con
`grupoId` y `posicion` (base 0). Si `grupoId` es el grupo actual, SHALL reubicarla y renumerar
las categorías del grupo, ocultas incluidas, de `0` a `n-1` sin huecos ni repetidos.

#### Scenario: Mover dentro del grupo
- **DADO** un grupo con las categorías `A` (0), `B` (1), `C` (2)
- **CUANDO** se mueve `A` a la posición `2` del mismo grupo
- **ENTONCES** el orden queda `B` (0), `C` (1), `A` (2)

#### Scenario: Posición fuera de rango dentro del grupo
- **DADO** un grupo con 3 categorías
- **CUANDO** se mueve una de ellas a su propio grupo con posición `3` o `-1`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y el orden no cambia

### Requirement: Mover una categoría a otro grupo
El sistema SHALL permitir mover una categoría a otro grupo del mismo presupuesto, colocándola en
la `posicion` indicada del destino, que va de `0` a la cantidad de categorías del destino (la
categoría ya forma parte de la lista final), y SHALL renumerar sin huecos ni repetidos las
categorías del grupo de origen y las del destino. Si el nombre ya existe en el destino, SHALL
responder `409` con código `CATEGORIA_YA_EXISTE` sin cambiar nada.

#### Scenario: Mover a otro grupo
- **DADO** el grupo `X` con `A` (0), `B` (1), `C` (2) y el grupo `Y` con `D` (0), `E` (1)
- **CUANDO** se mueve `B` al grupo `Y` en la posición `1`
- **ENTONCES** `X` queda `A` (0), `C` (1) e `Y` queda `D` (0), `B` (1), `E` (2)

#### Scenario: Mover al final de otro grupo
- **DADO** un grupo `Y` con 2 categorías
- **CUANDO** se mueve una categoría de otro grupo a `Y` en la posición `2`
- **ENTONCES** la categoría queda con `orden` `2` en `Y`

#### Scenario: Mover a un grupo vacío
- **DADO** un grupo `Y` sin categorías
- **CUANDO** se mueve una categoría de otro grupo a `Y` en la posición `0`
- **ENTONCES** la categoría queda con `orden` `0` en `Y` y el origen queda sin huecos

#### Scenario: Nombre repetido en el grupo destino
- **DADO** la categoría `Otros` en el grupo `X` y otra `otros` en el grupo `Y`
- **CUANDO** se mueve la de `X` a `Y`
- **ENTONCES** el sistema responde `409` con código `CATEGORIA_YA_EXISTE` y nada cambia

#### Scenario: Posición fuera de rango en otro grupo
- **DADO** un grupo destino con 2 categorías
- **CUANDO** se mueve una categoría a él con posición `3` o `-1`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y nada cambia

#### Scenario: Datos de mover ausentes
- **DADO** una categoría
- **CUANDO** se llama a mover sin `grupoId` o sin `posicion`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Árbol de grupos y categorías
El sistema SHALL devolver con `GET .../categorias` la lista completa, sin paginar, de los grupos
del presupuesto ordenados por `orden`, cada uno con sus categorías ordenadas por `orden`.
Con `incluirOcultas` en `false` (valor por defecto) SHALL omitir los grupos ocultos y las
categorías ocultas; con `true` SHALL incluirlos.

#### Scenario: Árbol ordenado
- **DADO** dos grupos con categorías creadas en cualquier orden y luego movidas
- **CUANDO** se pide el árbol
- **ENTONCES** el sistema responde `200` con grupos y categorías en su `orden`

#### Scenario: Ocultos omitidos por defecto
- **DADO** un grupo oculto y, en un grupo visible, una categoría oculta
- **CUANDO** se pide el árbol sin `incluirOcultas`
- **ENTONCES** el árbol no incluye el grupo oculto ni la categoría oculta

#### Scenario: Ocultos incluidos
- **DADO** un grupo oculto y, en un grupo visible, una categoría oculta
- **CUANDO** se pide el árbol con `incluirOcultas=true`
- **ENTONCES** el árbol incluye ambos con su estado de oculto

#### Scenario: Presupuesto sin grupos
- **DADO** un presupuesto sin grupos
- **CUANDO** se pide el árbol
- **ENTONCES** el sistema responde `200` con una lista vacía

#### Scenario: Aislamiento del árbol
- **DADO** dos presupuestos de la misma persona con grupos distintos
- **CUANDO** se pide el árbol de uno
- **ENTONCES** solo aparecen los grupos y categorías de ese presupuesto

### Requirement: Los grupos y las categorías no se borran
El sistema SHALL NOT ofrecer borrado de grupos ni de categorías; se ocultan.

#### Scenario: Intento de borrado de un grupo
- **DADO** un grupo existente
- **CUANDO** se llama a `DELETE` sobre el grupo
- **ENTONCES** el sistema responde `405`

#### Scenario: Intento de borrado de una categoría
- **DADO** una categoría existente
- **CUANDO** se llama a `DELETE` sobre la categoría
- **ENTONCES** el sistema responde `405`

### Requirement: Árbol inicial de un presupuesto
El sistema SHALL crear, junto con todo presupuesto nuevo, los grupos de categorías y las
categorías iniciales, sin nada oculto y con `orden` consecutivo desde 0 en los grupos y, dentro
de cada grupo, en las categorías. El orden y los nombres son:

- **Facturas**: Alquiler, Luz, Agua, Internet, Teléfono
- **Necesidades**: Comida, Transporte, Salud
- **Deseos**: Restaurantes, Ocio, Ropa
- **Ahorro**: Fondo de emergencia, Vacaciones

#### Scenario: Registro de una persona
- **DADO** una persona que se registra con datos válidos
- **CUANDO** consulta el árbol de categorías de su presupuesto "Mi presupuesto"
- **ENTONCES** el sistema responde `200` con los 4 grupos y sus categorías, en el orden y con los
  nombres indicados, todos visibles

#### Scenario: Presupuesto creado con POST
- **DADO** una persona autenticada
- **CUANDO** crea un presupuesto con `POST /api/v1/presupuestos`
- **ENTONCES** el árbol de categorías de ese presupuesto contiene los 4 grupos y sus categorías,
  en el orden y con los nombres indicados

#### Scenario: Cada presupuesto tiene su propio árbol
- **DADO** una persona con dos presupuestos
- **CUANDO** oculta o renombra un grupo o una categoría del primero
- **ENTONCES** el árbol del segundo conserva los 4 grupos y sus categorías sin cambios, y los ids
  de ambos árboles son distintos

#### Scenario: Los nombres iniciales ocupan sus nombres
- **DADO** un presupuesto recién creado
- **CUANDO** se crea un grupo "Facturas", o una categoría "Luz" en el grupo "Facturas"
- **ENTONCES** el sistema responde `409` con el código de nombre ya existente, como con cualquier
  otro grupo o categoría

#### Scenario: Presupuesto rechazado por nombre repetido
- **DADO** una persona que ya tiene un presupuesto llamado "Viajes"
- **CUANDO** crea otro presupuesto "Viajes" y el sistema responde `409`
- **ENTONCES** no se crea ningún grupo ni categoría nuevos

#### Scenario: Atomicidad ante un fallo
- **DADO** un fallo al crear los grupos o las categorías iniciales
- **CUANDO** se registra una persona o se crea un presupuesto
- **ENTONCES** no queda el presupuesto ni sus grupos o categorías y, en el registro, tampoco el
  usuario ni su perfil

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
