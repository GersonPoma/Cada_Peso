## Why

El backend ya concilia una cuenta contra el saldo de su extracto: calcula la diferencia, crea
una transacción de ajuste si se pide, reconcilia las transacciones conciliadas hasta la fecha del
extracto y guarda el historial (spec `conciliacion`). Pero el frontend no lo usa: la persona solo
puede marcar transacciones como conciliadas una por una y nunca cierra el ciclo, así que no hay
forma de saber si sus cuentas cuadran con el banco ni de bloquear lo ya revisado.

## What Changes

- **Nueva feature `features/conciliacion/`** con la pantalla
  `/presupuestos/:presupuestoId/cuentas/:cuentaId/conciliacion` (sin entrada en el menú lateral):
  - Asistente: saldo del extracto con la calculadora (admite negativos) y fecha (hoy por defecto,
    no futura); consulta `GET .../conciliacion/estado` 300 ms después del último cambio,
    ignorando respuestas atrasadas, y muestra el saldo conciliado al corte, la diferencia con
    texto y las transacciones no conciliadas hasta la fecha, con un enlace a la lista de
    transacciones filtrada.
  - Con diferencia 0, `Reconciliar`; con diferencia distinta de 0, la opción de crear una
    transacción de ajuste con la categoría según las reglas del backend (obligatoria, opcional o
    no admitida). Confirmación explícita de que no se puede deshacer antes de enviar.
  - Resultado real de `POST .../conciliacion` (transacciones reconciliadas y ajuste creado) y
    recarga del estado y del historial.
  - Historial de la cuenta (`GET .../conciliacion`), con estados de carga, vacío y error.
  - Cuenta cerrada: pantalla de solo lectura con el historial y el aviso de que hay que reabrirla.
  - Modelos y servicios propios (conciliación, lectura de la cuenta con su tipo, lectura de
    categorías con `esPagoTarjeta`), función pura de la regla de categoría del ajuste y diálogo de
    confirmación propio.
- **Puntos de entrada**: `Conciliar` en el menú de cada cuenta abierta y `Ver conciliaciones` en
  el de cada cuenta cerrada (pantalla de Cuentas), y el botón `Conciliar` en la pantalla de
  Transacciones cuando está filtrada por una cuenta abierta. Son enlaces a la ruta: ninguna
  feature importa a otra.
- `app.routes.ts`: una ruta hija nueva, sin `data: seccion(...)`.
- `AGENTS.md`: la conciliación del frontend vive en `features/conciliacion`.

Fuera de alcance: deshacer una conciliación, importación de CSV, conciliación automática contra
el extracto y editar o borrar conciliaciones del historial (el backend no lo permite). No cambia
el backend ni se agregan librerías.

## Capabilities

### New Capabilities
- `conciliacion-frontend`: asistente de conciliación (estado con espera, diferencia, no
  conciliadas hasta la fecha), ajuste con su regla de categoría, confirmación irreversible,
  resultado, historial, cuenta cerrada, puntos de entrada y errores por código.

### Modified Capabilities
<!-- Ninguna: los puntos de entrada se agregan a los menús y encabezados existentes sin cambiar
sus requisitos; se describen en conciliacion-frontend. -->

## Impact

- **Frontend** (`frontend/src/app`): archivos nuevos en `features/conciliacion/`; cambios
  puntuales en `app.routes.ts` (una ruta), `features/cuentas/pages/cuentas.page.{ts,html}` (dos
  entradas del menú) y `features/transacciones/pages/transacciones.page.{ts,html}` (un botón). El
  menú lateral, `core/` y `shared/` no cambian.
- **API consumida**: `GET .../cuentas/{cuentaId}/conciliacion/estado`, `POST` y `GET
  .../cuentas/{cuentaId}/conciliacion`, `GET .../cuentas/{cuentaId}` y
  `GET .../categorias?incluirOcultas=true`; ya implementadas.
- **Dependencias**: ninguna nueva.
- **Documentación**: `AGENTS.md`.
