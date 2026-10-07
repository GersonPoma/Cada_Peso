# Spec Delta

## ADDED Requirements

### Requirement: Beneficiario vinculado a la transacción
Al crear o editar una transacción con `beneficiario` (texto) no vacío, el sistema SHALL buscar
el beneficiario del presupuesto por su nombre sin distinguir mayúsculas y, si no existe,
crearlo sin categoría predeterminada; SHALL vincular la transacción a él y SHALL guardar como
texto `beneficiario` el nombre del beneficiario. Sin `beneficiario`, o vacío, la transacción SHALL
quedar sin vínculo y con `beneficiario` y `beneficiarioId` nulos. Las transferencias SHALL NOT
llevar beneficiario.

#### Scenario: Beneficiario nuevo
- **DADO** un presupuesto sin el beneficiario `"Netflix"`
- **CUANDO** se crea una transacción con `beneficiario` `"  Netflix "`
- **ENTONCES** el sistema responde `201` con `beneficiario` `"Netflix"` y un `beneficiarioId`, y
  el beneficiario `"Netflix"` existe en el presupuesto sin categoría predeterminada

#### Scenario: Beneficiario existente con otras mayúsculas
- **DADO** un beneficiario `"Netflix"` del presupuesto
- **CUANDO** se crea una transacción con `beneficiario` `"NETFLIX"`
- **ENTONCES** el sistema responde `201` con `beneficiario` `"Netflix"` y el `beneficiarioId` del
  beneficiario existente, sin crear otro

#### Scenario: Mismo nombre en otro presupuesto
- **DADO** un beneficiario `"Netflix"` en otro presupuesto de la persona
- **CUANDO** se crea una transacción con `beneficiario` `"Netflix"` en este presupuesto
- **ENTONCES** el sistema crea un beneficiario propio de este presupuesto y vincula la
  transacción a él

#### Scenario: Sin beneficiario
- **DADO** una transacción creada sin `beneficiario` o con `"   "`
- **CUANDO** se consulta
- **ENTONCES** tiene `beneficiario` y `beneficiarioId` nulos

#### Scenario: Editar cambia el vínculo
- **DADO** una transacción vinculada a `"Netflix"`
- **CUANDO** se edita con `beneficiario` `"Spotify"`
- **ENTONCES** el sistema responde `200` con `beneficiario` `"Spotify"` y el `beneficiarioId` de
  ese beneficiario, que se crea si no existía; `"Netflix"` no se borra

#### Scenario: Editar sin beneficiario quita el vínculo
- **DADO** una transacción vinculada a `"Netflix"`
- **CUANDO** se edita sin `beneficiario`
- **ENTONCES** el sistema responde `200` con `beneficiario` y `beneficiarioId` nulos

#### Scenario: Transacción existente sin vínculo
- **DADO** una transacción anterior con texto de beneficiario y sin vínculo
- **CUANDO** se consulta, se lista o se aprueba
- **ENTONCES** conserva su texto, `beneficiarioId` es nulo y no se crea ningún beneficiario

#### Scenario: Una transacción anterior se vincula al editarla
- **DADO** una transacción anterior con texto `"Tienda"` y sin vínculo
- **CUANDO** se edita con `beneficiario` `"Tienda"`
- **ENTONCES** queda vinculada a un beneficiario `"Tienda"`

#### Scenario: Mover de cuenta y cambiar el estado no tocan el vínculo
- **DADO** una transacción vinculada a un beneficiario
- **CUANDO** se mueve de cuenta, se aprueba o se cambia su estado
- **ENTONCES** su `beneficiario` y su `beneficiarioId` no cambian

#### Scenario: Una pata de transferencia no tiene beneficiario
- **DADO** una transferencia creada, con su salida y su entrada
- **CUANDO** se consulta cada pata por su id y en el listado de transacciones
- **ENTONCES** las dos patas tienen `beneficiario` y `beneficiarioId` nulos, y la transferencia
  no crea ningún beneficiario en el presupuesto

### Requirement: Categoría recordada del beneficiario
Cuando una transacción, al crearse o editarse, queda con `categoriaId` (y sin
subtransacciones) y con beneficiario vinculado, el sistema SHALL fijar esa categoría como la
categoría predeterminada del beneficiario, reemplazando la anterior. Una transacción dividida o
sin categoría SHALL NOT cambiar la categoría predeterminada.

