# Proposal

## Why

El backend ya aplica las reglas de las tarjetas de crédito (change archivado
`tarjetas-credito-backend`): cada tarjeta del presupuesto tiene una categoría `Pago: <tarjeta>` en
un grupo `Pagos de tarjetas de crédito`, ambos protegidos, que no admiten transacciones, y el tipo
de una cuenta no cambia desde ni hacia tarjeta. El frontend no lo sabe: ofrece acciones que el
backend rechaza con `422` y las muestra con un aviso genérico o con un mensaje equivocado, y no
hay forma clara de pagar una tarjeta. Solo Conciliación, Programadas, Beneficiarios y
Auto-asignar excluyen las categorías de pago.

## What Changes

Hallazgos de la auditoría, confirmados leyendo el código:

- **Modelos**: `CategoriaResponse` y `GrupoCategoriaConCategoriasResponse`
  (`features/categorias`) y `CategoriaResumen`/`GrupoCategoriasResumen` (`features/transacciones`)
  no declaran `esPagoTarjeta`, `cuentaId` ni el `tipo` del grupo (`NORMAL`/`PAGOS_TARJETA`).
  `CuentaResumen` (`features/transacciones`) tampoco declara el `tipo` de la cuenta. Se agregan
  como campos aditivos opcionales.
- **Categorías**: el grupo y las categorías de pago se ven y se comportan como las normales. Se
  muestran como **protegidos**: etiqueta con ícono `credit_card`, sin menú de acciones, sin asa de
  arrastre, sin `Agregar categoría`, y el grupo no se ofrece como destino de `Mover a...` ni
  acepta soltar categorías. Un `422` `REGLA_NEGOCIO_VIOLADA` muestra un mensaje fijo en vez del
  aviso genérico (la vista ya vuelve al orden anterior al fallar un movimiento: eso no cambia).
- **Presupuesto mensual**: la fila de una categoría de pago lleva la etiqueta, una aclaración de
  que su actividad es una reserva para pagar la tarjeta y un enlace a los movimientos de la
  tarjeta. Asignar, mover dinero y metas siguen permitidos.
- **Selectores de categoría**: el diálogo de transacción, el editor de división, la barra de lote
  (`Categorizar`) y el diálogo de transferencia ofrecen las categorías de pago y el backend las
  rechaza. Se excluyen con **un solo filtro compartido** (`shared/`). En el filtro por categoría
  de la lista se ocultan, salvo la que esté elegida en la URL.
- **Cuentas**: el selector de tipo permite el cambio desde o hacia `TARJETA_CREDITO` al editar, y
  cualquier `REGLA_NEGOCIO_VIOLADA` se muestra como el mensaje del saldo negativo. Se bloquea el
  cambio con un mensaje en el campo (y `Guardar` deshabilitado) y el mensaje del `422` pasa a
  depender del contexto de la operación, no de `detail`.
- **Pagar una tarjeta**: no existe el flujo. El menú de una tarjeta abierta del presupuesto gana
  `Pagar tarjeta`, que lleva a Transacciones (la tarjeta como filtro) y abre allí el diálogo de
  transferencia **ya existente** con la tarjeta como destino fijo, solo cuentas de origen del
  presupuesto que no son tarjeta y una nota de que el pago rebaja la reserva. El diálogo de
  transacción avisa al registrar una entrada sin categoría en una tarjeta.
  Al cerrar el pago, el foco vuelve a `Agregar transferencia` (el pago se abre por URL, sin botón de
  origen).
- Fuera de alcance: backend, el cálculo de la reserva, pantallas nuevas, límite o fecha de corte
  de la tarjeta, y los cuatro lugares que ya excluyen las categorías de pago (no se tocan ni se
  migran al filtro compartido).

Se propone ejecutar el change en **dos etapas independientes** (ver `design.md` y `tasks.md`):
**A** (etiqueta y filtro compartidos, modelos, Categorías y selectores de Transacciones) y **B**
(Cuentas, presupuesto mensual y Pagar tarjeta). B solo necesita de A la etiqueta compartida.

## Capabilities

### New Capabilities

- `tarjetas-credito-frontend`: lo transversal de las tarjetas en el frontend: la etiqueta de pago
  de tarjeta, el filtro compartido que excluye las categorías de pago de los selectores de
  Transacciones (transacción, división, lote, transferencia y filtro de la lista) y los mensajes
  fijos.

### Modified Capabilities

- `categorias-frontend`: **ADDED** grupo y categorías de pago protegidos (presentación, acciones
  ocultas, sin arrastre, sin destino de movimiento, errores `422`).
- `presupuesto-mensual-frontend`: **ADDED** fila de una categoría de pago de tarjeta.
- `cuentas-frontend`: **MODIFIED** "Errores del diálogo de cuenta según su código" (el mensaje de
  `REGLA_NEGOCIO_VIOLADA` depende del contexto); **ADDED** tipo de tarjeta fijo al editar y
  `Pagar tarjeta` en el menú.
- `transacciones-frontend`: **ADDED** aviso de entrada sin categoría en una tarjeta y apertura del
  pago de tarjeta desde la URL.
- `transferencias-frontend`: **ADDED** modo de pago de tarjeta del diálogo de transferencia.

## Impact

- Solo frontend (`frontend/src/app`): `shared/pago-tarjeta/` (nuevo), `features/categorias`,
  `features/transacciones`, `features/cuentas` y `features/presupuesto-mensual`. Sin dependencias
  nuevas, sin cambios de API ni de backend, sin tablas.
- **Una excepción declarada a "las specs existentes no cambian"**: la aserción final del test
  `con saldo inicial negativo, un tipo que no lo admite marca el tipo`
  (`cuentas/components/dialogo-cuenta.component.spec.ts`) afirma que una tarjeta puede pasar a
  `Préstamo`, algo que el backend ya responde con `422`. Es el mismo caso que el change
  `tarjetas-credito-backend` declaró como `BREAKING (acotado)`. Ver `design.md`.
- Riesgo de trabajo en paralelo: se concentra en `transacciones.page.ts`,
  `dialogo-transferencia.component.*` y `dialogo-cuenta.component.*` (tabla de archivos en
  `design.md`).
