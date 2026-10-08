# Spec Delta

## MODIFIED Requirements

### Requirement: Auto-asignar
`POST /meses/{mes}/auto-asignar` con `{ estrategia, categoriaIds?, simular? }` SHALL fijar el
asignado del mes de las categorías según la estrategia. `estrategia` es obligatoria y una
ausente o desconocida responde `400` `DATOS_INVALIDOS`. Si `categoriaIds` falta se consideran
todas las categorías visibles del presupuesto, salvo las categorías de pago de tarjeta; las ocultas
y las de pago solo si se listan; una lista vacía
responde `400`; una categoría ajena o inexistente responde `404` y no se aplica nada. Con
`simular` verdadero (por defecto `false`) se calcula y se responde sin guardar nada. Solo se
cambian las categorías cuyo nuevo asignado difiere del actual. No hay límite por
`listoParaAsignar`, que puede quedar negativo (la misma regla que asignar a mano). La operación
es atómica. Las estrategias fijan el nuevo asignado así:
- `FALTANTE_META`: `asignado + faltante` en las categorías con meta; las demás no cambian.
- `ASIGNADO_MES_PASADO`: lo asignado en el mes anterior.
- `GASTADO_MES_PASADO`: `max(0, -actividad del mes anterior)`.
- `PROMEDIO_ASIGNADO` y `PROMEDIO_GASTADO`: suma de los 3 meses anteriores dividida entre 3,
  redondeada hacia abajo, de lo asignado o de `max(0, -actividad)` mes a mes; un mes sin datos o
  anterior a `2000-01` cuenta 0.

Responde `{ aplicado, listoParaAsignarAntes, listoParaAsignarDespues, cambios }` con `aplicado`
falso al simular, `cambios` con `categoriaId`, `nombre`, `asignadoAntes` y `asignadoDespues` en el
orden del árbol, y `listoParaAsignarDespues = listoParaAsignarAntes - suma(asignadoDespues -
asignadoAntes)`. Sin cambios, `cambios` es vacío y los dos `listoParaAsignar` son iguales.

#### Scenario: Faltante de meta
- **DADO** la categoría Comida con meta `MENSUAL` de `100000` y asignado `30000`
- **CUANDO** se auto-asigna con `FALTANTE_META`
- **ENTONCES** su asignado queda en `100000` y las categorías sin meta no cambian

#### Scenario: Asignado del mes pasado
- **DADO** septiembre con asignado `50000` y octubre con `0`
- **CUANDO** se auto-asigna octubre con `ASIGNADO_MES_PASADO`
- **ENTONCES** octubre queda en `50000`

#### Scenario: Gastado del mes pasado
- **DADO** septiembre con actividad `-35000`, y otra categoría con actividad `+4000`
- **CUANDO** se auto-asigna octubre con `GASTADO_MES_PASADO`
- **ENTONCES** la primera queda en `35000` y la segunda en `0`

#### Scenario: Promedios
- **DADO** tres meses anteriores con asignado `30000`, `60000` y `0`
- **CUANDO** se auto-asigna con `PROMEDIO_ASIGNADO`
- **ENTONCES** el asignado queda en `30000`; con `PROMEDIO_GASTADO` y actividades `-10000`,
  `-10000` y `-11` en esos meses queda en `6670` (`20011 / 3 = 6670,33`, redondeado hacia abajo)

#### Scenario: Simular no guarda
- **DADO** una auto-asignación que cambiaría dos categorías
- **CUANDO** se envía con `simular: true`
- **ENTONCES** el sistema responde `200` con `aplicado: false` y los mismos `cambios`, y los
  asignados guardados no cambian

#### Scenario: Categorías explícitas
- **DADO** `categoriaIds` con una categoría oculta
- **CUANDO** se auto-asigna
- **ENTONCES** la categoría oculta se procesa; sin `categoriaIds` no se procesa

#### Scenario: Una categoría ajena no aplica nada
- **DADO** `categoriaIds` con una categoría propia y una de otro presupuesto
- **CUANDO** se auto-asigna
- **ENTONCES** el sistema responde `404` y la categoría propia conserva su asignado

#### Scenario: Sin cambios y listo para asignar
- **DADO** que todas las categorías ya tienen el asignado de la estrategia
- **CUANDO** se auto-asigna
- **ENTONCES** `cambios` es vacío y `listoParaAsignarAntes` es igual a `listoParaAsignarDespues`;
  si hay cambios, `listoParaAsignarDespues` baja lo que sube la suma de las diferencias y puede
  quedar negativo

#### Scenario: Lista de categorías vacía
- **DADO** un cuerpo con una estrategia válida y `categoriaIds` igual a `[]`
- **CUANDO** se auto-asigna, con `simular` verdadero o falso
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y no cambia ningún asignado

#### Scenario: Estrategia inválida
- **DADO** un cuerpo sin `estrategia` o con una desconocida
- **CUANDO** se auto-asigna
- **ENTONCES** el sistema responde `400` `DATOS_INVALIDOS` y no cambia nada

#### Scenario: Sin categoriaIds se omiten las categorías de pago
- **DADO** la categoría `Pago: Visa` con asignado `30000` y la estrategia `ASIGNADO_MES_PASADO`
  con septiembre en `50000` para esa categoría
- **CUANDO** se auto-asigna octubre sin `categoriaIds`
- **ENTONCES** `Pago: Visa` no aparece en `cambios` y conserva su asignado de octubre

#### Scenario: Con categoriaIds explícitos se procesan las de pago
- **DADO** la misma situación
- **CUANDO** se auto-asigna octubre con `categoriaIds` igual a `[id de Pago: Visa]`
- **ENTONCES** `Pago: Visa` aparece en `cambios` con `asignadoDespues` `50000`

## ADDED Requirements

### Requirement: Las categorías de pago de tarjeta admiten meta
El sistema SHALL permitir guardar, consultar, borrar y posponer una meta en una categoría de
pago de tarjeta con las mismas reglas que en cualquier categoría, y su estado del mes SHALL
calcularse con el `asignado`, la `actividad` y el `disponible` de la categoría de pago.

#### Scenario: Meta sobre la categoría de pago
- **DADO** la categoría `Pago: Visa`
- **CUANDO** se guarda `{tipo: MONTO_MENSUAL, monto: 100000, frecuencia: MENSUAL}`
- **ENTONCES** el sistema responde `200` con esa meta y `GET` de la meta devuelve lo mismo

#### Scenario: Estado de la meta de la categoría de pago
- **DADO** una meta `MONTO_MENSUAL` `MENSUAL` de `50000` en `Pago: Visa`, con `asignado` `0` y un
  gasto de `40000` con la tarjeta en el mes (la categoría de pago tiene `actividad` `40000` y
  `disponible` `40000`)
- **CUANDO** se consultan las metas del mes
- **ENTONCES** la meta de `Pago: Visa` trae `disponible` `40000`, `necesidad` `50000`, `faltante`
  `50000` y `estado` `FALTA`: la meta mide lo asignado en el mes y no cuenta la reserva
  automática, porque la actividad del mes no entra en el inicial
