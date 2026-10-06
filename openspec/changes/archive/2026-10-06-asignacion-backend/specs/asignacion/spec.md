## ADDED Requirements

### Requirement: Autenticación obligatoria
El sistema SHALL exigir autenticación en todas las rutas de
`/api/v1/presupuestos/{presupuestoId}/meses/{mes}`.

#### Scenario: Petición sin token
- **DADO** una petición sin token de acceso
- **CUANDO** se llama a cualquier ruta de un mes de un presupuesto
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

### Requirement: Aislamiento por presupuesto y por persona
El sistema SHALL validar primero, en cada operación, que el presupuesto de la URL pertenece a la
persona autenticada, y SHALL buscar toda categoría dentro de ese presupuesto. Un presupuesto o
una categoría inexistente o ajena SHALL responder `404` con código `RECURSO_NO_ENCONTRADO`,
nunca `403`.

#### Scenario: Presupuesto de otra persona
- **DADO** una persona B autenticada y un presupuesto de la persona A con asignaciones
- **CUANDO** B asigna, consulta el mes o mueve dinero en el presupuesto de A
- **ENTONCES** el sistema responde `404` en cada caso y no modifica nada

#### Scenario: Categoría de otra persona
- **DADO** una categoría del presupuesto de A y una persona B con su propio presupuesto
- **CUANDO** B asigna a esa categoría, o la usa como origen o destino al mover dinero, por la URL
  de su propio presupuesto
- **ENTONCES** el sistema responde `404` en cada caso y no crea ni cambia ninguna asignación

#### Scenario: Categoría de otro presupuesto de la misma persona
- **DADO** una persona con dos presupuestos y una categoría en el primero
- **CUANDO** asigna a esa categoría, o la usa como origen o destino, por la URL del segundo
- **ENTONCES** el sistema responde `404` en cada caso

#### Scenario: Presupuesto o categoría inexistente
- **DADO** un id que no existe
- **CUANDO** se usa como presupuesto, como categoría al asignar o como origen o destino
- **ENTONCES** el sistema responde `404` con código `RECURSO_NO_ENCONTRADO`

### Requirement: Formato del mes
El `mes` de la URL SHALL tener el formato `yyyy-MM` con mes entre `01` y `12` y año entre 2000 y
2100. Cualquier otro valor SHALL responder `400` con código `DATOS_INVALIDOS`. Se permiten meses
pasados y futuros.

#### Scenario: Mes inválido
- **DADO** un mes `2026-13`, `2026-1`, `2026-00`, `26-01`, `enero`, `1999-12` o `2101-01`
- **CUANDO** se consulta, se asigna o se mueve dinero
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y no modifica nada

#### Scenario: Mes futuro o pasado
- **DADO** un mes lejano en el futuro (`2030-05`) o en el pasado (`2020-01`)
- **CUANDO** se consulta o se asigna
- **ENTONCES** el sistema responde `200`

### Requirement: Asignar dinero a una categoría
`PUT /categorias/{categoriaId}` con `{ asignado }` SHALL fijar (no sumar) el asignado de la
categoría en ese mes, en milésimas, creando o actualizando la única fila de ese par categoría y
mes, y responder `200` con la fila calculada de la categoría y el `listoParaAsignar` del mes
actualizado. `asignado` es obligatorio y puede ser 0 o negativo. Una categoría oculta SHALL poder
asignarse.

#### Scenario: Primera asignación
- **DADO** una categoría sin asignación en `2026-01`
- **CUANDO** se asigna `100000`
- **ENTONCES** el sistema responde `200` con la categoría con `asignado` `100000` y `disponible`
  `100000`, y el `listoParaAsignar` actualizado

#### Scenario: Reasignar actualiza la misma fila
- **DADO** una categoría con `asignado` `100000` en `2026-01`
- **CUANDO** se asigna `60000`
- **ENTONCES** el sistema responde `200` con `asignado` `60000` (no `160000`) y existe una sola
  fila para esa categoría y mes

#### Scenario: Asignar cero o negativo
- **DADO** una categoría con `asignado` `100000`
- **CUANDO** se asigna `0`, y luego `-5000`
- **ENTONCES** el sistema responde `200` en ambos casos con ese `asignado`

