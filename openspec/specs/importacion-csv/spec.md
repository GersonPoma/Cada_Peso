# importacion-csv Specification

## Purpose

Permite importar a una cuenta las transacciones de un extracto CSV del banco: ver una vista
previa fila por fila, detectar duplicados al reimportar y crear las filas nuevas de forma atómica,
con límites de tamaño y de filas, sin guardar nunca el archivo y sin que nadie más pueda verlo ni
tocarlo.

## Requirements

### Requirement: Autenticación, aislamiento y orden de errores
El sistema SHALL exigir autenticación en las rutas de
`/api/v1/presupuestos/{presupuestoId}/cuentas/{cuentaId}/importacion`. En cada operación SHALL
validar en este orden: presupuesto (`404`), cuenta dentro del presupuesto (`404`), archivo y
parámetros (`400`), cuenta cerrada (`422`) y filas inválidas (`422`). Un presupuesto o una
cuenta inexistente o ajena SHALL responder `404` con código `RECURSO_NO_ENCONTRADO`, nunca
`403`, y sin revelar ni cambiar nada. La única excepción al orden es un archivo mayor que el
techo del transporte, que SHALL responder `400` antes que los `404`, porque el servidor lo corta
antes de llegar al controller.

#### Scenario: Petición sin token
- **DADO** una petición sin token de acceso
- **CUANDO** se llama a cualquier ruta de importación
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

#### Scenario: Presupuesto o cuenta ajenos
- **DADO** una cuenta de un presupuesto de otra persona, o de otro presupuesto propio
- **CUANDO** se llama a la vista previa o a la importación con ella
- **ENTONCES** el sistema responde `404` y no crea ninguna transacción

#### Scenario: Cuenta inexistente con archivo inválido
- **DADO** una cuenta inexistente
- **CUANDO** se envía un archivo inválido o parámetros inválidos
- **ENTONCES** el sistema responde `404`, no `400`

#### Scenario: Cuenta cerrada con archivo inválido
- **DADO** una cuenta cerrada
- **CUANDO** se envía un archivo con una codificación inválida
- **ENTONCES** el sistema responde `400` (el archivo se valida antes que el estado de la cuenta)

### Requirement: Vista previa sin efectos
`POST .../importacion/vista-previa` SHALL interpretar el archivo con el mapeo recibido y
responder `200` con los totales (`total`, `nuevas`, `duplicadas`, `invalidas`, `sinCategoria`) y una entrada por
fila de datos con su número de fila (`fila`, contado sobre las filas de datos, desde 1), la
`fecha`, el `monto` (milésimas con signo), el `beneficiario` y el `memo` ya interpretados, el
`estado` (`NUEVA`, `DUPLICADA` o `INVALIDA`) y, solo si es `INVALIDA`, el `motivo` en español.
`sinCategoria` es la cantidad de transacciones que quedarían sin categoría al importar. La
vista previa MUST NOT crear ni modificar ninguna transacción ni beneficiario. Un archivo sin
filas de datos válidas (solo encabezado, o solo filas inválidas) SHALL responder con los
totales que correspondan (en 0 las nuevas y duplicadas) y sin consultar las transacciones de
la cuenta.

#### Scenario: Vista previa de un archivo mixto
- **DADO** una cuenta con una transacción del 2026-03-05, `-4500`, beneficiario "Cafe Luna", y
  un archivo con esa misma fila, una fila nueva y una fila con fecha `31/02/2026`
- **CUANDO** se pide la vista previa
- **ENTONCES** responde `200` con `total=3`, `nuevas=1`, `duplicadas=1`, `invalidas=1`, `sinCategoria=1`, y la
  tercera fila trae `estado=INVALIDA` y un `motivo`

#### Scenario: Archivo sin filas válidas
- **DADO** un archivo con solo el encabezado, o con solo filas inválidas
- **CUANDO** se pide la vista previa
- **ENTONCES** responde `200` con `nuevas=0`, `duplicadas=0` y `sinCategoria=0` (con
  `invalidas` igual a las filas inválidas), y no se consultan las transacciones de la cuenta

