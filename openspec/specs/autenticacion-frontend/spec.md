# autenticacion-frontend Specification

## Purpose

Define cómo el frontend guarda y usa la sesión de la persona (token con vencimiento), protege
las pantallas que requieren sesión, permite iniciar y cerrar sesión y muestra la página de
inicio, apoyándose en la API de autenticación del backend.

## Requirements

### Requirement: Sesión guardada en el navegador
El sistema SHALL guardar el token de acceso y su instante de expiración en el almacenamiento
local del navegador cuando la persona se registra o inicia sesión, y SHALL restaurar la sesión al
arrancar la aplicación solo si el token no ha expirado. Un valor guardado ilegible, incompleto o
con una expiración inválida SHALL tratarse como sin sesión y borrarse.

#### Scenario: Sesión restaurada al arrancar
- **DADO** un token guardado cuya expiración es dentro de 2 horas
- **CUANDO** arranca la aplicación
- **ENTONCES** hay sesión y la persona puede entrar a las pantallas protegidas

#### Scenario: Token expirado al arrancar
- **DADO** un token guardado cuya expiración ya pasó
- **CUANDO** arranca la aplicación
- **ENTONCES** no hay sesión
- **Y** el token guardado se borra del almacenamiento local

#### Scenario: Valor guardado ilegible
- **DADO** un valor guardado que no es un token con expiración (por ejemplo, texto cualquiera)
- **CUANDO** arranca la aplicación
- **ENTONCES** no hay sesión
- **Y** el valor guardado se borra

#### Scenario: Sin almacenamiento disponible
- **DADO** un navegador donde el almacenamiento local no se puede leer ni escribir
- **CUANDO** la persona inicia sesión
- **ENTONCES** la sesión funciona mientras la página siga abierta, sin errores

### Requirement: Token en las peticiones a la API
El sistema SHALL enviar el header `Authorization: Bearer <token>` en las peticiones a `/api/v1`
cuando hay sesión, y SHALL NOT enviarlo en peticiones a cualquier otra URL ni cuando no hay
sesión.

#### Scenario: Petición a la API con sesión
- **DADO** una sesión con el token `abc`
- **CUANDO** el sistema hace una petición a `/api/v1/usuarios/yo`
- **ENTONCES** la petición lleva el header `Authorization: Bearer abc`

#### Scenario: Petición a la API sin sesión
- **DADO** que no hay sesión
- **CUANDO** el sistema hace una petición a `/api/v1/auth/login`
- **ENTONCES** la petición no lleva el header `Authorization`

#### Scenario: Petición a otra URL
- **DADO** una sesión con el token `abc`
- **CUANDO** el sistema hace una petición a una URL que no empieza por `/api/v1`
- **ENTONCES** la petición no lleva el header `Authorization`

### Requirement: Cierre de sesión ante un 401 de la API
El sistema SHALL, ante una respuesta 401 a cualquier petición a `/api/v1` que no sea de
`/api/v1/auth` (inicio de sesión y registro), borrar la sesión y llevar a la persona a `/login`.
Las respuestas 401 del inicio de sesión SHALL NOT cerrar la sesión ni redirigir, porque significan
credenciales incorrectas.

#### Scenario: Token vencido en una petición autenticada
- **DADO** una sesión cuyo token el backend ya no acepta
- **CUANDO** una petición a `/api/v1/usuarios/yo` responde 401 con código `NO_AUTENTICADO`
- **ENTONCES** la sesión se borra
- **Y** la persona llega a `/login`

#### Scenario: 401 del inicio de sesión
- **DADO** la pantalla `/login` sin sesión
- **CUANDO** `/api/v1/auth/login` responde 401 con código `CREDENCIALES_INVALIDAS`
- **ENTONCES** la persona permanece en `/login` con el mensaje de credenciales incorrectas

#### Scenario: Otro error del servidor
- **DADO** una sesión vigente
- **CUANDO** una petición a `/api/v1/usuarios/yo` responde 500
- **ENTONCES** la sesión se conserva y la persona no es redirigida

