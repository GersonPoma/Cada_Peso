# Design

## Context

Ver proposal.md - Why. Estado observado leyendo el código y la spec archivada `importacion-csv`
(todo bajo `backend/src/main/java/com/presupuesto/importacion` y `frontend/src/app`):

**API confirmada** (`ImportacionController`, `ParametrosImportacion`, DTO de respuesta):
`POST .../cuentas/{cuentaId}/importacion/vista-previa` (200) y `POST .../importacion` (201), ambos
`multipart/form-data` con la parte `archivo` y los parámetros como campos de texto, que el
service valida (no Bean Validation) para que el orden de errores sea 404, 400 y 422.
`separador`, `separadorDecimal` y `separadorMiles` viajan por el NOMBRE del enum; `formatoFecha`
por su ETIQUETA (`yyyy-MM-dd`, `dd/MM/yyyy`, `MM/dd/yyyy`, `dd-MM-yyyy`). Respuesta de la vista
previa: `{ totales: {total, nuevas, duplicadas, invalidas, sinCategoria}, filas: [{fila, fecha,
monto, beneficiario, memo, estado, motivo}] }` (todas las filas, hasta 5000; en las `INVALIDA`
todo es nulo salvo `motivo`, un texto fijo). Respuesta de importar: `{importadas, duplicadas,
omitidas, sinCategoria}` (`omitidas` son las inválidas omitidas).

**Hallazgos que cambian el diseño** (no estaban en el pedido o lo matizan):

| # | Hallazgo | Consecuencia |
|---|---|---|
| 1 | Tamaño (`ImportacionService` y `MaxUploadSizeExceededException`), vacío, no UTF-8, más de 5000 filas, comilla sin cerrar y mapeo inválido responden **todos** `400 DATOS_INVALIDOS` sin mapa `errores` | El frontend no puede distinguirlos por `codigo` (y no lee `detail`): se previenen en el cliente y un `400` muestra un único mensaje fijo (D1) |
| 2 | `422 REGLA_NEGOCIO_VIOLADA` sirve para cuenta cerrada y para filas inválidas sin `omitirInvalidas` | Se resuelve por contexto (D9) |
| 3 | `fila` cuenta registros de datos desde 1: sin encabezado, sin líneas vacías, una celda multilínea es un registro | La columna se llama `Fila de datos`, no es el número de línea (D12) |
| 4 | Un archivo con solo encabezado o con solo el BOM responde `201` en 0 / vista previa vacía | El cliente es **más estricto** a propósito: nada que importar (D3) |
| 5 | Con el change de tarjetas, una entrada sin categoría solo sube `Listo para asignar` en una cuenta del presupuesto que no es tarjeta | La nota depende de `tipo` y `enPresupuesto` (D8) |
| 6 | En el frontend no hay `MatStepper`, ni `<input type="file">`, ni `FormData`; `core/material` traduce `MatPaginator` y el calendario | Pasos propios; archivo nativo (D4) |
| 7 | `cuentas.page.spec.ts` (línea 337) afirma que el menú de `Banco` tiene **exactamente un enlace** (`Conciliar`) | El ítem nuevo no puede ser un enlace (D11) |
| 8 | AGENTS.md prohíbe importar servicios o modelos de otra feature; conciliación y transacciones ya duplican `CuentaLecturaService` | La feature trae el suyo (D10) |
| 9 | jsdom 28 trae `File.arrayBuffer()`, `TextDecoder` y `FormData` | Los tests del lector y del multipart no necesitan polyfills |
| 10 | Analizar 2 MiB en el hilo principal toma de unos 14 ms a unos 220 ms según la forma del archivo (medido en V8) | Hilo principal con estado `Leyendo`, análisis calculado una vez y lectura obsoleta descartada (D3) |
| 11 | `Enter` sobre un botón dispara el clic en un navegador real, pero jsdom no lo simula | El test de teclado del ítem comprueba rol, foco y orden con las flechas; `Enter` y `Espacio` se comprueban en la revisión manual (D11) |

Línea base de tests al preparar el change: 125 archivos y 1400 tests, todos pasando.

