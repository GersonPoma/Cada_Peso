# Design

## Context

Ver proposal.md - Why. Estado actual observado en `/frontend`:

- Angular `21.2.25` (`@angular/core` instalado), builder `@angular/build`, tests con el builder
  `@angular/build:unit-test` (Vitest 4 + jsdom) y `src/test-setup.ts` como `setupFiles`, que fija
  `process.env['TZ'] = 'America/New_York'` para toda la suite.
- `angular.json` → `build.options.styles`: solo `src/styles.scss` (vacío);
  `inlineStyleLanguage: scss`. `src/index.html` no carga ninguna fuente externa.
- `app.config.ts`: `provideBrowserGlobalErrorListeners()` y `provideRouter(routes)`.
- `src/app/core/api-base-url.ts`, `src/app/shared/formato/` (`regionUsuario()`, `MontoPipe`,
  `FechaPipe` y sus tests) y `src/app/features/` vacío.
- Prettier con `.prettierrc` (`printWidth 100`, `singleQuote`).
- Versiones publicadas compatibles: `@angular/material` y `@angular/cdk` `21.2.14` (peer
  `@angular/core ^21.0.0 || ^22.0.0`; no piden `@angular/animations`) y `material-symbols`
  `0.47.6` (fuentes variables `woff2` y CSS de Material Symbols empaquetadas en npm).
- Node 22 aplica en el momento un cambio de `process.env.TZ` (comprobado en esta máquina:
  `new Date(2026, 9, 1)` es `2026-10-01T04:00Z` en `America/New_York` y `2026-09-30T15:00Z` en
  `Asia/Tokyo`, es decir, `toISOString()` corre el día en zonas con desfase positivo).
- `Intl` numérico por región: `es-CL` → `01-10-2026`, `es-BO`/`es-ES` → `1/10/2026`,
  `en-US` → `10/1/2026`. Con `day`/`month` en `2-digit`, `formatToParts` da el orden y el
  separador: `es-BO` → día-mes-año con `/`, `es-CL` → día-mes-año con `-`, `en-US` → mes-día-año
  con `/`, `de-DE` → día-mes-año con `.`, `ja-JP` → año-mes-día con `/`, `hu-HU`/`ko-KR` →
  año-mes-día con `". "` y punto final.
- `new Date(2003, 1, 31)` no falla: se ajusta al 3 de marzo de 2003.

## Goals / Non-Goals

**Goals:**
- Angular Material listo para usar en cualquier pantalla, con el tema de Cada Peso, íconos
  locales y textos internos en español, configurado una sola vez en `core/`.
- Una única forma, probada, de convertir el `Date` del datepicker en `yyyy-MM-dd`.
- Reglas documentadas para que ningún change futuro introduzca otra librería de UI o rompa la
  estructura de carpetas del frontend.

**Non-Goals:**
- Pantallas de negocio, layout de la aplicación (toolbar, menú) o rutas nuevas.
- Modo oscuro o selección de tema por el usuario.
- Conversión inversa `yyyy-MM-dd` → `Date` para precargar un datepicker (la agregará el primer
  change que edite una fecha existente, junto a la utilidad de esta).
- Traducir componentes de Material que todavía no se configuran (stepper, sort header, etc.): se
  traducen en el change que los use por primera vez, con la misma técnica.
- Cambios en el backend.

## Decisions

**Instalación con `npm install`, no con `ng add @angular/material`.**
Se instalan `@angular/material@^21.2.14`, `@angular/cdk@^21.2.14` y `material-symbols@^0.47.6`
como dependencias. `ng add` es interactivo y su schematic agrega a `index.html` links a Google
Fonts (Roboto y Material Icons) y estilos que aquí no se quieren; todo lo que hace falta se
configura a mano en los puntos siguientes. No se agrega `@angular/animations`: Material 21 ya no
lo necesita.