### Requirement: Pantallas protegidas y pantallas para invitados
El sistema SHALL permitir la ruta `/` solo con sesión (sin sesión lleva a `/login`) y SHALL
permitir `/login` y `/registro` solo sin sesión (con sesión lleva a `/`). Cualquier ruta
desconocida SHALL llevar a `/`.

#### Scenario: Entrar al inicio sin sesión
- **DADO** que no hay sesión
- **CUANDO** la persona entra a `/`
- **ENTONCES** llega a `/login`

#### Scenario: Entrar al inicio con sesión
- **DADO** una sesión vigente
- **CUANDO** la persona entra a `/`
- **ENTONCES** ve la página de inicio

#### Scenario: Entrar al login con sesión
- **DADO** una sesión vigente
- **CUANDO** la persona entra a `/login`
- **ENTONCES** llega a `/`

#### Scenario: Entrar al registro con sesión
- **DADO** una sesión vigente
- **CUANDO** la persona entra a `/registro`
- **ENTONCES** llega a `/`

#### Scenario: Entrar al login y al registro sin sesión
- **DADO** que no hay sesión
- **CUANDO** la persona entra a `/login` o a `/registro`
- **ENTONCES** ve la pantalla pedida, sin redirección

#### Scenario: Ruta desconocida
- **DADO** que no hay sesión
- **CUANDO** la persona entra a `/cualquier-cosa`
- **ENTONCES** llega a `/login`, pasando por `/`

### Requirement: Campo de contraseña con mostrar y ocultar
El campo de contraseña de las pantallas de sesión SHALL mostrarla oculta por defecto y SHALL
ofrecer un botón que alterna entre mostrarla y ocultarla, con un ícono `visibility` o
`visibility_off` y una etiqueta accesible que describa la acción. Alternar SHALL NOT cambiar el
valor escrito.

#### Scenario: Contraseña oculta por defecto
- **DADO** una pantalla de sesión recién abierta
- **CUANDO** se observa el campo de contraseña
- **ENTONCES** el texto está oculto
- **Y** el botón muestra el ícono `visibility` con la etiqueta `Mostrar contraseña`

#### Scenario: Mostrar la contraseña
- **DADO** el campo de contraseña con el valor `secreta123`
- **CUANDO** la persona pulsa el botón de mostrar
- **ENTONCES** el texto `secreta123` se ve
- **Y** el botón muestra el ícono `visibility_off` con la etiqueta `Ocultar contraseña`

#### Scenario: Volver a ocultar la contraseña
- **DADO** la contraseña visible
- **CUANDO** la persona pulsa el botón de ocultar
- **ENTONCES** el texto vuelve a estar oculto y el valor sigue siendo `secreta123`

### Requirement: Inicio de sesión
El sistema SHALL ofrecer en `/login` un formulario con email y contraseña, ambos obligatorios, sin
mostrar reglas de longitud ni de formato de contraseña, con el botón de enviar deshabilitado
mientras el formulario sea inválido o se esté enviando. SHALL enviar el email sin espacios al
inicio ni al final y en minúsculas, y la contraseña sin modificar. Si la respuesta es correcta,
SHALL guardar la sesión y llevar a la persona a `/`.

#### Scenario: Campos obligatorios
- **DADO** el formulario de inicio de sesión vacío
- **CUANDO** la persona toca los campos sin escribir
- **ENTONCES** se muestran `El email es obligatorio` y `La contraseña es obligatoria`
- **Y** el botón de enviar está deshabilitado

#### Scenario: Sin reglas de formato ni de longitud
- **DADO** el email `abc` y la contraseña `x`
- **CUANDO** se completa el formulario
- **ENTONCES** el formulario es válido y no muestra ningún mensaje de formato ni de longitud

#### Scenario: Datos enviados
- **DADO** el email `"  Ana@Ejemplo.COM "` y la contraseña `" secreta123 "`
- **CUANDO** la persona envía el formulario
- **ENTONCES** la petición lleva el email `ana@ejemplo.com`
- **Y** la contraseña `" secreta123 "`, con sus espacios

