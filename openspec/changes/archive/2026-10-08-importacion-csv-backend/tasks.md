## 1. Configuración y errores transversales

- [x] 1.1 Agregar a `application.properties` `importacion.max-bytes=2097152`,
  `importacion.max-filas=5000`, `spring.servlet.multipart.max-file-size=4MB`,
  `max-request-size=4MB` y `file-size-threshold=4MB`, con un comentario que explique que las
  tres últimas se suben juntas con `max-bytes`; verificar que la app arranca (`.\mvnw.cmd test`
  sobre un test existente de contexto).
- [x] 1.2 En `ManejadorGlobalExcepciones` agregar manejadores de `MaxUploadSizeExceededException`
  y `MissingServletRequestPartException` → `400 DATOS_INVALIDOS` con mensaje fijo, sin importar
  ninguna feature; verificar con un test en `comun/excepcion` (controller de prueba solo en
  `src/test`) que un multipart de más de 4 MB y uno sin parte `archivo` responden `400`.

## 2. Lote en `transaccion`

- [x] 2.1 Crear `ClaveMovimiento` (record público, `de(fecha, monto, textoBeneficiario)` con
  `Beneficiario.normalizar` y `""` si es nulo) y su `ClaveMovimientoTest` (mayúsculas, nulo,
  vacío, caracteres no ASCII con `Locale.ROOT`).
- [x] 2.2 Agregar `TransaccionRepository.contarPorClave` (agrupada por fecha, monto y
  beneficiario crudo, acotada por cuenta y rango de fechas) y
  `TransaccionService.contarExistentesPorClave` que normaliza y suma en Java; verificar con
  `TransaccionServiceLoteTest` (suma "Cafe Luna" + "CAFE LUNA", incluye otros estados, ignora
  otra cuenta y fechas fuera de rango).
- [x] 2.3 Refactorizar `guardar(...)` para compartir la construcción con una `Cuenta` ya cargada
  y sin flush, y agregar `crearLote(presupuesto, cuenta, requests)` con caché de beneficiarios
  y un solo flush; verificar que **todos los tests existentes de transacciones, programadas y
  conciliación siguen pasando sin tocar aserciones** y que `TransaccionServiceLoteTest` cubre:
  cuenta cerrada 422, beneficiario auto-creado y reutilizado, `NO_CONCILIADA`,
  `aprobada=false`, sin categoría, y atomicidad (una fila que falla no deja ninguna).

## 3. Núcleo puro de la importación

- [x] 3.1 Crear `LectorCsv` (comillas, `""`, saltos dentro de comillas, `LF`/`CRLF`/`CR`, líneas
  vacías, comilla sin cerrar, corte a `max-filas`) y `LectorCsvTest` de tabla; verificar que
  pasa.
- [x] 3.2 Crear `DecodificadorUtf8` (BOM, `REPORT`) y su test con UTF-8 válido, con BOM,
  ISO-8859-1 con `é`, UTF-16 y archivo vacío.
- [x] 3.3 Crear `InterpreteFilas` (fecha estricta por formato, monto en milésimas con signo,
  separadores y máximo 3 decimales, débito/crédito, texto recortado y truncado por puntos de
  código) y `InterpreteFilasTest` con los ejemplos del design (`-1.234,5` → `-1234500`,
  `4,50` → `4500`, `1.234` con miles punto → `1234000`, 4 decimales, débito y crédito a la vez,
  cero, desbordamiento, `31/02/2026`, `25/03/2026` en `MM/dd`, 150 caracteres → 100, par
  sustituto en el corte).
- [x] 3.4 Crear `ClasificadorDuplicados` y `ClasificadorDuplicadosTest` con la tabla del design
  (E/F: 0/1, 0/2, 1/2, 2/2, 2/1, 0/3), más claves distintas intercaladas y orden de archivo.
- [x] 3.5 Crear `ParametrosImportacion` con los enums y la validación a `DatosInvalidosException`
  (faltantes, fuera de lista, índices negativos, monto en una y dos columnas, miles igual a
  decimal, `omitirInvalidas` no booleano) y `ParametrosImportacionTest`.

- [x] 3.6 En `InterpreteFilasTest` agregar tres casos de monto: `1.234` con `separadorMiles=PUNTO`
  y `separadorDecimal=COMA` → `1234000`; `9223372036854776` → `INVALIDA` sin lanzar excepción;
  `1\u00A0234,50` con `separadorMiles=ESPACIO` → `1234500`.

## 4. Servicio, controller y respuestas

- [x] 4.1 Crear `ImportacionProperties` (registrado igual que `JwtProperties`), los records de
  `dto/response` con `desde(...)` y `ImportacionService` con vista previa e importación según
  D9 (orden 404 → 404 → 400 → 422 cuenta cerrada → 422 inválidas; bloqueo de cuenta solo en la
  importación; `sinCategoria` en totales y respuesta según D12; sin filas válidas no se llama a
  `contarExistentesPorClave`); verificar que compila y que la suite existente sigue verde.
- [x] 4.2 Crear `ImportacionController` bajo
  `/api/v1/presupuestos/{presupuestoId}/cuentas/{cuentaId}/importacion` (`POST /vista-previa` y
  `POST`, `consumes = multipart/form-data`, parámetros como texto sin `@Valid`, `201` en la
  importación); verificar con una petición de humo en `ImportacionControllerTest`.
