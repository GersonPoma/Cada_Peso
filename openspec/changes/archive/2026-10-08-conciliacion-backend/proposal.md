# Proposal

## Why

Hoy cada transacción se marca `CONCILIADA` a mano, pero nada compara el saldo conciliado de una
cuenta con el saldo del extracto del banco, ni cierra el periodo pasando las transacciones a
`RECONCILIADA`. Ese estado existe y está protegido (no se edita, mueve, borra ni cambia), pero
ningún flujo lo fija. Este change agrega el flujo de conciliación de una cuenta, solo backend.

## What Changes

- Nueva feature `conciliacion` bajo
  `/api/v1/presupuestos/{presupuestoId}/cuentas/{cuentaId}/conciliacion`:
  - `GET /estado`: compara el saldo conciliado de la cuenta con el saldo del extracto (solo
    lectura) y lista las transacciones `NO_CONCILIADA` para marcar.
  - `POST`: cierra la conciliación en una sola transacción de base: crea el ajuste si hay
    diferencia (y se pidió), pasa a `RECONCILIADA` las `CONCILIADA` hasta la fecha del extracto y
    guarda el registro (`201`).
  - `GET`: historial de conciliaciones de la cuenta, más reciente primero.
- Nueva tabla `conciliaciones` (registro de historial inmutable: sin `PUT` ni `DELETE`).
- Cambio mínimo y aditivo en `transaccion` y `cuenta`: métodos nuevos (crear el ajuste,
  reconciliar por lote, saldo conciliado a una fecha, listar `NO_CONCILIADA`, bloqueo de la
  cuenta). No cambia ningún comportamiento ni aserción existente.
- Fuera de alcance: **deshacer una conciliación** (un error exige intervención manual),
  frontend y edición del registro.

## Capabilities

### New Capabilities
- `conciliacion`: comparar una cuenta con el extracto, cerrar la conciliación (con ajuste
  opcional y reconciliación por lote) y consultar el historial.

### Modified Capabilities
<!-- Ninguna: no cambia ningún requisito de `transacciones`, `transferencias`, `cuentas` ni
     `asignacion`. `RECONCILIADA` ya está definida y protegida; solo se empieza a fijar. -->

## Impact

- Código: paquete nuevo `com.presupuesto.conciliacion`; adiciones en `transaccion.service`,
  `transaccion.repository` y `cuenta.repository`.
- API: 3 rutas nuevas bajo `/cuentas/{cuentaId}/conciliacion`; las existentes no cambian.
- Base de datos: tabla `conciliaciones` (Hibernate `ddl-auto=update`); `transacciones` sin
  columnas nuevas.
- Dependencias: `conciliacion` importa `transaccion`, `cuenta`, `presupuesto` y `comun`; nadie
  importa `conciliacion`.
- Línea base: los 1315 tests existentes pasan sin modificar ninguna aserción.