## Goals / Non-Goals

**Goals:**
- Que ninguna causa evitable de `400` llegue al servidor, y que las que llegan se expliquen sin
  leer `detail` ni reflejar contenido del archivo.
- Un lector local que cuente igual que el servidor; donde difiere a propósito, que esté escrito.
- Tocar solo dos archivos compartidos (`app.routes.ts`, `cuentas.page.html`) más AGENTS.md.

**Non-Goals:**
- Guardar mapeos, OFX, XLSX, otras codificaciones, deshacer, categorizar automáticamente.
- Interpretar montos en el cliente: la lectura local muestra las celdas como texto y el servidor
  es la autoridad.
- Elegir cuenta dentro del asistente: la cuenta viene en la ruta.

## Decisions

### D1. Prevenir los 400 en el cliente y un único mensaje para los que lleguen
Cada causa evitable tiene su validación y su mensaje fijo (ver la spec): tamaño (2 097 152 bytes
exactos), vacío o sin filas de datos, UTF-8 estricto y NUL, más de 5000 filas y comilla sin
cerrar, y las reglas del mapeo (miles distinto de decimal, fecha y descripción obligatorias, monto
en una columna **o** débito y crédito). Las columnas se eligen entre las detectadas, así que una
columna fuera de rango no puede enviarse (el servidor solo la trataría como fila inválida). Si
igual llega un `400`, se muestra UN mensaje fijo con las correcciones posibles. *Alternativa*:
leer `detail`; descartada (AGENTS.md y el patrón de todo el frontend lo prohíben, y reflejaría
texto del servidor).

### D2. El lector local es un port literal de `LectorCsv`, con sus casos
`services/lector-csv.ts` reimplementa la misma máquina de estados que `LectorCsv.java` (no una
expresión regular ni una librería, que se comportarían distinto en los bordes): una comilla solo
abre un campo si el campo está vacío y no fue ya comillado; `""` dentro de comillas es una comilla;
`LF`, `CRLF` y `CR` terminan el registro; las líneas completamente vacías se ignoran; texto tras
una comilla de cierre se concatena (`"ab"cd` da `abcd`); una comilla sin cerrar es un error; se
corta al superar el máximo de registros. Sus tests de tabla **copian los casos del backend**
(fuente: `LectorCsvTest` y `DecodificadorUtf8Test`, commit `7c9ed9d`, 2026-10-08; ver la tabla de
casos más abajo). Si el backend cambia la regla, se actualizan juntos. La vista previa del
servidor sigue siendo la autoridad y el paso lo dice. *Alternativa*: una librería de CSV (PapaParse);
descartada: agrega una dependencia y difiere del servidor en bordes (líneas vacías, texto tras
las comillas).

