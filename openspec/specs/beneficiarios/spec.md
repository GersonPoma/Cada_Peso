# beneficiarios Specification

## Purpose

Permite gestionar los beneficiarios (payees) de un presupuesto, recordar la categoría con que se
usaron por última vez y buscarlos por prefijo para autocompletar, sin que nadie más pueda verlos
ni tocarlos.

## Requirements

### Requirement: Autenticación obligatoria
El sistema SHALL exigir un token válido en todas las rutas de beneficiarios y SHALL responder
`401` con código `NO_AUTENTICADO` si falta o es inválido.

#### Scenario: Sin token
- **DADO** una petición sin `Authorization`
- **CUANDO** se llama a cualquier ruta de beneficiarios
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

### Requirement: Aislamiento por presupuesto y por persona
El sistema SHALL validar primero que el presupuesto sea de la persona autenticada en toda
operación. Un presupuesto inexistente o ajeno, o un beneficiario de otro presupuesto, SHALL
responder `404`, nunca `403`.

#### Scenario: Presupuesto de otra persona
- **DADO** un presupuesto de otra persona
- **CUANDO** se lista, crea, consulta o edita un beneficiario en él
- **ENTONCES** el sistema responde `404` con código `RECURSO_NO_ENCONTRADO` en cada operación

#### Scenario: Beneficiario de otro presupuesto de la misma persona
- **DADO** dos presupuestos de la misma persona y un beneficiario del segundo
- **CUANDO** se consulta o edita ese id desde la ruta del primero
- **ENTONCES** el sistema responde `404`

#### Scenario: Presupuesto inexistente
- **DADO** un `presupuestoId` que no existe
- **CUANDO** se llama a cualquier ruta de beneficiarios
- **ENTONCES** el sistema responde `404`

### Requirement: Crear un beneficiario
El sistema SHALL permitir crear un beneficiario con `nombre` y, opcionalmente, `categoriaId`
(su categoría predeterminada), y SHALL responder `201` con `id`, `nombre` y
`categoriaPredeterminadaId` (nulo si no hay). El nombre SHALL recortarse y tener entre 1 y 100
caracteres.

#### Scenario: Creación mínima
- **DADO** un presupuesto de la persona
- **CUANDO** se crea un beneficiario `"  Tienda Don Pepe  "` sin categoría
- **ENTONCES** el sistema responde `201` con `nombre` `"Tienda Don Pepe"` y
  `categoriaPredeterminadaId` nulo

#### Scenario: Creación con categoría predeterminada
- **DADO** una categoría del presupuesto
- **CUANDO** se crea un beneficiario con su `categoriaId`
- **ENTONCES** el sistema responde `201` con `categoriaPredeterminadaId` igual a ese id

#### Scenario: Categoría oculta
- **DADO** una categoría oculta del presupuesto
- **CUANDO** se crea un beneficiario con ella
- **ENTONCES** el sistema responde `201`

#### Scenario: Categoría ajena o inexistente
- **DADO** una categoría de otro presupuesto o inexistente
- **CUANDO** se crea un beneficiario con ella
- **ENTONCES** el sistema responde `404` y no se crea nada

#### Scenario: Nombre inválido
- **DADO** un nombre ausente, vacío, solo espacios o de más de 100 caracteres tras recortar
- **CUANDO** se crea el beneficiario
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y el campo `nombre` en
  `errores`

#### Scenario: Cuerpo ilegible
- **DADO** un cuerpo vacío o con JSON mal formado
- **CUANDO** se crea el beneficiario
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Nombre único por presupuesto
El sistema SHALL rechazar con `409` y código `BENEFICIARIO_YA_EXISTE` un nombre ya usado en el
mismo presupuesto, sin distinguir mayúsculas, y SHALL permitir el mismo nombre en presupuestos
distintos.

#### Scenario: Nombre repetido con otras mayúsculas
- **DADO** un beneficiario `"Netflix"` en el presupuesto
- **CUANDO** se crea otro llamado `"  NETFLIX "`
- **ENTONCES** el sistema responde `409` con código `BENEFICIARIO_YA_EXISTE`

