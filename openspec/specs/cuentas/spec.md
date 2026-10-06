# cuentas Specification

## Purpose

Permite gestionar las cuentas financieras (corriente, ahorro, efectivo, tarjeta, inversión,
préstamo) de un presupuesto: crearlas, consultarlas, editarlas de forma limitada y cerrarlas o
reabrirlas, sin que nadie más pueda verlas ni tocarlas.

## Requirements

### Requirement: Autenticación obligatoria
El sistema SHALL exigir autenticación en todas las rutas de
`/api/v1/presupuestos/{presupuestoId}/cuentas`.

#### Scenario: Petición sin token
- **DADO** una petición sin token de acceso
- **CUANDO** se llama a cualquier ruta de cuentas de un presupuesto
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

### Requirement: Aislamiento por presupuesto y por persona
El sistema SHALL validar, en cada operación, que el presupuesto de la URL pertenece a la persona
autenticada, y SHALL buscar la cuenta dentro de ese presupuesto. Un presupuesto o una cuenta
inexistente o ajeno SHALL responder `404` con código `RECURSO_NO_ENCONTRADO`, nunca `403`.

#### Scenario: Presupuesto de otra persona
- **DADO** una persona B autenticada y un presupuesto de la persona A
- **CUANDO** B crea, lista, consulta, edita, cierra o reabre cuentas en el presupuesto de A
- **ENTONCES** el sistema responde `404` en cada caso y no modifica nada

#### Scenario: Cuenta de otra persona
- **DADO** una cuenta del presupuesto de A y una persona B con su propio presupuesto
- **CUANDO** B pide esa cuenta por la URL de su propio presupuesto
- **ENTONCES** el sistema responde `404`

#### Scenario: Cuenta de otro presupuesto de la misma persona
- **DADO** una persona con dos presupuestos y una cuenta en el primero
- **CUANDO** pide esa cuenta por la URL del segundo presupuesto
- **ENTONCES** el sistema responde `404`

#### Scenario: Presupuesto o cuenta inexistente
- **DADO** una persona autenticada
- **CUANDO** usa un `presupuestoId` o un `id` de cuenta que no existe
- **ENTONCES** el sistema responde `404`

### Requirement: Crear una cuenta
El sistema SHALL permitir crear una cuenta enviando nombre, tipo y, opcionalmente,
`enPresupuesto` (por defecto `true`) y `saldoInicial` en milésimas (por defecto `0`), y SHALL
responder `201` con la cuenta (id, nombre, tipo, `enPresupuesto`, `saldoInicial`, `cerrada` y
fechas de auditoría). La cuenta nueva SHALL nacer abierta (`cerrada` en `false`).

#### Scenario: Creación con valores por defecto
- **DADO** una persona con un presupuesto
- **CUANDO** crea una cuenta con nombre `Efectivo` y tipo `EFECTIVO`
- **ENTONCES** el sistema responde `201` con `enPresupuesto` `true`, `saldoInicial` `0` y
  `cerrada` `false`

#### Scenario: Creación de cuenta de seguimiento con saldo
- **DADO** una persona con un presupuesto
- **CUANDO** crea una cuenta tipo `INVERSION` con `enPresupuesto` `false` y `saldoInicial`
  `5000000`
- **ENTONCES** el sistema responde `201` con esos valores

### Requirement: Validación de los datos de la cuenta
El sistema SHALL guardar el nombre sin espacios al inicio ni al final y SHALL validar su longitud,
de 1 a 100 caracteres, sobre el valor ya recortado. El tipo SHALL ser obligatorio y uno de
`CORRIENTE`, `AHORRO`, `EFECTIVO`, `TARJETA_CREDITO`, `INVERSION` o `PRESTAMO`. Un dato inválido
SHALL responder `400` con código `DATOS_INVALIDOS`.

#### Scenario: Nombre recortado
- **DADO** una persona con un presupuesto
- **CUANDO** crea una cuenta con nombre `"  Banco  "`
- **ENTONCES** el sistema responde `201` con nombre `Banco`

#### Scenario: Nombre vacío o solo espacios
- **DADO** una persona con un presupuesto
- **CUANDO** crea una cuenta con nombre `"   "`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y un error en `nombre`