#### Scenario: Al crear
- **DADO** un beneficiario `"Netflix"` sin categoría predeterminada y una categoría `Ocio`
- **CUANDO** se crea una transacción con `beneficiario` `"Netflix"` y `categoriaId` de `Ocio`
- **ENTONCES** `categoriaPredeterminadaId` de `"Netflix"` pasa a ser el id de `Ocio`

#### Scenario: Al editar
- **DADO** un beneficiario con categoría predeterminada `Ocio`
- **CUANDO** se edita una transacción suya con `categoriaId` de `Comida`
- **ENTONCES** `categoriaPredeterminadaId` pasa a ser el id de `Comida`

#### Scenario: Transacción dividida
- **DADO** un beneficiario con categoría predeterminada `Ocio`
- **CUANDO** se crea o edita una transacción suya con subtransacciones
- **ENTONCES** su categoría predeterminada sigue siendo `Ocio`

#### Scenario: Sin categoría
- **DADO** un beneficiario con categoría predeterminada `Ocio`
- **CUANDO** se crea una transacción suya sin `categoriaId`
- **ENTONCES** su categoría predeterminada sigue siendo `Ocio`

#### Scenario: Categoría ajena no deja efectos
- **DADO** una categoría de otro presupuesto
- **CUANDO** se crea una transacción con un beneficiario nuevo y esa categoría
- **ENTONCES** el sistema responde `404` y el beneficiario no se crea

## MODIFIED Requirements

### Requirement: Consultar el detalle
El sistema SHALL devolver una transacción por su id con `id`, `cuentaId`, `fecha`, `monto`,
`categoriaId`, `beneficiario`, `beneficiarioId`, `memo`, `estado`, `aprobada`,
`subtransacciones`, `transaccionParId`, `fechaCreacion` y `fechaActualizacion`.
`transaccionParId` SHALL ser el id de la otra pata si la transacción es parte de una
transferencia y `null` si no lo es. `beneficiarioId` SHALL ser el id del beneficiario vinculado
y `null` si no tiene. El listado SHALL devolver los mismos campos en cada elemento.

#### Scenario: Detalle existente
- **DADO** una transacción del presupuesto
- **CUANDO** se consulta por su id
- **ENTONCES** el sistema responde `200` con todos esos campos y `transaccionParId` `null`

#### Scenario: Detalle con beneficiario vinculado
- **DADO** una transacción vinculada a un beneficiario
- **CUANDO** se consulta por su id y se lista
- **ENTONCES** ambas respuestas traen el mismo `beneficiarioId` y el `beneficiario` con el
  nombre del beneficiario

#### Scenario: Detalle de una pata de transferencia
- **DADO** una transferencia con su salida y su entrada
- **CUANDO** se consulta la salida por su id
- **ENTONCES** el sistema responde `200` con `transaccionParId` igual al id de la entrada

#### Scenario: Listado con patas de transferencia
- **DADO** una transferencia y una transacción normal
- **CUANDO** se lista
- **ENTONCES** las dos patas aparecen como transacciones normales, cada una con su
  `transaccionParId`, y la normal con `null`

### Requirement: Duplicar una transacción
El sistema SHALL duplicar una transacción con `POST /{id}/duplicar` y responder `201` con una
copia con la fecha de hoy, estado `NO_CONCILIADA`, `aprobada` `true`, y con copia de sus
subtransacciones. Cuenta, monto, categoría, beneficiario (texto y vínculo) y memo SHALL
mantenerse.

#### Scenario: Duplicado simple
- **DADO** una transacción `CONCILIADA` y no aprobada de hace un mes
- **CUANDO** se duplica
- **ENTONCES** el sistema responde `201` con un id nuevo, la fecha de hoy, estado
  `NO_CONCILIADA`, `aprobada` `true` y los demás campos iguales; la original no cambia

#### Scenario: Duplicado conserva el beneficiario
- **DADO** una transacción vinculada a un beneficiario
- **CUANDO** se duplica
- **ENTONCES** la copia tiene el mismo `beneficiario` y `beneficiarioId` y no se crea otro
  beneficiario

