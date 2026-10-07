# presupuestos-frontend Specification

## Purpose
Organiza la navegación del frontend alrededor de un presupuesto activo: lleva a la persona a uno
de sus presupuestos, le deja cambiar de presupuesto, crearlos y renombrarlos, y pone el
presupuesto actual a disposición de las pantallas de cuentas, categorías y transacciones.

## Requirements

### Requirement: Redirección al primer presupuesto
El sistema SHALL, al entrar a `/` con sesión, pedir la lista de presupuestos de la persona
(`GET /api/v1/presupuestos`, ordenada por nombre por el backend) y SHALL llevarla a
`/presupuestos/{id}` del primero de la lista, reemplazando `/` en el historial del navegador; desde
ahí SHALL abrirse la sección por defecto del presupuesto. Mientras la lista se carga SHALL mostrar
un indicador de carga.

#### Scenario: Un solo presupuesto
- **DADO** una sesión vigente, la API respondiendo un único presupuesto con id `7` y hoy en
  octubre de 2026
- **CUANDO** la persona entra a `/`
- **ENTONCES** llega a `/presupuestos/7/presupuesto/2026-10`

#### Scenario: Varios presupuestos
- **DADO** una sesión vigente y la API respondiendo `casa` (id `3`) y `Viajes` (id `1`), en ese
  orden
- **CUANDO** la persona entra a `/`
- **ENTONCES** llega al presupuesto `3`

#### Scenario: Sin volver a la redirección con el botón Atrás
- **DADO** que la persona entró a `/` y fue llevada al presupuesto `3`
- **CUANDO** se observa el historial del navegador
- **ENTONCES** `/` no quedó como una entrada propia

#### Scenario: Mientras carga
- **DADO** una sesión vigente y la lista de presupuestos todavía pendiente
- **CUANDO** se observa `/`
- **ENTONCES** se ve un indicador de carga

### Requirement: Persona sin presupuestos
El sistema SHALL, cuando la lista de presupuestos llega vacía, permanecer en `/` y mostrar un
mensaje que explique que todavía no hay presupuestos y un botón `Crear mi primer presupuesto`
que abre el diálogo de crear. Al crearse, SHALL llevar a la persona a `/presupuestos/{id}` del
presupuesto nuevo.

#### Scenario: Lista vacía
- **DADO** una sesión vigente y la API respondiendo una lista vacía
- **CUANDO** la persona entra a `/`
- **ENTONCES** permanece en `/`
- **Y** ve el botón `Crear mi primer presupuesto`

#### Scenario: Crear el primer presupuesto
- **DADO** la pantalla sin presupuestos
- **CUANDO** la persona pulsa `Crear mi primer presupuesto`, escribe `Casa` y confirma, y la
  API responde `201` con el id `9`
- **ENTONCES** llega a `/presupuestos/9`

### Requirement: Error al cargar la lista de presupuestos
El sistema SHALL, si la lista de presupuestos no se puede cargar por un error distinto de `401`,
mostrar el aviso genérico `No pudimos completar la operación. Inténtalo de nuevo.` en un
`MatSnackBar` y ofrecer `Reintentar`, que vuelve a pedir la lista. Un `401` SHALL NOT mostrar
aviso, porque la sesión ya se cerró y la persona fue llevada a `/login`.

#### Scenario: Error del servidor
- **DADO** una sesión vigente
- **CUANDO** `GET /api/v1/presupuestos` responde `500`
- **ENTONCES** se muestra el aviso genérico con la opción `Reintentar`
- **Y** la sesión se conserva

#### Scenario: Reintentar
- **DADO** que la lista falló con `500`
- **CUANDO** la persona pulsa `Reintentar` y la API responde un presupuesto con id `7`
- **ENTONCES** llega a `/presupuestos/7`

#### Scenario: 401 al cargar la lista
- **DADO** una sesión cuyo token el backend ya no acepta
- **CUANDO** `GET /api/v1/presupuestos` responde `401`
- **ENTONCES** no se muestra ningún aviso
- **Y** la persona llega a `/login`

### Requirement: Presupuesto activo según la URL
El sistema SHALL tomar como presupuesto activo el que indica `:presupuestoId` en
`/presupuestos/:presupuestoId`, buscándolo en la lista de presupuestos de la persona. Si el id no
está en la lista (no existe o es de otra persona), SHALL llevar a la persona a `/`. Las rutas
bajo `/presupuestos/` SHALL exigir sesión igual que `/`.

#### Scenario: Entrar a un presupuesto propio
- **DADO** una persona con los presupuestos `Casa` (id `3`, `BOB`) y `Viajes` (id `1`, `USD`)
- **CUANDO** entra a `/presupuestos/1`
- **ENTONCES** el presupuesto activo es `Viajes`, con id `1` y moneda `USD`

