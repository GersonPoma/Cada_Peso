# Proposal

## Why

El backend ya importa un CSV de extracto bancario a una cuenta (change archivado
`importacion-csv`, endpoints `POST .../importacion/vista-previa` y `POST .../importacion`), pero
no hay forma de usarlo desde la aplicación: la persona tendría que cargar a mano cada movimiento
del extracto. Esta pantalla cierra ese hueco con un asistente que lee el archivo en el navegador,
deja elegir las columnas, muestra qué pasaría y recién entonces importa.

## What Changes

Un asistente de cinco pasos para **una cuenta abierta**, en la ruta
`/presupuestos/:presupuestoId/cuentas/:cuentaId/importacion` (sin entrada en el menú lateral):

- **a) Archivo**: se elige el CSV y se **previenen en el cliente** las causas de `400`: más de
  2 MiB, archivo vacío o sin filas de datos, no UTF-8 (incluye UTF-16), más de 5000 filas de datos
  y comilla sin cerrar. El backend responde todas con `DATOS_INVALIDOS` sin mapa `errores`, así que
  el frontend no puede distinguirlas por `codigo`: si igual llega un `400`, un único mensaje fijo
  lista las correcciones posibles. Nunca se lee `detail`.
- **b) Mapeo**: muestra las primeras filas leídas en el navegador (nada se envía todavía), con
  sugerencias **editables** de separador, encabezado, formato de fecha y separador decimal, y las
  columnas elegidas entre las detectadas (mostradas desde 1, enviadas desde 0). El monto va en
  una columna con signo **o** en débito y crédito, nunca mezclados.
- **c) Vista previa**: con un botón explícito. Totales, y una tabla paginada en el cliente con
  filtro por estado (nuevas, duplicadas, inválidas), con fechas y montos en formato regional.
- **d) Confirmar**: `omitirInvalidas` solo si hay filas inválidas; con 0 filas nuevas no se puede
  importar.
- **e) Resultado**: importadas, duplicadas, omitidas (inválidas omitidas) y sin categoría, una nota
  sobre "Listo para asignar" que **depende de la cuenta** y un enlace a sus transacciones sin
  aprobar.

Entrada: ítem `Importar CSV` en el menú de cada cuenta **abierta** de Cuentas.

Se propone ejecutar el change en **dos etapas integrables por separado** (ver `design.md` y
`tasks.md`): **A** (pasos a a c: ruta, entrada, lectura local y validaciones, mapeo y vista previa)
y **B** (pasos d y e: confirmar, importar y resultado). La etapa A llega hasta la vista previa y
no importa nada.

Fuera de alcance: guardar mapeos, OFX, XLSX, otras codificaciones, deshacer una importación,
categorización automática, cambios de backend, elegir la cuenta dentro del asistente y
interpretar montos en el cliente (la lectura local solo muestra las celdas como texto).

## Capabilities

### New Capabilities

- `importacion-csv-frontend`: el asistente de importación de un CSV a una cuenta: entrada y estados
  de la pantalla, validaciones previas a la subida, lectura local, mapeo, vista previa, importación,
  resultado y su nota de "Listo para asignar", errores por `codigo` y accesibilidad.

### Modified Capabilities

- `cuentas-frontend`: **ADDED** `Importar CSV` en el menú de las cuentas abiertas.

## Impact

- Solo frontend (`frontend/src/app`): feature nueva `features/importacion/`, y dos archivos
  compartidos con su spec: `app.routes.ts` y `features/cuentas/pages/cuentas.page.html`. Sin
  dependencias nuevas, sin cambios de API ni de backend, sin tablas. No se toca `core/material`
  (no se usa `MatStepper`).
- `AGENTS.md`: el árbol del frontend y una sección "Importación de CSV (frontend)".
- **Riesgo para tests existentes, resuelto en el diseño**: `cuentas.page.spec.ts` afirma que el
  menú de una cuenta abierta (`Banco`) tiene **exactamente un enlace** (`Conciliar`). Un ítem
  `Importar CSV` implementado como enlace rompería esa aserción; por eso se implementa como botón
  con `routerLink` (no cuenta como enlace en ese test). Ver D11 en `design.md` y la alternativa.
- Riesgo de trabajo en paralelo: bajo; todo lo nuevo vive en su propia carpeta.
- Línea base de tests al preparar este change: 125 archivos y 1400 tests, todos pasando; se vuelve
  a medir al empezar.
