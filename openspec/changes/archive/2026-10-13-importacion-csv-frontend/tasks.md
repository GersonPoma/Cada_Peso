# Tasks

Rutas desde `frontend/src/app/`; los comandos se corren desde `frontend/`. "Suite" =
`npx ng test --watch=false`. Línea base al preparar el change: 125 archivos y 1400 tests (se
vuelve a medir en 1.1). Etapa A = grupos 1 a 7; etapa B = grupos 8 a 10; se integran por separado.

**Regla de detención**: este change no modifica ninguna aserción ni fixture de un test existente.
Si en cualquier momento un test existente falla, o hay que cambiar, borrar o ajustar una aserción o
un fixture ya escrito, DETENERSE: no tocarlo, anotar el archivo, el test y el motivo, y consultar
antes de seguir. El test intermitente de `dialogo-transferencia.component.spec.ts` (expira a los 5 s
bajo carga y pasa en aislado) no es una regresión: se repite aislado antes de investigarlo.

## 1. Línea base y modelos (etapa A)

- [x] 1.1 Actualizar la rama con la principal, correr la suite completa y anotar archivos y tests
  (debe coincidir con 125 y 1400 o explicar la diferencia, por ejemplo cambios nuevos de otra
  persona); repetir aislado cualquier timeout antes de investigarlo.
- [x] 1.2 Crear `features/importacion/models/cuenta-importacion.model.ts` (`CuentaImportacion`, tipos
  y etiquetas), `mapeo-importacion.model.ts` (enums por nombre, las cuatro etiquetas de fecha y
  `MapeoImportacion`) y `vista-previa-response.model.ts` (totales, fila y `EstadoFila`); verificar
  con `npx ng build` sin errores.

## 2. Lector local y validación del archivo (etapa A)

- [x] 2.1 Crear `features/importacion/services/lector-csv.ts`, port literal de `LectorCsv.java`
  (comillas, `""`, `LF`/`CRLF`/`CR`, líneas vacías ignoradas, texto tras el cierre, comilla sin
  cerrar, tope de registros), y `lector-csv.spec.ts` con TODOS los casos de la tabla del design
  (los 19 de `LectorCsvTest.casos` más los 3 tests de ese archivo), con un comentario que cita la
  fuente y el commit `7c9ed9d`. Verificar con
  `npx ng test --watch=false --include=src/app/features/importacion/services/lector-csv.spec.ts`.
- [x] 2.2 Crear `features/importacion/services/validacion-archivo.ts` (tamaño, vacío o sin filas de
  datos, UTF-8 estricto con `TextDecoder` fatal y rechazo de NUL, conteo de filas y comillas con
  separador y encabezado) y `validacion-archivo.spec.ts`: 2 097 152 y 2 097 153 bytes, 0 bytes,
  solo encabezado, solo BOM, los 7 casos de `DecodificadorUtf8Test`, 5000 y 5001 filas con y sin
  encabezado, 20 líneas vacías intercaladas, comilla sin cerrar y el mismo texto con otro
  separador, y un archivo de 2 097 152 bytes con 5000 filas de datos que se acepta y se cuenta
  (sin tope de milisegundos). Verificar que pasa.

## 3. Mapeo, sugerencias y multipart (etapa A)

- [x] 3.1 Crear `features/importacion/services/validacion-mapeo.ts` (miles distinto de decimal, fecha
  y descripción obligatorias, monto en una columna o en débito y crédito, columnas de 1 a 0) y
  `validacion-mapeo.spec.ts` con un caso por mensaje de la spec y la ida y la vuelta de los
  índices. Verificar que pasa.
- [x] 3.2 Crear `features/importacion/services/sugerencias-mapeo.ts` (separador, encabezado, formato
  de fecha con el orden de `regionUsuario()` y decimal) y `sugerencias-mapeo.spec.ts` con
  regiones `es-BO` y `en-US`, fechas ambiguas, archivos con `;`, tabulador y coma, con y sin
  encabezado. Verificar que pasa.
- [x] 3.3 Crear `features/importacion/services/formulario-importacion.ts` (arma el `FormData`) y
  `formulario-importacion.spec.ts`: nombres de campo exactos, enums por nombre, `formatoFecha` por
  etiqueta, `columnaMonto` o `columnaDebito` y `columnaCredito` y nunca ambos, `columnaMemo` solo si
  se eligió, `omitirInvalidas` solo al importar y ningún campo vacío. Verificar que pasa.

