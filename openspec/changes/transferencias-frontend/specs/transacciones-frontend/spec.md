## MODIFIED Requirements

### Requirement: Acciones según el estado de cada transacción
El menú de cada fila SHALL ofrecer `Editar`, `Duplicar`, `Mover a otra cuenta`, `Aprobar` (solo
si no está aprobada), `Marcar conciliada` o `Marcar no conciliada`, y `Borrar`, con estas
excepciones, y cada acción deshabilitada SHALL mostrar su motivo:
- Una transacción `RECONCILIADA` SHALL tener deshabilitados `Editar`, `Mover a otra cuenta`,
  `Borrar` y el cambio de estado, con el motivo `Está reconciliada`.
- Una pata de transferencia SHALL ofrecer `Editar transferencia`, `Borrar transferencia`,
  `Aprobar` (solo si no está aprobada) y el cambio de estado, en lugar de `Editar` y `Borrar`;
  `Duplicar` y `Mover a otra cuenta` SHALL estar deshabilitados con el motivo
  `Es parte de una transferencia`. Las reglas de `Editar transferencia` y `Borrar
  transferencia` son las de la capacidad `transferencias-frontend`.
- Una transacción de una cuenta cerrada SHALL NOT ofrecer `Editar`.

#### Scenario: Reconciliada
- **DADO** una transacción `RECONCILIADA`
- **CUANDO** la persona abre su menú
- **ENTONCES** `Editar`, `Mover a otra cuenta`, `Borrar` y el cambio de estado están
  deshabilitados con `Está reconciliada`

#### Scenario: Pata de transferencia
- **DADO** una transacción con `transaccionParId`, no reconciliada y de una cuenta abierta
- **CUANDO** la persona abre su menú
- **ENTONCES** `Editar transferencia`, `Borrar transferencia`, `Aprobar` y `Marcar conciliada`
  están habilitados
- **Y** `Duplicar` y `Mover a otra cuenta` están deshabilitados con
  `Es parte de una transferencia`
- **Y** no aparecen `Editar` ni `Borrar`
