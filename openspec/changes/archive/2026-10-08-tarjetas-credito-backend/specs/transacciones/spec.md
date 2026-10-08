# Spec Delta

## ADDED Requirements

### Requirement: Una categoría de pago de tarjeta no admite transacciones
El sistema SHALL responder `422` con `REGLA_NEGOCIO_VIOLADA` y no guardar nada cuando una
transacción, una subtransacción, una transferencia, una edición o una operación en lote
`CATEGORIZAR` use como categoría una categoría de pago de tarjeta. Filtrar el listado
por una categoría de pago SHALL seguir siendo válido y devolver una lista vacía.

#### Scenario: Crear una transacción en la categoría de pago
- **DADO** la categoría `Pago: Visa`
- **CUANDO** se crea una transacción con ese `categoriaId`
- **ENTONCES** el sistema responde `422` con `REGLA_NEGOCIO_VIOLADA`

#### Scenario: Subtransacción en la categoría de pago
- **DADO** una división donde una parte usa `Pago: Visa`
- **CUANDO** se crea la transacción
- **ENTONCES** el sistema responde `422` y no se guarda nada

#### Scenario: Editar o categorizar en lote
- **DADO** una transacción sin categoría
- **CUANDO** se edita, o se categoriza en lote, con `Pago: Visa`
- **ENTONCES** el sistema responde `422` y la transacción no cambia

#### Scenario: Transferencia con la categoría de pago
- **DADO** una transferencia del presupuesto a una cuenta externa, que exige categoría
- **CUANDO** se crea con el `categoriaId` de `Pago: Visa`
- **ENTONCES** el sistema responde `422`

#### Scenario: Filtrar por la categoría de pago
- **DADO** la categoría `Pago: Visa`
- **CUANDO** se lista con `categoriaId` igual a esa categoría
- **ENTONCES** el sistema responde `200` con la lista vacía

#### Scenario: Categoría de pago de otro presupuesto
- **DADO** una categoría de pago de otro presupuesto
- **CUANDO** se usa como categoría de una transacción
- **ENTONCES** el sistema responde `404`