#### Scenario: Asignado ausente
- **DADO** un cuerpo sin `asignado` o con un valor no numérico
- **CUANDO** se asigna
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Categoría oculta
- **DADO** una categoría oculta del presupuesto
- **CUANDO** se le asigna dinero
- **ENTONCES** el sistema responde `200`

#### Scenario: Meses distintos son filas distintas
- **DADO** una categoría con `100000` asignado en `2026-01`
- **CUANDO** se asigna `30000` en `2026-02`
- **ENTONCES** `2026-01` conserva `100000` y `2026-02` tiene `30000`

### Requirement: Consultar el presupuesto del mes
`GET` SHALL responder `mes` (`yyyy-MM`), `listoParaAsignar`, `totalAsignado`, `totalActividad`,
`totalDisponible` y los grupos en su orden con sus categorías en su orden; cada categoría con
`categoriaId`, `nombre`, `oculta`, `asignado`, `actividad`, `disponible` y `sobregastada`
(`disponible` menor que 0). Los totales son la suma de las categorías incluidas en la respuesta;
`listoParaAsignar` considera siempre todo el presupuesto. Con `incluirOcultas` en `false` (por
defecto) SHALL omitir los grupos y las categorías ocultos, igual que el árbol de categorías. Una
categoría sin datos SHALL aparecer con todo en 0.

#### Scenario: Presupuesto recién creado
- **DADO** un presupuesto con su árbol inicial, sin asignaciones ni transacciones
- **CUANDO** se consulta cualquier mes
- **ENTONCES** `listoParaAsignar` es `0`, todos los totales son `0`, y cada categoría aparece con
  `asignado`, `actividad` y `disponible` en `0` y `sobregastada` `false`

#### Scenario: Orden de grupos y categorías
- **DADO** un presupuesto con grupos y categorías reordenados
- **CUANDO** se consulta el mes
- **ENTONCES** los grupos y las categorías vienen en el mismo orden que en el árbol de categorías

#### Scenario: Ocultas omitidas o incluidas
- **DADO** un grupo oculto y una categoría oculta en un grupo visible
- **CUANDO** se consulta con `incluirOcultas` `false` y luego con `true`
- **ENTONCES** la primera respuesta no los trae y la segunda sí, con `oculta` `true` en la
  categoría oculta

#### Scenario: Totales del mes
- **DADO** Comida con `asignado` `100000` y `actividad` `-30000`, y Ocio con `asignado` `20000` y
  sin actividad, en `2026-01`
- **CUANDO** se consulta el mes
- **ENTONCES** `totalAsignado` es `120000`, `totalActividad` es `-30000` y `totalDisponible` es
  `90000`

### Requirement: Actividad de una categoría
La `actividad` de una categoría en un mes SHALL ser la suma de los montos de las transacciones
con fecha dentro de ese mes calendario que tienen esa categoría, más la de las subtransacciones
con esa categoría, contando solo las cuentas con `enPresupuesto` verdadero (abiertas y cerradas).
Las salidas son negativas y las entradas positivas.

#### Scenario: Transacciones simples del mes
- **DADO** Comida con transacciones de `-12000` el `2026-01-05`, `-8000` el `2026-01-31` y
  `-5000` el `2026-02-01`
- **CUANDO** se consulta `2026-01` y `2026-02`
- **ENTONCES** la actividad de Comida es `-20000` en enero y `-5000` en febrero

#### Scenario: Subtransacciones
- **DADO** una transacción dividida de `-30000` con una parte de `-10000` en Comida y otra de
  `-20000` en Ocio, el `2026-01-10`
- **CUANDO** se consulta `2026-01`
- **ENTONCES** la actividad de Comida es `-10000` y la de Ocio es `-20000`

#### Scenario: Entrada con categoría
- **DADO** una transacción de `+4000` en Comida (un reembolso) y otra de `-10000`, en enero
- **CUANDO** se consulta `2026-01`
- **ENTONCES** la actividad de Comida es `-6000`

#### Scenario: Cuentas fuera del presupuesto
- **DADO** una transacción de `-50000` en Comida en una cuenta con `enPresupuesto` falso
- **CUANDO** se consulta el mes
- **ENTONCES** esa transacción no cuenta en la actividad

#### Scenario: Cuentas cerradas
- **DADO** una transacción de `-7000` en Comida en una cuenta cerrada con `enPresupuesto`
  verdadero