**Tema Material 3 en SCSS, generado desde `#2E7D32`, con el primario exacto del favicon.**
- `ng generate @angular/material:theme-color` con `#2E7D32` como color primario genera
  `src/_theme-colors.scss` con las paletas tonales M3 (`$primary-palette` y
  `$tertiary-palette`). Se versiona como archivo generado y no se edita a mano.
- `src/styles.scss` aplica el tema en `html` con `mat.theme(...)`: `color` con esas paletas y
  `theme-type: light`, `color-scheme: light`, `density: 0`, y tipografía con una pila de fuentes
  del sistema (`system-ui, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif`) para no
  descargar fuentes de texto de Google Fonts ni agregar otra dependencia.
- En una paleta tonal M3 el tono 40 no coincide exactamente con `#2E7D32`; para que el verde de
  botones y elementos primarios sea el del favicon se fija el token de sistema con
  `mat.theme-overrides((primary: #2E7D32))`. Se verifica en el CSS compilado que
  `--mat-sys-primary` vale `#2e7d32`.
- `body` usa los tokens del tema (`--mat-sys-surface`, `--mat-sys-on-surface`,
  `--mat-sys-body-large-font`) y `margin: 0`.
- Alternativa descartada: escribir las paletas a mano; el schematic garantiza tonos M3 válidos
  y accesibles.

**Íconos: `mat-icon` + Material Symbols Outlined desde npm.**
`node_modules/material-symbols/outlined.css` se agrega a `angular.json` →
`build.options.styles` antes de `src/styles.scss`; el build copia la fuente `woff2` a `dist/` y
no hay peticiones a Google Fonts. Un `provideAppInitializer` registra
`MatIconRegistry.setDefaultFontSetClass('material-symbols-outlined')`, así que
`<mat-icon>home</mat-icon>` usa Material Symbols sin atributos extra. Solo se importa la variante
Outlined para no empaquetar tres fuentes. Alternativa descartada: Material Icons clásico (fuente
congelada) o SVGs sueltos (otra forma de íconos que la regla de `AGENTS.md` prohíbe).

**Configuración de Material en `core/material/`, expuesta como `proveerMaterial()`.**

| Archivo (`src/app/core/material/`) | Contenido                                                   |
|------------------------------------|-------------------------------------------------------------|
| `proveer-material.ts`              | `proveerMaterial()`: devuelve los providers de abajo        |
| `adaptador-fecha-regional.ts`      | `AdaptadorFechaRegional extends NativeDateAdapter` y        |
|                                    | `FORMATOS_FECHA_REGIONAL` (`MatDateFormats`)                |
| `paginador-intl.ts`                | `PaginadorIntl extends MatPaginatorIntl`, textos en español |
| `calendario-intl.ts`               | `CalendarioIntl extends MatDatepickerIntl`, en español      |
| `proveer-material.spec.ts`         | providers: locale, adapter, formatos, Intl e íconos         |
| `adaptador-fecha-regional.spec.ts` | `parse()` y `format()` por región                           |
| `paginador-intl.spec.ts`           | etiquetas y `getRangeLabel`                                 |
| `calendario-intl.spec.ts`          | etiquetas del calendario                                    |

`proveerMaterial()` devuelve: `{ provide: DateAdapter, useClass: AdaptadorFechaRegional }` y
`{ provide: MAT_DATE_FORMATS, useValue: FORMATOS_FECHA_REGIONAL }` (en lugar de
`provideNativeDateAdapter()`, que registraría el `NativeDateAdapter` sin cambios);
`{ provide: MAT_DATE_LOCALE, useFactory: regionUsuario }` (se evalúa al crear el inyector, con la
misma región que usan los pipes); `{ provide: MatPaginatorIntl, useClass: PaginadorIntl }`;
`{ provide: MatDatepickerIntl, useClass: CalendarioIntl }`; y el `provideAppInitializer` de los
íconos. `app.config.ts` agrega `...proveerMaterial()`. Así toda la configuración de Material vive
en un solo lugar de `core/` (servicios y configuración globales).

