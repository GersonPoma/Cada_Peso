# Spec Delta

## Purpose

Permite definir una meta por categoría (monto mensual, monto para una fecha o saldo objetivo),
saber mes a mes cuánto falta para financiarla, posponerla en un mes y asignar dinero de forma
automática según una estrategia, sin que nadie más pueda verla ni tocarla.

## ADDED Requirements

### Requirement: Autenticación obligatoria
El sistema SHALL exigir autenticación en todas las rutas de metas bajo
`/api/v1/presupuestos/{presupuestoId}`.

#### Scenario: Petición sin token
- **DADO** una petición sin token de acceso
- **CUANDO** se llama a cualquier ruta de metas
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

### Requirement: Aislamiento por presupuesto y por persona
El sistema SHALL validar primero, en cada operación, que el presupuesto de la URL pertenece a la
persona autenticada, y SHALL buscar toda categoría dentro de ese presupuesto. Un presupuesto o
una categoría inexistente o ajena SHALL responder `404` con código `RECURSO_NO_ENCONTRADO`,
nunca `403`. El `mes` SHALL interpretarse después de validar el presupuesto.

#### Scenario: Presupuesto de otra persona
- **DADO** una persona B autenticada y un presupuesto de la persona A con metas
- **CUANDO** B guarda, consulta o borra una meta, lista las metas, consulta un mes, pospone,
  reanuda o auto-asigna en el presupuesto de A
- **ENTONCES** el sistema responde `404` en cada caso y no modifica nada

#### Scenario: Presupuesto ajeno con mes inválido
- **DADO** una persona B y el presupuesto de A
- **CUANDO** B consulta `/meses/2026-13/metas` en el presupuesto de A
- **ENTONCES** el sistema responde `404` y no `400`

#### Scenario: Categoría de otra persona o de otro presupuesto
- **DADO** una categoría del presupuesto de A
- **CUANDO** B (o la misma persona A por la URL de su segundo presupuesto) guarda, consulta o
  borra su meta, la pospone o la reanuda
- **ENTONCES** el sistema responde `404` en cada caso y no crea ni cambia nada

### Requirement: Guardar la meta de una categoría
`PUT /categorias/{categoriaId}/meta` SHALL crear la meta de la categoría o reemplazar la que
tiene y responder `200` con `categoriaId`, `tipo`, `monto`, `frecuencia`, `diaSemana`,
`intervaloDias`, `fechaInicio` y `fechaObjetivo`. Hay como máximo una meta por categoría
(restricción única en base de datos). Una categoría oculta SÍ puede tener meta. Reemplazar una
meta no borra sus pospuestas.

#### Scenario: Crear la meta
- **DADO** una categoría sin meta
- **CUANDO** se guarda `{tipo: MONTO_MENSUAL, monto: 100000, frecuencia: MENSUAL}`
- **ENTONCES** el sistema responde `200` con esa meta y `GET` de la meta devuelve lo mismo

#### Scenario: Reemplazar no duplica
- **DADO** una categoría con una meta `MONTO_MENSUAL`
- **CUANDO** se guarda una meta `SALDO_OBJETIVO` de `300000`
- **ENTONCES** el sistema responde `200`, la categoría sigue teniendo una sola meta y esta es la
  nueva, con `frecuencia`, `diaSemana`, `intervaloDias`, `fechaInicio` y `fechaObjetivo` en `null`

#### Scenario: Categoría oculta
- **DADO** una categoría oculta
- **CUANDO** se guarda su meta
- **ENTONCES** el sistema responde `200`

#### Scenario: Reemplazar conserva las pospuestas
- **DADO** una meta pospuesta en `2026-10`
- **CUANDO** se reemplaza por otra meta (por ejemplo de `MONTO_MENSUAL` a `SALDO_OBJETIVO`)
- **ENTONCES** el sistema responde `200` y la meta nueva sigue pospuesta en `2026-10`
  (estado `POSPUESTA`, necesidad `0`) y no queda pospuesta en otros meses