- **CUANDO** se consulta el mes
- **ENTONCES** esa transacción sí cuenta

#### Scenario: Categoría ajena no mezcla
- **DADO** transacciones con categorías de otro presupuesto
- **CUANDO** se consulta este presupuesto
- **ENTONCES** no afectan a ninguna categoría de este presupuesto

### Requirement: Disponible de una categoría
El `disponible` de una categoría en un mes SHALL ser el máximo entre 0 y el `disponible` del mes
anterior, más el `asignado` del mes, más la `actividad` del mes. El saldo positivo pasa al mes
siguiente; el negativo (sobregasto) no se arrastra a la categoría. El cálculo es acumulado desde
el primer mes con asignaciones o transacciones del presupuesto (cuyo mes anterior vale 0) hasta
el mes pedido.

#### Scenario: Saldo positivo que pasa al mes siguiente
- **DADO** Comida con `asignado` `100000` y `actividad` `-30000` en enero, y `asignado` `50000` y
  `actividad` `-20000` en febrero
- **CUANDO** se consulta enero y febrero
- **ENTONCES** el `disponible` es `70000` en enero y `100000` en febrero (`70000 + 50000 - 20000`)

#### Scenario: Sobregasto que no se arrastra
- **DADO** Comida con `asignado` `20000` y `actividad` `-50000` en enero, y `asignado` `10000`
  y sin actividad en febrero
- **CUANDO** se consulta enero y febrero
- **ENTONCES** el `disponible` es `-30000` con `sobregastada` `true` en enero, y `10000` con
  `sobregastada` `false` en febrero (el sobregasto no se arrastra a la categoría)

#### Scenario: Meses intermedios sin datos
- **DADO** Comida con `disponible` `40000` al cierre de enero y nada en febrero ni marzo
- **CUANDO** se consulta marzo
- **ENTONCES** el `disponible` de Comida es `40000`

#### Scenario: Antes del primer mes con datos
- **DADO** que el primer dato del presupuesto está en `2026-03`
- **CUANDO** se consulta `2026-01`
- **ENTONCES** todos los valores de las categorías son `0`

#### Scenario: Actividad sin asignación
- **DADO** un gasto de `-9000` en Ocio en un mes sin asignación
- **CUANDO** se consulta ese mes
- **ENTONCES** Ocio tiene `asignado` `0`, `actividad` `-9000`, `disponible` `-9000` y
  `sobregastada` `true`

### Requirement: Listo para asignar
`listoParaAsignar` de un mes SHALL ser los ingresos hasta el fin de ese mes, menos todo lo
asignado hasta ese mes (incluido), menos la suma de los sobregastos (`disponible` negativo) al
cierre de los meses anteriores a ese mes. Los ingresos son las transacciones con monto positivo y
sin categoría (ni subtransacciones) en cuentas con `enPresupuesto` verdadero con fecha hasta el
fin del mes, más los saldos iniciales positivos de las cuentas con `enPresupuesto` verdadero que
no son de tipo `TARJETA_CREDITO`. Puede ser negativo y se devuelve tal cual. Las transacciones
sin categoría con monto negativo y las de cuentas fuera del presupuesto no lo afectan.

#### Scenario: Ingresos sin categoría
- **DADO** una entrada de `+500000` sin categoría el `2026-01-01`
- **CUANDO** se consulta enero sin asignaciones
- **ENTONCES** `listoParaAsignar` es `500000`

#### Scenario: Asignar reduce el listo para asignar
- **DADO** ese ingreso de `500000` y Comida con `asignado` `100000` y Ocio con `20000` en enero
- **CUANDO** se consulta enero
- **ENTONCES** `listoParaAsignar` es `380000`

#### Scenario: Saldo inicial de cuentas
- **DADO** una cuenta corriente con saldo inicial `100000`, una `TARJETA_CREDITO` con saldo
  inicial `50000`, una cuenta con `enPresupuesto` falso con `70000` y una cuenta con saldo
  inicial negativo de `-20000`
- **CUANDO** se consulta cualquier mes
- **ENTONCES** `listoParaAsignar` es `100000` (solo cuentan los saldos iniciales positivos de
  cuentas del presupuesto que no son tarjeta)

