# Tasks

## 1. Línea base

- [x] 1.1 En `frontend`, ejecutar `npx ng build` y `npx ng test --watch=false` antes de cambiar
      nada; verificar que el build termina sin errores y anotar el número de tests que pasan
      (para compararlo al final).

## 2. Dependencias, tema e íconos

- [x] 2.1 Instalar con `npm install` `@angular/material@^21.2.14`, `@angular/cdk@^21.2.14` y
      `material-symbols@^0.47.6` (sin `ng add`); verificar con `npm ls @angular/material
      @angular/cdk material-symbols` que quedan instalados sin conflictos de peer dependencies,
      que `src/index.html` no cambió y que `npx ng build` sigue terminando sin errores.
- [x] 2.2 Generar `src/_theme-colors.scss` con
      `npx ng generate @angular/material:theme-color` usando `#2E7D32` como color primario; en
      `src/styles.scss` aplicar `mat.theme(...)` en `html` (paletas generadas, `theme-type:
      light`, `density: 0`, tipografía con la pila de fuentes del sistema de `design.md`),
      `mat.theme-overrides((primary: #2E7D32))`, `color-scheme: light` y los estilos de `body`
      con tokens `--mat-sys-*`; verificar que `npx ng build` termina sin errores y que el CSS
      de `dist/` contiene `--mat-sys-primary` con el valor `#2e7d32`.
- [x] 2.3 Agregar `node_modules/material-symbols/outlined.css` a `angular.json` →
      `build.options.styles` antes de `src/styles.scss`; verificar con `npx ng build` que `dist/`
      contiene la fuente `woff2` de Material Symbols Outlined y que ni `dist/` ni `src/`
      contienen `fonts.googleapis.com` ni `fonts.gstatic.com`.

## 3. Configuración de Material en `core/material/`

- [x] 3.1 Crear `src/app/core/material/paginador-intl.ts` (`PaginadorIntl extends
      MatPaginatorIntl`, `@Injectable`) con las etiquetas en español de `design.md` y
      `getRangeLabel`; verificar con `paginador-intl.spec.ts` los escenarios de
      `specs/idioma-interfaz/spec.md`: `getRangeLabel(1, 10, 25)` → `11 – 20 de 25`,
      `getRangeLabel(0, 10, 0)` → `0 de 0`, la última página acotada al total
      (`getRangeLabel(2, 10, 25)` → `21 – 25 de 25`) y las cinco etiquetas.
- [x] 3.2 Crear `src/app/core/material/calendario-intl.ts` (`CalendarioIntl extends
      MatDatepickerIntl`, `@Injectable`) con todas sus etiquetas en español; verificar con
      `calendario-intl.spec.ts` que `openCalendarLabel`, `prevMonthLabel`, `nextMonthLabel` y
      `switchToMultiYearViewLabel` son los de `specs/idioma-interfaz/spec.md` y que ninguna
      etiqueta de tipo `string` de la instancia queda igual a la de `MatDatepickerIntl` en inglés.
- [x] 3.3 Crear `src/app/core/material/adaptador-fecha-regional.ts` con
      `AdaptadorFechaRegional extends NativeDateAdapter` (`@Injectable()`) y
      `FORMATOS_FECHA_REGIONAL` según `design.md` (orden y separador de la región con
      `Intl.DateTimeFormat(...).formatToParts`, en caché por locale; `parse()` con `/`, `-` y
      `.`, año de 4 dígitos y `this.invalid()` para fechas inexistentes o texto que no cumple el
      patrón; `format()` en el orden de la región para el formato de entrada y `super.format`
      para el resto); verificar con `adaptador-fecha-regional.spec.ts` (TestBed con el adapter,
      `FORMATOS_FECHA_REGIONAL` y `MAT_DATE_LOCALE` fijado en cada caso) los escenarios de
      `specs/formato-regional/spec.md`: en `es-BO` `05/03/2003`, `05-03-2003` y `05.03.2003`
      → 5 de marzo de 2003; en `en-US` `05/03/2003` → 3 de mayo de 2003; en `es-BO`
      `31/02/2003`, `05/03/03`, `05/2003` y `hoy` → fecha inválida
      (`isValid(...)` falso, nunca el 3 de marzo); vacío y `null` → `null`; `format` del 5 de
      marzo de 2003 → `05/03/2003` en `es-BO`, `05-03-2003` en `es-CL`, `03/05/2003` en
      `en-US`, `2003/03/05` en `ja-JP` y `2003.03.05` en `hu-HU`; y que en cada una de esas regiones
      `parse(format(fecha))` devuelve la misma fecha.
