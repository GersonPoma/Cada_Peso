## Why

El backend ya permite crear, listar y renombrar presupuestos, y toda feature de negocio que
sigue (cuentas, categorías, transacciones) cuelga de un presupuesto. El frontend todavía no sabe
qué es un presupuesto: tras iniciar sesión solo muestra un saludo en `/`. Antes de construir las
pantallas de cuentas hace falta una estructura de navegación con un **presupuesto activo** que
esas features puedan leer, y pantallas para ver, crear y renombrar presupuestos.

## What Changes

- Nuevo servicio `PresupuestoActivoService` en `core/presupuesto-activo/`: expone en una señal
  de solo lectura el presupuesto actual (`id`, `nombre`, `moneda`) para que cualquier feature
  obtenga el id y la moneda sin importar de otra feature. Queda limpio al cerrar sesión.
- Nueva feature `features/presupuestos/` con modelos, servicio HTTP (`listar`, `crear`,
  `renombrar`), dos páginas y un diálogo:
  - `/` pasa a ser una página de redirección: lleva al primer presupuesto de la lista, o muestra
    un estado vacío con "Crear mi primer presupuesto" si la persona no tiene ninguno.
  - `/presupuestos/:presupuestoId` es el layout de la app: cabecera con selector de
    presupuesto, botones de gestionar (crear y renombrar) y "Cerrar sesión"; menú lateral
    (`mat-sidenav`) que en pantallas estrechas se abre con un botón de hamburguesa; y un
    `router-outlet` para las páginas de cada sección. Un id inexistente o ajeno vuelve a `/`.
  - Un diálogo de Material sirve para crear (nombre y moneda) y para renombrar (solo nombre),
    con los errores interpretados por `codigo`.
- **BREAKING (rutas del frontend)**: la página de inicio deja de vivir en `/` y pasa a ser la
  página hija por defecto de `/presupuestos/:presupuestoId`; pierde su cabecera propia y su
  botón "Cerrar sesión", que se mueven al layout.
- `CODIGOS_API` incorpora `PRESUPUESTO_YA_EXISTE`.
- `AGENTS.md`: árbol del frontend con `core/presupuesto-activo/` y `features/presupuestos/`, y la
  regla de que las features obtienen el presupuesto actual de `PresupuestoActivoService`.

Fuera de alcance: cuentas, categorías, transacciones, borrar o archivar presupuestos, cambiar la
moneda de un presupuesto y el tema oscuro. No se agregan librerías ni se ejecuta `npm install`
(`@angular/cdk` y `@angular/material` ya están instalados).

## Capabilities

### New Capabilities
- `presupuestos-frontend`: navegación con presupuesto activo (redirección al primero, estado sin
  presupuestos, selector y cambio de presupuesto, id inexistente o ajeno), diálogo de crear y
  renombrar con sus errores, presupuesto activo disponible para otras features y limpio al cerrar
  sesión, y menú lateral adaptable al ancho de pantalla.

### Modified Capabilities
- `autenticacion-frontend`: la página de inicio se muestra en `/presupuestos/:presupuestoId` y
  el botón "Cerrar sesión" pasa de la página de inicio al layout del presupuesto; la cabecera
  `Cada Peso` sigue en todas las pantallas. Los escenarios de los guards no cambian.

## Impact

- **Frontend** (`frontend/src/app`): `app.routes.ts` y su spec, `core/sesion/sesion.service.ts`
  (limpia el presupuesto activo al cerrar), `shared/api/problema-api.ts`, la página de inicio y
  su spec, y los archivos nuevos de `core/presupuesto-activo/` y `features/presupuestos/`.
- **API consumida**: `GET`, `POST` y `PUT /api/v1/presupuestos[/{id}]`, ya implementadas; el
  backend no cambia.
- **Dependencias**: ninguna nueva; se usan `@angular/cdk/layout` (`BreakpointObserver`) y los
  módulos de Material ya instalados (sidenav, list, select, dialog, icon, progress-spinner,
  snack-bar).
- **Documentación**: `AGENTS.md`.
