# presupuestos Specification

## Purpose

Permite a cada persona tener uno o más presupuestos propios, en una moneda fija, de los que
colgarán sus cuentas, categorías y transacciones, sin que nadie más pueda verlos ni tocarlos.

## Requirements

### Requirement: Autenticación obligatoria
El sistema SHALL exigir autenticación en todas las rutas de `/api/v1/presupuestos`.

#### Scenario: Petición sin token
- **DADO** una petición sin token de acceso
- **CUANDO** se llama a cualquier ruta de `/api/v1/presupuestos`
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

### Requirement: Crear un presupuesto
El sistema SHALL permitir a una persona autenticada crear un presupuesto enviando un nombre y,
opcionalmente, una moneda, y SHALL responder `201` con el presupuesto creado (id, nombre,
moneda y fechas de auditoría). El presupuesto SHALL pertenecer a quien lo creó.

#### Scenario: Creación con moneda explícita
- **DADO** una persona autenticada
- **CUANDO** crea un presupuesto con nombre `Viajes` y moneda `USD`
- **ENTONCES** el sistema responde `201` con nombre `Viajes` y moneda `USD`

#### Scenario: Creación sin moneda usa la del perfil
- **DADO** una persona autenticada cuya moneda predeterminada es `USD`
- **CUANDO** crea un presupuesto con nombre `Viajes` sin enviar moneda
- **ENTONCES** el sistema responde `201` con moneda `USD`

### Requirement: Validación de los datos del presupuesto
El sistema SHALL guardar el nombre sin espacios al inicio ni al final y SHALL validar su
longitud, de 1 a 100 caracteres, sobre el valor ya recortado. La moneda enviada SHALL ser un
código ISO 4217 existente escrito en mayúsculas. Un dato inválido SHALL responder `400` con
código `DATOS_INVALIDOS` y un error por campo.

#### Scenario: Nombre recortado
- **DADO** una persona autenticada
- **CUANDO** crea un presupuesto con nombre `"  Casa  "`
- **ENTONCES** el sistema responde `201` con nombre `Casa`

#### Scenario: Nombre vacío o solo espacios
- **DADO** una persona autenticada
- **CUANDO** crea un presupuesto con nombre `"   "`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y un error en el campo
  `nombre`

#### Scenario: Nombre de más de 100 caracteres
- **DADO** una persona autenticada
- **CUANDO** crea un presupuesto con un nombre de 101 caracteres
- **ENTONCES** el sistema responde `400` con un error en el campo `nombre`

#### Scenario: Moneda inexistente
- **DADO** una persona autenticada
- **CUANDO** crea un presupuesto con moneda `ZZZ`
- **ENTONCES** el sistema responde `400` con un error en el campo `moneda`

#### Scenario: Moneda en minúsculas
- **DADO** una persona autenticada
- **CUANDO** crea un presupuesto con moneda `usd`
- **ENTONCES** el sistema responde `400` con un error en el campo `moneda`

### Requirement: Nombre único por persona sin distinguir mayúsculas
El sistema SHALL rechazar con `409` y código `PRESUPUESTO_YA_EXISTE` crear o renombrar un
presupuesto con un nombre que, recortado y sin distinguir mayúsculas, ya usa otro presupuesto de
la misma persona. Dos personas distintas SHALL poder usar el mismo nombre.

#### Scenario: Nombre repetido con otras mayúsculas
- **DADO** una persona con un presupuesto llamado `Casa`
- **CUANDO** crea otro con nombre `" CASA "`
- **ENTONCES** el sistema responde `409` con código `PRESUPUESTO_YA_EXISTE`
- **Y** no se crea ningún presupuesto nuevo

#### Scenario: Mismo nombre en personas distintas
- **DADO** que la persona A tiene un presupuesto llamado `Casa`
- **CUANDO** la persona B crea un presupuesto llamado `Casa`
- **ENTONCES** el sistema responde `201`

#### Scenario: Renombrar a un nombre de otro presupuesto propio
- **DADO** una persona con presupuestos `Casa` y `Viajes`
- **CUANDO** renombra `Viajes` a `casa`
- **ENTONCES** el sistema responde `409` con código `PRESUPUESTO_YA_EXISTE`

### Requirement: Listar los presupuestos propios
El sistema SHALL responder `GET /api/v1/presupuestos` con la lista completa, sin paginación, de
los presupuestos de la persona autenticada, ordenada por nombre sin distinguir mayúsculas. La
lista SHALL NOT incluir presupuestos de otras personas.

#### Scenario: Lista ordenada y aislada
- **DADO** que la persona A tiene `Viajes` y `casa`, y la persona B tiene `Otro`
- **CUANDO** la persona A lista sus presupuestos
- **ENTONCES** el sistema responde `200` con `casa` y `Viajes`, en ese orden, y sin `Otro`

### Requirement: Consultar un presupuesto
El sistema SHALL responder `GET /api/v1/presupuestos/{id}` con el detalle del presupuesto si
pertenece a la persona autenticada.

#### Scenario: Detalle de un presupuesto propio
- **DADO** una persona con un presupuesto `Casa`
- **CUANDO** consulta su detalle por id
- **ENTONCES** el sistema responde `200` con id, nombre y moneda del presupuesto

### Requirement: Renombrar un presupuesto
El sistema SHALL permitir modificar solo el nombre de un presupuesto propio mediante
`PUT /api/v1/presupuestos/{id}`, aplicando las mismas reglas de nombre que al crear, y SHALL
responder `200` con el presupuesto actualizado. Renombrarlo con su mismo nombre, aunque cambien
las mayúsculas, SHALL ser válido.

#### Scenario: Renombre exitoso
- **DADO** una persona con un presupuesto `Casa`
- **CUANDO** lo renombra a `Hogar`
- **ENTONCES** el sistema responde `200` con nombre `Hogar`

#### Scenario: Cambiar solo las mayúsculas del propio nombre
- **DADO** una persona con un presupuesto `casa`
- **CUANDO** lo renombra a `Casa`
- **ENTONCES** el sistema responde `200` con nombre `Casa`

### Requirement: La moneda no se puede editar
El sistema SHALL NOT cambiar la moneda de un presupuesto después de crearlo; si un `PUT`
incluye una moneda, SHALL ignorarla.

#### Scenario: PUT con otra moneda
- **DADO** un presupuesto en moneda `BOB`
- **CUANDO** su dueño envía un `PUT` con nombre válido y moneda `USD`
- **ENTONCES** el sistema responde `200` y el presupuesto conserva la moneda `BOB`

### Requirement: Presupuestos ajenos o inexistentes responden 404
El sistema SHALL responder `404` con código `RECURSO_NO_ENCONTRADO` cuando el presupuesto no
existe o pertenece a otra persona, sin distinguir ambos casos y nunca con `403`. El sistema SHALL
NOT ofrecer una operación de borrado de presupuestos.

#### Scenario: Consultar el presupuesto de otra persona
- **DADO** que la persona A tiene un presupuesto
- **CUANDO** la persona B consulta o renombra ese presupuesto por su id
- **ENTONCES** el sistema responde `404` con código `RECURSO_NO_ENCONTRADO`
- **Y** el presupuesto de A no cambia

#### Scenario: Presupuesto inexistente
- **DADO** una persona autenticada
- **CUANDO** consulta un presupuesto con un id que no existe
- **ENTONCES** el sistema responde `404` con código `RECURSO_NO_ENCONTRADO`
