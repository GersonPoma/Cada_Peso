# transacciones-programadas-frontend Specification

## Purpose
Permite gestionar desde el frontend las transacciones programadas (plantillas recurrentes) del
presupuesto activo: verlas con su estado, crearlas, editarlas, pausarlas, reanudarlas, borrarlas
y generar a pedido sus ocurrencias vencidas.

## Requirements

### Requirement: Sección y lista de plantillas
El sistema SHALL ofrecer la pantalla `/presupuestos/:presupuestoId/programadas` con la entrada
`Programadas` en el menú lateral. La pantalla SHALL pedir las plantillas
(`GET .../transacciones-programadas`), las cuentas con cerradas y las categorías con ocultas, y
mostrar cada plantilla en el orden de la API con: nombre de la cuenta, beneficiario (o `—`),
nombre de la categoría (o `Sin categoría`), monto con el pipe `monto` y la moneda del presupuesto
junto a `Salida` (negativo) o `Entrada` (positivo), frecuencia legible, próxima fecha con el pipe
`fecha` (o `—` si es `null`) y estado. Mientras carga SHALL mostrar un indicador; sin plantillas,
`No hay transacciones programadas` con el botón para crear una; si falla, el aviso genérico con
`Reintentar`.

#### Scenario: Entrada desde el menú
- **DADO** el presupuesto `3` activo
- **CUANDO** la persona pulsa `Programadas` en el menú lateral
- **ENTONCES** llega a `/presupuestos/3/programadas` y ve sus plantillas

#### Scenario: Fila de una plantilla
- **DADO** una plantilla de `-150000` en `Banco`, categoría `Alquiler`, beneficiario `Inmobiliaria`,
  `MENSUAL`, `proximaFecha` `2026-11-05`, activa y sin error
- **CUANDO** se muestra la lista
- **ENTONCES** la fila dice `Banco`, `Inmobiliaria`, `Alquiler`, el monto con `Salida`,
  `Cada mes`, la fecha del 5 de noviembre de 2026 según la región y `Activa`

#### Scenario: Lista vacía
- **DADO** un presupuesto sin plantillas
- **CUANDO** se abre la pantalla
- **ENTONCES** se lee `No hay transacciones programadas` y el botón `Nueva programada`

#### Scenario: Error al cargar
- **DADO** que la API no responde
- **CUANDO** se abre la pantalla
- **ENTONCES** se muestra el aviso genérico y `Reintentar` vuelve a pedir los datos

### Requirement: Frecuencia y estado legibles
La frecuencia SHALL mostrarse como `Cada día` (`DIARIA`), `Cada semana` (`SEMANAL`),
`Cada 2 semanas` (`CADA_2_SEMANAS`), `Cada mes` (`MENSUAL`), `Cada 3 meses` (`CADA_3_MESES`) o
`Cada año` (`ANUAL`), y SHALL acompañarse de `hasta {fechaFin}` si tiene fecha de fin. El estado
SHALL decidirse en este orden y mostrarse con texto e ícono además de color:
`No se pudo generar` si está activa y tiene `ultimoError`; `Pausada` si `activa` es falso;
`Finalizada` si `proximaFecha` es `null`; si no, `Activa`. Con `ultimoError` SHALL mostrarse el
motivo precedido de `La última generación falló:` y la sugerencia `Revisa la cuenta y la
categoría y pulsa Generar ahora`.

#### Scenario: Pausada
- **DADO** una plantilla con `activa` falso
- **CUANDO** se muestra
- **ENTONCES** el estado dice `Pausada`

#### Scenario: Finalizada
- **DADO** una plantilla activa con `proximaFecha` `null` y `fechaFin` `2026-09-15`
- **CUANDO** se muestra
- **ENTONCES** el estado dice `Finalizada`, la próxima fecha `—` y la frecuencia incluye
  `hasta` el 15 de septiembre de 2026

#### Scenario: Con error
- **DADO** una plantilla activa con `ultimoError` `La cuenta está cerrada`
- **CUANDO** se muestra
- **ENTONCES** el estado dice `No se pudo generar` y se lee `La última generación falló: La cuenta
  está cerrada` con la sugerencia

