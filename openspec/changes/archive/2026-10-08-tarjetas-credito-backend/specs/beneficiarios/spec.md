# Spec Delta

## ADDED Requirements

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