#### Scenario: Una sola meta por categoría en la base
- **DADO** una categoría con meta
- **CUANDO** se intenta persistir una segunda meta para la misma categoría
- **ENTONCES** la base rechaza la fila por la restricción única

### Requirement: Validación de la meta según su tipo
El `monto` SHALL ser mayor que 0 en todos los tipos y `tipo` SHALL ser obligatorio. Según el
tipo: `MONTO_MENSUAL` exige `frecuencia`; con `SEMANAL` exige `diaSemana` de 1 (lunes) a 7
(domingo); con `MENSUAL` no exige nada más; con `PERSONALIZADA` exige `intervaloDias` de 2 a 365
y `fechaInicio`. `MONTO_PARA_FECHA` exige `fechaObjetivo` (puede ser pasada). `SALDO_OBJETIVO`
solo usa el monto. Los campos que no aplican al tipo SHALL descartarse antes de validar y
guardarse en `null`. Un dato faltante o inválido SHALL responder `400` con código
`DATOS_INVALIDOS` y el campo en `errores`.

#### Scenario: Faltan datos del tipo
- **DADO** un cuerpo `MONTO_MENSUAL` sin `frecuencia`, `SEMANAL` sin `diaSemana`,
  `PERSONALIZADA` sin `intervaloDias` o sin `fechaInicio`, o `MONTO_PARA_FECHA` sin
  `fechaObjetivo`
- **CUANDO** se guarda
- **ENTONCES** el sistema responde `400` `DATOS_INVALIDOS` con el campo faltante en `errores`

#### Scenario: Valores fuera de rango
- **DADO** `monto` 0 o negativo, `diaSemana` 0 u 8, `intervaloDias` 1 o 366, o un `tipo` o
  `frecuencia` desconocido
- **CUANDO** se guarda
- **ENTONCES** el sistema responde `400` `DATOS_INVALIDOS` y no cambia la meta existente

#### Scenario: Campos que no aplican se descartan
- **DADO** un cuerpo `SALDO_OBJETIVO` con `frecuencia: SEMANAL`, `diaSemana: 9` y
  `fechaObjetivo`, o un `MONTO_MENSUAL` `MENSUAL` con `diaSemana: 3`
- **CUANDO** se guarda
- **ENTONCES** el sistema responde `200` y los campos que no aplican quedan en `null`

#### Scenario: Fecha objetivo pasada
- **DADO** un `MONTO_PARA_FECHA` con `fechaObjetivo` anterior a hoy
- **CUANDO** se guarda
- **ENTONCES** el sistema responde `200`

### Requirement: Consultar y borrar la meta de una categoría
`GET /categorias/{categoriaId}/meta` SHALL responder `200` con la meta, o `404` si la categoría
no tiene. `DELETE` SHALL responder `204` y borrar la meta junto con sus pospuestas, o `404` si no
tiene meta (una meta no tiene historial, por eso aquí sí se borra). No hay otro `DELETE` ni
edición parcial.

#### Scenario: Categoría sin meta
- **DADO** una categoría del presupuesto sin meta
- **CUANDO** se consulta o se borra su meta
- **ENTONCES** el sistema responde `404` con código `RECURSO_NO_ENCONTRADO`

#### Scenario: Borrar una meta con pospuestas
- **DADO** una meta pospuesta en dos meses
- **CUANDO** se borra
- **ENTONCES** el sistema responde `204`, la meta y sus dos pospuestas dejan de existir y
  consultar la meta responde `404`

### Requirement: Listar las metas del presupuesto
`GET /metas` SHALL devolver la lista completa (sin paginación) de las metas del presupuesto,
con el mismo formato de `MetaResponse`, ordenada por el orden de su grupo y luego el de su
categoría, incluidas las de categorías ocultas.

#### Scenario: Orden y aislamiento
- **DADO** metas en categorías de dos grupos y metas de otro presupuesto
- **CUANDO** se listan
- **ENTONCES** el sistema devuelve solo las del presupuesto, en el orden del árbol