#### Scenario: Nombre de más de 100 caracteres
- **DADO** una persona con un presupuesto
- **CUANDO** crea una cuenta con un nombre de 101 caracteres
- **ENTONCES** el sistema responde `400` con un error en `nombre`

#### Scenario: Tipo ausente o desconocido
- **DADO** una persona con un presupuesto
- **CUANDO** crea una cuenta sin tipo, o con tipo `OTRO`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Saldo inicial negativo según el tipo
El sistema SHALL admitir un `saldoInicial` negativo solo en cuentas `TARJETA_CREDITO` y
`PRESTAMO`. En cualquier otro tipo SHALL responder `422` con código `REGLA_NEGOCIO_VIOLADA`.

#### Scenario: Saldo negativo en tarjeta de crédito
- **DADO** una persona con un presupuesto
- **CUANDO** crea una cuenta `TARJETA_CREDITO` con `saldoInicial` `-250000`
- **ENTONCES** el sistema responde `201` con `saldoInicial` `-250000`

#### Scenario: Saldo negativo en préstamo
- **DADO** una persona con un presupuesto
- **CUANDO** crea una cuenta `PRESTAMO` con `saldoInicial` `-1000000`
- **ENTONCES** el sistema responde `201`

#### Scenario: Saldo negativo en un tipo que no lo admite
- **DADO** una persona con un presupuesto
- **CUANDO** crea una cuenta `CORRIENTE` con `saldoInicial` `-1`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no crea la cuenta

### Requirement: Nombre único por presupuesto
El sistema SHALL rechazar una cuenta cuyo nombre ya exista en el mismo presupuesto, sin distinguir
mayúsculas, con `409` y código `CUENTA_YA_EXISTE`, también ante dos peticiones simultáneas. El
mismo nombre SHALL poder usarse en presupuestos distintos. La base de datos SHALL garantizar la
unicidad de `(presupuesto, nombre sin distinguir mayúsculas)`.

#### Scenario: Nombre repetido con otras mayúsculas
- **DADO** una cuenta `Banco` en un presupuesto
- **CUANDO** se crea otra con nombre `BANCO` en el mismo presupuesto
- **ENTONCES** el sistema responde `409` con código `CUENTA_YA_EXISTE`

#### Scenario: Mismo nombre en otro presupuesto
- **DADO** una cuenta `Banco` en un presupuesto
- **CUANDO** se crea una cuenta `Banco` en otro presupuesto
- **ENTONCES** el sistema responde `201`

#### Scenario: Restricción única en base de datos
- **DADO** una cuenta guardada con nombre normalizado `banco` en un presupuesto
- **CUANDO** se guarda y se vuelca a la base otra cuenta con el mismo presupuesto y nombre
  normalizado
- **ENTONCES** la base rechaza la operación por violación de integridad

### Requirement: Listar cuentas
El sistema SHALL devolver con `GET` la lista completa, sin paginación, de las cuentas del
presupuesto, ordenada por nombre sin distinguir mayúsculas. Por defecto SHALL omitir las cuentas
cerradas; con `incluirCerradas=true` SHALL incluirlas.

#### Scenario: Lista por defecto
- **DADO** un presupuesto con una cuenta abierta y una cerrada
- **CUANDO** se llama a `GET` sin parámetros
- **ENTONCES** el sistema responde `200` solo con la abierta

#### Scenario: Incluir cerradas
- **DADO** un presupuesto con una cuenta abierta y una cerrada
- **CUANDO** se llama a `GET` con `incluirCerradas=true`
- **ENTONCES** el sistema responde `200` con ambas, ordenadas por nombre sin distinguir
  mayúsculas

#### Scenario: Solo las cuentas del presupuesto
- **DADO** dos presupuestos de la misma persona, cada uno con cuentas
- **CUANDO** se lista el primero
- **ENTONCES** la respuesta no contiene cuentas del segundo

### Requirement: Consultar una cuenta
El sistema SHALL devolver con `GET /{id}` el detalle de una cuenta del presupuesto, aunque esté
cerrada.