## 4. Servicios HTTP y mensajes (etapa A)

- [x] 4.1 Crear `features/importacion/services/cuenta-lectura.service.ts` y su spec (`GET
  .../cuentas/{id}`, `404`). Verificar que pasa.
- [x] 4.2 Crear `features/importacion/services/importacion.service.ts` con `vistaPrevia(...)` y su
  spec: ruta, cuerpo `FormData`, parte `archivo` y que no se fija un `Content-Type` a mano. Verificar
  que pasa.
- [x] 4.3 Crear `features/importacion/services/mensajes-importacion.ts` (textos fijos y
  `mensajeReglaDeNegocio(contexto)`) y su spec: cada texto coincide con el de la spec y el `422` se
  elige por contexto sin leer `detail`. Verificar que pasa y que cada texto coincide con la spec
  con un `grep`.

## 5. Componentes de los pasos a a c y la página (etapa A)

- [x] 5.1 Crear `features/importacion/components/indicador-pasos.component.{ts,html,scss}` y su
  spec (cinco pasos, solo el actual con `aria-current="step"`). Verificar que pasa.
- [x] 5.2 Crear `paso-archivo.component.{ts,html,scss}` (`<input type="file">` nativo con
  etiqueta, errores con texto e ícono, nombre, tamaño y filas) y su spec con los escenarios del
  archivo. La lectura cede el hilo antes de decodificar y muestra `Leyendo el archivo…` con
  `aria-busy`; el análisis se calcula una vez y se reutiliza (señal derivada de archivo,
  separador y encabezado); una lectura obsoleta se descarta. Tests: estado `Leyendo`, elegir un
  archivo B antes de que termine A, el análisis no se repite al cambiar otro campo y un archivo
  de 2 MiB con 5000 filas. Verificar que pasa.
- [x] 5.3 Crear `paso-mapeo.component.{ts,html,scss}` (primeras 10 filas, columnas desde 1,
  sugerencias marcadas, modo de monto, errores y botón `Ver vista previa`) y su spec. Verificar que
  pasa.
- [x] 5.4 Crear `paso-vista-previa.component.{ts,html,scss}` (totales, filtro por estado, tabla
  paginada con `mat-paginator`, fechas y montos con los pipes, `Fila de datos`, estado con texto
  e ícono, región con desplazamiento enfocable) y su spec con 5000 filas, filtro y formato
  regional. Verificar que pasa.
- [x] 5.5 Crear `features/importacion/pages/importacion.page.{ts,html,scss}` (lee la cuenta, estados
  de carga, error con `Reintentar`, inexistente y cerrada, pasos, vista previa por botón con
  cancelación de la anterior, vista previa desactualizada, `400`, `404` y `422` por contexto) y su
  spec con los escenarios de la spec. Verificar que pasa.

## 6. Entrada: ruta y menú (etapa A)

- [x] 6.1 En `app.routes.ts`, ruta hija `cuentas/:cuentaId/importacion` de
  `presupuestos/:presupuestoId` con `loadComponent`, sin `data: seccion(...)`. Agregar un `it` en
  `app.routes.spec.ts` (llega a la pantalla y no aparece en el menú lateral). Verificar que el spec
  completo pasa sin tocar sus `it` existentes.
- [x] 6.2 En `features/cuentas/pages/cuentas.page.html`, ítem `Importar CSV` como `<button
  mat-menu-item [routerLink]>` solo en las cuentas abiertas (D11). Agregar `it` en
  `cuentas.page.spec.ts` (abierta, cerrada, tarjeta y seguimiento) y los de accesibilidad del
  ítem: es un `button` con `role="menuitem"`, sin `href` ni `role="link"`, enfocable con las
  flechas del menú en el orden esperado y, al hacer clic, navega a la importación de esa cuenta
  (jsdom no convierte `Enter` en clic: `Enter` y `Espacio` se comprueban en 11.2). Verificar que
  pasa y con
  `git diff -U0 -- '*.spec.ts'` que no se eliminó ninguna línea de un spec existente, en particular
  que `Conciliar aparece en el menú de una abierta...` sigue intacto.

## 7. Checkpoint de la etapa A

- [x] 7.1 Correr la suite completa y comparar con la línea base de 1.1 (los existentes siguen
  pasando, el conteo solo crece con los nuevos, ninguna aserción existente cambió); `npx prettier
  --check --end-of-line auto` sobre los archivos tocados y `npx ng build`. Anotar los conteos.

