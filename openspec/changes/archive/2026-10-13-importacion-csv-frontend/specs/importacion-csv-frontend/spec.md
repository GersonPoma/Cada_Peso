# Spec Delta

## Purpose

Permite importar a una cuenta las transacciones de un CSV de extracto bancario desde la aplicación:
leer y validar el archivo en el navegador, mapear sus columnas, ver qué pasaría y confirmar la
importación con su resultado.

## ADDED Requirements

### Requirement: Pantalla del asistente y estado de la cuenta
El sistema SHALL ofrecer la pantalla `/presupuestos/:presupuestoId/cuentas/:cuentaId/importacion`,
con sesión y sin enlace propio en el menú lateral. SHALL pedir la cuenta (`GET .../cuentas/{id}`)
con un servicio de lectura propio, mostrar su nombre y su tipo y un enlace para volver a `Cuentas`.
Una cuenta inexistente SHALL mostrar un aviso y llevar a `Cuentas`; una cerrada SHALL mostrar
`La cuenta está cerrada; reábrela en Cuentas para importar`, sin el asistente.

#### Scenario: Desde Cuentas
- **DADO** la cuenta abierta `Banco` (id `5`) en el presupuesto `3`
- **CUANDO** la persona pulsa `Importar CSV` en su menú
- **ENTONCES** llega a `/presupuestos/3/cuentas/5/importacion` y ve `Banco`, su tipo y el primer
  paso del asistente

#### Scenario: Cuenta cerrada
- **DADO** la URL de importación de la cuenta cerrada `Vieja`
- **CUANDO** la pantalla carga la cuenta
- **ENTONCES** se lee `La cuenta está cerrada; reábrela en Cuentas para importar`, con el enlace a
  `Cuentas` y sin campos para elegir un archivo

#### Scenario: Cuenta inexistente
- **DADO** la URL de importación de una cuenta que no existe
- **CUANDO** la API responde `404`
- **ENTONCES** se muestra un aviso y la persona llega a `Cuentas`

#### Scenario: Error al cargar la cuenta
- **DADO** un fallo al pedir la cuenta
- **CUANDO** la pantalla muestra el error
- **ENTONCES** ofrece `Reintentar`, que vuelve a pedirla, y no muestra el asistente

### Requirement: Validación del archivo antes de subirlo
Al elegir el archivo el sistema SHALL rechazar, con un mensaje fijo y sin enviar nada, un archivo de
más de 2 MiB (2 097 152 bytes): `El archivo supera el máximo de 2 MB`; vacío o sin filas de datos:
`El archivo está vacío o no tiene filas de datos`; que no sea UTF-8 estricto (con o sin BOM), o
tenga caracteres NUL: `El archivo debe estar codificado en UTF-8`. Mientras haya un error, la
persona SHALL NOT poder avanzar.

#### Scenario: Archivo válido
- **DADO** un CSV UTF-8 de 3 KB con 12 filas de datos
- **CUANDO** la persona lo elige
- **ENTONCES** se ven su nombre, su tamaño y `12 filas de datos`, y se habilita `Continuar`

#### Scenario: Justo en el límite y por encima
- **DADO** un archivo de 2 097 152 bytes y otro de 2 097 153 bytes
- **CUANDO** la persona elige cada uno
- **ENTONCES** el primero se acepta y el segundo muestra `El archivo supera el máximo de 2 MB`

#### Scenario: Archivo vacío o solo con encabezado
- **DADO** un archivo de 0 bytes y otro con solo la línea de encabezado
- **CUANDO** la persona elige cada uno
- **ENTONCES** ambos muestran `El archivo está vacío o no tiene filas de datos`

#### Scenario: Codificación no UTF-8
- **DADO** un archivo ISO-8859-1 con `é` (byte `0xE9`), uno UTF-16 con BOM y uno UTF-16 sin BOM
- **CUANDO** la persona elige cada uno
- **ENTONCES** todos muestran `El archivo debe estar codificado en UTF-8`

#### Scenario: UTF-8 con BOM
- **DADO** un archivo UTF-8 que empieza con el BOM
- **CUANDO** la persona lo elige
- **ENTONCES** se acepta y el BOM no forma parte del primer campo