### D3. Lectura completa para validar, diez filas para mostrar; archivo solo en memoria
Al elegir el archivo se lee completo (`File.arrayBuffer()`, máximo 2 MiB) para validar tamaño,
UTF-8 con `TextDecoder('utf-8', { fatal: true })` (que ya descarta el BOM) más el rechazo de NUL,
y contar filas. El paso del mapeo muestra solo las primeras 10. El `File` vive en una señal de la
página y se descarta al salir: no se guarda en `localStorage`, `sessionStorage` ni IndexedDB, y no
se envía nada hasta pulsar `Ver vista previa`. **Más estricto que el servidor a propósito**: un
archivo con solo encabezado o solo BOM se rechaza en el cliente (el servidor respondería `201` en
0), porque no hay nada que importar. La spec lo declara (requisito "El cliente es más estricto que
el servidor en dos casos").

**Dónde se lee y se cuenta, y qué se espera con 2 MiB.** Leer el `File` (`arrayBuffer()`) es
asíncrono y no bloquea el hilo; decodificar, analizar y contar filas corren en el **hilo
principal**, sin `Worker`. Medido en V8 (Node) con un port simple del lector, sobre 2 MiB: 5000
filas de unos 420 bytes, mediana 87 ms (peor de 15, 154 ms); una sola celda entre comillas de 2
MiB, mediana 157 ms (peor, 220 ms); filas cortas, que cortan en la fila 5001, unos 14 ms. Un
extracto típico (cientos de filas) queda por debajo de 10 ms. Se acepta porque el costo es único
por archivo y por cambio de separador o de encabezado, nunca por tecla. Para que se mantenga:
(a) el análisis se calcula una vez y se reutiliza (señal derivada de archivo, separador y
encabezado), sin repetirlo al editar otros campos del mapeo; (b) antes de decodificar se cede el
hilo (`setTimeout(0)`) para que se pinte `Leyendo el archivo…`; (c) si mientras tanto se elige otro
archivo o se cambia el separador, la lectura anterior se descarta; (d) el lector puede acumular
campos por rangos en vez de carácter a carácter si eso mejora el tiempo, siempre que pasen los
casos de la tabla. Sin tope en milisegundos en los tests (serían intermitentes): se mide en la
revisión manual (11.2) y, si un archivo de 2 MiB tarda más de 250 ms en analizarse, se abre un
seguimiento para pasarlo a un `Worker`.

### D4. Pasos propios con señales, sin `MatStepper`
`MatStepper` trae textos internos (etiquetas de "opcional", "editar") que AGENTS.md obliga a
traducir en `core/material`, y menos control sobre el foco. La página guarda `paso` y los datos de
cada paso en señales; un `app-indicador-pasos` presenta los cinco pasos con `aria-current="step"`
y la página mueve el foco al título del paso al cambiar. *Alternativa*: `MatStepper` más un
`StepperIntl`; descartada por tocar un archivo compartido más.

### D5. Vista previa por botón y respuestas atrasadas
`Ver vista previa` lanza la petición con `switchMap` sobre un `Subject` (cancela la anterior) y
guarda además la "versión del mapeo" con la que se pidió. Editar el mapeo mientras la petición viaja
la **cancela** y vuelve a habilitar el botón (por eso no hay dos peticiones a la vez); editarlo
después de una vista previa la marca desactualizada y `Importar` no se ofrece. El botón se
deshabilita mientras viaja. *Alternativa*: recalcular en vivo con debounce; descartada porque
subiría hasta 2 MiB por cada cambio de un campo.

### D6. Sugerencias editables
`services/sugerencias-mapeo.ts` propone, solo desde las primeras líneas del archivo: el separador
(el candidato cuyo conteo por línea es constante y mayor que 0), el encabezado (la primera fila no
tiene ninguna celda que parezca fecha o número), el formato de fecha (los candidatos que leen
todas las fechas de la columna probable; si más de uno encaja, gana el orden de la región con
`regionUsuario()`) y el separador decimal (el de la región, salvo que el patrón de las celdas
indique otro). Los controles se precargan marcados como `Sugerido` y siguen siendo editables; nada
se envía.

### D7. Tabla paginada en el cliente
La respuesta trae todas las filas. La página guarda la lista, el filtro de estado y el estado del
paginador en señales; una señal derivada recorta la página visible. `mat-paginator` (ya traducido
por `PaginadorIntl`) con tamaños 10, 25, 50 y 100 y 25 por defecto; el filtro es un
`mat-button-toggle-group`. La tabla es un `mat-table` dentro de una región con nombre, `tabindex="0"`
y desplazamiento propio, de modo que a 390 px la página no se desborda.

### D8. La nota de "Listo para asignar" según la cuenta
Función pura `notaListoParaAsignar(cuenta)`: si `tipo` es tarjeta, el texto de tarjeta; si no está
`enPresupuesto`, el de seguimiento; si no, el de una cuenta del presupuesto. Los tres son fijos.
El tipo y `enPresupuesto` salen de la lectura de la cuenta que la página ya hizo.

### D9. Errores y el `422` por contexto
`mensajeReglaDeNegocio(contexto)` elige, sin leer `detail`: cuenta cerrada, o filas inválidas en la
vista previa sin `omitirInvalidas`, o un mensaje genérico de regla. Como el asistente solo arranca
con la cuenta abierta, ante un `422` la página **vuelve a leer la cuenta** para saber si se cerró en
otra sesión (sin ocultar el asistente). `400` siempre el
mensaje único de D1; `404` un aviso fijo y volver a pedir la cuenta; `401` lo maneja el
interceptor; cualquier otro, el aviso genérico. Ningún aviso incluye texto del archivo (la tabla
sí muestra beneficiario y memo, que es lo que la persona está importando).

### D10. Servicio propio de lectura de cuentas
`features/importacion/services/cuenta-lectura.service.ts` (solo GET) con su modelo mínimo (id,
nombre, tipo, enPresupuesto, cerrada) y su mapa de etiquetas de tipo, como conciliación. Es la
duplicación pequeña y a propósito que pide AGENTS.md.

### D11. `Importar CSV` es un botón con `routerLink`, no un enlace
`<button mat-menu-item type="button" [routerLink]="[...]">`. El test existente `Conciliar aparece
en el menú de una abierta...` lista `a[mat-menu-item]` y espera **exactamente** `Conciliar`; un
enlace nuevo en el menú de `Banco` lo rompería, y la regla de este change es no cambiar
aserciones existentes. `routerLink` funciona en un botón y el comportamiento observable es el
mismo (navega al pulsar), salvo abrir en pestaña nueva. **Cómo se anuncia y se opera**: `mat-menu-item` le pone `role="menuitem"`, así que un lector
de pantalla lo anuncia como ítem de menú (no como enlace: no tiene `href` ni `role="link"`), es
enfocable con las flechas del menú y, al ser un botón nativo, se activa con `Enter` y con
`Espacio` (un `<a>` no se activa con `Espacio`). *Alternativa*: un `<a>` y actualizar esa
única aserción (precedente: el change de tarjetas declaró una excepción igual); descartada hasta
que se pida, y de elegirse hay que declararla antes de implementar.

### D12. Numeración y columnas
La columna del número de fila se titula `Fila de datos`. Las columnas se muestran como `Columna
1..n` y se envían restando 1; un test cubre la ida y la vuelta.

### D13. Dos etapas
**A** (pasos a a c): modelos, servicios, lector, validaciones, sugerencias, mapeo, vista previa,
página, ruta y menú. Termina en la vista previa y no importa nada. **B** (pasos d y e):
confirmar, importar, resultado, notas y AGENTS.md. Cada una cierra con la suite completa.

## Archivos y tests (rutas desde `src/app/`)

Riesgo: **B**ajo (archivo nuevo), **M**edio (compartido, poco tocado), **A**lto (archivo que suele
cambiar en paralelo). Todo lo nuevo vive en `features/importacion/`.

### Etapa A

| Archivo | Contenido | Riesgo | Test |
|---|---|---|---|
| `features/importacion/models/cuenta-importacion.model.ts` | `CuentaImportacion`, `TipoCuentaImportacion`, etiquetas | B | lo usan los specs de abajo |
| `features/importacion/models/mapeo-importacion.model.ts` | enums por nombre, etiquetas de fecha, `MapeoImportacion` | B | ídem |
| `features/importacion/models/vista-previa-response.model.ts` | totales, fila, estado | B | ídem |
| `features/importacion/services/cuenta-lectura.service.ts` | `GET .../cuentas/{id}` | B | `cuenta-lectura.service.spec.ts` (nuevo) |
| `features/importacion/services/lector-csv.ts` | port de `LectorCsv` | B | `lector-csv.spec.ts` (nuevo, tabla de casos) |
| `features/importacion/services/validacion-archivo.ts` | tamaño, vacío, UTF-8/NUL, conteo de filas | B | `validacion-archivo.spec.ts` (nuevo) |
| `features/importacion/services/sugerencias-mapeo.ts` | sugerencias | B | `sugerencias-mapeo.spec.ts` (nuevo) |
| `features/importacion/services/validacion-mapeo.ts` | reglas del mapeo, columnas 1 a 0 | B | `validacion-mapeo.spec.ts` (nuevo) |
| `features/importacion/services/formulario-importacion.ts` | arma el `FormData` | B | `formulario-importacion.spec.ts` (nuevo) |
| `features/importacion/services/importacion.service.ts` | `vistaPrevia(...)` | B | `importacion.service.spec.ts` (nuevo) |
| `features/importacion/services/mensajes-importacion.ts` | textos fijos, `mensajeReglaDeNegocio` | B | `mensajes-importacion.spec.ts` (nuevo) |
| `features/importacion/components/indicador-pasos.component.{ts,html,scss}` | pasos y `aria-current` | B | `indicador-pasos.component.spec.ts` (nuevo) |
| `features/importacion/components/paso-archivo.component.{ts,html,scss}` | archivo y validaciones | B | `paso-archivo.component.spec.ts` (nuevo) |
| `features/importacion/components/paso-mapeo.component.{ts,html,scss}` | muestra y mapeo | B | `paso-mapeo.component.spec.ts` (nuevo) |
| `features/importacion/components/paso-vista-previa.component.{ts,html,scss}` | totales, filtro, tabla | B | `paso-vista-previa.component.spec.ts` (nuevo) |
| `features/importacion/pages/importacion.page.{ts,html,scss}` | cuenta, pasos y estado | B | `importacion.page.spec.ts` (nuevo) |
| `app.routes.ts` | ruta hija `cuentas/:cuentaId/importacion`, sin `seccion` | M | `app.routes.spec.ts` (nuevo `it`) |
| `features/cuentas/pages/cuentas.page.html` | ítem `Importar CSV` (D11) | M | `cuentas.page.spec.ts` (nuevos `it`) |

### Etapa B

| Archivo | Contenido | Riesgo | Test |
|---|---|---|---|
| `features/importacion/models/importacion-response.model.ts` | resultado | B | lo usan los specs de abajo |
| `features/importacion/services/importacion.service.ts` | `importar(...)` | B | `importacion.service.spec.ts` (nuevos `it`) |
| `features/importacion/services/nota-listo-para-asignar.ts` | D8 | B | `nota-listo-para-asignar.spec.ts` (nuevo) |
| `features/importacion/components/paso-confirmar.component.{ts,html,scss}` | `omitirInvalidas`, `Importar` | B | `paso-confirmar.component.spec.ts` (nuevo) |
| `features/importacion/components/paso-resultado.component.{ts,html,scss}` | cifras, nota, enlace | B | `paso-resultado.component.spec.ts` (nuevo) |
| `features/importacion/pages/importacion.page.{ts,html}` | importar y resultado | B | `importacion.page.spec.ts` (nuevos `it`) |
| `../../AGENTS.md` (raíz) | árbol del frontend y sección | M | - |

Todo componente nuevo es standalone con `ChangeDetectionStrategy.OnPush`, `mat-icon` y los
tokens `--mat-sys-*`.

## Casos del lector local (fuente en el backend, commit `7c9ed9d`)

Cada fila es un caso de `lector-csv.spec.ts`; `L` es `LectorCsvTest` y `D`
`DecodificadorUtf8Test`. Los saltos se escriben `\n`, `\r\n` y `\r`.

| Caso | Entrada | Separador | Resultado esperado | Fuente |
|---|---|---|---|---|
| Un registro | `a,b,c` | `,` | `[[a,b,c]]` | L.casos |
| LF | `a,b\nc,d` | `,` | `[[a,b],[c,d]]` | L.casos |
| CRLF final | `a,b\r\nc,d\r\n` | `,` | `[[a,b],[c,d]]` | L.casos |
| CR solo | `a,b\rc,d` | `,` | `[[a,b],[c,d]]` | L.casos |
| Punto y coma | `a;b;c` | `;` | `[[a,b,c]]` | L.casos |
| Tabulador | `a\tb` | `\t` | `[[a,b]]` | L.casos |
| Separador entre comillas | `"a,b",c` | `,` | `[["a,b","c"]]` | L.casos |
| Comillas escapadas | `"di ""hola""",x` | `,` | `["di \"hola\"","x"]` | L.casos |
| LF entre comillas | `"uno\ndos",x` | `,` | `["uno\ndos","x"]` (1 registro) | L.casos |
| CRLF entre comillas | `"uno\r\ndos",x` | `,` | `["uno\r\ndos","x"]` | L.casos |
| Campo vacío en medio | `a,,c` | `,` | `["a","","c"]` | L.casos |
| Campo final vacío | `a,b,` | `,` | `["a","b",""]` | L.casos |
| Solo el separador | `,` | `,` | `["",""]` | L.casos |
| Comillado vacío | `"",x` | `,` | `["","x"]` | L.casos |
| Líneas vacías | `a,b\n\n\nc,d\n` | `,` | `[[a,b],[c,d]]` | L.casos |
| Texto vacío | `` | `,` | `[]` | L.casos |
| Solo saltos | `\n\r\n` | `,` | `[]` | L.casos |
| Comilla en medio | `ab"cd,e` | `,` | `["ab\"cd","e"]` | L.casos |
| Texto tras el cierre | `"ab"cd,e` | `,` | `["abcd","e"]` | L.casos |
| Comilla sin cerrar | `a,"b\nc` y `"` | `,` | error `comilla sin cerrar` | L.unaComillaSinCerrar |
| Tope de registros | `1\n2\n3` con tope 3; `1\n2\n3\n4` con tope 3 | `,` | 3 registros; error en el segundo | L.cortaAlSuperar |
| Vacías y tope | `1\n\n2\n\n` con tope 2 | `,` | 2 registros | L.laLineaVacia |
| UTF-8 con acentos | bytes de `Café ñandú` | - | `Café ñandú` | D.decodificaUtf8 |
| BOM | `EF BB BF` + `fecha,monto` | - | `fecha,monto` | D.descartaElBom |
| ISO-8859-1 | bytes de `Café` en ISO-8859-1 | - | rechazado (UTF-8) | D.iso88591 |
| UTF-16 con BOM | bytes de `fecha` en UTF-16 | - | rechazado | D.utf16ConBom |
| UTF-16 sin BOM | bytes de `fecha` en UTF-16LE | - | rechazado por NUL | D.utf16SinBom |
| Secuencia truncada | `61 C3` | - | rechazado | D.unaSecuenciaTruncada |
| ASCII | bytes ASCII | - | válido | D.unArchivoAscii |

Casos propios del cliente (no vienen del backend): conteo de 5000 y 5001 filas con y sin
encabezado y con líneas vacías, y el mismo texto con otro separador.

**Bordes entre `TextDecoder` y el decodificador del servidor**: el BOM (`TextDecoder` lo quita por
defecto, el servidor quita los tres bytes iniciales), UTF-16 (ambos lo rechazan, el servidor por
bytes inválidos o NUL) y la secuencia truncada (`fatal: true` lanza, el servidor responde `400`).
Un archivo con solo BOM o solo encabezado es el caso en que el cliente es deliberadamente más
estricto (D3).

## Risks / Trade-offs

- **El lector local se desincroniza del servidor** → la tabla de casos copiada, su fuente fijada
  en un commit y un comentario en el spec; la vista previa del servidor decide.
- **Un único mensaje para el `400`** es menos preciso → las causas evitables se previenen antes;
  el mensaje solo aparece si algo se escapa.
- **Renderizar 5000 filas** → solo se pinta la página actual (10, 25, 50 o 100).
- **Dos copias del archivo en memoria** (`File` y texto decodificado, máximo unos 4 MiB) → se
  liberan al salir de la pantalla.
- **El ítem del menú es un botón, no un enlace** → no se puede abrir en una pestaña nueva; se
  acepta para no cambiar una aserción existente (D11).
- **Analizar un archivo de 2 MiB puede congelar la pantalla unos 100 a 200 ms en el peor caso** →
  estado `Leyendo`, análisis único, y el umbral de 250 ms para pasarlo a un `Worker` (D3).
- **El test intermitente de `dialogo-transferencia.component.spec.ts`** (5 s bajo carga, pasa en
  aislado) no es una regresión: se repite aislado antes de investigar.
- **Sugerencias equivocadas** → son editables y la vista previa del servidor las desmiente antes de
  importar.

## Migration Plan

Solo frontend, sin datos ni API. Cada etapa se integra por separado; la etapa A no importa nada.
Revertir una etapa es revertir sus archivos.