#### Scenario: Mismo nombre en otro presupuesto
- **DADO** un beneficiario `"Netflix"` en un presupuesto
- **CUANDO** se crea `"Netflix"` en otro presupuesto, de la misma persona o de otra
- **ENTONCES** el sistema responde `201`

#### Scenario: Peticiones simultáneas con el mismo nombre
- **DADO** que la base de datos rechaza un duplicado que la consulta previa no vio
- **CUANDO** se guarda el beneficiario
- **ENTONCES** el sistema responde `409` con código `BENEFICIARIO_YA_EXISTE`, no `500`

### Requirement: Listar beneficiarios
El sistema SHALL devolver la lista completa de beneficiarios del presupuesto, sin paginar,
ordenada por nombre sin distinguir mayúsculas, con `id`, `nombre` y `categoriaPredeterminadaId`
en cada elemento. Un presupuesto sin beneficiarios SHALL devolver una lista vacía.

#### Scenario: Lista ordenada
- **DADO** los beneficiarios `"zapatería"`, `"Banco"` y `"alquiler"`
- **CUANDO** se listan sin parámetros
- **ENTONCES** el sistema responde `200` con `alquiler`, `Banco` y `zapatería`, en ese orden

#### Scenario: Solo los del presupuesto
- **DADO** beneficiarios en dos presupuestos
- **CUANDO** se lista el primero
- **ENTONCES** solo aparecen los de ese presupuesto

#### Scenario: Sin beneficiarios
- **DADO** un presupuesto sin beneficiarios
- **CUANDO** se lista
- **ENTONCES** el sistema responde `200` con una lista vacía

### Requirement: Buscar por prefijo para autocompletar
El sistema SHALL aceptar el parámetro `q` y devolver solo los beneficiarios cuyo nombre empieza
por `q`, sin distinguir mayúsculas y tratando `%` y `_` como texto literal, ordenados por
nombre. El parámetro `limite` (por defecto `10`, entre `1` y `50`) SHALL acotar el resultado
solo cuando viene `q`. Un `q` vacío o de solo espacios SHALL tratarse como ausente.

#### Scenario: Prefijo sin distinguir mayúsculas
- **DADO** los beneficiarios `"Netflix"`, `"Nestlé"` y `"Banco"`
- **CUANDO** se lista con `q=ne`
- **ENTONCES** el sistema responde `200` con `Nestlé` y `Netflix`, en ese orden

#### Scenario: Solo prefijo, no contiene
- **DADO** un beneficiario `"Banco Unión"`
- **CUANDO** se lista con `q=unión`
- **ENTONCES** el resultado no lo incluye

#### Scenario: Comodines como texto literal
- **DADO** los beneficiarios `"100% Natural"`, `"100 Natural"` y `"a_b"`
- **CUANDO** se lista con `q=100%` y luego con `q=a_`
- **ENTONCES** la primera devuelve solo `100% Natural` y la segunda solo `a_b`

#### Scenario: Límite por defecto y explícito
- **DADO** 15 beneficiarios cuyo nombre empieza por `"a"`
- **CUANDO** se lista con `q=a` y luego con `q=a&limite=3`
- **ENTONCES** la primera devuelve 10 elementos y la segunda los 3 primeros por nombre

#### Scenario: Sin q no se aplica el límite
- **DADO** 15 beneficiarios
- **CUANDO** se lista sin `q`, con o sin `limite` válido
- **ENTONCES** el sistema devuelve los 15

#### Scenario: Límite fuera de rango
- **DADO** un `limite` de `0`, `51` o negativo
- **CUANDO** se lista
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Límite que no es número
- **DADO** `limite=abc`
- **CUANDO** se lista
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Consultar el detalle de un beneficiario
El sistema SHALL devolver un beneficiario por su id con `id`, `nombre` y
`categoriaPredeterminadaId`.

