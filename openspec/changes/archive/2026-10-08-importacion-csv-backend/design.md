## Context

Motivación y alcance: ver `proposal.md`. Estado actual relevante (verificado en el código):

- `TransaccionService` crea transacciones con un método privado `guardar(...)` que resuelve la
  cuenta (`referencias.cuenta`), divide/valida la categoría, exige cuenta abierta, vincula el
  beneficiario con `BeneficiarioService.obtenerOCrear` y hace `saveAndFlush`. Ya hay precedentes
  de métodos públicos de creación para otras features (`crearProgramada`, `crearAjuste`).
- `CrearTransaccionRequest` ya recorta beneficiario y memo y limita a 100/500 con `@Size`, y
  `@MontoNoCero` rechaza 0. Las filas importadas se arman como ese request, así que las reglas
  salen de un solo sitio.
- `ConciliacionService.crear` ya usa `CuentaRepository.findByIdAndPresupuestoIdParaActualizar`
  (bloqueo `PESSIMISTIC_WRITE`, un solo bloqueo por transacción): se reutiliza tal cual.
- `Beneficiario.normalizar` es `toLowerCase(Locale.ROOT)`. `Transaccion.beneficiario` guarda el
  texto, sin normalizar; no hay columna de beneficiario normalizado en `transacciones`.
- `ActividadMensualRepository.ingresosSinCategoria` suma los montos `> 0`, sin categoría y sin
  subtransacciones de cuentas `enPresupuesto` que no son tarjeta, y excluye la entrada de una
  transferencia cuya pata par está en el presupuesto.
- Spring Boot trae un límite multipart de 1 MB y, con umbral 0, escribe cada parte a un
  directorio temporal en disco. Ninguna de las dos cosas sirve para esta feature (ver D6).

## Goals / Non-Goals

**Goals:** importar un CSV con vista previa fiel, duplicados predecibles, atomicidad y límites
duros; reutilizar las reglas de transacción; no tocar el comportamiento existente.

**Non-Goals:** mapeo guardado (el cliente reenvía los parámetros; queda fuera de alcance para un
change posterior), frontend, otros formatos, deshacer una importación (no se guarda un id de
lote en `transacciones`; no hay cambio de esquema), categorización automática, duplicados
difusos, transferencias y subtransacciones.

## Decisions

### D1. Paquete `importacion` y dependencias

`importacion` importa `transaccion`, `cuenta`, `presupuesto` y `comun`; **nadie importa
`importacion`**. No importa `beneficiario` ni `categoria`: la normalización del beneficiario y la
creación viven en `transaccion`. Se añade `importacion` a la alternancia del `grep` de
dependencias de `comun/`.

| Clase | Paquete | Test |
|---|---|---|
| `ImportacionController` | `com.presupuesto.importacion.controller` | mismo paquete, `ImportacionControllerTest` (HTTP) |
| `ImportacionService` | `com.presupuesto.importacion.service` | `ImportacionServiceTest` (obligatorio: verifica que sin filas válidas no se consulta) |
| `ImportacionProperties` (record `@ConfigurationProperties("importacion")`) | `...importacion.service` | cubierto por HTTP |
| `ParametrosImportacion` (record + validación a `DatosInvalidosException`) | `...importacion.dto.request` | `ParametrosImportacionTest` |
| `LectorCsv` (parser, package-private) | `...importacion.service` | `LectorCsvTest` (puro) |
| `DecodificadorUtf8` (package-private) | `...importacion.service` | `DecodificadorUtf8Test` (puro) |
| `InterpreteFilas` (fecha, monto, texto; package-private) | `...importacion.service` | `InterpreteFilasTest` (puro) |
| `ClasificadorDuplicados` (package-private) | `...importacion.service` | `ClasificadorDuplicadosTest` (puro) |
| `VistaPreviaResponse`, `FilaPreviaResponse`, `ImportacionResponse` (con `desde(...)`) | `...importacion.dto.response` | `...ResponseTest` solo si tienen lógica |
| enums `Separador`, `SeparadorDecimal`, `SeparadorMiles`, `FormatoFecha`, `EstadoFila` | `...importacion.dto.request` (los de entrada) / `...dto.response` (`EstadoFila`) | `ParametrosImportacionTest` |
| `ClaveMovimiento` (record público) | `com.presupuesto.transaccion.service` | `ClaveMovimientoTest` |
| `TransaccionService.crearLote`, `.contarExistentesPorClave` (métodos nuevos) | `...transaccion.service` | `TransaccionServiceLoteTest` |
| `TransaccionRepository.contarPorClave` (consulta nueva) | `...transaccion.repository` | cubierto por el test anterior |
| tests de integración HTTP de los flujos | `com.presupuesto.importacion.controller` | `ImportacionControllerTest` |