- [x] 3.4 Crear `src/app/core/material/proveer-material.ts` con `proveerMaterial()`
      (`DateAdapter` → `AdaptadorFechaRegional`, `MAT_DATE_FORMATS` →
      `FORMATOS_FECHA_REGIONAL`, `MAT_DATE_LOCALE` con `useFactory: regionUsuario`,
      `PaginadorIntl`, `CalendarioIntl` y el `provideAppInitializer` que fija
      `material-symbols-outlined` como clase de fuente por defecto de `MatIconRegistry`) y
      agregarlo a `app.config.ts`; verificar con `proveer-material.spec.ts` (TestBed con
      `proveerMaterial()` y `navigator.language` simulado) que con `es-CL` `MAT_DATE_LOCALE` es
      `es-CL`, el `DateAdapter` inyectado es un `AdaptadorFechaRegional`, formatea el 1 de
      octubre de 2026 como `01-10-2026` con `MAT_DATE_FORMATS.display.dateInput` y el mes 10 se
      llama `octubre`; que con `en-US` lo formatea como `10/01/2026`; que `MatPaginatorIntl` y
      `MatDatepickerIntl` son instancias de `PaginadorIntl` y `CalendarioIntl`; y que
      `MatIconRegistry.getDefaultFontSetClass()` contiene `material-symbols-outlined`.

## 4. Conversión de fechas en `shared/fecha/`

- [x] 4.1 Crear `src/app/shared/fecha/fecha-negocio.ts` con
      `aFechaNegocio(fecha: Date | null | undefined): string | null` según `design.md` (valores
      locales, mes y día en dos dígitos, `null` sin fecha, error con `Date` inválido, sin
      `toISOString()` ni `getUTC*`); verificar con `fecha-negocio.spec.ts`, con un `describe`
      para `America/New_York` y otro para `Asia/Tokyo` que fijan `process.env.TZ` en
      `beforeEach` y restauran `America/New_York` en `afterEach`, los escenarios de
      `specs/formato-regional/spec.md` (medianoche y 23:59 del 1 de octubre de 2026 en ambas
      zonas → `2026-10-01`, 5 de marzo → `2026-03-05`, `null`/`undefined` → `null`), que un
      `Date` inválido lanza un error, y el control de que en `Asia/Tokyo`
      `toISOString().slice(0, 10)` de la medianoche del 1 de octubre da `2026-09-30`.

## 5. Documentación y planificación

- [x] 5.1 Agregar a `AGENTS.md` la sección "Angular Material (única librería de UI)" con todo lo
      descrito en `design.md` (componentes a usar, `mat-form-field` + `mat-error` para los
      mensajes de validación, `mat-icon` con Material Symbols Outlined, prohibición de otras
      librerías de componentes o íconos, tokens `--mat-sys-*`, configuración en
      `core/material/`, traducción de componentes nuevos, `AdaptadorFechaRegional` como único
      `DateAdapter` —fechas escritas según el orden de la región, inexistentes como inválidas— y
      conversión al backend con `aFechaNegocio()`); verificar que cubre cada punto y que ninguna
      línea supera 100 columnas.
- [x] 5.2 Reemplazar en `AGENTS.md` la sección "Estructura del frontend" por "Estructura
      obligatoria del frontend" con el árbol `core/`, `shared/`,
      `features/<feature>/{pages,components,services,models}`, qué va en cada carpeta, la
      declaración de que toda feature nueva la sigue sin excepciones, las convenciones de
      nombres de archivo, la ubicación de los tests y la regla de que `core/` y `shared/` no
      importan `features/`, conservando los párrafos de standalone + signals y de la URL
      relativa de la API; verificar que el árbol coincide con `src/app` (incluidos
      `core/material/` y `shared/fecha/`) y que ninguna línea supera 100 columnas.
- [x] 5.3 En `openspec/config.yaml`, agregar al `context` el resumen del frontend de
      `design.md` y a `rules` de `design` y `tasks` las reglas equivalentes del frontend (ruta
      de cada archivo según la estructura obligatoria del frontend de `AGENTS.md`, y en el
      design una tabla con la ruta de cada archivo y la de su test), sin tocar las reglas del
      backend y sin superar 100 columnas; verificar que
      `openspec instructions design --change angular-material --json` y
      `openspec instructions tasks --change angular-material --json` devuelven el `context` con el
      párrafo nuevo y, cada uno, sus dos reglas (backend y frontend), y que
      `openspec validate angular-material --strict` es válido.

## 6. Verificación integral

- [x] 6.1 En `frontend`, ejecutar `npx ng build` y `npx ng test --watch=false` y verificar que
      el build termina sin errores y que pasan los tests de la 1.1 más los nuevos de
      `core/material/` (incluido el adaptador de fechas) y `shared/fecha/`; ejecutar
      `npx prettier --check` sobre los archivos creados o modificados en `src/`; verificar que
      ningún archivo de `src/app/core` ni
      `src/app/shared` importa de `features/`; ejecutar
      `openspec validate angular-material --strict`.
- [x] 6.2 Levantar `npx ng serve` y, con `curl`, verificar que la página y su CSS se sirven, que
      el CSS incluye la regla `@font-face` de Material Symbols con una URL local (no de Google)
      y el token `--mat-sys-primary: #2e7d32`; detener el servidor.