#### Scenario: Id inexistente o ajeno
- **DADO** una persona con los presupuestos de id `3` y `1`
- **CUANDO** entra a `/presupuestos/99`
- **ENTONCES** llega a `/` y desde ahí a `/presupuestos/3`

#### Scenario: Sin sesión
- **DADO** que no hay sesión
- **CUANDO** la persona entra a `/presupuestos/3`
- **ENTONCES** llega a `/login`

### Requirement: Presupuesto activo disponible para otras pantallas
El sistema SHALL mantener en un único lugar compartido el presupuesto activo (id, nombre y
moneda) para que las pantallas de las demás features lo lean sin depender de la feature de
presupuestos, y SHALL vaciarlo al cerrar sesión, ya sea por el botón `Cerrar sesión` o por un
`401` de la API.

#### Scenario: Presupuesto fijado al entrar
- **DADO** una persona que entra a `/presupuestos/3`, de nombre `Casa` y moneda `BOB`
- **CUANDO** otra pantalla consulta el presupuesto activo
- **ENTONCES** obtiene id `3`, nombre `Casa` y moneda `BOB`

#### Scenario: Presupuesto vacío al cerrar sesión
- **DADO** un presupuesto activo
- **CUANDO** se cierra la sesión
- **ENTONCES** no hay presupuesto activo

### Requirement: Selector de presupuesto en la cabecera
El sistema SHALL mostrar en la cabecera de `/presupuestos/:presupuestoId` un selector con todos
los presupuestos de la persona, con el activo seleccionado. Elegir otro SHALL llevar a la
persona a `/presupuestos/{id}` de ese presupuesto y SHALL cambiar el presupuesto activo.

#### Scenario: Selector con el activo
- **DADO** una persona en `/presupuestos/3` con los presupuestos `Casa` (id `3`) y `Viajes`
  (id `1`)
- **CUANDO** abre el selector
- **ENTONCES** ve `Casa` y `Viajes`, con `Casa` seleccionado

#### Scenario: Cambiar de presupuesto
- **DADO** una persona en `/presupuestos/3`
- **CUANDO** elige `Viajes` en el selector
- **ENTONCES** llega a `/presupuestos/1`
- **Y** el presupuesto activo es `Viajes`

### Requirement: Crear un presupuesto desde la cabecera
El sistema SHALL ofrecer en la cabecera una acción para crear un presupuesto que abre un diálogo
con el campo nombre y un selector de moneda con las opciones `Moneda de mi perfil` (seleccionada
por defecto), `BOB`, `USD`, `EUR`, `ARS`, `BRL`, `CLP` y `PEN`. Con `Moneda de mi perfil` la
petición SHALL NOT incluir moneda (el backend usa la del perfil); con otra opción SHALL incluir
ese código. El nombre SHALL enviarse sin espacios al inicio ni al final. Al crearse, el diálogo
SHALL cerrarse y la persona SHALL llegar a `/presupuestos/{id}` del presupuesto nuevo, que
aparece en el selector.

#### Scenario: Crear sin elegir moneda
- **DADO** el diálogo de crear abierto
- **CUANDO** la persona escribe `"  Casa "` y confirma sin cambiar la moneda
- **ENTONCES** la petición lleva el nombre `Casa` y no lleva moneda

#### Scenario: Crear con moneda
- **DADO** el diálogo de crear abierto
- **CUANDO** la persona escribe `Viajes`, elige `USD` y confirma
- **ENTONCES** la petición lleva el nombre `Viajes` y la moneda `USD`

#### Scenario: Llegar al presupuesto creado
- **DADO** el diálogo de crear con datos válidos
- **CUANDO** la API responde `201` con el id `9`
- **ENTONCES** el diálogo se cierra
- **Y** la persona llega a `/presupuestos/9`

### Requirement: Renombrar el presupuesto activo
El sistema SHALL ofrecer en la cabecera una acción para renombrar el presupuesto activo que abre
el mismo diálogo con el nombre actual ya escrito y sin el campo de moneda. Al renombrarse, el
diálogo SHALL cerrarse y el nombre nuevo SHALL verse en el selector y en el presupuesto activo,
sin cambiar de URL.

#### Scenario: Diálogo de renombrar
- **DADO** el presupuesto activo `Casa`
- **CUANDO** la persona abre la acción de renombrar
- **ENTONCES** el campo nombre muestra `Casa`
- **Y** no hay campo de moneda

#### Scenario: Renombre exitoso
- **DADO** el diálogo de renombrar del presupuesto `Casa` (id `3`)
- **CUANDO** la persona escribe `Hogar`, confirma y la API responde `200`
- **ENTONCES** la petición es `PUT /api/v1/presupuestos/3` con nombre `Hogar`
- **Y** el selector y el presupuesto activo muestran `Hogar`
- **Y** la persona sigue en `/presupuestos/3`