#### Scenario: Ingresos acumulados por fecha
- **DADO** una entrada de `+200000` el `2026-01-05` y otra de `+80000` el `2026-02-10`, sin
  categoría
- **CUANDO** se consulta enero y febrero sin asignaciones
- **ENTONCES** `listoParaAsignar` es `200000` en enero y `280000` en febrero

#### Scenario: Lo que no es ingreso
- **DADO** una salida de `-30000` sin categoría, una entrada de `+9000` con categoría, una
  entrada dividida con subtransacciones y una entrada de `+70000` en una cuenta con
  `enPresupuesto` falso
- **CUANDO** se consulta el mes
- **ENTONCES** ninguna de ellas cambia `listoParaAsignar`

#### Scenario: Sobregasto que reduce los meses siguientes
- **DADO** un ingreso de `+500000` el `2026-01-01`, Comida con `asignado` `20000` y `actividad`
  `-50000` en enero (sobregasto de `30000`) y `asignado` `10000` en febrero
- **CUANDO** se consulta enero y febrero
- **ENTONCES** `listoParaAsignar` es `480000` en enero (`500000 - 20000`) y `440000` en febrero
  (`500000 - 30000` asignados - `30000` de sobregasto de enero); en marzo, sin más datos, sigue
  siendo `440000` (el sobregasto de enero se descuenta una sola vez, no mes a mes)

#### Scenario: Listo para asignar negativo
- **DADO** ingresos de `100000` y `asignado` total de `150000`
- **CUANDO** se consulta el mes
- **ENTONCES** `listoParaAsignar` es `-50000`

#### Scenario: Mes futuro con asignación
- **DADO** un ingreso de `+300000` en enero y Comida con `asignado` `120000` en `2026-06`
- **CUANDO** se consulta `2026-06`
- **ENTONCES** `listoParaAsignar` es `180000`, y `2026-01` mantiene `300000`

### Requirement: Mover dinero entre categorías
`POST /mover-dinero` con `{ origenId, destinoId, monto }` SHALL restar `monto` al asignado del
origen y sumarlo al del destino en ese mes, de forma atómica, y responder `200` con el mes
actualizado. `monto` SHALL ser mayor que 0, origen y destino SHALL ser distintos y estar en el
presupuesto, y el origen SHALL tener `disponible` mayor o igual a `monto` en ese mes.

#### Scenario: Movimiento correcto
- **DADO** Comida con `asignado` `100000` y `disponible` `70000`, y Ocio con `asignado` `0`
- **CUANDO** se mueven `30000` de Comida a Ocio
- **ENTONCES** el sistema responde `200` con Comida `asignado` `70000` y `disponible` `40000`, y
  Ocio `asignado` `30000` y `disponible` `30000`; `listoParaAsignar` no cambia

#### Scenario: Mover todo el disponible
- **DADO** Comida con `disponible` `70000`
- **CUANDO** se mueven `70000`
- **ENTONCES** el sistema responde `200` y Comida queda con `disponible` `0`

#### Scenario: Disponible insuficiente
- **DADO** Comida con `disponible` `70000`
- **CUANDO** se mueven `70001`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no cambia nada

#### Scenario: Origen sobregastado
- **DADO** Comida con `disponible` `-5000`
- **CUANDO** se mueve cualquier monto mayor que 0 desde Comida
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA`

#### Scenario: Origen igual al destino
- **DADO** el mismo id como origen y destino
- **CUANDO** se mueve dinero
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Monto cero o negativo
- **DADO** un `monto` `0` o `-100`
- **CUANDO** se mueve dinero
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y el campo `monto` en
  `errores`

#### Scenario: Campos obligatorios
- **DADO** un cuerpo sin `origenId`, sin `destinoId` o sin `monto`
- **CUANDO** se mueve dinero
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y el campo en `errores`

#### Scenario: Categoría ajena
- **DADO** un origen o un destino de otro presupuesto
- **CUANDO** se mueve dinero
- **ENTONCES** el sistema responde `404` y no cambia nada

#### Scenario: Atomicidad
- **DADO** un movimiento rechazado por cualquier regla (`400`, `404` o `422`)
- **CUANDO** se consulta el mes
- **ENTONCES** los `asignado` de origen y destino son los de antes y no se creó ninguna fila