Sin `entity` ni `repository` propios: no hay tablas nuevas.

### D2. Parser CSV propio, sin dependencia

Se escribe `LectorCsv` (~80 líneas): máquina de estados de cuatro estados (inicio de campo,
campo sin comillas, campo entrecomillado, comilla dentro de entrecomillado) sobre el texto ya
decodificado. Reconoce `""` como comilla, separadores y `LF`/`CRLF`/`CR` dentro de comillas,
ignora líneas vacías y falla (400) si el archivo termina dentro de comillas. Cuenta filas a
medida que avanza y corta al pasar `max-filas`, para no materializar un archivo gigante.

*Alternativas:* `commons-csv` u OpenCSV. Rechazadas: el subconjunto necesario es pequeño y
totalmente especificable; ambas traen más superficie (OpenCSV arrastra `commons-text`,
`commons-lang3`, `commons-beanutils`) y el proyecto ya evita dependencias de conveniencia (sin
MapStruct). Ganancia de una librería: casos exóticos (comentarios, escapes con `\`, separadores
multicarácter) que no están en alcance. Costo de la propia: mantenerla; se compensa con tests
puros de tabla (comillas, saltos, CRLF, comilla sin cerrar, campo vacío final, línea en blanco).
**No se agrega dependencia, por lo que no hay versión que verificar.** Si en el futuro se
necesitaran dialectos adicionales, se reevalúa `commons-csv` verificando entonces su versión
estable más reciente.

### D3. Interpretación de fecha y monto

- **Fecha:** `DateTimeFormatter.ofPattern(patrón).withResolverStyle(STRICT)` con `uuuu` en lugar
  de `yyyy` (en STRICT, `yyyy` exige era). Patrones fijos del enum `FormatoFecha`: `uuuu-MM-dd`,
  `dd/MM/uuuu`, `MM/dd/uuuu`, `dd-MM-uuuu`. Se recorta el texto antes de leerlo. `31/02/2026` y
  `25/03/2026` con `MM/dd` fallan por resolverse en STRICT.
- **Monto:** sin `double`, `BigDecimal` ni `Number()`. Se recorta; signo opcional `+`/`-`; se
  separa por el `separadorDecimal` (como mucho uno); en la parte entera se quita el
  `separadorMiles` (y, con `ESPACIO`, también el espacio no separable `U+00A0` y `U+202F`); lo
  que queda debe ser solo dígitos; los decimales, 0 a 3 dígitos, se rellenan a la derecha hasta
  3. Valor = `entero * 1000 + decimales` con `Math.multiplyExact`/`addExact`; el desbordamiento
  deja la fila `INVALIDA`. Un separador de miles que aparece en la parte decimal o un decimal
  repetido es `INVALIDA`.
  Ejemplos con `COMA` decimal y `PUNTO` miles: `-1.234,5` → `-1234500`; `4,50` → `4500`;
  `1.234` → `1234000` (con miles `PUNTO`, es 1234 unidades, no 1,234).
- **Débito/crédito:** `monto = |crédito| - |débito|` (los bancos publican ambos positivos).
  Celda vacía = 0. Ambos no cero → `INVALIDA` ("débito y crédito a la vez"); resultado 0 →
  `INVALIDA` (el monto no puede ser 0), igual que `@MontoNoCero`.
- El motivo de `INVALIDA` es el primero que se detecte, en este orden: columnas faltantes,
  fecha, monto. Los motivos son texto fijo en español que **nunca incluye el contenido de la
  celda** (evita reflejar datos del archivo en logs o respuestas de error).

### D4. Texto importado

Recortar con `strip()`, vacío → `null`, truncar a 100/500 por puntos de código
(`offsetByCodePoints`), recortar de nuevo. Truncar en vez de rechazar: un banco puede enviar
descripciones largas que no son un error del usuario. El texto no se sanea de `=`, `+`, `-`, `@`
iniciales: la inyección de fórmulas ocurre al exportar a una hoja de cálculo, no al leer, y esta
API no exporta. Como el beneficiario se trunca *antes* de calcular la clave, reimportar da la
misma clave que la fila guardada (esto hace el truncado compatible con los duplicados).

### D5. Regla de duplicados: contar ocurrencias

Clave: `ClaveMovimiento(fecha, monto, beneficiarioNormalizado)`, con
`Beneficiario.normalizar(texto)` y `""` si no hay beneficiario. Sea `E` las transacciones de la
cuenta con esa clave y `F` las filas válidas del archivo con esa clave: las primeras `E` filas
(en orden de archivo) son `DUPLICADA` y las restantes `NUEVA`; es decir, se crean
`max(0, F - E)`.

Ejemplos (clave K = 2026-03-05, `-4500`, "cafe luna"):

| E (cuenta) | F (archivo) | Resultado | Total en cuenta tras importar |
|---|---|---|---|
| 0 | 1 | 1 nueva | 1 |
| 0 | 2 (dos cafés legítimos) | 2 nuevas | 2 |
| 1 (cargado a mano) | 2 | 1 duplicada + 1 nueva | 2 |
| 2 | 2 (reimportar) | 2 duplicadas, 0 nuevas | 2 |
| 2 | 1 | 1 duplicada, 0 nuevas, no borra | 2 |
| 0 | 3 | 3 nuevas | 3 |

**Justificación:** tras importar, la cuenta tiene `max(E, F)` filas de esa clave, así que una
reimportación ve `E' = F` y crea 0 (idempotente) y, a la vez, no se pierden movimientos
legítimos repetidos. *Alternativas:* (a) compararlo todo contra la cuenta sin contar: con
`E=1, F=2` marcaría ambas como duplicadas y se perdería un café; (b) colapsar duplicados dentro
del archivo: con `E=0, F=2` se perdería un café siempre; (c) ignorar duplicados: reimportar
duplica todo. Límite conocido y aceptado: dos transacciones realmente distintas con la misma
fecha, monto y beneficiario, cargadas a mano antes, se tratan como la misma.

*Conteo de existentes:* una consulta agrupada en `TransaccionRepository`,
`select t.fecha, t.monto, t.beneficiario, count(t) ... where t.cuenta.id = :cuentaId and t.fecha
between :desde and :hasta group by t.fecha, t.monto, t.beneficiario`, con `desde`/`hasta` el
mínimo y el máximo de las fechas válidas del archivo. Se agrupa por el texto crudo y es Java
quien normaliza y suma (así la comparación usa exactamente `Beneficiario.normalizar` y no
`lower()` de PostgreSQL, que difiere en alfabetos no ASCII). Una sola consulta por petición, sin
N+1. Se cuentan todos los estados (incluida `RECONCILIADA`) y las patas de transferencia de la
cuenta.

### D6. Límites, memoria y "nunca en disco"

| Límite | Valor por defecto | Propiedad | Si se excede |
|---|---|---|---|
| Tamaño del archivo | 2 MB (2 097 152 bytes) | `importacion.max-bytes` | `400`, antes de leer el contenido |
| Filas de datos | 5000 | `importacion.max-filas` | `400`, el parser corta al llegar a la fila 5001 |
| Techo del transporte | 4 MB | `spring.servlet.multipart.max-file-size` y `max-request-size` | `400` por `MaxUploadSizeExceededException` |
| Umbral en memoria | 4 MB | `spring.servlet.multipart.file-size-threshold` | — |

- **Por qué el techo multipart:** sin él, Spring corta a 1 MB con una excepción propia que hoy
  caería en `500`; con 4 MB el límite de la aplicación (2 MB) da el mensaje preciso para
  archivos de 2–4 MB y el techo solo atrapa abusos. Un handler nuevo en
  `ManejadorGlobalExcepciones` lo traduce a `400 DATOS_INVALIDOS` con mensaje fijo; otro maneja
  `MissingServletRequestPartException` (falta `archivo`) igual. Ambos viven en `comun` y no
  importan ninguna feature.
- **Por qué el umbral:** con `file-size-threshold=0` (por defecto) Tomcat vuelca cada parte a un
  archivo temporal; poniéndolo en el techo, las partes admitidas quedan en memoria. Esto es lo
  que hace verdadera la regla "el archivo nunca se guarda en disco". Las dos propiedades se
  suben juntas si alguien aumenta `importacion.max-bytes`; se documenta en `application.properties`.
- **Memoria por petición (peor caso):** 2 MB de bytes + hasta ~4 MB de `String` (UTF-16) + 5000
  filas × ~1 KB de objetos ≈ 11 MB, más ~1,3 MB de JSON de la vista previa (5000 × ~250 B). Con
  decenas de importaciones simultáneas sigue siendo acotado.
- **Escritura:** `IDENTITY` impide el batching de inserts de Hibernate, así que 5000 filas son
  5000 `INSERT` en una transacción; se espera del orden de segundos y es aceptable para una
  operación de uso esporádico. `crearLote` hace `saveAll` y un único `flush` al final.
- La respuesta de error nunca incluye contenido del archivo ni el nombre del archivo (el nombre
  se ignora por completo, así no hay path traversal posible), y el `Content-Type` de la parte
  no se usa para decidir nada.

### D7. Codificación

`DecodificadorUtf8` usa un `CharsetDecoder` UTF-8 con `REPORT` para entrada malformada y
caracteres no mapeables, de modo que cualquier byte inválido lanza `400` ("El archivo debe estar
codificado en UTF-8") en vez de reemplazarse silenciosamente por `U+FFFD`. Si los tres primeros
bytes son `EF BB BF`, se descartan. UTF-16 con BOM y ISO-8859-1 con acentos caen en el error.
Un archivo ISO-8859-1 con solo caracteres ASCII es UTF-8 válido y se acepta (no hay diferencia).

### D8. Reutilización: `crearLote` en `TransaccionService`

`public List<Transaccion> crearLote(Presupuesto presupuesto, Cuenta cuenta,
List<CrearTransaccionRequest> requests)`, `@Transactional`: exige la cuenta abierta una vez,
pasa cada request por la misma construcción de `guardar(...)` (se refactoriza el cuerpo a un
método privado que recibe la `Cuenta` ya cargada y no hace flush, para que `crear`,
`crearProgramada` y el lote compartan código sin cambiar su comportamiento), y cachea el
beneficiario por nombre normalizado dentro del lote para no consultar 5000 veces. Hace un solo
`flush` al final. No valida usuario (el llamador ya resolvió presupuesto y cuenta), igual que
`crearProgramada`. `contarExistentesPorClave(cuentaId, desde, hasta)` devuelve
`Map<ClaveMovimiento, Integer>`.

Los requests se arman con `categoriaId=null`, `aprobada=false`, sin subtransacciones. No se
recuerda categoría de beneficiario porque no hay categoría. Estado `NO_CONCILIADA` es el valor
por defecto de la entidad.

### D9. Flujo del servicio

Ambas operaciones: `presupuestoService.obtenerDelUsuario` (404) → cuenta del presupuesto (404;
en la importación con `findByIdAndPresupuestoIdParaActualizar`, en la vista previa
`findByIdAndPresupuestoId`) → validar parámetros y archivo (400) → decodificar y leer filas
(400) → `exigirAbierta` (422) → interpretar filas → contar existentes → clasificar → (importar:
422 si hay inválidas y no se omiten; `crearLote` de las nuevas; 201).

**Sin filas válidas no se consulta la base:** la consulta de existentes necesita el rango de
fechas de las filas válidas; si no hay ninguna (archivo solo de encabezado, solo de filas
`INVALIDA`, o sin filas de datos) el servicio salta el conteo, no llama a
`contarExistentesPorClave` y la clasificación es vacía. Un archivo con encabezado y sin filas de
datos no es un error: responde `200` en la vista previa (todos los totales en 0) y `201` con
todo en 0 en la importación. Se verifica con un test de servicio que inyecta un
`TransaccionService` espiado (`verify(..., never())`).

Los parámetros del formulario llegan al controller como texto/booleanos opcionales *sin*
`@Valid` ni conversión a enum por Spring, y el service los valida: así un valor mal escrito no
produce un `400` antes del `404` de presupuesto/cuenta (misma técnica que conciliación). El
`omitirInvalidas` no válido (`"quizas"`) es `400`.

El `422` por inválidas es un `ReglaNegocioException` con mensaje fijo "El archivo tiene N
filas inválidas; revisa la vista previa o usa omitirInvalidas" (N es un número, no contenido del
archivo). No se agregan códigos al enum `CodigoError`.

### D10. Concurrencia

La importación toma `PESSIMISTIC_WRITE` sobre la fila de la cuenta (un único bloqueo por
transacción, sin riesgo de interbloqueo entre cuentas ni con el bloqueo del presupuesto: no se
toma otro). Una segunda importación de la misma cuenta espera; al continuar (READ COMMITTED) su
consulta de existentes ve las filas confirmadas por la primera y resulta en 0 nuevas, sin
duplicados. La vista previa no bloquea y puede quedar desfasada, razón por la que la
importación recalcula. Límites documentados: (1) un `POST /transacciones` manual no toma el
bloqueo, así que una transacción creada a mano *durante* la importación puede no verse como
existente y duplicarse; el daño es una fila repetida, no una incoherencia, y es consistente con
cómo trata el resto de la app las creaciones manuales; (2) el bloqueo se mantiene mientras se
insertan hasta 5000 filas (segundos), durante los cuales otras operaciones que también bloquean
esa cuenta (conciliación) esperan. **La carrera real no tiene test automático** (requiere dos
transacciones reales concurrentes y sincronización frágil); se verifica a mano lanzando dos
`curl` simultáneos con el mismo archivo y comprobando que la cuenta quedó con una sola copia.
Hay un test en serie (misma cuenta, importar dos veces) que cubre la lógica de reimportación.

### D11. Efecto sobre `listoParaAsignar`

No se modifica `asignacion`. Una fila positiva sin categoría en una cuenta del presupuesto que
no sea tarjeta ya cuenta como ingreso (`ingresosSinCategoria`); una negativa sin categoría no
resta nada. Importar un extracto grande puede por tanto inflar `listoParaAsignar`.

Ejemplo numérico (cuenta corriente del presupuesto, mes con `listoParaAsignar = 0` y todos los
`disponible` en 0; importes en unidades, en milésimas son ×1000): el extracto trae un sueldo
`+5.000,00`, un reembolso `+1.200,00` y gastos por `-3.000,00` en total. Invariante que la app
mantiene: saldo de las cuentas del presupuesto = `listoParaAsignar` + suma de los `disponible`
de las categorías.

- **Justo después de importar:** el saldo de la cuenta sube `3.200,00`; `listoParaAsignar` sube
  `6.200,00` (solo cuentan las entradas sin categoría); **ningún `disponible` baja**, porque los
  gastos no tienen categoría y por eso no afectan a ninguna. El desajuste es exactamente
  `listoParaAsignar` inflado en `3.000,00` respecto del dinero que realmente hay.
- **Al categorizar los gastos:** el `disponible` de las categorías elegidas baja `3.000,00` en
  total y el invariante se recompone (`3.200,00 = 6.200,00 - 3.000,00`). `listoParaAsignar` no
  se mueve: nunca restó esos gastos. Una categoría puede quedar en negativo (sobregastada) y el
  usuario la cubre moviendo dinero entre categorías.
- **Pasos del usuario:** (1) abrir las transacciones de la cuenta filtradas por "sin aprobar"
  (la respuesta de la importación trae `sinCategoria` para avisarle cuántas faltan); (2)
  categorizar los gastos, también por lote con `CATEGORIZAR`; (3) aprobarlas; (4) revisar el mes.
  Hasta el paso 2 no debe asignar dinero usando ese `listoParaAsignar`.
- Una entrada categorizada a mano después deja de contar como ingreso sin categoría. El efecto
  alcanza a entradas de meses pasados (el cálculo es acumulado). Una tarjeta de crédito o una
  cuenta fuera del presupuesto no inflan nada. Se documenta en la spec (escenario) y se
  comprueba con un test.

### D12. `sinCategoria` en la vista previa y en la respuesta

La vista previa añade `sinCategoria` a sus totales y la respuesta `201` lo añade junto a
`importadas`, `duplicadas` y `omitidas`. Es la cantidad de filas que quedan (en la vista previa,
que quedarían) como transacciones sin categoría. Hoy toda fila importada nace sin categoría, así
que el valor es igual a `nuevas` (vista previa) y a `importadas` (respuesta); se expone como
campo propio para que el cliente avise "N transacciones sin categoría" sin depender de esa
igualdad, que dejaría de cumplirse si una versión futura categorizara al importar.

## Risks / Trade-offs

- **Clave de duplicado débil** (misma fecha, monto y beneficiario) → falsos duplicados entre
  movimientos realmente distintos; la vista previa los muestra antes de importar y el usuario
  puede crear a mano los que falten.
- **Parser propio** puede fallar en dialectos raros → tests de tabla y mensaje claro (`400`);
  alternativa documentada en D2.
- **Mapeo reenviado en cada llamada** → riesgo de que vista previa e importación difieran por
  error del cliente; mitigado porque la importación nunca confía en la previa.
- **5000 `INSERT` con bloqueo de cuenta** → latencia de segundos; aceptado y configurable.
- **Fechas de entrada ambiguas** (`dd/MM` vs `MM/dd`) → el cliente elige el formato; un formato
  equivocado suele dar filas `INVALIDA` (mes 13+), pero no siempre (`03/04/2026`); se documenta
  y la vista previa muestra la fecha interpretada.
- **Inflar `listoParaAsignar`** → documentado arriba; sin cambio de comportamiento a propósito.

## Migration Plan

Sin migraciones: no hay cambios de esquema (Hibernate `ddl-auto=update` no crea nada). Se
agregan propiedades con valor por defecto, así que ningún entorno necesita configuración nueva.
Reversión: retirar el paquete `importacion`, los métodos de lote y las propiedades; no queda
estado.

## Open Questions

- Ninguna que cambie la spec o las tareas. Pendiente para un change futuro: mapeo guardado por
  cuenta y deshacer una importación (requeriría un id de lote en `transacciones`).
