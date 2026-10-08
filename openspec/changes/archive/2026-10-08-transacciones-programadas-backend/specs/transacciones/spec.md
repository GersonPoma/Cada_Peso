# Spec Delta

## MODIFIED Requirements

### Requirement: Consultar el detalle
El sistema SHALL devolver una transacción por su id con `id`, `cuentaId`, `fecha`, `monto`,
`categoriaId`, `beneficiario`, `beneficiarioId`, `memo`, `estado`, `aprobada`,
`subtransacciones`, `transaccionParId`, `programadaId`, `fechaCreacion` y `fechaActualizacion`.
`transaccionParId` SHALL ser el id de la otra pata si la transacción es parte de una
transferencia y `null` si no lo es. `beneficiarioId` SHALL ser el id del beneficiario vinculado
y `null` si no tiene. `programadaId` SHALL ser el id de la transacción programada que la generó
y `null` si no fue generada por una plantilla (o si la plantilla se borró). El listado SHALL
devolver los mismos campos en cada elemento.

#### Scenario: Detalle existente
- **DADO** una transacción del presupuesto
- **CUANDO** se consulta por su id
- **ENTONCES** el sistema responde `200` con todos esos campos, `transaccionParId` `null` y
  `programadaId` `null`

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

#### Scenario: Detalle de una transacción generada por una plantilla
- **DADO** una transacción creada por el generador de una transacción programada
- **CUANDO** se consulta por su id y se lista
- **ENTONCES** ambas respuestas traen `programadaId` igual al id de la plantilla

## ADDED Requirements

### Requirement: Transacciones generadas por una plantilla
El sistema SHALL tratar una transacción generada por una plantilla como una transacción normal:
SHALL poder editarse, aprobarse, conciliarse y borrarse con los mismos endpoints y reglas.
Nace con estado `NO_CONCILIADA`, `aprobada` en `false` y la fecha de su ocurrencia.

#### Scenario: Aprobar una generada
- **DADO** una transacción generada con `aprobada` en `false`
- **CUANDO** se aprueba con `POST /transacciones/{id}/aprobar`
- **ENTONCES** el sistema responde `200` con `aprobada` en `true` y conserva su `programadaId`

#### Scenario: Borrar una generada no afecta la plantilla
- **DADO** una transacción generada por una plantilla
- **CUANDO** se borra con `DELETE /transacciones/{id}`
- **ENTONCES** el sistema responde `204` y la plantilla conserva su `proximaFecha`
- **Y** la ocurrencia borrada no se vuelve a generar en ninguna ejecución posterior del generador

#### Scenario: Las transacciones manuales no cambian
- **DADO** una transacción creada con `POST /transacciones`
- **CUANDO** se consulta
- **ENTONCES** `programadaId` es `null` y el resto de sus campos no cambia