- `PaginadorIntl`: `itemsPerPageLabel = 'Elementos por página:'`, `firstPageLabel = 'Primera
  página'`, `previousPageLabel = 'Página anterior'`, `nextPageLabel = 'Página siguiente'`,
  `lastPageLabel = 'Última página'`, y `getRangeLabel(page, pageSize, length)` que devuelve
  `'0 de 0'` sin elementos y `'<inicio> – <fin> de <total>'` en otro caso (con el fin acotado al
  total).
- `CalendarioIntl`: todas las etiquetas de `MatDatepickerIntl` (`openCalendarLabel = 'Abrir
  calendario'`, `prevMonthLabel = 'Mes anterior'`, `nextMonthLabel = 'Mes siguiente'`,
  `switchToMultiYearViewLabel = 'Elegir mes y año'`, año y rango de años anteriores/siguientes,
  `calendarLabel = 'Calendario'`, etc.).

**Fechas escritas a mano: `AdaptadorFechaRegional`, que interpreta y muestra según la región.**
El `NativeDateAdapter` de Material interpreta el texto escrito con `Date.parse`, que no conoce
el orden regional (`05/03/2003` siempre sería 3 de mayo) ni acepta `05-03-2003` como día-mes-año,
y `new Date(2003, 1, 31)` se ajusta solo al 3 de marzo (comprobado). Por eso
`core/material/adaptador-fecha-regional.ts` define `AdaptadorFechaRegional extends
NativeDateAdapter` (`@Injectable()`), que hereda todo lo demás (nombres de meses y días, primer día
de la semana, etc.) y sobrescribe:
- **Orden y separador de la región**: un método privado obtiene, para `this.locale` (que viene de
  `MAT_DATE_LOCALE`, es decir, de `regionUsuario()`), las partes de
  `new Intl.DateTimeFormat(locale, { day: '2-digit', month: '2-digit', year: 'numeric' })
  .formatToParts(new Date(2003, 2, 5))`: el orden de las partes `day`/`month`/`year` (comprobado:
  `es-BO` → día-mes-año, `en-US` → mes-día-año, `ja-JP` → año-mes-día) y el primer literal sin
  espacios como separador, si es `/`, `-` o `.` (en `hu-HU` el literal `". "` queda como `.`);
  si no, usa `/`. Se calcula una vez por locale y se guarda en caché; se recalcula
  si cambia el locale (`setLocale`).
- **`parse(valor)`**: número → `new Date(valor)`; `null`, `undefined` o texto vacío (tras `trim`)
  → `null` (campo vacío, sin error); texto → se separa por `/`, `-` o `.` y debe dar exactamente
  tres grupos de dígitos: día y mes de 1 o 2 dígitos y año de 4 dígitos, asignados según el orden
  de la región. Con esos números se crea la fecha (con `setFullYear` para no reinterpretar años
  menores de 100) y se comprueba que año, mes y día no cambiaron; si cambiaron (`31/02/2003`) o el
  texto no cumple el patrón (`05/03/03`, `05/2003`, `hoy`), devuelve `this.invalid()`, que hace que
  el datepicker marque el error `matDatepickerParse` en vez de ajustar la fecha. Cualquier otro
  tipo de valor también devuelve `this.invalid()`. Se exige año de 4 dígitos porque un año de 2
  (`03`) es ambiguo y `Date` lo interpretaría como 1903.
- **`format(fecha, formato)`**: cuando `formato` es el formato de entrada
  (`FORMATOS_FECHA_REGIONAL.display.dateInput`), arma el texto con día y mes en dos dígitos y año
  en cuatro, en el orden y con el separador de la región (`es-BO` → `05/03/2003`, `es-CL` →
  `05-03-2003`, `en-US` → `03/05/2003`, `ja-JP` → `2003/03/05`, `hu-HU` → `2003.03.05`), de modo
  que lo que se muestra siempre se puede volver a escribir y `parse()` lo entiende. Para los
  demás formatos (etiquetas de mes y año del calendario, textos accesibles) delega en
  `super.format`.