#### Scenario: Duplicado de una dividida
- **DADO** una transacción con subtransacciones
- **CUANDO** se duplica
- **ENTONCES** la copia tiene sus propias subtransacciones con ids nuevos y los mismos datos

#### Scenario: Duplicar una reconciliada
- **DADO** una transacción `RECONCILIADA`
- **CUANDO** se duplica
- **ENTONCES** el sistema responde `201` con la copia `NO_CONCILIADA`

#### Scenario: Duplicar en cuenta cerrada
- **DADO** una transacción en una cuenta cerrada
- **CUANDO** se duplica
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA`

### Requirement: Operaciones en lote
El sistema SHALL ofrecer `POST /lote` con `{ ids, operacion }`, donde `operacion` es
`CATEGORIZAR` (con `categoriaId`), `APROBAR` o `BORRAR`, y responder `200` con la cantidad
afectada. SHALL aceptar entre 1 y 100 ids y SHALL ser atómica: si falla alguna validación no
se aplica nada. `CATEGORIZAR` SHALL fijar además esa categoría como predeterminada de cada
beneficiario vinculado a las transacciones categorizadas.

#### Scenario: Categorizar
- **DADO** varias transacciones simples del presupuesto y una categoría del presupuesto
- **CUANDO** se envía `CATEGORIZAR` con esa categoría
- **ENTONCES** el sistema responde `200` con la cantidad y todas quedan con esa categoría

#### Scenario: Categorizar actualiza la categoría recordada
- **DADO** transacciones vinculadas a `"Netflix"` y `"Spotify"` y otra sin beneficiario
- **CUANDO** se envía `CATEGORIZAR` con la categoría `Ocio`
- **ENTONCES** `"Netflix"` y `"Spotify"` quedan con `Ocio` como categoría predeterminada y la
  transacción sin beneficiario solo se categoriza

#### Scenario: Categorizar sin categoría
- **DADO** una operación `CATEGORIZAR` sin `categoriaId`
- **CUANDO** se envía
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Categorizar con categoría ajena
- **DADO** una categoría de otro presupuesto
- **CUANDO** se envía `CATEGORIZAR` con ella
- **ENTONCES** el sistema responde `404` y no cambia nada

#### Scenario: Categorizar una dividida
- **DADO** entre los ids una transacción con subtransacciones
- **CUANDO** se envía `CATEGORIZAR`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no cambia ninguna,
  tampoco las categorías predeterminadas

#### Scenario: Aprobar en lote
- **DADO** varias transacciones sin aprobar, algunas ya aprobadas
- **CUANDO** se envía `APROBAR`
- **ENTONCES** el sistema responde `200` con la cantidad de ids y todas quedan aprobadas

#### Scenario: Borrar en lote
- **DADO** varias transacciones del presupuesto
- **CUANDO** se envía `BORRAR`
- **ENTONCES** el sistema responde `200` con la cantidad y dejan de existir

#### Scenario: Un id ajeno o inexistente
- **DADO** entre los ids uno de otro presupuesto, de otra persona o inexistente
- **CUANDO** se envía cualquier operación
- **ENTONCES** el sistema responde `404` y no se aplica nada a los demás

#### Scenario: Una reconciliada en el lote
- **DADO** entre los ids una transacción `RECONCILIADA`
- **CUANDO** se envía `CATEGORIZAR` o `BORRAR`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no se aplica nada

#### Scenario: Aprobar en lote con reconciliadas
- **DADO** entre los ids una transacción `RECONCILIADA` sin aprobar, igual que el aprobar
  individual
- **CUANDO** se envía `APROBAR`
- **ENTONCES** el sistema responde `200`, cuenta también la reconciliada y todas quedan aprobadas

#### Scenario: Ids repetidos
- **DADO** un mismo id repetido en `ids`
- **CUANDO** se envía una operación
- **ENTONCES** el sistema lo cuenta una sola vez

#### Scenario: Cantidad de ids u operación fuera de rango
- **DADO** una lista vacía, una con más de 100 ids, o una `operacion` ausente o desconocida
- **CUANDO** se envía
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`