#### Scenario: Cambiar de archivo
- **DADO** un archivo rechazado
- **CUANDO** la persona elige otro válido
- **ENTONCES** el error desaparece y se habilita `Continuar`

### Requirement: El cliente es más estricto que el servidor en dos casos
Por decisión deliberada, el cliente SHALL ser más estricto que el servidor en dos casos, y los
dos están cubiertos por tests. (1) Un archivo con solo encabezado, solo BOM o sin filas de datos
SHALL rechazarse en el cliente aunque el servidor lo aceptaría (responde `201` con todo en 0 o
una vista previa vacía), porque no hay nada que importar. (2) Las columnas SHALL elegirse entre
las detectadas, de modo que no puede enviarse una columna fuera de rango, que el servidor
trataría como fila inválida.

#### Scenario: Solo el BOM
- **DADO** un archivo de 3 bytes con solo el BOM de UTF-8
- **CUANDO** la persona lo elige
- **ENTONCES** se rechaza con `El archivo está vacío o no tiene filas de datos` y no se hace
  ninguna petición al servidor

#### Scenario: Solo el encabezado
- **DADO** un archivo con una línea de encabezado y ninguna fila de datos
- **CUANDO** la persona lo elige
- **ENTONCES** se rechaza con el mismo mensaje, aunque el servidor respondería `201` con todo en 0

#### Scenario: Columna fuera de lo detectado
- **DADO** un archivo cuyas primeras filas tienen 4 columnas
- **CUANDO** la persona abre la lista de columnas de la fecha
- **ENTONCES** ofrece de `Columna 1` a `Columna 4` y ninguna más

### Requirement: Lectura del archivo sin bloquear la pantalla
Leer el archivo SHALL ser asíncrono. Mientras se lee y se analiza, el paso SHALL mostrar
`Leyendo el archivo…` (con `aria-busy`) y mantener `Continuar` deshabilitado. El análisis SHALL
calcularse una sola vez por archivo, separador y encabezado, y reutilizarse; elegir otro
archivo o cambiar el separador o el encabezado antes de que termine SHALL descartar la lectura
anterior. Un archivo de 2 MiB SHALL poder elegirse, leerse y analizarse sin ninguna petición al
servidor.

#### Scenario: Lectura en curso
- **DADO** un archivo grande recién elegido
- **CUANDO** todavía se está leyendo
- **ENTONCES** se ve `Leyendo el archivo…` y `Continuar` está deshabilitado; al terminar se ven
  su nombre, su tamaño y sus filas de datos

#### Scenario: Se descarta la lectura anterior
- **DADO** un archivo A en lectura
- **CUANDO** la persona elige un archivo B antes de que termine A
- **ENTONCES** la pantalla muestra solo el resultado de B, aunque la lectura de A termine después

#### Scenario: Archivo de 2 MiB
- **DADO** un archivo UTF-8 de 2 097 152 bytes o menos con 5000 filas de datos
- **CUANDO** la persona lo elige
- **ENTONCES** se acepta, se cuentan sus 5000 filas y no se hace ninguna petición al servidor

#### Scenario: El análisis se reutiliza
- **DADO** un archivo ya analizado
- **CUANDO** la persona cambia el formato de fecha o una columna del mapeo
- **ENTONCES** el archivo no se vuelve a leer ni a analizar; solo cambiar el separador o el
  encabezado lo repite

### Requirement: Límite de filas y comillas con el separador vigente
El sistema SHALL contar las filas de datos con el mismo criterio que el servidor: el encabezado no
cuenta, las líneas vacías se ignoran, los saltos pueden ser `LF`, `CRLF` o `CR` y una celda entre
comillas con saltos es UN registro. SHALL rechazar más de 5000 filas de datos
(`El archivo tiene más de 5000 filas de datos`) y una comilla sin cerrar
(`El archivo tiene una comilla sin cerrar`), y SHALL recalcular ambos al cambiar el separador o
`tieneEncabezado`.

#### Scenario: Exactamente 5000 filas
- **DADO** un archivo con encabezado y 5000 filas de datos
- **CUANDO** la persona lo elige con `tieneEncabezado` activo
- **ENTONCES** se acepta; con 5001 filas muestra `El archivo tiene más de 5000 filas de datos`

#### Scenario: Las líneas vacías no cuentan
- **DADO** un archivo con 5000 filas de datos y 20 líneas vacías entre ellas
- **CUANDO** la persona lo elige
- **ENTONCES** se acepta

