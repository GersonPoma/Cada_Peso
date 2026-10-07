# Proposal

## Why

Hoy el beneficiario de una transacción es texto libre: no hay una lista de beneficiarios del
presupuesto, ni autocompletado, ni forma de recordar en qué categoría se gasta con cada uno.
Capturar movimientos es lento y propenso a errores de escritura ("Netflix" / "netflix "). Con
beneficiarios como recurso propio, la próxima pantalla de transacciones podrá sugerir el
beneficiario y su categoría habitual.

## What Changes

- Nueva feature `beneficiario` (solo backend) con la entidad `Beneficiario` (tabla
  `beneficiarios`): nombre, nombre normalizado, categoría predeterminada opcional y unicidad por
  (presupuesto, nombre normalizado) sin distinguir mayúsculas.
- Endpoints `GET` (lista completa, con `q` por prefijo y `limite` para autocompletado), `POST`,
  `GET /{id}` y `PUT /{id}` bajo `/api/v1/presupuestos/{presupuestoId}/beneficiarios`. No hay
  `DELETE`.
- Nuevo código de error `BENEFICIARIO_YA_EXISTE` (409).
- `Transaccion` gana un vínculo opcional al beneficiario y `TransaccionResponse` gana
  `beneficiarioId`. El campo de texto `beneficiario` se mantiene (sin romper el contrato).
- Al crear o editar una transacción con beneficiario (texto), se busca o se crea el
  beneficiario del presupuesto y se vincula; el texto guardado pasa a ser el nombre del
  beneficiario. Si la transacción queda con categoría (no dividida), esa categoría se recuerda
  como `categoriaPredeterminada` del beneficiario.
- Duplicar copia el vínculo; el lote `CATEGORIZAR` también actualiza la categoría recordada.
- Nueva dependencia `transaccion → beneficiario`, documentada en `AGENTS.md`.
- Sin migración masiva: las transacciones existentes se vinculan solo al editarlas.

## Capabilities

### New Capabilities
- `beneficiarios`: gestión de los beneficiarios de un presupuesto (crear, listar con
  autocompletado, consultar, renombrar y fijar o quitar su categoría predeterminada), con
  aislamiento por presupuesto y persona.

### Modified Capabilities
- `transacciones`: `beneficiarioId` en la respuesta, creación y vínculo automático del
  beneficiario al crear y editar, categoría recordada (incluido el lote `CATEGORIZAR`) y
  duplicado que conserva el vínculo.

## Impact

- Backend: paquete nuevo `com.presupuesto.beneficiario`; cambios en `transaccion` (entidad,
  response, `TransaccionService`, `TransaccionLoteService`) y en `comun/excepcion/CodigoError`.
- Base de datos: tabla nueva `beneficiarios` y columna nueva `transacciones.beneficiario_id`
  (Hibernate `ddl-auto=update`, nula en las filas existentes).
- API: campo nuevo `beneficiarioId` en las respuestas de transacción (aditivo, compatible).
- `AGENTS.md`: árbol, regla de dependencias y `grep` de `comun/`.
- Fuera de alcance: frontend, renombrado automático, fusión, borrado, importación y sugerencia
  por ubicación.
