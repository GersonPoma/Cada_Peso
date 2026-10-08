# Proposal

## Why

Hoy gastar con una cuenta `TARJETA_CREDITO` baja el disponible de la categoría, pero el dinero
sigue "libre" en las cuentas: nada reserva lo necesario para pagar la tarjeta, y el presupuesto
miente sobre cuánto se puede gastar. En el presupuesto base cero, el gasto con tarjeta debe
mover el dinero de la categoría a una reserva de pago, de modo que pagar la tarjeta sea siempre
posible con lo reservado.

## What Changes

- Cada tarjeta de crédito **del presupuesto** (`enPresupuesto` verdadero) tiene una categoría
  `Pago: <nombre de la tarjeta>` dentro del grupo `Pagos de tarjetas de crédito`. El grupo se crea
  la primera vez que el presupuesto tiene una tarjeta (no al registrarse: el árbol inicial de 13
  categorías no cambia). El grupo se localiza por su tipo `PAGOS_TARJETA`, nunca por nombre; si ya
  existe un grupo normal con ese nombre, el de pagos usa un nombre alterno y la tarjeta se crea
  igual (también en la migración).
- El nombre `Pago: <nombre>` se recorta para no pasar de 100 caracteres (cuenta y categoría
  admiten 100 y el prefijo suma 6).
- Crear, renombrar, cerrar y reabrir la tarjeta crea, renombra, oculta y muestra su categoría. La
  feature `cuenta` publica eventos (`CuentaCreadaEvento`, `CuentaRenombradaEvento`,
  `CuentaCerradaEvento`, `CuentaReabiertaEvento`) y `categoria` los escucha de forma síncrona,
  en la misma transacción; `cuenta` no importa `categoria`.
- La `actividad` de una categoría de pago en un mes es la suma de `-monto` de los gastos con
  categoría hecha con esa tarjeta (subtransacciones incluidas) más `-monto` de las patas de
  transferencia hacia la tarjeta cuya otra pata está en una cuenta del presupuesto (los pagos).
  La reserva es siempre el monto completo del gasto, aunque la categoría normal no tenga
  disponible; así el sobregasto con tarjeta se trata igual que el normal.
- El `disponible` de la categoría de pago usa la regla de arrastre de las demás. La actividad de
  las categorías normales no cambia. Todo con consultas agregadas.
- Protecciones 422 sobre el grupo y las categorías de pago (renombrar, ocultar/mostrar, mover,
  nota, crear o mover categorías dentro del grupo) y sobre registrar transacciones en ellas.
  Asignar y mover dinero hacia/desde una categoría de pago sí se permite.
- Una categoría de pago no puede ser la categoría predeterminada de un beneficiario (422 al
  crear y editar).
- Las categorías de pago SÍ admiten meta, como cualquier categoría.
- Cambiar el tipo de una cuenta desde o hacia `TARJETA_CREDITO` responde 422.
- `listoParaAsignar`: las entradas sin categoría en una tarjeta ya no son ingreso (no entra
  dinero a ninguna cuenta que no sea tarjeta); necesario para el invariante.
- Migración idempotente al arrancar: crea el grupo y las categorías que falten para las tarjetas
  y presupuestos ya existentes (sin Flyway).
- Auto-asignar sin `categoriaIds` ignora las categorías de pago de tarjeta.
- Contratos aditivos: el árbol de categorías y las filas del mes ganan `esPagoTarjeta` y
  `cuentaId`; los grupos del árbol ganan `tipo`.
- **BREAKING (acotado)**: editar una cuenta `TARJETA_CREDITO` a `PRESTAMO` (o al revés) pasa de
  200 a 422. Se modifican a propósito los 2 tests que lo afirmaban (ver design).

## Capabilities

### New Capabilities

(ninguna)

### Modified Capabilities

- `cuentas`: **MODIFICADO** "Editar nombre y tipo" (422 al cambiar el tipo desde o hacia
  `TARJETA_CREDITO`; el escenario "Cambio a un tipo que sí admite saldo negativo" pasa a 422).
- `categorias`: **ADDED** grupo `Pagos de tarjetas de crédito`; categoría de pago por tarjeta y
  su sincronía con la cuenta; protecciones 422 del grupo y de las categorías de pago; campos
  aditivos del árbol.
- `asignacion`: **ADDED** actividad, disponible y reserva de las categorías de pago, asignar y
  mover permitidos, campos aditivos de las filas del mes e invariante del dinero; **MODIFICADO**
  "Listo para asignar" (las entradas sin categoría en tarjetas no son ingreso).
- `transacciones`: **ADDED** una categoría de pago no admite transacciones (422), en todas las
  vías que asignan categoría.
- `metas`: **MODIFICADO** "Auto-asignar" (sin `categoriaIds` omite las categorías de pago; con
  ids explícitos las procesa); **ADDED** las categorías de pago admiten meta.
- `beneficiarios`: **ADDED** la categoría predeterminada no puede ser una categoría de pago (422
  al crear y editar).

## Impact

- Backend: `cuenta` (eventos, validación de tipo), `categoria` (entidades, oyente, migración,
  protecciones, responses), `asignacion` (consultas agregadas y cálculo puro de pagos),
  `transaccion` y `beneficiario` (validación de categoría), `meta` (auto-asignar). Esquema: columnas nuevas
  `grupos_categoria.tipo` y `categorias.cuenta_tarjeta_id` vía `ddl-auto=update`.
- `presupuesto`: solo un método nuevo de consulta con bloqueo de escritura en
  `PresupuestoRepository`, cambio interno sin requisitos modificados (no hay delta de
  `presupuestos`).
- Sin cambios de frontend. Línea base: 1053 tests, sin modificar ninguna aserción existente salvo
  las 2 del cambio de tipo declaradas arriba.