#### Scenario: Comilla sin cerrar
- **DADO** un archivo con una línea `a,"b` sin cerrar
- **CUANDO** la persona lo elige
- **ENTONCES** se lee `El archivo tiene una comilla sin cerrar` y no se puede avanzar

#### Scenario: El conteo depende del separador
- **DADO** un archivo con `;` como separador cuyas celdas llevan comillas y comas
- **CUANDO** la persona cambia el separador de `COMA` a `PUNTO_Y_COMA`
- **ENTONCES** el recuento de filas y la comprobación de comillas se recalculan con el nuevo
  separador

### Requirement: Lectura local de las primeras filas
El sistema SHALL leer el archivo en el navegador, completo solo para validar y mostrando solamente
las primeras 10 filas del archivo para elegir columnas, con las mismas reglas que el lector del
servidor: campos entre comillas, comillas escapadas con `""`, separadores y saltos dentro de
comillas, y líneas vacías ignoradas. El archivo MUST NOT guardarse (ni en almacenamiento del
navegador) y nada SHALL enviarse al servidor hasta pulsar `Ver vista previa`. El paso SHALL avisar
que la vista previa del servidor es la que decide.

#### Scenario: Primeras filas
- **DADO** un archivo de 100 filas
- **CUANDO** la persona llega al paso del mapeo
- **ENTONCES** ve las primeras 10 filas del archivo, no las 100, y cada columna numerada desde 1

#### Scenario: Campo con comillas y separador
- **DADO** la línea `"Pizzería, Marco ""Polo""",135`
- **CUANDO** se lee con `COMA`
- **ENTONCES** la primera celda es `Pizzería, Marco "Polo"` y la segunda `135`

#### Scenario: Salto de línea dentro de comillas
- **DADO** la celda `"uno` y `dos"` en dos líneas del archivo
- **CUANDO** se lee el archivo
- **ENTONCES** es una sola celda `uno\ndos` y cuenta como un único registro

#### Scenario: Nada se envía antes de la vista previa
- **DADO** un archivo elegido y el mapeo editado
- **CUANDO** la persona aún no pulsa `Ver vista previa`
- **ENTONCES** no se hace ninguna petición al servidor y el archivo no queda guardado en el
  navegador

### Requirement: Mapeo de columnas con sugerencias editables
El paso del mapeo SHALL ofrecer separador, `tieneEncabezado`, columna de fecha y su formato, el
monto en UNA columna con signo o en DÉBITO y CRÉDITO (nunca mezclados), separador decimal,
separador de miles, columna de descripción y columna de memo opcional. SHALL sugerir el separador
(por conteo en las primeras líneas), el encabezado, el formato de fecha y el separador decimal,
precargando esos controles como sugeridos y editables sin enviar nada; el orden de la fecha y el
decimal por defecto salen de la región. Las columnas SHALL mostrarse desde 1 y enviarse desde 0.

#### Scenario: Sugerencia de separador
- **DADO** un archivo cuyas primeras líneas separan con `;`
- **CUANDO** la persona llega al paso del mapeo
- **ENTONCES** `PUNTO_Y_COMA` aparece sugerido y la persona puede cambiarlo

#### Scenario: Formato regional por defecto
- **DADO** una región `es-BO` y un archivo cuyas fechas caben en `dd/MM/yyyy` y `MM/dd/yyyy`
- **CUANDO** se sugiere el formato de fecha
- **ENTONCES** se sugiere `dd/MM/yyyy`, el orden de día y mes de la región

#### Scenario: Columnas desde 1
- **DADO** la columna de fecha elegida como `Columna 1`
- **CUANDO** se envía el mapeo
- **ENTONCES** `columnaFecha` viaja como `0`

#### Scenario: Monto en débito y crédito
- **DADO** el modo de monto `Débito y crédito` con la columna 4 y la columna 5
- **CUANDO** se envía el mapeo
- **ENTONCES** viajan `columnaDebito` `3` y `columnaCredito` `4` y no viaja `columnaMonto`

#### Scenario: Separador de miles igual al decimal
- **DADO** `separadorDecimal` `COMA` y `separadorMiles` `COMA`
- **CUANDO** la persona intenta pedir la vista previa
- **ENTONCES** se lee `El separador de miles no puede ser igual al de decimales` y el botón queda
  deshabilitado

