## Why

El backend ya guarda plantillas de transacciones recurrentes y genera sus ocurrencias cada día
(spec `transacciones-programadas`), pero el frontend no las ofrece: la persona tiene que cargar a
mano cada alquiler, sueldo o suscripción, y si una plantilla deja de generar (cuenta cerrada,
categoría de pago de tarjeta) nadie se entera.

## What Changes

- **Nueva feature `features/transacciones-programadas/`** con la pantalla
  `/presupuestos/:presupuestoId/programadas` y su entrada `Programadas` en el menú lateral (una
  ruta hija con `data: seccion(...)`):
  - Lista de plantillas en el orden de la API con cuenta, beneficiario, categoría, monto con
    `Salida`/`Entrada`, frecuencia legible (`Cada mes`, `Cada 2 semanas`...), próxima fecha y
    estado con texto e ícono además de color: `Activa`, `Pausada`, `Finalizada` o
    `No se pudo generar` con el motivo (`ultimoError`). Estados de carga, vacío y error con
    `Reintentar`.
  - Diálogo de crear y editar: cuenta (solo abiertas), `Salida`/`Entrada` + monto con la
    calculadora, frecuencia, fecha de inicio y de fin con calendario (`yyyy-MM-dd` local),
    categoría (sin las de pago de tarjeta), beneficiario y memo. Al editar, la cuenta y la fecha
    de inicio se muestran en solo lectura.
  - Pausar, reanudar (avisando que no se generan las ocurrencias del período pausado) y borrar
    con confirmación que aclara que las transacciones ya generadas se conservan.
  - `Generar ahora`, con el resultado (`generadas` y `plantillasConError`) y recarga de la lista.
  - Errores por `codigo`: `DATOS_INVALIDOS` por campo, `REGLA_NEGOCIO_VIOLADA` en el diálogo sin
    cerrarlo, `RECURSO_NO_ENCONTRADO` con aviso y recarga; botones deshabilitados mientras se
    envía y respuestas atrasadas descartadas.
- **Decisiones de alcance** (detalle en design):
  - **Sin vista de próximas fechas calculadas en el cliente**: exigiría duplicar
    `CalendarioProgramado` (pasos desde `fechaInicio`, último día del mes); se muestra la
    `proximaFecha` del servidor y una descripción de la regla de fin de mes.
  - **Beneficiario como texto libre, sin autocompletado**: `app-campo-beneficiario` vive en
    `features/transacciones` y una feature no importa otra; moverlo a `shared/` tocaría archivos
    de otras personas. El backend crea o vincula el beneficiario al generar.
  - **Sin indicador de "programada" en la lista de transacciones**: `TransaccionResponse` ya trae
    `programadaId`, pero mostrarlo exige tocar `tabla-transacciones`; queda como mejora aparte.
- `app.routes.ts`: una ruta hija `programadas` con `data: seccion('Programadas',
  'event_repeat')`.
- `AGENTS.md`: la feature del frontend y sus decisiones.

Fuera de alcance: editar ocurrencias individuales, calendario visual, notificaciones,
transferencias y divisiones programadas (el backend no las admite). No cambia el backend ni se
agregan dependencias.

## Capabilities

### New Capabilities
- `transacciones-programadas-frontend`: lista con estado y frecuencia legibles, crear y editar
  con campos de solo lectura, pausar, reanudar, borrar, generar ahora, último error y errores por
  código.

### Modified Capabilities
<!-- Ninguna: la lista de transacciones no cambia. -->

## Impact

- **Frontend** (`frontend/src/app`): archivos nuevos en `features/transacciones-programadas/`.
  Archivos compartidos modificados: **solo** `app.routes.ts` (una ruta) y `app.routes.spec.ts`
  (un caso). El layout del menú lateral, `core/`, `shared/` y las demás features no cambian.
- **API consumida**: `GET`, `POST` `.../transacciones-programadas`; `PUT`, `DELETE`
  `.../transacciones-programadas/{id}`; `POST .../{id}/pausar`, `.../{id}/reanudar` y
  `.../generar`; `GET .../cuentas?incluirCerradas=true` y `GET .../categorias?incluirOcultas=true`.
  Ya implementadas.
- **Dependencias**: ninguna nueva.
- **Documentación**: `AGENTS.md`.
