# Spec Delta

## Purpose

Permite mover dinero entre dos cuentas del mismo presupuesto como una sola operación que crea,
edita y borra a la vez sus dos transacciones enlazadas, con una regla de categoría según si las
cuentas son del presupuesto o externas.

## ADDED Requirements

### Requirement: Autenticación obligatoria
El sistema SHALL exigir autenticación en todas las rutas de
`/api/v1/presupuestos/{presupuestoId}/transferencias`.

#### Scenario: Petición sin token
- **DADO** una petición sin token de acceso
- **CUANDO** se llama a cualquier ruta de transferencias
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

### Requirement: Aislamiento por presupuesto y por persona
El sistema SHALL validar primero, en cada operación, que el presupuesto de la URL es de la
persona autenticada, y SHALL buscar toda transferencia, cuenta y categoría dentro de ese
presupuesto. Lo inexistente o ajeno SHALL responder `404` con código `RECURSO_NO_ENCONTRADO`,
nunca `403`.

#### Scenario: Presupuesto de otra persona
- **DADO** una persona B y un presupuesto de la persona A con una transferencia
- **CUANDO** B crea, consulta, edita o borra en el presupuesto de A
- **ENTONCES** el sistema responde `404` en cada caso y no cambia nada

#### Scenario: Transferencia de otro presupuesto
- **DADO** una transferencia del primer presupuesto de una persona
- **CUANDO** se consulta, edita o borra por la URL de otro presupuesto, de ella o de otra persona
- **ENTONCES** el sistema responde `404` en cada caso y la transferencia no cambia

#### Scenario: Cuenta de otro presupuesto
- **DADO** una cuenta de otro presupuesto, de la misma persona o de otra
- **CUANDO** se crea una transferencia con ella como origen o como destino
- **ENTONCES** el sistema responde `404` y no se crea ninguna transacción

#### Scenario: Transacción que no es una transferencia
- **DADO** una transacción normal del presupuesto
- **CUANDO** se consulta, edita o borra por `/transferencias/{transaccionId}`
- **ENTONCES** el sistema responde `404` y la transacción no cambia

### Requirement: Crear una transferencia
`POST /` con `{ cuentaOrigenId, cuentaDestinoId, fecha, monto, categoriaId?, memo? }` SHALL crear
dos transacciones enlazadas y responder `201` con `{ salida, entrada }`. La salida SHALL estar en
la cuenta origen con `-monto` y la entrada en la destino con `+monto`, ambas con la misma `fecha`
y el mismo `memo`, estado `NO_CONCILIADA`, `aprobada` `true` y `transaccionParId` apuntando a la
otra. El `monto` va en milésimas y SHALL ser mayor que 0.

#### Scenario: Transferencia entre dos cuentas del presupuesto
- **DADO** dos cuentas abiertas del presupuesto
- **CUANDO** se crea una transferencia de `30000` de la primera a la segunda
- **ENTONCES** el sistema responde `201` con una salida de `-30000` en la primera, una entrada
  de `30000` en la segunda, la misma fecha y memo, y cada una con el id de la otra en
  `transaccionParId`

#### Scenario: Monto cero o negativo
- **DADO** un `monto` de `0` o negativo
- **CUANDO** se crea la transferencia
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y no crea nada

#### Scenario: Origen igual al destino
- **DADO** la misma cuenta como origen y destino
- **CUANDO** se crea la transferencia
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Campos obligatorios ausentes
- **DADO** un cuerpo sin `cuentaOrigenId`, `cuentaDestinoId`, `fecha` o `monto`
- **CUANDO** se crea la transferencia
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Cuenta cerrada
- **DADO** que la cuenta origen o la destino está cerrada
- **CUANDO** se crea la transferencia
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no crea nada

### Requirement: Regla de categoría de una transferencia
La categoría SHALL depender de `enPresupuesto` de las dos cuentas. Si ambas son del presupuesto o
ambas son externas, la transferencia SHALL ir sin categoría y enviar `categoriaId` SHALL
responder `422`. Si el dinero sale del presupuesto (origen del presupuesto, destino externo),
`categoriaId` SHALL ser obligatoria (`422` si falta). Si el dinero entra al presupuesto (origen
externo, destino del presupuesto), `categoriaId` SHALL ser opcional. La categoría SHALL ser del
presupuesto de la URL (una oculta sirve) y SHALL guardarse solo en la pata de la cuenta del
presupuesto; la pata de la cuenta externa queda sin categoría.

#### Scenario: Ambas del presupuesto con categoría
- **DADO** dos cuentas del presupuesto
- **CUANDO** se crea con `categoriaId`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no crea nada

#### Scenario: Ambas externas
- **DADO** dos cuentas con `enPresupuesto` falso
- **CUANDO** se crea sin categoría
- **ENTONCES** el sistema responde `201` con las dos patas sin categoría