- `FORMATOS_FECHA_REGIONAL` copia `MAT_NATIVE_DATE_FORMATS` y reemplaza `display.dateInput` por
  una constante propia exportada, que es la que `format()` reconoce por identidad.

Alternativa descartada: un adapter de otra librería (Moment, Luxon, date-fns), que agregaría una
dependencia de fechas solo para esto; y fijar un único formato (`dd/MM/yyyy`) para todas las
regiones, que rompería el formato regional que fija `formato-regional`.

**Utilidad de fechas en `shared/fecha/fecha-negocio.ts`: `aFechaNegocio(fecha)`.**
`aFechaNegocio(fecha: Date | null | undefined): string | null` devuelve `null` si no hay fecha;
si la fecha es inválida (`isNaN(fecha.getTime())`) lanza un error, igual que `FechaPipe` ante un
formato irreconocible (es un error de programación, no una entrada del usuario); y en otro caso
arma `${anio}-${mes}-${dia}` con `getFullYear()`, `getMonth() + 1` y `getDate()` (los valores
**locales**, que son los del día que el usuario eligió en el calendario), con mes y día en dos
dígitos (`padStart(2, '0')`). Nunca usa `toISOString()`, `toJSON()` ni métodos `getUTC*`, que
convierten a UTC y corren el día. Va en `shared/` porque la usarán todas las features con
fechas de negocio (transacciones, meses del presupuesto); en una carpeta propia, `fecha/`,
porque convierte valores, no los formatea para mostrar como `formato/`.

**Tests de la utilidad con dos zonas horarias en la misma ejecución.**
`fecha-negocio.spec.ts` tiene un `describe` por zona: `America/New_York` (desfase negativo) y
`Asia/Tokyo` (desfase positivo). Cada uno fija `process.env.TZ` en `beforeEach` y restaura
`'America/New_York'` (el valor de `test-setup.ts`) en `afterEach`, para no afectar a otros tests.
Casos: medianoche y 23:59 del 1 de octubre de 2026 en ambas zonas → `2026-10-01`; 5 de marzo →
`2026-03-05`; `null`/`undefined` → `null`; `Date` inválido → error. Para probar que el test
detecta el bug, en Tokio se verifica además que `toISOString().slice(0, 10)` de esa misma fecha
**sí** da `2026-09-30`; si algún día ese supuesto deja de cumplirse (por ejemplo, porque el cambio
de `TZ` no se aplicara), el test lo avisa en vez de pasar en falso.

**`AGENTS.md`: Angular Material como única librería de UI y estructura obligatoria del
frontend.**
- Nueva sección "Angular Material (única librería de UI)": botones (`mat-button` y variantes),
  inputs y formularios con `mat-form-field` + `matInput` + `mat-error` (los mensajes en español
  de la sección de validación van dentro de `mat-error`), selects, datepicker, diálogos
  (`MatDialog`), tablas (`mat-table` + `mat-paginator`), snackbars e íconos solo con `mat-icon`
  (Material Symbols Outlined). Prohibido agregar otras librerías de componentes (PrimeNG,
  Bootstrap, Tailwind UI, etc.) o de íconos (Font Awesome, SVGs sueltos, etc.). La configuración
  vive en `core/material/`; los colores salen de los tokens `--mat-sys-*`, nunca de hex sueltos
  en los componentes; `AdaptadorFechaRegional` es el único `DateAdapter` (las fechas escritas a
  mano se interpretan según el orden de la región y una fecha inexistente es inválida; nunca se
  registra otro adapter ni se usa `Date.parse` para fechas del usuario); las fechas del
  datepicker se convierten con `aFechaNegocio()` de `shared/fecha/` y nunca con
  `toISOString()`; todo componente de Material nuevo con textos internos se traduce en
  `core/material/` el día que se empieza a usar.