#### Scenario: Faltan columnas obligatorias
- **DADO** el mapeo sin columna de fecha, o sin descripción, o sin monto
- **CUANDO** la persona intenta pedir la vista previa
- **ENTONCES** se lee `Elige la columna de la fecha`, `Elige la columna de la descripción` o
  `Elige la columna del monto` según el caso, y el botón queda deshabilitado

#### Scenario: Débito sin crédito
- **DADO** el modo `Débito y crédito` con solo la columna de débito elegida
- **CUANDO** la persona intenta pedir la vista previa
- **ENTONCES** se lee `Elige las columnas de débito y de crédito` y el botón queda deshabilitado

### Requirement: Vista previa por botón y respuestas atrasadas
La vista previa SHALL pedirse solo al pulsar `Ver vista previa`, con `POST .../importacion/vista-previa`
en `multipart/form-data`: la parte `archivo` y los campos `separador` y `separadorDecimal` (por
nombre), `formatoFecha` (por su etiqueta), `tieneEncabezado`, las columnas y `separadorMiles`,
sin fijar el `Content-Type` a mano. Mientras viaja el botón SHALL estar deshabilitado. Editar el
mapeo tras una vista previa SHALL marcarla desactualizada, y una respuesta atrasada SHALL
descartarse.

#### Scenario: Armado del multipart
- **DADO** el mapeo `COMA`, con encabezado, fecha en la columna 1 como `dd/MM/yyyy`, monto en la
  columna 3, decimal `COMA`, miles `PUNTO` y descripción en la columna 2
- **CUANDO** se pide la vista previa
- **ENTONCES** el cuerpo lleva `separador=COMA`, `tieneEncabezado=true`, `columnaFecha=0`,
  `formatoFecha=dd/MM/yyyy`, `columnaMonto=2`, `separadorDecimal=COMA`, `separadorMiles=PUNTO` y
  `columnaDescripcion=1`, y no lleva `omitirInvalidas` ni `columnaMemo`

#### Scenario: Botón deshabilitado mientras viaja
- **DADO** la vista previa pedida y sin respuesta
- **CUANDO** la persona mira el botón
- **ENTONCES** `Ver vista previa` está deshabilitado

#### Scenario: Mapeo editado después de la vista previa
- **DADO** una vista previa mostrada
- **CUANDO** la persona cambia el separador de miles
- **ENTONCES** la vista previa se marca desactualizada, `Importar` no se ofrece y se pide pulsar
  `Ver vista previa` de nuevo

#### Scenario: Editar el mapeo con la petición en camino
- **DADO** una vista previa A pedida y sin respuesta
- **CUANDO** la persona cambia el mapeo
- **ENTONCES** la petición A se cancela, `Ver vista previa` vuelve a habilitarse y la respuesta de A,
  si llegara, no se muestra

#### Scenario: Respuesta atrasada
- **DADO** una vista previa A cancelada y una vista previa B pedida después
- **CUANDO** B responde
- **ENTONCES** la pantalla muestra solo la respuesta de B

### Requirement: Totales y tabla de la vista previa
La vista previa SHALL mostrar los totales (`total`, `nuevas`, `duplicadas`, `invalidas`) y una tabla
con `Fila de datos`, fecha, monto, beneficiario, memo, estado y motivo. SHALL poder filtrarse por
estado (`Todas`, `Nuevas`, `Duplicadas`, `Inválidas`) y paginarse en el cliente con `mat-paginator`.
Las fechas y los montos SHALL usar el formato regional y la moneda del presupuesto (los montos
llegan en milésimas). El estado SHALL verse con texto e ícono.

#### Scenario: Totales
- **DADO** una respuesta con `total` 10, `nuevas` 6, `duplicadas` 3 e `invalidas` 1
- **CUANDO** se muestra la vista previa
- **ENTONCES** se leen 10 filas en total, 6 nuevas, 3 duplicadas y 1 inválida

#### Scenario: Filtrar por estado
- **DADO** la tabla con filas de los tres estados
- **CUANDO** la persona elige `Inválidas`
- **ENTONCES** solo se ven las inválidas, con su motivo, y el paginador cuenta solo esas

