# Spec Delta

## MODIFIED Requirements

### Requirement: Consultar el detalle
El sistema SHALL devolver una transacción por su id con `id`, `cuentaId`, `fecha`, `monto`,
`categoriaId`, `beneficiario`, `memo`, `estado`, `aprobada`, `subtransacciones`,
`transaccionParId`, `fechaCreacion` y `fechaActualizacion`. `transaccionParId` SHALL ser el id de
la otra pata si la transacción es parte de una transferencia y `null` si no lo es. El listado
SHALL devolver el mismo campo en cada elemento.

#### Scenario: Detalle existente
- **DADO** una transacción del presupuesto
- **CUANDO** se consulta por su id
- **ENTONCES** el sistema responde `200` con todos esos campos y `transaccionParId` `null`

#### Scenario: Detalle de una pata de transferencia
- **DADO** una transferencia con su salida y su entrada
- **CUANDO** se consulta la salida por su id
- **ENTONCES** el sistema responde `200` con `transaccionParId` igual al id de la entrada

#### Scenario: Listado con patas de transferencia
- **DADO** una transferencia y una transacción normal
- **CUANDO** se lista
- **ENTONCES** las dos patas aparecen como transacciones normales, cada una con su
  `transaccionParId`, y la normal con `null`

### Requirement: Saldos por cuenta
El sistema SHALL responder `GET /saldos` con, por cada cuenta del presupuesto (abiertas y
cerradas), `cuentaId`, `saldo` (saldo inicial más la suma de todas sus transacciones) y
`saldoConciliado` (saldo inicial más la suma de las `CONCILIADA` y `RECONCILIADA`), en
milésimas.

#### Scenario: Cuenta sin transacciones
- **DADO** una cuenta con saldo inicial `100000` y sin transacciones
- **CUANDO** se piden los saldos
- **ENTONCES** su `saldo` y su `saldoConciliado` son `100000`

#### Scenario: Cuenta con transacciones
- **DADO** una cuenta con saldo inicial `100000`, una `NO_CONCILIADA` de `-20000`, una
  `CONCILIADA` de `-10000` y una `RECONCILIADA` de `5000`
- **CUANDO** se piden los saldos
- **ENTONCES** `saldo` es `75000` y `saldoConciliado` es `95000`

#### Scenario: Cuenta cerrada
- **DADO** una cuenta cerrada con transacciones
- **CUANDO** se piden los saldos
- **ENTONCES** aparece con sus saldos calculados

#### Scenario: Sin cuentas
- **DADO** un presupuesto sin cuentas
- **CUANDO** se piden los saldos
- **ENTONCES** el sistema responde `200` con una lista vacía

#### Scenario: Aislamiento de saldos
- **DADO** transacciones en cuentas de otro presupuesto
- **CUANDO** se piden los saldos de este presupuesto
- **ENTONCES** solo cuentan las transacciones de las cuentas de este presupuesto

#### Scenario: Saldos con una transferencia
- **DADO** dos cuentas con saldos `100000` y `40000` y una transferencia de `30000` de la primera
  a la segunda
- **CUANDO** se piden los saldos
- **ENTONCES** la primera tiene `saldo` `70000`, la segunda `70000`, y la suma de los dos saldos
  sigue siendo `140000`

## ADDED Requirements

### Requirement: Patas de transferencia protegidas
Una transacción que es pata de una transferencia (`transaccionParId` no nulo) SHALL rechazar con
`422` y código `REGLA_NEGOCIO_VIOLADA`, con el mensaje "es parte de una transferencia; usa
/transferencias", editarla (`PUT`), borrarla (`DELETE`), moverla de cuenta y duplicarla; y en
`/lote` las operaciones `BORRAR` y `CATEGORIZAR`, de forma atómica como el resto del lote. Aprobar
(individual y en lote) y cambiar el estado entre `NO_CONCILIADA` y `CONCILIADA` SHALL seguir
permitidos por pata. Esta regla SHALL prevalecer sobre los requisitos de editar, borrar, mover,
duplicar y operar en lote.

#### Scenario: Editar o borrar una pata
- **DADO** una pata de transferencia
- **CUANDO** se llama a `PUT` o `DELETE` de `/transacciones/{id}` con su id
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y la transferencia no
  cambia

#### Scenario: Mover o duplicar una pata
- **DADO** una pata de transferencia
- **CUANDO** se mueve de cuenta o se duplica
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no se crea ni cambia
  nada

#### Scenario: Borrar o categorizar una pata en lote
- **DADO** entre los ids del lote una pata de transferencia y otras transacciones normales
- **CUANDO** se envía `BORRAR` o `CATEGORIZAR`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no se aplica nada a
  ninguna

#### Scenario: Aprobar una pata en lote
- **DADO** entre los ids del lote una pata de transferencia sin aprobar
- **CUANDO** se envía `APROBAR`
- **ENTONCES** el sistema responde `200` y la pata queda aprobada

#### Scenario: Aprobar y cambiar el estado de una pata
- **DADO** una pata de transferencia
- **CUANDO** se aprueba o se cambia su estado a `CONCILIADA`
- **ENTONCES** el sistema responde `200` y solo cambia esa pata