#### Scenario: Inicio de sesión correcto
- **DADO** credenciales correctas
- **CUANDO** la persona envía el formulario y la API responde con un token
- **ENTONCES** la sesión se guarda
- **Y** la persona llega a `/`

#### Scenario: Botón deshabilitado mientras se envía
- **DADO** un formulario válido enviado, con la respuesta todavía pendiente
- **CUANDO** se observa el botón de enviar
- **ENTONCES** está deshabilitado

#### Scenario: Credenciales incorrectas
- **DADO** un email o una contraseña incorrectos
- **CUANDO** la API responde 401 con código `CREDENCIALES_INVALIDAS`
- **ENTONCES** el formulario muestra el mensaje general `Email o contraseña incorrectos`
- **Y** no indica si falló el email o la contraseña ni marca ningún campo
- **Y** no hay sesión

#### Scenario: Enlace al registro
- **DADO** la pantalla `/login`
- **CUANDO** la persona pulsa `Regístrate`
- **ENTONCES** llega a `/registro`

### Requirement: Mensajes de error de la API según su código
El sistema SHALL decidir el mensaje de un error de la API por el campo `codigo` de la respuesta y
nunca por su texto `detail`. `DATOS_INVALIDOS` con mapa `errores` SHALL mostrarse en el campo
correspondiente; `CREDENCIALES_INVALIDAS` como mensaje general del inicio de sesión;
`EMAIL_YA_REGISTRADO` en el campo email del registro. Cualquier otro código, una respuesta sin
cuerpo reconocible o un error de red SHALL mostrar un aviso genérico en un `MatSnackBar`.

#### Scenario: Error de campo en el inicio de sesión
- **DADO** una petición de inicio de sesión
- **CUANDO** la API responde 400 con código `DATOS_INVALIDOS` y `errores` con una clave
  `email`
- **ENTONCES** el mensaje de `errores.email` se muestra en el campo email

#### Scenario: Código desconocido
- **DADO** una petición cualquiera
- **CUANDO** la API responde 500 con un código que el frontend no conoce
- **ENTONCES** se muestra el aviso genérico `No pudimos completar la operación. Inténtalo de
  nuevo.`
- **Y** no se muestra el texto `detail` de la respuesta

#### Scenario: Error de red
- **DADO** que el servidor no responde
- **CUANDO** falla la petición, sin respuesta del servidor
- **ENTONCES** se muestra el aviso genérico

### Requirement: Página de inicio protegida
El sistema SHALL mostrar en `/` el saludo `Hola, {nombre}` con el nombre que devuelve
`GET /api/v1/usuarios/yo`, y un botón para cerrar sesión que borra la sesión y lleva a `/login`.

#### Scenario: Saludo
- **DADO** una sesión vigente y la API respondiendo el nombre `Ana`
- **CUANDO** la persona entra a `/`
- **ENTONCES** ve `Hola, Ana`

#### Scenario: Mientras carga
- **DADO** una sesión vigente y la respuesta de la API todavía pendiente
- **CUANDO** se observa la página
- **ENTONCES** se ve un indicador de carga y todavía no hay saludo

#### Scenario: Cerrar sesión
- **DADO** la página de inicio con sesión
- **CUANDO** la persona pulsa `Cerrar sesión`
- **ENTONCES** la sesión se borra del almacenamiento local
- **Y** la persona llega a `/login`

#### Scenario: Error al cargar los datos
- **DADO** una sesión vigente
- **CUANDO** `GET /api/v1/usuarios/yo` responde 500
- **ENTONCES** se muestra el aviso genérico y el texto `No pudimos cargar tus datos.`
- **Y** la sesión se conserva

### Requirement: Cabecera con el nombre de la aplicación
Las pantallas `/login`, `/registro` y `/` SHALL mostrar una barra superior (`mat-toolbar`) con el
nombre `Cada Peso`.

#### Scenario: Cabecera en las pantallas
- **DADO** cualquiera de las pantallas `/login`, `/registro` o `/`
- **CUANDO** se observa la parte superior
- **ENTONCES** se ve una barra con el texto `Cada Peso`
