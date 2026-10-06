## Why

El corazón de YNAB es darle un trabajo a cada peso: repartir el dinero disponible entre las
categorías, mes a mes, y ver cuánto queda en cada una. El backend de asignación ya calcula el
asignado, la actividad, el disponible y el "Listo para asignar" de cada mes, y permite mover
dinero entre categorías, pero el frontend no tiene ninguna pantalla para usarlo. Sin ella, la
app no cumple su propósito de presupuesto base cero.

## What Changes

- Nueva feature `features/presupuesto-mensual/` con modelos que copian los records de
  `com.presupuesto.asignacion.dto`, un servicio (`obtener`, `asignar`, `moverDinero`) y funciones
  puras de mes (`yyyy-MM` con la fecha **local**, nunca con `toISOString()`).
- Página `/presupuestos/:presupuestoId/presupuesto/:mes` (enlace "Presupuesto" en el menú
  lateral), con:
  - **Barra de mes**: flechas, botón "Hoy" y el mes en texto largo localizado. Las flechas se
    deshabilitan en 2000-01 y 2100-12, y el mes vive en la URL.
  - **Tarjeta "Listo para asignar"** con tres estados (positivo, todo asignado, asignado de más),
    que no dependen solo del color.
  - **Grupos plegables** con sus totales, filas con columnas Categoría, Asignado, Actividad y
    Disponible, fila total del mes e interruptor "Mostrar ocultas".
  - **Celda de asignado editable** en el lugar, con guardado optimista, reversión ante error y
    Tab hacia la siguiente categoría.
  - **Diálogo "Mover dinero"** y acción "Cubrir sobregasto".
  - Estados de carga, error y vacío.
- **BREAKING (rutas del frontend)**: la pantalla principal de un presupuesto pasa a ser la del
  presupuesto mensual. `/presupuestos/:id` lleva a `/presupuestos/:id/presupuesto` y de ahí al
  mes actual. La página de inicio ("Hola, {nombre}") pasa a `/presupuestos/:id/inicio`.
- `CODIGOS_API` incorpora `CONFLICTO` y `RECURSO_NO_ENCONTRADO`.
- `AGENTS.md`: árbol con `features/presupuesto-mensual/`, la regla de que los montos se editan
  siempre con `aMilliunits()` y la de que el mes se arma con la fecha local.

Fuera de alcance: metas, auto-asignar, indicador amarillo, categorías de pago de tarjeta, notas
por mes, deshacer, atajos de teclado globales, calculadora en los campos, transacciones,
transferencias y tema oscuro. No se agregan librerías ni se ejecuta `npm install`.

## Capabilities

### New Capabilities
- `presupuesto-mensual-frontend`: navegación por mes (flechas, "Hoy", URL, límites, mes
  inválido), "Listo para asignar" y sus tres estados, grupos plegables y totales, indicadores de
  disponible y sobregasto, mostrar ocultas, edición del asignado, mover dinero y cubrir
  sobregasto, y estados de carga, error y vacío.

### Modified Capabilities
- `presupuestos-frontend`: la pantalla de un presupuesto abre por defecto el presupuesto
  mensual del mes actual, y el menú lateral incluye "Presupuesto" e "Inicio".
- `autenticacion-frontend`: la página de inicio deja de ser la sección por defecto y vive en
  `/presupuestos/:presupuestoId/inicio`.

## Impact

- **Frontend** (`frontend/src/app`): archivos nuevos en `features/presupuesto-mensual/`;
  cambios en `app.routes.ts` (rutas hijas `''`, `presupuesto`, `presupuesto/:mes` e `inicio`) y
  su spec, y en `shared/api/problema-api.ts`.
- **API consumida**: `GET /meses/{mes}`, `PUT /meses/{mes}/categorias/{categoriaId}` y
  `POST /meses/{mes}/mover-dinero`, bajo `/api/v1/presupuestos/{presupuestoId}`, ya
  implementadas. El backend no cambia.
- **Dependencias**: ninguna nueva.
- **Documentación**: `AGENTS.md`.