### Requirement: Validación del diálogo de presupuesto
El diálogo SHALL exigir un nombre de 1 a 100 caracteres medidos sin los espacios de los extremos,
con los mensajes `El nombre es obligatorio` y `El nombre no puede superar los 100 caracteres`, y
SHALL mantener deshabilitado el botón de confirmar mientras el formulario sea inválido o se esté
enviando.

#### Scenario: Nombre vacío o solo espacios
- **DADO** el diálogo abierto
- **CUANDO** la persona deja el nombre en `"   "` y sale del campo
- **ENTONCES** se muestra `El nombre es obligatorio`
- **Y** el botón de confirmar está deshabilitado

#### Scenario: Nombre de más de 100 caracteres
- **DADO** el diálogo abierto
- **CUANDO** la persona escribe un nombre de 101 caracteres
- **ENTONCES** se muestra `El nombre no puede superar los 100 caracteres`

#### Scenario: Botón deshabilitado mientras se envía
- **DADO** un formulario válido enviado, con la respuesta todavía pendiente
- **CUANDO** se observa el botón de confirmar
- **ENTONCES** está deshabilitado

### Requirement: Errores del diálogo según su código
El diálogo SHALL decidir cómo mostrar un error de la API por su `codigo`, nunca por `detail`:
`PRESUPUESTO_YA_EXISTE` SHALL mostrarse en el campo nombre como `Ya tienes un presupuesto con
ese nombre`; `DATOS_INVALIDOS` con `errores` SHALL mostrar cada mensaje en su campo; cualquier
otro error SHALL mostrar el aviso genérico en un `MatSnackBar`. En todos los casos el diálogo
SHALL quedar abierto con lo escrito.

#### Scenario: Nombre repetido
- **DADO** una persona con un presupuesto `Casa`
- **CUANDO** crea o renombra otro con el nombre `casa` y la API responde `409` con código
  `PRESUPUESTO_YA_EXISTE`
- **ENTONCES** el campo nombre muestra `Ya tienes un presupuesto con ese nombre`
- **Y** el diálogo sigue abierto

#### Scenario: Error de campo del backend
- **DADO** el diálogo de crear
- **CUANDO** la API responde `400` con código `DATOS_INVALIDOS` y `errores.moneda`
- **ENTONCES** el mensaje de `errores.moneda` se muestra en el campo moneda

#### Scenario: Error desconocido
- **DADO** el diálogo abierto
- **CUANDO** la API responde `500`
- **ENTONCES** se muestra el aviso genérico
- **Y** el diálogo sigue abierto

### Requirement: Estructura de la pantalla del presupuesto
El sistema SHALL mostrar en `/presupuestos/:presupuestoId` la cabecera `Cada Peso` con el
selector, las acciones de crear y renombrar y el botón `Cerrar sesión`; debajo, un menú lateral
con los enlaces de las secciones del presupuesto (`Presupuesto`, `Inicio`, `Cuentas`,
`Transacciones` y `Categorías`, en ese orden) y, junto a él, el contenido de la sección elegida.
La sección por defecto SHALL ser `Presupuesto`: `/presupuestos/:presupuestoId` SHALL llevar a
`/presupuestos/:presupuestoId/presupuesto`, que a su vez lleva al mes actual. En pantallas anchas
el menú SHALL estar siempre visible; en pantallas estrechas SHALL ocultarse y abrirse sobre el
contenido con un botón de menú en la cabecera, cerrándose al elegir un enlace. `Cerrar sesión`
SHALL borrar la sesión y llevar a `/login`.

#### Scenario: Sección por defecto
- **DADO** una persona con el presupuesto `3` y hoy en octubre de 2026
- **CUANDO** entra a `/presupuestos/3`
- **ENTONCES** llega a `/presupuestos/3/presupuesto/2026-10`

#### Scenario: Pantalla ancha
- **DADO** una pantalla de escritorio en el presupuesto `3`
- **CUANDO** se observa la pantalla
- **ENTONCES** el menú lateral con los enlaces `Presupuesto`, `Inicio`, `Cuentas`,
  `Transacciones` y `Categorías` está visible
- **Y** no hay botón de menú en la cabecera

#### Scenario: Pantalla estrecha
- **DADO** una pantalla de teléfono en el presupuesto `3`
- **CUANDO** la persona pulsa el botón de menú de la cabecera
- **ENTONCES** el menú lateral se abre sobre el contenido
- **Y** al pulsar `Inicio` el menú se cierra

#### Scenario: Cerrar sesión desde la cabecera
- **DADO** una persona en el presupuesto `3`
- **CUANDO** pulsa `Cerrar sesión`
- **ENTONCES** la sesión se borra del almacenamiento local
- **Y** no hay presupuesto activo
- **Y** la persona llega a `/login`