#### Scenario: Detalle
- **DADO** una cuenta del presupuesto
- **CUANDO** se llama a `GET /{id}`
- **ENTONCES** el sistema responde `200` con los datos de la cuenta

### Requirement: Editar nombre y tipo
El sistema SHALL permitir con `PUT /{id}` cambiar únicamente el nombre y el tipo, con las mismas
validaciones que al crear, y SHALL responder `200`. `enPresupuesto` y `saldoInicial` SHALL
ignorarse si llegan en la petición. Un nombre ya usado por otra cuenta del presupuesto SHALL
responder `409` con `CUENTA_YA_EXISTE`; conservar el propio nombre, aunque cambie su
capitalización, SHALL ser válido. Con `saldoInicial` negativo, un tipo que no lo admite SHALL
responder `422` con `REGLA_NEGOCIO_VIOLADA`.

#### Scenario: Cambiar nombre y tipo
- **DADO** una cuenta `Banco` tipo `CORRIENTE`
- **CUANDO** se edita con nombre `Ahorros` y tipo `AHORRO`
- **ENTONCES** el sistema responde `200` con los nuevos valores

#### Scenario: Cambiar solo la capitalización
- **DADO** una cuenta `banco`
- **CUANDO** se edita con nombre `Banco`
- **ENTONCES** el sistema responde `200` con nombre `Banco`

#### Scenario: Nombre de otra cuenta
- **DADO** dos cuentas `Banco` y `Efectivo` en el presupuesto
- **CUANDO** se edita `Efectivo` con nombre `banco`
- **ENTONCES** el sistema responde `409` con código `CUENTA_YA_EXISTE`

#### Scenario: saldoInicial y enPresupuesto no se editan
- **DADO** una cuenta con `enPresupuesto` `true` y `saldoInicial` `1000`
- **CUANDO** se edita enviando además `enPresupuesto` `false` y `saldoInicial` `9999`
- **ENTONCES** el sistema responde `200` y la cuenta conserva `true` y `1000`

#### Scenario: Tipo que no admite el saldo negativo existente
- **DADO** una cuenta `TARJETA_CREDITO` con `saldoInicial` negativo
- **CUANDO** se edita cambiando el tipo a `AHORRO`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y la cuenta no cambia

#### Scenario: Cambio a un tipo que sí admite saldo negativo
- **DADO** una cuenta `TARJETA_CREDITO` con `saldoInicial` negativo
- **CUANDO** se edita cambiando el tipo a `PRESTAMO`
- **ENTONCES** el sistema responde `200`

#### Scenario: Datos inválidos al editar
- **DADO** una cuenta del presupuesto
- **CUANDO** se edita con nombre vacío
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Cerrar y reabrir una cuenta
El sistema SHALL permitir cerrar una cuenta con `POST /{id}/cerrar` y reabrirla con
`POST /{id}/reabrir`, ambos con respuesta `200` y la cuenta resultante. Ambas operaciones SHALL
ser idempotentes.

#### Scenario: Cerrar
- **DADO** una cuenta abierta
- **CUANDO** se llama a `POST /{id}/cerrar`
- **ENTONCES** el sistema responde `200` con `cerrada` `true`

#### Scenario: Cerrar una cuenta ya cerrada
- **DADO** una cuenta cerrada
- **CUANDO** se llama otra vez a `POST /{id}/cerrar`
- **ENTONCES** el sistema responde `200` con `cerrada` `true`

#### Scenario: Reabrir
- **DADO** una cuenta cerrada
- **CUANDO** se llama a `POST /{id}/reabrir`
- **ENTONCES** el sistema responde `200` con `cerrada` `false`

#### Scenario: Reabrir una cuenta ya abierta
- **DADO** una cuenta abierta
- **CUANDO** se llama a `POST /{id}/reabrir`
- **ENTONCES** el sistema responde `200` con `cerrada` `false`

### Requirement: Las cuentas no se borran
El sistema SHALL NOT ofrecer borrado de cuentas.

#### Scenario: DELETE
- **DADO** una cuenta del presupuesto
- **CUANDO** se llama a `DELETE /{id}`
- **ENTONCES** el sistema responde `405` y la cuenta sigue existiendo
