# Proposal

## Why

El frontend todavía no tiene librería de componentes: el próximo change (`auth-frontend`) y las
pantallas de negocio necesitan formularios, botones, diálogos, tablas, íconos y un selector de
fechas coherentes entre sí. Conviene fijar ahora una única librería, con el tema visual de Cada
Peso y los textos en español, antes de que cada feature elija sus propios componentes. Además, el
selector de fechas devuelve un `Date` a medianoche local, y convertirlo con `toISOString()`
corre la fecha un día en zonas horarias con desfase positivo (comprobado: el 1 de octubre a
medianoche en Tokio sale como `2026-09-30`); hace falta una conversión única y probada antes de
la primera pantalla que envíe una fecha de negocio (`LocalDate`) al backend.

## What Changes

- Instalar `@angular/material` y `@angular/cdk` 21 (la versión que corresponde a Angular 21) con
  `npm install`, sin el schematic `ng add` (que agrega links a Google Fonts en `index.html`).
- Tema **Material 3** propio en SCSS con color principal verde `#2E7D32` (el del favicon), solo
  modo claro.
- Íconos con `mat-icon` usando **Material Symbols**, con la fuente instalada desde npm y
  empaquetada en el build (sin peticiones a Google Fonts en tiempo de ejecución).
- Datepicker con `MAT_DATE_LOCALE` igual a `regionUsuario()` y un `DateAdapter` propio en
  `core/material/` que extiende `NativeDateAdapter`: interpreta las fechas escritas a mano según
  el orden de día, mes y año de la región (con `/`, `-` o `.` como separador), trata una fecha
  inexistente como inválida en vez de ajustarla a otro día (`31/02/2003` no pasa a 3 de marzo) y
  muestra la fecha en ese mismo orden, de modo que lo mostrado se puede volver a escribir.
- Nueva utilidad en `shared/` que convierte el `Date` del datepicker en el string `yyyy-MM-dd`
  del `LocalDate` del backend con `getFullYear()`/`getMonth()`/`getDate()` locales, nunca con
  `toISOString()`, con tests en zonas horarias de desfase negativo y positivo.
- Textos internos de los componentes de Material que se configuran (paginador y datepicker) en
  español.
- `AGENTS.md`: Angular Material como **única** librería de UI (botones, inputs, formularios con
  `mat-form-field` y `mat-error`, diálogos, tablas, íconos con `mat-icon`); prohibido agregar
  otras librerías de componentes o de íconos; las fechas del datepicker se convierten con la
  utilidad de `shared/`. También la **estructura obligatoria de carpetas del frontend** (`core/`,
  `shared/`, `features/<feature>/` con `pages`, `components`, `services` y `models`).
- `openspec/config.yaml`: resumen de la estructura del frontend y de la regla de Angular Material
  en el `context`, y en `rules` de `design` y `tasks` el equivalente del frontend de las reglas
  del backend: toda feature nueva o change que cree o mueva archivos del frontend indica la ruta
  de cada archivo según la estructura obligatoria del frontend, y el design incluye una tabla con
  la ruta de cada archivo y la de su test.

Sin pantallas de negocio y sin cambios en el backend.

## Capabilities

### New Capabilities

- `idioma-interfaz`: idioma de los textos que muestran los propios componentes de interfaz (no
  los textos escritos por la aplicación), que deben aparecer en español.

### Modified Capabilities

- `formato-regional`: se agregan requisitos para el selector de fechas (calendario según la
  región detectada), para interpretar las fechas escritas a mano según la región y para la
  conversión de la fecha elegida al formato `yyyy-MM-dd` sin desplazarse un día por zona horaria.
  Los requisitos existentes no cambian.

## Impact

- **Frontend, dependencias**: `@angular/material`, `@angular/cdk` y `material-symbols` en
  `package.json`.
- **Frontend, código**: `src/styles.scss` y un parcial de colores del tema; `app.config.ts`
  (providers de Material); `core/material/` (configuración de Material: adaptador de fechas
  regional, locale, textos en español, íconos) y `shared/fecha/` (utilidad de conversión), cada
  uno con sus tests.
- **Documentación y planificación**: `AGENTS.md` y `openspec/config.yaml`.
- **Backend**: ninguno.