- [x] 4.3 `ImportacionServiceTest` con `TransaccionService` espiado: archivo solo con
  encabezado, archivo sin filas de datos y archivo solo con filas `INVALIDA` →
  `verify(transaccionService, never()).contarExistentesPorClave(...)` en vista previa e
  importación, y los totales en 0; con al menos una fila válida se llama exactamente una vez.

## 5. Tests de integración por flujo (`importacion/controller`)

Cada test crea su propio usuario, presupuesto y cuentas, y cuenta solo dentro de su presupuesto.

- [x] 5.1 Autenticación y aislamiento: sin token 401; presupuesto/cuenta ajenos 404 (incluye
  archivo inválido → 404, no 400); verificar el orden 404 → 400 → 422.
- [x] 5.2 Vista previa sin guardar: totales y estados por fila (`NUEVA`, `DUPLICADA`,
  `INVALIDA` con motivo), y comprobar que cuenta y beneficiarios no cambian.
- [x] 5.3 Importación feliz y estado de las filas (`NO_CONCILIADA`, `aprobada=false`, sin
  categoría, beneficiario vinculado/creado sin distinguir mayúsculas); cuenta fuera del
  presupuesto y tarjeta admitidas.
- [x] 5.4 Reimportar el mismo archivo = 0 nuevas; cuenta con 1 y archivo con 2; cuenta vacía y
  archivo con 2; vista previa vieja ignorada por la importación (transacción creada a mano
  entremedio); otra cuenta no cuenta.
- [x] 5.5 Fila inválida: `422` sin crear nada ni beneficiarios; con `omitirInvalidas=true`,
  `201` con `omitidas`; `sinCategoria` en la vista previa y en la respuesta (igual a `nuevas` y
  a `importadas`, y 0 cuando todo es duplicado o no hay filas válidas); monto cero inválido; cuenta cerrada `422` en ambos endpoints.
- [x] 5.6 Límites: 2 MB + 1 byte `400`; archivo de 3 MB (entre 2 y 4 MB) `400` con el mensaje
  del límite de la aplicación (se comprueba que menciona el máximo en MB y no es el mensaje
  fijo del techo); archivo de más de 4 MB `400` (no `500`) con el mensaje fijo del techo;
  petición multipart sin la parte `archivo` `400` (no `500`); estos tres en ambos endpoints;
  5001 filas `400`, 5000 filas `201`, archivo vacío `400`, codificación ISO-8859-1 `400` con mensaje de
  UTF-8, UTF-8 con BOM aceptado, comilla sin cerrar `400`; los límites se bajan por propiedad de
  test (`@TestPropertySource`) cuando se pueda, para no generar archivos enormes.
- [x] 5.7 Mapeo: débito/crédito, punto y coma con decimal coma y miles punto, tabulador, cada
  formato de fecha, con y sin encabezado; parámetros inválidos `400`.
- [x] 5.8 `listoParaAsignar`: importar una entrada y una salida sin categoría a una cuenta del
  presupuesto y comprobar con el endpoint del mes que `listoParaAsignar` sube solo por la
  entrada, que ningún `disponible` cambia y que `sinCategoria=2`; luego categorizar la salida
  y comprobar que el `disponible` de su categoría baja y `listoParaAsignar` no se mueve; una
  tarjeta no lo cambia. El archivo no deja rastro: sin archivos nuevos en `java.io.tmpdir` tras importar
  (conteo antes y después).

- [x] 5.9 Beneficiario de 150 caracteres: importar dos veces el mismo archivo; la primera crea
  1 con beneficiario de 100 caracteres y la segunda responde `201` con `importadas=0` y
  `duplicadas=1`.
- [x] 5.10 Cuenta cerrada con un archivo de filas inválidas: `422` de cuenta cerrada (se
  comprueba el mensaje, distinto del de filas inválidas) en vista previa e importación, con y
  sin `omitirInvalidas`.
- [x] 5.11 `omitirInvalidas=quizas` y `tieneEncabezado=quizas` → `400 DATOS_INVALIDOS` con
  cuenta existente; los mismos valores con cuenta inexistente → `404`.

## 6. Documentación y verificación final

- [x] 6.1 Actualizar `AGENTS.md`: árbol de paquetes (`importacion`), regla de dependencias
  (`importacion` importa `transaccion`, `cuenta`, `presupuesto` y `comun`; nadie la importa),
  una sección "Importación de CSV" con los límites, la regla de duplicados, la nota de
  `listoParaAsignar` y la de concurrencia; agregar `importacion` a la alternancia del `grep`.
  Verificar leyendo que las secciones existen y no contradicen las vigentes.
- [x] 6.2 Verificar dependencias desde `backend/src`: el `grep` de `AGENTS.md` (con
  `importacion` en la alternancia) sobre `main/java/com/presupuesto/comun` y
  `test/java/com/presupuesto/comun` devuelve cero líneas;
  `grep -rn "import com.presupuesto" main/java/com/presupuesto/importacion` solo muestra
  `transaccion`, `cuenta`, `presupuesto` y `comun`; y ninguna otra feature importa `importacion`.
- [x] 6.3 Ejecutar la suite completa con `.\mvnw.cmd test` desde `backend/`: los 1381 tests de la
  línea base pasan más los nuevos, y `git diff` no muestra aserciones existentes modificadas
  (los archivos de test existentes no cambian, salvo adiciones).
- [x] 6.4 Prueba manual de la carrera: dos `curl` simultáneos con el mismo archivo contra la
  misma cuenta; verificar que quedó una sola copia de cada fila (sin test automático, por
  diseño D10).
- [x] 6.5 Ejecutar `openspec validate importacion-csv-backend --strict` sin errores.