### Requirement: Reglas de cálculo de la necesidad
Para una categoría con meta en el mes M, el sistema SHALL tomar `asignado`, `actividad` y
`disponible` del mes que calcula `asignacion` (con las categorías ocultas incluidas) y calcular
`inicial = disponible - asignado - actividad` (el saldo positivo que llega del mes anterior).
La `necesidad` (cuánto hay que asignar en el mes) SHALL ser, en milésimas:
- `MONTO_MENSUAL`: `monto × vencimientos del mes`. `MENSUAL`: 1 vencimiento. `SEMANAL`: las veces
  que cae `diaSemana` en el mes. `PERSONALIZADA`: las fechas `fechaInicio + k × intervaloDias`
  (k ≥ 0) que caen en el mes; antes de `fechaInicio` no hay vencimientos (necesidad 0).
- `MONTO_PARA_FECHA`: `mesesRestantes` = meses desde M hasta el mes de `fechaObjetivo`, ambos
  incluidos, con mínimo 1 (si M es posterior, vale 1). `necesidad = max(0, ceil((monto - inicial)
  / mesesRestantes))`, con la división redondeada hacia arriba al milliunit.
- `SALDO_OBJETIVO`: `max(0, monto - inicial)`.

`faltante = max(0, necesidad - asignado)`. Si la meta está pospuesta en M, `necesidad` y
`faltante` son 0.

#### Scenario: A. Monto mensual
- **DADO** una meta `MONTO_MENSUAL` `MENSUAL` de `100000` y `asignado` `60000`
- **CUANDO** se consulta el mes
- **ENTONCES** `necesidad` es `100000`, `faltante` es `40000` y el estado es `FALTA`; con
  `asignado` `100000` el `faltante` es `0` y el estado `FINANCIADA`

#### Scenario: B. Semanal
- **DADO** una meta `SEMANAL` con `diaSemana` 1 (lunes) de `20000`
- **CUANDO** se consulta octubre y noviembre de 2026
- **ENTONCES** octubre tiene 4 lunes (5, 12, 19, 26) y `necesidad` es `80000`; noviembre tiene 5
  (2, 9, 16, 23, 30) y `necesidad` es `100000`

#### Scenario: C. Personalizada
- **DADO** una meta `PERSONALIZADA` cada 14 días desde `2026-10-02` de `10000`
- **CUANDO** se consulta septiembre, octubre y noviembre de 2026
- **ENTONCES** `necesidad` es `0` en septiembre (antes de `fechaInicio`), `30000` en octubre
  (2, 16, 30) y `20000` en noviembre (13, 27)

#### Scenario: C2. Intervalo largo
- **DADO** una meta `PERSONALIZADA` cada 30 días desde `2026-10-02` de `10000` (vencimientos:
  octubre 2, noviembre 1, diciembre 1 y 31, enero 30 y 1 de marzo)
- **CUANDO** se consulta febrero de 2027 y diciembre de 2026
- **ENTONCES** `necesidad` es `0` en febrero de 2027 (el primer vencimiento en o después del mes
  es el 1 de marzo, posterior al fin del mes) y `20000` en diciembre de 2026 (el 1 y el 31)

#### Scenario: D. Monto para una fecha
- **DADO** una meta `MONTO_PARA_FECHA` de `600000` con `fechaObjetivo` `2026-12-15`
- **CUANDO** se consulta `2026-10` con `inicial` `0`
- **ENTONCES** quedan 3 meses y `necesidad` es `200000`; con `inicial` `150000` es `150000`; con
  monto `100000` e `inicial` `0` es `33334` (redondeo hacia arriba); en `2027-01` con `inicial`
  `450000` es `150000` (un solo mes restante)

#### Scenario: E. Saldo objetivo
- **DADO** una meta `SALDO_OBJETIVO` de `300000` e `inicial` `120000`
- **CUANDO** se consulta el mes con `asignado` `0`
- **ENTONCES** `necesidad` y `faltante` son `180000` y el estado es `FALTA`; con `asignado`
  `180000` el estado es `FINANCIADA`; con `inicial` `400000` la `necesidad` es `0` y el estado
  `FINANCIADA`