#### Scenario: Detalle existente
- **DADO** un beneficiario del presupuesto
- **CUANDO** se consulta por su id
- **ENTONCES** el sistema responde `200` con sus datos

#### Scenario: Inexistente
- **DADO** un id que no existe
- **CUANDO** se consulta
- **ENTONCES** el sistema responde `404` con código `RECURSO_NO_ENCONTRADO`

### Requirement: Editar un beneficiario
El sistema SHALL permitir editar un beneficiario con `{ nombre, categoriaId? }` y responder
`200`: renombra y fija la categoría predeterminada, o la quita si `categoriaId` es nulo o falta.
Conservar su propio nombre, aunque cambie la capitalización, SHALL ser válido.

#### Scenario: Renombrar y cambiar la categoría
- **DADO** un beneficiario sin categoría y una categoría del presupuesto
- **CUANDO** se edita con un nombre nuevo y esa categoría
- **ENTONCES** el sistema responde `200` con los valores nuevos

#### Scenario: Quitar la categoría
- **DADO** un beneficiario con categoría predeterminada
- **CUANDO** se edita con `categoriaId` nulo
- **ENTONCES** el sistema responde `200` con `categoriaPredeterminadaId` nulo

#### Scenario: Cambiar solo la capitalización
- **DADO** un beneficiario `"netflix"`
- **CUANDO** se edita con el nombre `"Netflix"`
- **ENTONCES** el sistema responde `200` con `nombre` `"Netflix"`

#### Scenario: Nombre de otro beneficiario
- **DADO** dos beneficiarios `"Netflix"` y `"Spotify"`
- **CUANDO** se edita el segundo con el nombre `"netflix"`
- **ENTONCES** el sistema responde `409` con código `BENEFICIARIO_YA_EXISTE` y no cambia nada

#### Scenario: Categoría ajena
- **DADO** una categoría de otro presupuesto
- **CUANDO** se edita un beneficiario con ella
- **ENTONCES** el sistema responde `404` y no cambia nada

#### Scenario: Datos inválidos al editar
- **DADO** un nombre vacío o de más de 100 caracteres
- **CUANDO** se edita
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Los beneficiarios no se borran
El sistema SHALL NOT ofrecer una ruta para borrar beneficiarios.

#### Scenario: Borrado no soportado
- **DADO** un beneficiario existente
- **CUANDO** se llama a `DELETE` sobre su ruta
- **ENTONCES** el sistema responde `405` y el beneficiario sigue existiendo

### Requirement: La categoría predeterminada no puede ser una categoría de pago
El sistema SHALL responder `422` con `REGLA_NEGOCIO_VIOLADA` y no guardar nada al crear o editar
un beneficiario con un `categoriaId` que sea una categoría de pago de tarjeta, porque esas
categorías no admiten transacciones. Una categoría ajena o inexistente sigue respondiendo `404`.

#### Scenario: Crear con una categoría de pago
- **DADO** la categoría `Pago: Visa`
- **CUANDO** se crea un beneficiario con ese `categoriaId`
- **ENTONCES** el sistema responde `422` con `REGLA_NEGOCIO_VIOLADA` y no se crea el beneficiario

#### Scenario: Editar con una categoría de pago
- **DADO** un beneficiario con la categoría predeterminada Comida
- **CUANDO** se edita con el `categoriaId` de `Pago: Visa`
- **ENTONCES** el sistema responde `422` y el beneficiario conserva Comida y su nombre

#### Scenario: Quitar la categoría sigue siendo válido
- **DADO** un beneficiario con categoría predeterminada
- **CUANDO** se edita con `categoriaId` nulo
- **ENTONCES** el sistema responde `200` con `categoriaPredeterminadaId` nulo

#### Scenario: Categoría de pago de otro presupuesto
- **DADO** la categoría de pago de un presupuesto ajeno
- **CUANDO** se crea un beneficiario con ese `categoriaId`
- **ENTONCES** el sistema responde `404`