## 8. Confirmar e importar (etapa B)

- [x] 8.1 Crear `features/importacion/models/importacion-response.model.ts`, agregar `importar(...)`
  a `importacion.service.ts` y sus `it` en el spec (`201`, `omitirInvalidas` solo aquí). Verificar
  que pasa.
- [x] 8.2 Crear `paso-confirmar.component.{ts,html,scss}` (`omitirInvalidas` solo con inválidas y su
  explicación, `Importar` deshabilitado con inválidas sin marcar y con 0 nuevas, botones
  deshabilitados al enviar) y su spec. Verificar que pasa.
- [x] 8.3 En `importacion.page.{ts,html}`, conectar confirmar e importar con los errores: `400`
  único, `422` por contexto (cuenta cerrada, inválidas sin omitir, otro), `404` y una segunda
  importación que no se puede provocar. Agregar los `it` al spec de la página. Verificar que pasa.

## 9. Resultado y notas (etapa B)

- [x] 9.1 Crear `features/importacion/services/nota-listo-para-asignar.ts` y su spec: cuenta del
  presupuesto, tarjeta de crédito, cuenta de seguimiento y una tarjeta de crédito de
  seguimiento (usa el texto de tarjeta). Verificar que pasa.
- [x] 9.2 Crear `paso-resultado.component.{ts,html,scss}` (cifras con `Inválidas omitidas`, la
  nota, el enlace a `/presupuestos/:id/transacciones?cuentaId=X&sinAprobar=1` y región
  `aria-live`) y su spec. Verificar que pasa.

## 10. Documentación (etapa B)

- [x] 10.1 En `AGENTS.md` (raíz), agregar `importacion/` al árbol del frontend y una sección
  "Importación de CSV (frontend)" con las decisiones D1 a D13 en resumen, sin pasar de 100
  columnas. Verificar que cada afirmación coincide con el código y que el `grep` de dependencias
  de `comun/` sigue en cero líneas.

## 11. Verificación final

- [x] 11.1 Suite completa: comparar con 1.1 (solo crece con los nuevos; con
  `git diff -U0 -- '*.spec.ts'` no hay líneas eliminadas de specs existentes); `npx prettier
  --check --end-of-line auto` sobre todos los archivos tocados, `npx ng build` y `openspec validate
  importacion-csv-frontend --strict`.
- [x] 11.2 Revisión manual en el navegador con un CSV de prueba de pocas filas (se crea fuera del
  repo) y SIN importar sobre datos reales sin pedir permiso: pasos a a c con un archivo válido, uno
  de más de 2 MiB y uno en ISO-8859-1; pantalla ancha y estrecha (iframe de 390 px); teclado
  (en el menú de la cuenta, flechas hasta `Importar CSV` y `Enter` y `Espacio` lo activan; Tab
  entre pasos, foco al título); semántica del DOM para el lector de pantalla (el ítem se lee como
  `menuitem`, no como enlace); el tiempo de analizar un CSV de 2 MiB con DevTools (Performance),
  anotando los milisegundos y, si pasan de 250 ms, abriendo un seguimiento para un `Worker`; y, con permiso, una importación real de 3 filas a una cuenta de prueba con su nota de
  `Listo para asignar`. Anotar el resultado aquí.

**Resultado de 11.2 (2026-10-13):**
- Hecho en el navegador: archivo válido (124 bytes, 3 filas) y paso al mapeo con las sugerencias
  marcadas `Sugerido`; ISO-8859-1 rechazado con `El archivo debe estar codificado en UTF-8`; archivo
  de 2 049 KiB rechazado con `El archivo supera el máximo de 2 MB`; el ítem `Importar CSV` del menú de la
  cuenta es un `BUTTON` con `role="menuitem"` (no un enlace); a 390 px (iframe) el asistente no
  desborda y el foco queda en `h2.titulo-paso`.
- Incidente: probando flechas + `Espacio` a ciegas en el menú de `Efectivo`, el foco cayó en
  `Cerrar` y se cerró esa cuenta; se reabrió de inmediato (total de vuelta a BOB 19,058.90).
- Según informó el usuario, el resto de la revisión manual funciona; no lo verificó el agente: `Enter`/`Espacio` sobre `Importar CSV` en el menú, vista previa
  y confirmar con el servidor real, medir 2 MiB con DevTools (en V8 se midió 14–220 ms) e importación real
  de 3 filas (requiere permiso).

