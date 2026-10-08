## Why

El backend ya crea, edita y borra transferencias como dos transacciones enlazadas, pero el
frontend solo las muestra: la persona no puede registrar un pago de tarjeta, un ahorro o un
traspaso entre cuentas sin dos transacciones a mano que no quedan enlazadas ni respetan la regla
de categoría. Además, la columna de una pata dice solo "Transferencia", sin indicar a qué cuenta.

## What Changes

- **Todo dentro de `features/transacciones/`** (igual que en el backend, sin feature nueva):
  - Modelos `CrearTransferenciaRequest`, `ActualizarTransferenciaRequest` y
    `TransferenciaResponse`, copiados de los records de `com.presupuesto.transaccion.dto`.
  - `TransferenciaService`: crear, obtener, actualizar y borrar.
  - Función pura `reglaCategoriaTransferencia(origen, destino)` que decide si la categoría se
    oculta, es obligatoria u opcional según `enPresupuesto`.
  - `DialogoTransferenciaComponent` (crear y editar): cuentas origen y destino excluyentes
    agrupadas, `Invertir`, fecha local por defecto, `app-campo-monto`, categoría dinámica con
    su ayuda y memo; al editar, las cuentas fijas y los datos de `GET /transferencias/{id}`.
- **Pantalla de transacciones**:
  - Botón `Agregar transferencia` junto a `Agregar transacción`, con la cuenta filtrada como
    origen.
  - El menú de una pata pasa a ofrecer `Editar transferencia` y `Borrar transferencia` (con una
    confirmación que avisa que se borran las dos transacciones), además de `Aprobar` y
    conciliar; deshabilitados con motivo si una pata está reconciliada o una cuenta cerrada.
    Siguen sin `Duplicar` ni `Mover`.
  - La columna de beneficiario de una pata dice `Transferencia a {cuenta}` o
    `Transferencia desde {cuenta}` con la pata par de la misma página, o `Transferencia` si no
    está, sin peticiones por fila.
  - Tras crear, editar o borrar, se recargan la página y los saldos.
- `AGENTS.md`: las transferencias del frontend viven en `features/transacciones` y la regla de
  categoría según `enPresupuesto`.

Fuera de alcance: monedas distintas, transferencias programadas, crear transferencias desde
la pantalla de cuentas, conciliación guiada, deshacer y tema oscuro. El lote no cambia. No se
agregan librerías ni cambia el backend.

## Capabilities

### New Capabilities
- `transferencias-frontend`: crear con las cuatro combinaciones de `enPresupuesto` y su regla y
  ayudas de categoría, invertir, editar con cuentas bloqueadas, borrar con confirmación de las
  dos patas, acciones deshabilitadas por pata reconciliada o cuenta cerrada, descripción
  `Transferencia a/desde` con y sin la pata par en la página, recarga y errores por `codigo`.

### Modified Capabilities
- `transacciones-frontend`: el menú de una pata de transferencia incluye `Editar transferencia`
  y `Borrar transferencia` (antes solo `Aprobar` y conciliar).

## Impact

- **Frontend** (`frontend/src/app/features/transacciones/`): modelos, servicio, función pura y
  diálogo nuevos; cambios en `acciones-transaccion.ts`, `tabla-transacciones`,
  `transacciones.page` y `datos-dialogos-transacciones.model.ts`.
- **API consumida**: `/transferencias` (POST, GET, PUT, DELETE), `GET /cuentas` y
  `GET /categorias`, ya implementadas.
- **Dependencias**: ninguna nueva.
- **Documentación**: `AGENTS.md`.
- **Relación con `beneficiarios-frontend`**: ambos tocan la tabla y el diálogo de transacciones;
  este change no modifica los mismos requisitos de la spec (la descripción de las patas es un
  requisito nuevo de `transferencias-frontend`).