- La sección "Estructura del frontend" pasa a "Estructura obligatoria del frontend", con el árbol
  `src/app/{core,shared,features/<feature>/{pages,components,services,models}}`, qué va en cada
  carpeta y las reglas: toda feature nueva la sigue sin excepciones (como en el backend), solo las
  carpetas que necesite, archivos en kebab-case con el sufijo técnico (`*.page.ts`,
  `*.component.ts`, `*.service.ts`, `*.model.ts`), tests `*.spec.ts` junto al archivo que prueban,
  y `core/` y `shared/` nunca importan nada de `features/` (mismo sentido de dependencias que en el
  backend). `pages/` contiene los componentes enrutables (uno por ruta); `components/`, los
  componentes de presentación de la feature; `services/`, el acceso a la API de la feature con
  `HttpClient`; `models/`, las interfaces que reflejan los DTO de request/response del backend.

**`openspec/config.yaml`: resumen del frontend en el `context` y reglas equivalentes a las del
backend.**
- Al `context` se agrega un párrafo con la estructura obligatoria del frontend (`core/`,
  `shared/`, `features/<feature>/` con `pages`, `components`, `services` y `models`), Angular
  Material como única librería de UI (íconos con `mat-icon`, Material Symbols),
  `AdaptadorFechaRegional` como `DateAdapter` y la conversión de fechas con `aFechaNegocio()`,
  remitiendo a `AGENTS.md`.
- A `rules` se agrega, junto a cada regla existente del backend, su equivalente del frontend
  (las reglas del backend no cambian):
  - `design`: toda feature nueva, y todo change que cree o mueva archivos del frontend, ubica
    cada archivo según la estructura obligatoria del frontend de `AGENTS.md` ("Estructura
    obligatoria del frontend": `core/`, `shared/`, `features/<feature>/` con `pages`,
    `components`, `services` y `models`) e incluye en el design una tabla con la ruta de cada
    archivo nuevo o movido (desde `src/app/`) y la de su test.
  - `tasks`: cada tarea que crea o mueve un archivo del frontend indica su ruta y la de su test,
    según esa misma estructura.
- Se verifica que `openspec instructions design|tasks --json` devuelven las dos reglas de cada
  artefacto (backend y frontend) y el `context` nuevo.

## Risks / Trade-offs

- [La fuente variable de Material Symbols Outlined pesa varios MB] → Se descarga una vez y queda
  en caché del navegador; no cuenta para el presupuesto de bundle inicial de JS/CSS. Si molesta,
  un change futuro puede cambiar a una variante estática de un solo peso.
- [`theme-overrides` fija solo el token `primary`; los tonos derivados (contenedores, estados)
  salen de la paleta generada y pueden diferir levemente del `#2E7D32`] → Es el comportamiento
  previsto de M3; el verde visible en botones y elementos primarios es exacto.
- [`AdaptadorFechaRegional` depende de detalles internos de `NativeDateAdapter` (`this.locale`,
  `invalid()`, `setLocale`), que una versión mayor de Material podría cambiar] → Son parte de la
  API pública de `DateAdapter`; `adaptador-fecha-regional.spec.ts` y `proveer-material.spec.ts`
  fallan si una actualización los rompe.
- [Con un año de dos dígitos (`05/03/03`) el campo queda inválido en vez de adivinar el siglo] →
  Es intencional: evita interpretar 1903 o 2003 por error; el usuario corrige o usa el
  calendario.
- [En regiones cuyo separador, sin espacios, no es `/`, `-` ni `.`, el campo muestra `/` en vez
  del separador nativo; y en `hu-HU` muestra `2003.03.05` sin los espacios ni el punto final de
  `2003. 03. 05.`] → Garantiza que lo mostrado se pueda volver a escribir; el orden
  de día, mes y año sigue siendo el de la región.
- [Cambiar `process.env.TZ` dentro de un test depende de que Node lo aplique en el momento] →
  Comprobado con Node 22; el test de control con `toISOString()` falla si dejara de cumplirse.
- [`MAT_DATE_LOCALE` se calcula una vez al arrancar] → Igual que los pipes: cambiar el idioma
  del navegador requiere recargar la página.

## Migration Plan

No aplica: no hay datos ni API. Rollback: revertir el commit y `npm install` para quitar las
dependencias.