### Requirement: Estado de las metas de un mes
`GET /meses/{mes}/metas` SHALL responder `{ mes, totalFaltante, metas }` con, solo de las
categorías con meta y en el orden del árbol, `categoriaId`, `nombre`, `tipo`, `monto`,
`necesidad`, `asignado`, `disponible`, `faltante` y `estado`. El `mes` tiene el formato
`yyyy-MM` con año entre 2000 y 2100; cualquier otro valor responde `400` `DATOS_INVALIDOS`.
`incluirOcultas` (por defecto `false`) omite las categorías ocultas, y `totalFaltante` suma el
`faltante` de las categorías incluidas. El `estado` SHALL ser, en este orden de prioridad:
`SOBREGASTADA` (`disponible < 0`), `POSPUESTA` (pospuesta en ese mes), `FALTA` (`faltante > 0`) y
`FINANCIADA` (`faltante = 0`).

#### Scenario: F. Sobregastada manda sobre todo
- **DADO** una categoría con `disponible` `-5000` cuya meta está pospuesta o financiada
- **CUANDO** se consulta el mes
- **ENTONCES** el estado es `SOBREGASTADA`

#### Scenario: Ocultas y total
- **DADO** una meta visible con `faltante` `40000` y una meta de categoría oculta con `faltante`
  `10000`
- **CUANDO** se consulta con `incluirOcultas` ausente y luego con `true`
- **ENTONCES** la primera respuesta trae una meta y `totalFaltante` `40000`; la segunda trae dos
  y `totalFaltante` `50000`

#### Scenario: Mes inválido o categorías sin meta
- **DADO** el mes `2026-13`, `1999-12` o `2101-01`
- **CUANDO** se consulta
- **ENTONCES** el sistema responde `400` `DATOS_INVALIDOS`; un presupuesto sin metas responde
  `200` con `metas` vacío y `totalFaltante` `0`

### Requirement: Posponer y reanudar una meta en un mes
`POST /meses/{mes}/metas/{categoriaId}/posponer` y `/reanudar` SHALL marcar o desmarcar la meta
como pospuesta solo en ese mes, de forma idempotente, y responder `200` con el elemento de ese
mes (el de `GET /meses/{mes}/metas`). Una categoría sin meta SHALL responder `404`.

#### Scenario: G. Posponer solo un mes
- **DADO** una meta `MONTO_MENSUAL` de `100000` pospuesta en octubre de 2026
- **CUANDO** se consulta octubre y noviembre
- **ENTONCES** octubre tiene `necesidad` `0` y estado `POSPUESTA`; noviembre vuelve a calcularse
  con normalidad

#### Scenario: Idempotencia
- **DADO** una meta ya pospuesta en un mes
- **CUANDO** se pospone otra vez, o se reanuda un mes que no estaba pospuesto
- **ENTONCES** el sistema responde `200` sin duplicar ni fallar, y queda una sola fila por meta y
  mes

#### Scenario: Reanudar
- **DADO** una meta pospuesta en un mes
- **CUANDO** se reanuda
- **ENTONCES** el sistema responde `200` y el mes se calcula con normalidad

#### Scenario: Categoría sin meta
- **DADO** una categoría sin meta
- **CUANDO** se pospone o se reanuda
- **ENTONCES** el sistema responde `404`

### Requirement: Auto-asignar
`POST /meses/{mes}/auto-asignar` con `{ estrategia, categoriaIds?, simular? }` SHALL fijar el
asignado del mes de las categorías según la estrategia. `estrategia` es obligatoria y una
ausente o desconocida responde `400` `DATOS_INVALIDOS`. Si `categoriaIds` falta se consideran
todas las categorías visibles del presupuesto; las ocultas solo si se listan; una lista vacía
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