#### Scenario: La vista previa no guarda
- **DADO** un archivo con filas nuevas
- **CUANDO** se pide la vista previa
- **ENTONCES** la cuenta sigue con las mismas transacciones y no existen beneficiarios nuevos

### Requirement: Mapeo de columnas por parámetros
Ambos endpoints SHALL recibir el mapeo como parámetros del formulario multipart junto al
`archivo`; el sistema MUST NOT guardar mapeos entre llamadas. Los parámetros son: `separador`
(`COMA`, `PUNTO_Y_COMA`, `TABULADOR`), `tieneEncabezado` (booleano), `columnaFecha` y
`formatoFecha` (`yyyy-MM-dd`, `dd/MM/yyyy`, `MM/dd/yyyy`, `dd-MM-yyyy`), el monto como
`columnaMonto` (una columna con signo) **o** como `columnaDebito` y `columnaCredito` (dos
columnas), `separadorDecimal` (`PUNTO` o `COMA`), `separadorMiles` (`NINGUNO`, `PUNTO`, `COMA`
o `ESPACIO`; por defecto `NINGUNO`), `columnaDescripcion` (beneficiario) y `columnaMemo`
(opcional). Los índices de columna son enteros desde 0. Faltar un parámetro obligatorio, un
valor fuera de la lista, un índice negativo, indicar a la vez una columna de monto y las de
débito/crédito (o ninguna), o un separador de miles igual al decimal SHALL responder `400`
`DATOS_INVALIDOS`.

#### Scenario: Parámetro obligatorio ausente
- **DADO** una petición sin `formatoFecha`
- **CUANDO** se llama a la vista previa
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS` y no procesa el archivo

#### Scenario: Formato de fecha fuera de la lista
- **DADO** `formatoFecha=yy/MM/dd`
- **CUANDO** se llama a cualquiera de los dos endpoints
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Monto en una y en dos columnas a la vez
- **DADO** `columnaMonto=2` junto con `columnaDebito=3`
- **CUANDO** se llama a cualquiera de los dos endpoints
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Booleanos que no son booleanos
- **DADO** `omitirInvalidas=quizas` en la importación, o `tieneEncabezado=quizas` en cualquiera
  de los dos endpoints
- **CUANDO** se llama con una cuenta existente
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS` y no crea nada

#### Scenario: Booleano inválido con cuenta inexistente
- **DADO** `omitirInvalidas=quizas` o `tieneEncabezado=quizas` y una cuenta inexistente
- **CUANDO** se llama a cualquiera de los dos endpoints
- **ENTONCES** responde `404` y no `400`

#### Scenario: Separador de miles igual al decimal
- **DADO** `separadorDecimal=COMA` y `separadorMiles=COMA`
- **CUANDO** se llama a cualquiera de los dos endpoints
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS`

### Requirement: Lectura del CSV
El sistema SHALL leer el archivo como CSV con campos entre comillas dobles, comillas escapadas
duplicándolas (`""`), separadores y saltos de línea (`LF`, `CRLF` o `CR`) dentro de campos
entrecomillados, y SHALL ignorar las líneas completamente vacías. Con `tieneEncabezado=true`
la primera línea no es una fila de datos. Una fila con menos columnas que las que el mapeo
referencia SHALL quedar `INVALIDA` (motivo "falta la columna N"); las columnas sobrantes se
ignoran. Una comilla sin cerrar al final del archivo SHALL responder `400`.

#### Scenario: Campo con separador y comillas escapadas
- **DADO** el separador `PUNTO_Y_COMA` y la línea `01/03/2026;"Cafe ""Luna""; centro";-4,50`
- **CUANDO** se pide la vista previa con la descripción en la columna 1
- **ENTONCES** el beneficiario interpretado es `Cafe "Luna"; centro`

#### Scenario: Salto de línea dentro de comillas
- **DADO** un campo de memo entrecomillado que contiene un salto de línea
- **CUANDO** se pide la vista previa
- **ENTONCES** es una sola fila y el memo conserva el salto de línea

#### Scenario: Fila corta
- **DADO** una fila con tres columnas y un mapeo que referencia la columna 5
- **CUANDO** se pide la vista previa
- **ENTONCES** esa fila queda `INVALIDA` y las demás se procesan

#### Scenario: Comilla sin cerrar
- **DADO** un archivo cuyo último campo abre comillas y nunca las cierra
- **CUANDO** se llama a cualquiera de los dos endpoints
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS`