#### Scenario: Paginación en el cliente
- **DADO** una respuesta con 5000 filas
- **CUANDO** se muestra la tabla
- **ENTONCES** se ve una página de filas con `mat-paginator` y cambiar de página no hace ninguna
  petición

#### Scenario: Formato regional
- **DADO** una fila con fecha `2026-10-13` y monto `-135000` en un presupuesto en `BOB`
- **CUANDO** se muestra en la región `es-BO`
- **ENTONCES** la fecha y el monto se ven con ese formato regional y el monto como `-Bs 135,00`
  (en `en-US` sería `-BOB 135.00`)

#### Scenario: Fila inválida
- **DADO** una fila `INVALIDA` con fecha, monto, beneficiario y memo nulos y `motivo`
- **CUANDO** se muestra en la tabla
- **ENTONCES** las celdas vacías se ven como un guion, el estado dice `Inválida` con su ícono y se
  lee el motivo

#### Scenario: Numeración
- **DADO** un archivo con encabezado
- **CUANDO** se muestra la columna del número de fila
- **ENTONCES** su título es `Fila de datos` y la primera fila de datos es la 1

### Requirement: Confirmar la importación
El paso de confirmación SHALL mostrar `omitirInvalidas`, con una explicación, solo si hay filas
inválidas; mientras haya inválidas sin marcar, `Importar` SHALL estar deshabilitado. Con 0 filas
nuevas, `Importar` SHALL estar deshabilitado con una explicación. Importar SHALL enviar
`POST .../importacion` con el mismo archivo y mapeo, más `omitirInvalidas`, y mientras viaja los
botones SHALL estar deshabilitados.

#### Scenario: Sin inválidas
- **DADO** una vista previa con 6 nuevas, 3 duplicadas y 0 inválidas
- **CUANDO** la persona llega a la confirmación
- **ENTONCES** no ve `omitirInvalidas` y `Importar` está habilitado

#### Scenario: Con inválidas
- **DADO** una vista previa con 1 fila inválida
- **CUANDO** la persona llega a la confirmación
- **ENTONCES** ve `Omitir las filas inválidas` con su explicación y `Importar` está deshabilitado
  hasta marcarlo; al marcarlo se habilita y se envía `omitirInvalidas=true`

#### Scenario: Cero filas nuevas
- **DADO** una vista previa con 0 nuevas y todas duplicadas
- **CUANDO** la persona llega a la confirmación
- **ENTONCES** `Importar` está deshabilitado y se lee que no hay filas nuevas porque ya existen

#### Scenario: Importación en curso
- **DADO** la importación enviada y sin respuesta
- **CUANDO** la persona mira los botones
- **ENTONCES** `Importar` y `Volver` están deshabilitados y no puede enviarse una segunda vez

### Requirement: Resultado y nota de Listo para asignar
Tras el `201` el asistente SHALL mostrar `importadas`, `duplicadas`, `omitidas` (rotulada como
`Inválidas omitidas`) y `sinCategoria`, una nota sobre `Listo para asignar` elegida por la cuenta
y un enlace a sus transacciones sin aprobar (`/presupuestos/:id/transacciones?cuentaId=X&sinAprobar=1`).
La nota SHALL decir que las entradas suben `Listo para asignar` y las salidas no lo bajan hasta
categorizarlas solo en una cuenta del presupuesto que no es tarjeta; en una tarjeta de crédito o en
una cuenta de seguimiento, que no lo cambia.

#### Scenario: Cuenta del presupuesto
- **DADO** una importación a una cuenta corriente del presupuesto
- **CUANDO** se muestra el resultado
- **ENTONCES** la nota dice que las entradas importadas suben `Listo para asignar` y que las salidas
  no lo bajan hasta categorizarlas

#### Scenario: Tarjeta de crédito
- **DADO** una importación a una tarjeta de crédito
- **CUANDO** se muestra el resultado
- **ENTONCES** la nota dice que importar a una tarjeta de crédito no cambia `Listo para asignar`

#### Scenario: Cuenta de seguimiento
- **DADO** una importación a una cuenta de seguimiento
- **CUANDO** se muestra el resultado
- **ENTONCES** la nota dice que importar a una cuenta de seguimiento no cambia `Listo para asignar`