#### Scenario: Ambas externas con categoría
- **DADO** dos cuentas con `enPresupuesto` falso
- **CUANDO** se crea con `categoriaId`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA`

#### Scenario: Del presupuesto a una externa con categoría
- **DADO** una cuenta del presupuesto como origen, una externa como destino y una categoría
- **CUANDO** se crea con esa categoría
- **ENTONCES** el sistema responde `201`, la salida lleva la categoría y la entrada, en la
  cuenta externa, no lleva ninguna

#### Scenario: Del presupuesto a una externa sin categoría
- **DADO** una cuenta del presupuesto como origen y una externa como destino
- **CUANDO** se crea sin `categoriaId`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no crea nada

#### Scenario: De una externa al presupuesto sin categoría
- **DADO** una cuenta externa como origen y una del presupuesto como destino
- **CUANDO** se crea sin `categoriaId`
- **ENTONCES** el sistema responde `201` con las dos patas sin categoría

#### Scenario: De una externa al presupuesto con categoría
- **DADO** una cuenta externa como origen, una del presupuesto como destino y una categoría
- **CUANDO** se crea con esa categoría
- **ENTONCES** el sistema responde `201`, la entrada, en la cuenta del presupuesto, lleva la
  categoría y la salida, en la externa, no lleva ninguna

#### Scenario: Categoría ajena
- **DADO** una categoría de otro presupuesto
- **CUANDO** se crea o edita una transferencia con ella
- **ENTONCES** el sistema responde `404` y no cambia nada

#### Scenario: Categoría oculta
- **DADO** una categoría oculta del presupuesto
- **CUANDO** se crea con ella una transferencia que admite categoría
- **ENTONCES** el sistema responde `201`

### Requirement: Consultar una transferencia
`GET /{transaccionId}` SHALL devolver `{ salida, entrada }` de la transferencia a la que
pertenece la transacción indicada, sea cualquiera de sus dos patas.

#### Scenario: Leer por la salida o por la entrada
- **DADO** una transferencia creada
- **CUANDO** se consulta con el id de la salida y con el id de la entrada
- **ENTONCES** el sistema responde `200` con la misma transferencia en ambos casos

### Requirement: Editar una transferencia
`PUT /{transaccionId}` con `{ fecha, monto, categoriaId?, memo? }` SHALL actualizar en ambas
patas, de forma atómica, `fecha`, el valor absoluto de `monto`, la categoría y `memo`, y
responder `200` con `{ salida, entrada }`. Las cuentas, el estado y `aprobada` no SHALL cambiar.
Aplican las mismas reglas que al crear, incluida la de categoría según las cuentas actuales.
Si alguna cuenta está cerrada, o alguna pata es `RECONCILIADA`, SHALL responder `422` con código
`REGLA_NEGOCIO_VIOLADA` sin cambiar nada.

#### Scenario: Edición correcta
- **DADO** una transferencia de `30000`
- **CUANDO** se edita con monto `45000`, otra fecha y otro memo
- **ENTONCES** el sistema responde `200` con la salida en `-45000` y la entrada en `45000`, ambas
  con la nueva fecha y memo, y las mismas cuentas

#### Scenario: Cambiar la categoría
- **DADO** una transferencia del presupuesto a una externa con categoría Comida
- **CUANDO** se edita con la categoría Ocio
- **ENTONCES** la salida queda con Ocio y la entrada sin categoría

#### Scenario: Regla de categoría al editar
- **DADO** una transferencia del presupuesto a una externa, y otra entre cuentas del presupuesto
- **CUANDO** se edita la primera sin `categoriaId` y la segunda con `categoriaId`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no cambia nada

#### Scenario: Monto inválido al editar
- **DADO** una transferencia
- **CUANDO** se edita con monto `0` o negativo
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Una pata reconciliada
- **DADO** una transferencia cuya salida o entrada es `RECONCILIADA`
- **CUANDO** se edita
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y ninguna pata cambia

#### Scenario: Cuenta cerrada al editar
- **DADO** una transferencia con una de sus cuentas cerrada
- **CUANDO** se edita
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y ninguna pata cambia

#### Scenario: Atomicidad
- **DADO** una edición que viola alguna regla
- **CUANDO** se edita
- **ENTONCES** ninguna de las dos patas cambia

### Requirement: Borrar una transferencia
`DELETE /{transaccionId}` SHALL borrar las dos patas de la transferencia y responder `204`. Si
alguna pata es `RECONCILIADA` SHALL responder `422` con código `REGLA_NEGOCIO_VIOLADA` sin
borrar nada.

#### Scenario: Borrado correcto
- **DADO** una transferencia
- **CUANDO** se borra con el id de cualquiera de sus patas
- **ENTONCES** el sistema responde `204` y ninguna de las dos transacciones existe

#### Scenario: Borrar con una pata reconciliada
- **DADO** una transferencia con una pata `RECONCILIADA`
- **CUANDO** se borra
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y las dos patas siguen
  existiendo

#### Scenario: Borrar dos veces
- **DADO** una transferencia ya borrada
- **CUANDO** se vuelve a borrar
- **ENTONCES** el sistema responde `404`

### Requirement: Estado y aprobación por pata
Cada pata SHALL conservar su propio estado y su propio `aprobada`, porque cada cuenta se concilia
por separado. Aprobar y cambiar el estado (entre `NO_CONCILIADA` y `CONCILIADA`) SHALL seguir
haciéndose por pata con los endpoints de transacciones y SHALL afectar solo a esa pata.

#### Scenario: Aprobar una pata
- **DADO** una transferencia con las dos patas sin aprobar
- **CUANDO** se aprueba la entrada
- **ENTONCES** la entrada queda aprobada y la salida no cambia

#### Scenario: Cambiar el estado de una pata
- **DADO** una transferencia `NO_CONCILIADA`
- **CUANDO** se cambia la salida a `CONCILIADA`
- **ENTONCES** la salida queda `CONCILIADA` y la entrada sigue `NO_CONCILIADA`