### Requirement: Límites y codificación del archivo
El sistema SHALL rechazar con `400` `DATOS_INVALIDOS`, sin procesar el contenido, un archivo
ausente, vacío, de más de `importacion.max-bytes` bytes (2 MB por defecto) o con más de
`importacion.max-filas` filas de datos (5000 por defecto). Ambos límites SHALL ser
configurables por propiedad. El archivo SHALL decodificarse como UTF-8, con o sin BOM; una
secuencia de bytes que no sea UTF-8 válido SHALL responder `400` con un mensaje que indique que
el archivo debe estar en UTF-8. El archivo MUST NOT guardarse en disco: se procesa en memoria y
no queda rastro de él salvo las transacciones creadas.

#### Scenario: Archivo demasiado grande
- **DADO** un archivo de 2 MB + 1 byte
- **CUANDO** se llama a cualquiera de los dos endpoints
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS` y no crea nada

#### Scenario: Archivo entre el límite de la aplicación y el techo del transporte
- **DADO** un archivo de 3 MB, con `importacion.max-bytes` de 2 MB y techo multipart de 4 MB
- **CUANDO** se llama a cualquiera de los dos endpoints
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS` y el mensaje del límite de la
  aplicación (indica el máximo en MB), y no crea nada

#### Scenario: Archivo mayor que el techo del transporte
- **DADO** un archivo de más de 4 MB
- **CUANDO** se llama a cualquiera de los dos endpoints
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS` y un mensaje fijo, nunca `500`, y no
  crea nada

#### Scenario: Falta la parte archivo
- **DADO** una petición multipart sin la parte `archivo`
- **CUANDO** se llama a cualquiera de los dos endpoints
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS`, nunca `500`

#### Scenario: Demasiadas filas
- **DADO** un archivo con 5001 filas de datos dentro del límite de bytes
- **CUANDO** se llama a cualquiera de los dos endpoints
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS` y no crea nada

#### Scenario: Exactamente en el límite de filas
- **DADO** un archivo con 5000 filas de datos válidas y nuevas
- **CUANDO** se importa
- **ENTONCES** responde `201` con `importadas=5000`

#### Scenario: Codificación no UTF-8
- **DADO** un archivo en ISO-8859-1 con un carácter `é` (byte `0xE9`)
- **CUANDO** se llama a cualquiera de los dos endpoints
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS` y un mensaje que menciona UTF-8

#### Scenario: UTF-8 con BOM
- **DADO** un archivo UTF-8 que empieza con el BOM y cuyo encabezado es la primera línea
- **CUANDO** se pide la vista previa
- **ENTONCES** el BOM no forma parte del primer campo