### Requirement: Crear y editar en un diálogo
`Nueva programada` SHALL abrir un diálogo con: `Cuenta` (solo cuentas abiertas, obligatoria),
`Salida`/`Entrada` (por defecto `Salida`), `Monto` con `app-campo-monto` (obligatorio, mayor que
cero, como máximo 3 decimales), `Frecuencia` (obligatoria, por defecto `Cada mes`),
`Fecha de inicio` (obligatoria, hoy local por defecto), `Fecha de fin` (opcional, no anterior a
la de inicio: `La fecha de fin no puede ser anterior a la de inicio`), `Categoría` (opcional, sin
categorías de pago de tarjeta ni ocultas), `Beneficiario` (texto libre, máximo 100 caracteres) y
`Memo` (máximo 500). Al enviar SHALL hacer `POST` con el monto en milésimas con el signo del tipo,
las fechas como `yyyy-MM-dd` local y beneficiario y memo recortados (vacío como `null`). El diálogo
SHALL explicar que la plantilla no genera nada al crearla: las ocurrencias vencidas se crean en
la siguiente generación o con `Generar ahora`, y nacen `No conciliada` y sin aprobar. En las
frecuencias mensual, cada 3 meses y anual con día de inicio 29, 30 o 31 SHALL mostrarse
`En los meses sin ese día se usa el último día del mes`.

`Editar` SHALL abrir el mismo diálogo con los valores de la plantilla; la cuenta y la fecha de
inicio SHALL mostrarse en solo lectura (nombre de la cuenta aunque esté cerrada) con la nota
`Para cambiarlas, crea otra programada`, y SHALL enviar `PUT` con `monto`, `categoriaId`,
`beneficiario`, `memo`, `frecuencia` y `fechaFin`, sin `cuentaId` ni `fechaInicio`. La categoría
actual SHALL seguir disponible aunque esté oculta. El diálogo SHALL aclarar que los cambios solo
afectan a las ocurrencias futuras. Mientras envía, `Guardar` SHALL estar deshabilitado.

#### Scenario: Crear una salida mensual
- **DADO** el diálogo vacío
- **CUANDO** la persona elige `Banco`, `Salida`, escribe `1500`, `Cada mes`, inicio 5 de
  noviembre de 2026 y guarda
- **ENTONCES** se envía `POST` con `cuentaId` de `Banco`, `monto -1500000`, `frecuencia MENSUAL`,
  `fechaInicio 2026-11-05`, `fechaFin null` y el diálogo se cierra con la lista recargada

#### Scenario: Monto con expresión
- **CUANDO** la persona escribe `30+20,5` como `Entrada` en `es-BO`
- **ENTONCES** se envía `monto 50500`

#### Scenario: Fecha de fin anterior
- **DADO** inicio 5 de noviembre de 2026
- **CUANDO** la persona elige fin 1 de noviembre de 2026
- **ENTONCES** se lee `La fecha de fin no puede ser anterior a la de inicio` y no se envía nada

#### Scenario: Día 31
- **CUANDO** la persona elige `Cada mes` e inicio 31 de enero de 2027
- **ENTONCES** se lee `En los meses sin ese día se usa el último día del mes`

#### Scenario: Editar
- **DADO** una plantilla de `Banco` con inicio 5 de noviembre de 2026
- **CUANDO** la persona la edita, cambia el monto a `1600` y guarda
- **ENTONCES** ve la cuenta y la fecha de inicio sin poder cambiarlas y se envía `PUT` con
  `monto -1600000` y sin `cuentaId` ni `fechaInicio`

#### Scenario: Doble clic
- **CUANDO** la persona pulsa `Guardar` dos veces seguidas
- **ENTONCES** se envía una sola petición

### Requirement: Errores del diálogo por código
Los errores SHALL interpretarse por `codigo`, nunca por `detail`: `DATOS_INVALIDOS` con `errores`
SHALL mostrarse en cada campo; sin `errores`, `Revisa las fechas y el monto` en el diálogo;
`REGLA_NEGOCIO_VIOLADA` SHALL mostrar `No se pudo guardar: la cuenta está cerrada o la categoría
no admite transacciones.` en el diálogo, sin cerrarlo y conservando lo escrito;
`RECURSO_NO_ENCONTRADO` SHALL cerrar el diálogo, avisar `La programada, la cuenta o la categoría
ya no existe. Actualizamos los datos.` y recargar la pantalla; cualquier otro error SHALL mostrar
el aviso genérico sin cerrar el diálogo.