#### Scenario: Cifras
- **DADO** una respuesta `201` con `importadas` 2, `duplicadas` 3, `omitidas` 1 y `sinCategoria` 2
- **CUANDO** se muestra el resultado
- **ENTONCES** se leen 2 importadas, 3 duplicadas, 1 inválida omitida y 2 sin categoría

#### Scenario: Enlace a las transacciones
- **DADO** el resultado de la cuenta `5` del presupuesto `3`
- **CUANDO** la persona pulsa `Ver transacciones sin aprobar`
- **ENTONCES** llega a `/presupuestos/3/transacciones?cuentaId=5&sinAprobar=1`

### Requirement: Errores por código sin reflejar el archivo
El asistente SHALL decidir por `codigo`, nunca por `detail`, y sin cerrarse ni perder el mapeo:
`DATOS_INVALIDOS` (sin mapa `errores`) con un único mensaje fijo, `No pudimos leer el archivo con
ese mapeo. Revisa que sea un CSV en UTF-8 de hasta 2 MB y 5000 filas, que las columnas elegidas
existan y que los formatos de fecha y de monto coincidan`; `REGLA_NEGOCIO_VIOLADA` según el
contexto (cuenta cerrada, filas inválidas sin omitir, u otro); `RECURSO_NO_ENCONTRADO` con un
aviso; cualquier otro error con el aviso genérico. Ningún aviso SHALL incluir contenido del
archivo.

#### Scenario: Un 400
- **DADO** una vista previa o una importación pedidas
- **CUANDO** la API responde `400` con `DATOS_INVALIDOS`, sin `errores` y con un `detail` con texto
  interno
- **ENTONCES** se muestra el mensaje fijo, nunca el `detail`, y el mapeo se conserva

#### Scenario: 422 con la cuenta cerrada
- **DADO** una cuenta que se cerró en otra sesión
- **CUANDO** la API responde `422` con `REGLA_NEGOCIO_VIOLADA` y la pantalla vuelve a leer la
  cuenta para saber por qué
- **ENTONCES** se lee `La cuenta está cerrada; reábrela en Cuentas para importar` y el asistente
  sigue abierto

#### Scenario: 422 con filas inválidas sin omitir
- **DADO** una vista previa con filas inválidas y `omitirInvalidas` desmarcado
- **CUANDO** la API responde `422` con `REGLA_NEGOCIO_VIOLADA`
- **ENTONCES** se lee `El archivo tiene filas inválidas: márcalas para omitirlas o corrige el
  archivo`

#### Scenario: 422 sin contexto conocido
- **DADO** una cuenta abierta y sin filas inválidas en la vista previa
- **CUANDO** la API responde `422` con `REGLA_NEGOCIO_VIOLADA`
- **ENTONCES** se lee `No se pudo importar: el archivo o la cuenta ya no cumplen las reglas`

#### Scenario: 404
- **DADO** una importación pedida
- **CUANDO** la API responde `404` con `RECURSO_NO_ENCONTRADO`
- **ENTONCES** se muestra un aviso y se vuelve a pedir la cuenta

### Requirement: Accesibilidad y pantallas estrechas
El asistente SHALL indicar el paso actual con `aria-current="step"` y llevar el foco al título del
paso al avanzar o retroceder. Elegir el archivo SHALL usar un `<input type="file">` nativo con
etiqueta visible. La tabla SHALL vivir en una región con nombre y desplazamiento enfocable que se
desplaza dentro de su contenedor, y el resultado y los errores SHALL anunciarse con `aria-live`. En
pantallas estrechas los pasos y las tablas SHALL apilarse sin desbordar la página.

#### Scenario: Paso actual
- **DADO** el asistente en el paso del mapeo
- **CUANDO** se lee el indicador de pasos
- **ENTONCES** solo ese paso lleva `aria-current="step"`

#### Scenario: Foco al avanzar
- **DADO** el paso del archivo completo
- **CUANDO** la persona pulsa `Continuar`
- **ENTONCES** el foco queda en el título del paso del mapeo

#### Scenario: Tabla en pantalla estrecha
- **DADO** una pantalla de 390 px de ancho
- **CUANDO** se muestra la tabla de la vista previa
- **ENTONCES** la página no tiene desplazamiento horizontal y la tabla se desplaza dentro de su
  contenedor
