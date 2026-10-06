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