#### Scenario: Cuenta cerrada al guardar
- **DADO** una cuenta que otra sesión cerró
- **CUANDO** se guarda y la API responde `422`
- **ENTONCES** se lee el mensaje de la regla en el diálogo, que sigue abierto con los datos

#### Scenario: Error por campo
- **CUANDO** la API responde `400` con `errores.memo`
- **ENTONCES** el mensaje aparece bajo `Memo`

#### Scenario: Plantilla borrada en otra sesión
- **CUANDO** se edita y la API responde `404`
- **ENTONCES** el diálogo se cierra, se muestra el aviso y la lista se recarga

### Requirement: Pausar, reanudar y borrar
Cada plantilla SHALL ofrecer en su menú `Editar`, `Pausar` (si está activa) o `Reanudar` (si está
pausada) y `Borrar`. `Pausar` SHALL llamar a `POST .../{id}/pausar` y `Reanudar` a
`POST .../{id}/reanudar`, actualizar la fila con la respuesta y, al reanudar, avisar
`Reanudada. No se generan las ocurrencias del período pausado.` `Borrar` SHALL pedir confirmación
con `Las transacciones que ya generó se conservan.` (foco inicial en `Cancelar`) y, al confirmar,
llamar a `DELETE` y quitar la fila. Mientras una acción de una plantilla está en curso, sus
acciones SHALL estar deshabilitadas. Un `404` SHALL avisar y recargar la lista; otro error, el
aviso genérico.

#### Scenario: Reanudar
- **DADO** una plantilla pausada
- **CUANDO** la persona pulsa `Reanudar`
- **ENTONCES** la fila pasa a `Activa` con la `proximaFecha` de la respuesta y se lee el aviso
  sobre el período pausado

#### Scenario: Borrar
- **DADO** una plantilla con transacciones generadas
- **CUANDO** la persona pulsa `Borrar` y confirma
- **ENTONCES** se envía `DELETE`, la fila desaparece y el diálogo había dicho que las
  transacciones generadas se conservan

#### Scenario: Cancelar el borrado
- **CUANDO** la persona pulsa `Cancelar` en la confirmación
- **ENTONCES** no se envía nada

### Requirement: Generar ahora
La pantalla SHALL tener el botón `Generar ahora`, que llama a
`POST .../transacciones-programadas/generar`, queda deshabilitado mientras espera, anuncia el
resultado y recarga la lista: `Se generaron {n} transacciones` (`Se generó 1 transacción`), o
`No había ocurrencias pendientes` con `0`; si `plantillasConError` es mayor que 0, agrega
`{m} programadas no se pudieron generar`. Un error SHALL mostrar el aviso genérico.

#### Scenario: Con ocurrencias
- **CUANDO** la API responde `generadas 2` y `plantillasConError 0`
- **ENTONCES** se lee `Se generaron 2 transacciones` y la lista se recarga

#### Scenario: Idempotente
- **CUANDO** se pulsa de nuevo y la API responde `generadas 0`
- **ENTONCES** se lee `No había ocurrencias pendientes`

#### Scenario: Con errores
- **CUANDO** la API responde `generadas 1` y `plantillasConError 1`
- **ENTONCES** se lee `Se generó 1 transacción` y `1 programadas no se pudieron generar`, y la
  fila con error muestra su motivo tras recargar

### Requirement: Respuestas atrasadas, accesibilidad y pantallas estrechas
Una recarga de la lista SHALL descartar la respuesta de una recarga anterior (`switchMap`). Los
campos SHALL tener etiqueta, el foco SHALL verse en botones, menús y campos, el estado no SHALL
depender solo del color, y los avisos (resultado de generar, errores) SHALL anunciarse a los
lectores de pantalla. En un ancho de 360 px la lista SHALL mostrarse como tarjetas apiladas o
desplazarse dentro de su contenedor, sin desplazamiento horizontal de la página.

#### Scenario: Recargas superpuestas
- **DADO** una recarga en curso
- **CUANDO** se pide otra y la primera responde después
- **ENTONCES** se muestran los datos de la segunda

#### Scenario: Pantalla estrecha
- **DADO** un ancho de 360 px
- **CUANDO** se muestra la lista
- **ENTONCES** cada plantilla se lee completa sin desplazar la página a lo ancho