#### Scenario: Archivo vacío
- **DADO** un archivo de 0 bytes
- **CUANDO** se llama a cualquiera de los dos endpoints
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS`

### Requirement: Interpretación de fecha y monto
La fecha SHALL leerse con el `formatoFecha` indicado de forma estricta: una fecha inexistente
(`31/02/2026`), con ceros de más o de menos que el formato no admite, o con texto sobrante
deja la fila `INVALIDA`. El monto SHALL leerse a milésimas sin aritmética de coma flotante:
admite un signo `+` o `-` inicial, los dígitos, el `separadorDecimal` y, en la parte entera, el
`separadorMiles`; como máximo 3 decimales; cualquier otro carácter (símbolos de moneda,
paréntesis, letras) deja la fila `INVALIDA`. Con `columnaMonto` el signo del archivo es el signo
del monto. Con dos columnas, el monto es el valor absoluto de la columna de crédito menos el
valor absoluto de la de débito; si ambas traen un valor distinto de cero la fila es `INVALIDA`;
una celda vacía cuenta como 0. Un monto de 0 SHALL dejar la fila `INVALIDA` ("el monto no puede
ser 0"). Un monto que no cabe en `long` SHALL dejar la fila `INVALIDA`.

#### Scenario: Monto con signo, miles y decimales con coma
- **DADO** `separadorDecimal=COMA`, `separadorMiles=PUNTO` y el texto `-1.234,5`
- **CUANDO** se interpreta
- **ENTONCES** el monto es `-1234500`

#### Scenario: Miles con punto y decimal con coma sin decimales
- **DADO** `separadorMiles=PUNTO`, `separadorDecimal=COMA` y el texto `1.234`
- **CUANDO** se interpreta
- **ENTONCES** el monto es `1234000` (1234 unidades, no 1,234)

#### Scenario: Monto que no cabe en el entero
- **DADO** el texto `9223372036854776` (su valor en milésimas supera el máximo de un entero
  de 64 bits)
- **CUANDO** se interpreta
- **ENTONCES** la fila queda `INVALIDA` y el resto del archivo se procesa

#### Scenario: Espacio no separable como separador de miles
- **DADO** `separadorMiles=ESPACIO` y el texto `1\u00A0234,50` con espacio no separable
  (U+00A0) y decimal coma
- **CUANDO** se interpreta
- **ENTONCES** se acepta y el monto es `1234500`

#### Scenario: Más de tres decimales
- **DADO** el texto `10.0005` con separador decimal punto
- **CUANDO** se interpreta
- **ENTONCES** la fila queda `INVALIDA`

#### Scenario: Débito y crédito
- **DADO** dos columnas, una fila con débito `45,00` y crédito vacío y otra con débito vacío y
  crédito `100,00`
- **CUANDO** se interpretan
- **ENTONCES** los montos son `-45000` y `100000`

#### Scenario: Débito y crédito a la vez
- **DADO** una fila con débito `10,00` y crédito `5,00`
- **CUANDO** se interpreta
- **ENTONCES** la fila queda `INVALIDA`

#### Scenario: Monto cero
- **DADO** una fila con monto `0,00`
- **CUANDO** se interpreta
- **ENTONCES** la fila queda `INVALIDA` con motivo "el monto no puede ser 0"

#### Scenario: Fecha inexistente
- **DADO** `formatoFecha=dd/MM/yyyy` y la fecha `31/02/2026`
- **CUANDO** se interpreta
- **ENTONCES** la fila queda `INVALIDA`

#### Scenario: Formato de fecha distinto del declarado
- **DADO** `formatoFecha=MM/dd/yyyy` y la fecha `25/03/2026`
- **CUANDO** se interpreta
- **ENTONCES** la fila queda `INVALIDA` (no existe el mes 25)

### Requirement: Texto importado
La descripción SHALL ser el beneficiario y la columna de memo, el memo. Ambos SHALL recortarse
de espacios, quedar en `null` si resultan vacíos y truncarse a 100 caracteres (beneficiario) y
500 (memo), contados por punto de código, sin partir un par sustituto. Tras truncar se vuelve a
recortar. Un texto largo MUST NOT dejar la fila `INVALIDA`: se trunca. Los textos se guardan
tal cual (el sistema no interpreta fórmulas).

#### Scenario: Beneficiario largo
- **DADO** una descripción de 150 caracteres
- **CUANDO** se pide la vista previa
- **ENTONCES** el beneficiario interpretado tiene 100 caracteres

#### Scenario: Reimportar un beneficiario largo
- **DADO** un archivo con una fila cuya descripción tiene 150 caracteres, ya importado una vez
  (se guardó el beneficiario truncado a 100 caracteres)
- **CUANDO** se importa de nuevo el mismo archivo
- **ENTONCES** responde `201` con `importadas=0` y `duplicadas=1`, porque la clave se calcula
  con el texto ya truncado

#### Scenario: Descripción vacía
- **DADO** una fila con descripción vacía o solo espacios
- **CUANDO** se importa
- **ENTONCES** la transacción se crea sin beneficiario y no se crea ningún beneficiario

### Requirement: Detección de duplicados por ocurrencias
Una fila de datos válida SHALL clasificarse como `DUPLICADA` según cuántas transacciones con su
misma clave (fecha, monto y beneficiario normalizado, sin distinguir mayúsculas; sin
beneficiario equivale a vacío) existan ya en la cuenta (`E`) y cuántas filas válidas con esa
clave traiga el archivo (`F`): las primeras `E` filas del archivo con esa clave, en orden de
archivo, son `DUPLICADA` y el resto `NUEVA`. Se cuentan las transacciones de la cuenta de
cualquier estado. Las filas `INVALIDA` no participan.

#### Scenario: Cuenta con una, archivo con dos idénticas
- **DADO** una cuenta con 1 transacción de clave K y un archivo con 2 filas de clave K
- **CUANDO** se pide la vista previa
- **ENTONCES** la primera es `DUPLICADA` y la segunda `NUEVA`

#### Scenario: Dos cafés iguales en cuenta vacía
- **DADO** una cuenta sin transacciones de clave K y un archivo con 2 filas de clave K
- **CUANDO** se importa
- **ENTONCES** se crean 2 transacciones

#### Scenario: Reimportar el mismo archivo
- **DADO** un archivo ya importado con éxito
- **CUANDO** se importa de nuevo con el mismo mapeo
- **ENTONCES** responde `201` con `importadas=0` y todas las filas válidas como `duplicadas`

#### Scenario: La cuenta tiene más que el archivo
- **DADO** una cuenta con 2 transacciones de clave K y un archivo con 1
- **CUANDO** se importa
- **ENTONCES** la fila es `DUPLICADA`, no se crea nada y no se borra nada

#### Scenario: Mayúsculas y beneficiario ausente
- **DADO** una transacción con beneficiario "Cafe Luna" y una fila con "CAFE LUNA", misma fecha
  y monto
- **CUANDO** se pide la vista previa
- **ENTONCES** la fila es `DUPLICADA`

#### Scenario: Otra cuenta no cuenta
- **DADO** una transacción idéntica en otra cuenta del mismo presupuesto
- **CUANDO** se pide la vista previa
- **ENTONCES** la fila es `NUEVA`

### Requirement: Importación atómica
`POST .../importacion` SHALL volver a interpretar el archivo y recalcular los duplicados en el
servidor, sin confiar en ninguna vista previa anterior, y crear las filas `NUEVA` en una sola
transacción de base de datos. Si hay alguna fila `INVALIDA` y `omitirInvalidas` no es `true`,
SHALL responder `422` `REGLA_NEGOCIO_VIOLADA` sin crear nada. Con `omitirInvalidas=true` SHALL
crear las nuevas y omitir las inválidas. SHALL responder `201` con `importadas` (creadas),
`duplicadas` (omitidas por duplicado), `omitidas` (inválidas omitidas, siempre 0 sin
`omitirInvalidas`) y `sinCategoria` (cuántas de las transacciones creadas quedaron sin
categoría, para que el cliente pida categorizarlas). Un archivo sin filas válidas SHALL
responder `201` con todo en 0 sin consultar las transacciones de la cuenta. Si falla la creación de cualquier fila, no queda ninguna creada.

#### Scenario: Importación feliz
- **DADO** un archivo con 3 filas válidas nuevas
- **CUANDO** se importa
- **ENTONCES** responde `201` con `importadas=3`, `duplicadas=0`, `omitidas=0`, `sinCategoria=3`
  y la cuenta tiene 3 transacciones más

#### Scenario: Fila inválida sin omitir
- **DADO** un archivo con 2 filas válidas y 1 inválida
- **CUANDO** se importa sin `omitirInvalidas`
- **ENTONCES** responde `422` con código `REGLA_NEGOCIO_VIOLADA` y la cuenta no cambia ni se
  crean beneficiarios

#### Scenario: Fila inválida con omitirInvalidas
- **DADO** el mismo archivo
- **CUANDO** se importa con `omitirInvalidas=true`
- **ENTONCES** responde `201` con `importadas=2`, `duplicadas=0`, `omitidas=1`, `sinCategoria=2`

#### Scenario: Ignora una vista previa anterior
- **DADO** una vista previa que marcó una fila `NUEVA`, y después se creó a mano una
  transacción con su misma clave
- **CUANDO** se importa el mismo archivo
- **ENTONCES** esa fila se cuenta en `duplicadas` y no se crea

#### Scenario: Archivo sin filas válidas
- **DADO** un archivo con solo el encabezado
- **CUANDO** se importa
- **ENTONCES** responde `201` con `importadas=0`, `duplicadas=0`, `omitidas=0` y
  `sinCategoria=0`

#### Scenario: Todo duplicado
- **DADO** un archivo cuyas filas ya existen todas
- **CUANDO** se importa
- **ENTONCES** responde `201` con `importadas=0` y `sinCategoria=0`

### Requirement: Reglas de una transacción manual
Cada fila importada SHALL pasar por las mismas reglas que una transacción creada a mano: SHALL
responder `422` si la cuenta está cerrada (antes de evaluar las filas), crear el beneficiario
si no existe (comparación sin distinguir mayúsculas, sin recordar categoría), no tener
categoría, nacer `NO_CONCILIADA` y con `aprobada=false`, y no tener subtransacciones. Una cuenta
fuera del presupuesto y una tarjeta de crédito SHALL admitir la importación.

#### Scenario: Estado de las importadas
- **DADO** un archivo con filas nuevas
- **CUANDO** se importa y se listan las transacciones de la cuenta
- **ENTONCES** todas son `NO_CONCILIADA`, `aprobada=false` y sin categoría

#### Scenario: Cuenta cerrada
- **DADO** una cuenta cerrada y un archivo válido
- **CUANDO** se llama a la vista previa o a la importación
- **ENTONCES** responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no crea nada

#### Scenario: Cuenta cerrada con filas inválidas
- **DADO** una cuenta cerrada y un archivo válido en su forma pero con filas inválidas
- **CUANDO** se llama a la vista previa o a la importación (con y sin `omitirInvalidas`)
- **ENTONCES** responde `422` por cuenta cerrada, no por filas inválidas, en ambos endpoints
  y sin crear nada

#### Scenario: Beneficiario existente y nuevo
- **DADO** un beneficiario "Cafe Luna" ya existente y un archivo con "CAFE LUNA" y "Panaderia"
- **CUANDO** se importa
- **ENTONCES** la primera se vincula al existente y se crea solo "Panaderia"

#### Scenario: Cuenta fuera del presupuesto y tarjeta
- **DADO** una cuenta fuera del presupuesto y una tarjeta de crédito
- **CUANDO** se importa un archivo válido a cada una
- **ENTONCES** ambas responden `201`

### Requirement: Efecto en el listo para asignar
La importación MUST NOT cambiar el cálculo de `listoParaAsignar`: las entradas sin categoría
importadas a una cuenta del presupuesto que no sea tarjeta cuentan como ingreso, y las salidas
sin categoría no afectan nada hasta que se categorizan.

#### Scenario: Entradas sin categoría suben el listo para asignar
- **DADO** un mes con `listoParaAsignar` de 0 y un archivo con una entrada de `5000000` y una
  salida de `-3000000`, importado a una cuenta del presupuesto
- **CUANDO** se consulta el mes
- **ENTONCES** `listoParaAsignar` es `5000000`, no `2000000`, ningún `disponible` cambia y la
  respuesta de la importación trae `sinCategoria=2`

### Requirement: Importaciones simultáneas
Mientras dure una importación, el sistema SHALL bloquear la cuenta de forma exclusiva para que
otra importación de la misma cuenta espere y, al continuar, vea las transacciones ya creadas.
Dos importaciones simultáneas del mismo archivo SHALL crear cada fila una sola vez en total.
La vista previa no bloquea.

#### Scenario: Segunda importación del mismo archivo en serie
- **DADO** una importación terminada de un archivo con 3 filas nuevas
- **CUANDO** una segunda importación del mismo archivo llega después
- **ENTONCES** responde `201` con `importadas=0` y `duplicadas=3`
