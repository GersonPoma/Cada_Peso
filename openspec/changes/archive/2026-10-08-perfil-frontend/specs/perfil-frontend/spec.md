# Spec Delta

## Purpose

Define la pantalla de perfil de la persona autenticada: cómo muestra sus datos, cómo cambia su
nombre y su contraseña apoyándose en la API de perfil del backend, y qué ocurre con la sesión al
cambiar la contraseña.

## ADDED Requirements

### Requirement: Pantalla de perfil dentro del presupuesto
El sistema SHALL mostrar la pantalla de perfil en `/presupuestos/:presupuestoId/perfil`, con la
cabecera del presupuesto, protegida por la sesión, y SHALL ofrecer el acceso `Mi perfil` en la
cabecera. La ruta SHALL NOT tener enlace en el menú lateral.

#### Scenario: Entrar desde la cabecera
- **DADO** una persona con sesión en el presupuesto `3`
- **CUANDO** pulsa `Mi perfil` en la cabecera
- **ENTONCES** llega a `/presupuestos/3/perfil` y ve el título `Mi perfil`

#### Scenario: El acceso es accesible sin texto visible
- **DADO** la cabecera del presupuesto
- **CUANDO** se observa el acceso al perfil
- **ENTONCES** es un botón de ícono `account_circle` con la etiqueta accesible `Mi perfil`

#### Scenario: Sin sesión
- **DADO** una persona sin sesión
- **CUANDO** abre `/presupuestos/3/perfil`
- **ENTONCES** llega a `/login`

### Requirement: Datos del perfil
El sistema SHALL consultar `GET /api/v1/usuarios/yo` al abrir la pantalla y SHALL mostrar el
nombre (editable), el correo, el apellido, la fecha de nacimiento, el teléfono, la moneda
predeterminada y el presupuesto actual. El correo y los demás datos SHALL mostrarse solo como
texto, sin ningún control para editarlos, y el rol SHALL NOT mostrarse.

#### Scenario: Datos mostrados
- **DADO** la API respondiendo correo `ana@ejemplo.com`, nombre `Ana`, apellido `Pérez`, nacida
  el `2003-03-05`, sin teléfono y moneda `BOB`, y el presupuesto actual `Casa` en `USD`
- **CUANDO** la persona abre su perfil
- **ENTONCES** ve el correo `ana@ejemplo.com`, el apellido `Pérez`, la fecha con el formato de
  su región, el teléfono como `No registrado` y la moneda predeterminada `BOB · Boliviano`
- **Y** ve el presupuesto actual `Casa (USD)`
- **Y** el campo de nombre contiene `Ana`

#### Scenario: Sin controles de edición para el resto
- **DADO** el perfil cargado
- **CUANDO** se observan los controles de entrada
- **ENTONCES** los únicos campos editables son el nombre y las tres contraseñas
- **Y** el correo y la moneda no son campos de entrada

#### Scenario: Mientras carga
- **DADO** la respuesta de `GET /api/v1/usuarios/yo` pendiente
- **CUANDO** se observa la pantalla
- **ENTONCES** se ve un indicador de carga y todavía no hay formularios

#### Scenario: Error al cargar con Reintentar
- **DADO** `GET /api/v1/usuarios/yo` respondiendo 500
- **CUANDO** se observa la pantalla
- **ENTONCES** se ve `No pudimos cargar tus datos.` en un aviso anunciado a lectores de pantalla
- **Y** un botón `Reintentar` que, al pulsarlo, repite la petición
- **Y** la sesión se conserva

#### Scenario: Token vencido al cargar
- **DADO** una sesión cuyo token el backend ya no acepta
- **CUANDO** `GET /api/v1/usuarios/yo` responde 401
- **ENTONCES** la sesión se borra y la persona llega a `/login`, sin aviso genérico

### Requirement: Nombre de la moneda junto al código
El sistema SHALL mostrar una moneda como su código ISO 4217 seguido de su nombre en el idioma de
la región detectada (`USD · Dólar estadounidense`), con la primera letra en mayúscula. Si el
navegador no conoce el nombre o el código no es válido, SHALL mostrar solo el código, sin lanzar
errores.

#### Scenario: Nombre en la región del usuario
- **DADO** la región `es-BO` y el código `USD`
- **CUANDO** se formatea la moneda
- **ENTONCES** el resultado es `USD · Dólar estadounidense`

#### Scenario: Nombre en otra región
- **DADO** la región `en-US` y el código `USD`
- **CUANDO** se formatea la moneda
- **ENTONCES** el resultado es `USD · US Dollar`

#### Scenario: Código sin nombre conocido
- **DADO** un código sin nombre en el navegador, o un valor que no es un código válido
- **CUANDO** se formatea la moneda
- **ENTONCES** el resultado es solo el código recibido, sin errores

### Requirement: Cambio de nombre
El sistema SHALL validar el nombre con las reglas del registro (obligatorio, de 2 a 100
caracteres medidos sin espacios al inicio ni al final) y SHALL enviarlo recortado con
`PUT /api/v1/usuarios/yo` y cuerpo `{ "nombre": ... }`. El botón `Guardar nombre` SHALL estar
deshabilitado si el nombre es inválido, igual al actual (ya recortado) o mientras se envía. El
campo SHALL mostrar un contador `n/100` con el largo ya recortado.

#### Scenario: Nombre enviado recortado
- **DADO** el nombre actual `Ana`
- **CUANDO** la persona escribe `"  María José "` y pulsa `Guardar nombre`
- **ENTONCES** la petición `PUT /api/v1/usuarios/yo` lleva el cuerpo `{ "nombre": "María José" }`

#### Scenario: Botón deshabilitado sin cambios
- **DADO** el nombre actual `Ana`
- **CUANDO** el campo contiene `Ana` o `"  Ana  "`
- **ENTONCES** el botón `Guardar nombre` está deshabilitado

#### Scenario: Botón deshabilitado si es inválido
- **DADO** el campo de nombre tocado
- **CUANDO** está vacío, es solo espacios, es `"  A  "` o tiene 101 caracteres
- **ENTONCES** se muestra `El nombre es obligatorio`, `El nombre debe tener al menos 2
  caracteres` o `El nombre no puede superar 100 caracteres`, según el caso
- **Y** el botón está deshabilitado

#### Scenario: Contador
- **DADO** el campo de nombre
- **CUANDO** contiene `"  María José "`
- **ENTONCES** el contador muestra `10/100`

#### Scenario: No se envía dos veces
- **DADO** un cambio de nombre en curso
- **CUANDO** la persona pulsa el botón otra vez antes de la respuesta
- **ENTONCES** solo se envía una petición y el botón permanece deshabilitado

#### Scenario: Guardado exitoso
- **DADO** la API respondiendo 200 con el nombre `María José`
- **CUANDO** se completa el cambio
- **ENTONCES** se muestra el aviso `Nombre actualizado`
- **Y** la pantalla muestra el nombre nuevo sin recargar
- **Y** el botón queda deshabilitado de nuevo

#### Scenario: Inicio refleja el nombre nuevo
- **DADO** un nombre cambiado a `María José`
- **CUANDO** la persona abre `Inicio`
- **ENTONCES** ve `Hola, María José` porque Inicio consulta el perfil cada vez que se abre

### Requirement: Errores del cambio de nombre
El sistema SHALL decidir el mensaje por el campo `codigo` de la respuesta: `DATOS_INVALIDOS` con
`errores.nombre` en el campo de nombre, y cualquier otro código, una clave de `errores` sin
campo o un error de red con el aviso genérico. Un 401 SHALL dejarlo al cierre de sesión del
interceptor, sin aviso.

#### Scenario: Error de campo del backend
- **DADO** un cambio de nombre enviado
- **CUANDO** la API responde 400 `DATOS_INVALIDOS` con `errores.nombre`
- **ENTONCES** ese mensaje se muestra en el campo de nombre
- **Y** el botón queda deshabilitado hasta que la persona edite el campo

#### Scenario: Otro error
- **DADO** un cambio de nombre enviado
- **CUANDO** la API responde 500
- **ENTONCES** se muestra el aviso genérico `No pudimos completar la operación. Inténtalo de
  nuevo.`
- **Y** lo escrito se conserva y el botón se habilita de nuevo

#### Scenario: Token vencido
- **DADO** un cambio de nombre enviado
- **CUANDO** la API responde 401
- **ENTONCES** la sesión se borra y la persona llega a `/login`, sin aviso genérico

### Requirement: Formulario de cambio de contraseña
El sistema SHALL ofrecer tres campos de contraseña: `Contraseña actual`, `Contraseña nueva` y
`Confirmar contraseña nueva`, cada uno con el botón de mostrar y ocultar del campo de contraseña
existente y con `autocomplete` `current-password`, `new-password` y `new-password`. El botón
`Cambiar contraseña` SHALL estar deshabilitado si el formulario es inválido o mientras se envía.
Las contraseñas SHALL NOT recortarse ni modificarse.

#### Scenario: Campos y autocompletado
- **DADO** el perfil cargado
- **CUANDO** se observa el formulario de contraseña
- **ENTONCES** hay tres campos de contraseña ocultos por defecto, con sus etiquetas visibles y
  los `autocomplete` `current-password`, `new-password` y `new-password`

#### Scenario: Obligatorios
- **DADO** los tres campos tocados
- **CUANDO** están vacíos o solo tienen espacios
- **ENTONCES** se muestra `La contraseña es obligatoria` en cada uno
- **Y** el botón está deshabilitado

#### Scenario: Petición con el cuerpo exacto
- **DADO** la actual `secreta123`, la nueva `" otraClave456 "` y la confirmación igual a la nueva
- **CUANDO** la persona pulsa `Cambiar contraseña`
- **ENTONCES** la petición `POST /api/v1/usuarios/yo/contrasena` lleva exactamente
  `{ "contrasenaActual": "secreta123", "contrasenaNueva": " otraClave456 " }`, con sus espacios
- **Y** la confirmación no se envía

### Requirement: Contraseña nueva con las reglas del registro
El sistema SHALL validar la contraseña nueva con la misma regla de la contraseña del registro
(obligatoria, al menos 8 caracteres y como máximo 72 bytes en UTF-8), definida una sola vez y
usada por ambas pantallas. La contraseña nueva SHALL ser distinta de la actual y la confirmación
SHALL coincidir con la nueva, ambas comprobaciones solo en el frontend. La comprobación "distinta
de la actual" ocurre únicamente en el frontend, antes de enviar; por eso un 422
`REGLA_NEGOCIO_VIOLADA` de este formulario solo puede significar contraseña actual incorrecta.

#### Scenario: Demasiado corta
- **DADO** la contraseña nueva `1234567`
- **CUANDO** se valida el campo
- **ENTONCES** se muestra `La contraseña debe tener al menos 8 caracteres`

#### Scenario: Multibyte dentro y fuera del límite
- **DADO** una contraseña nueva de 36 letras `ñ` (72 bytes) y otra de 37 letras `ñ` (74 bytes)
- **CUANDO** se valida cada una
- **ENTONCES** la primera es válida
- **Y** la segunda muestra `La contraseña no puede ocupar más de 72 bytes (la ñ y las vocales
  con tilde ocupan 2)`

#### Scenario: Misma regla que el registro
- **DADO** cualquier valor
- **CUANDO** se valida como contraseña nueva y como contraseña del registro
- **ENTONCES** ambas pantallas dan el mismo resultado y el mismo mensaje

#### Scenario: Confirmación distinta
- **DADO** la nueva `otraClave456` y la confirmación `otraClave457`
- **CUANDO** la confirmación está tocada
- **ENTONCES** se muestra `Las contraseñas no coinciden` y el botón está deshabilitado

#### Scenario: La confirmación se revalida al cambiar la nueva
- **DADO** una confirmación igual a la nueva
- **CUANDO** la persona edita la nueva
- **ENTONCES** la confirmación pasa a mostrar `Las contraseñas no coinciden`

#### Scenario: Nueva igual a la actual
- **DADO** la actual `secreta123`
- **CUANDO** la nueva es `secreta123`
- **ENTONCES** se muestra `La contraseña nueva debe ser distinta de la actual` en la nueva
- **Y** no se envía ninguna petición

### Requirement: Cambio de contraseña exitoso cierra la sesión local
El sistema SHALL, al recibir `204`, cerrar la sesión con el mismo mecanismo del botón `Cerrar
sesión` (borrar la sesión y el presupuesto activo), llevar a `/login` y mostrar el aviso
`Contraseña cambiada, inicia sesión de nuevo`. El sistema SHALL NOT enviar ninguna otra petición
para invalidar el token anterior: ese token sigue siendo válido en el servidor hasta expirar
(24 horas), así que el cierre afecta solo a este navegador.

#### Scenario: Éxito
- **DADO** un cambio de contraseña correcto
- **CUANDO** la API responde 204
- **ENTONCES** la sesión se borra del almacenamiento local y no hay presupuesto activo
- **Y** la persona llega a `/login`
- **Y** se muestra el aviso `Contraseña cambiada, inicia sesión de nuevo`

#### Scenario: El aviso sigue visible después de navegar a /login
- **DADO** un cambio de contraseña correcto
- **CUANDO** la navegación a `/login` termina
- **ENTONCES** el aviso `Contraseña cambiada, inicia sesión de nuevo` sigue visible en la pantalla
  de inicio de sesión, aunque la pantalla de perfil ya no exista

#### Scenario: Sin peticiones extra
- **DADO** un cambio de contraseña correcto
- **CUANDO** termina
- **ENTONCES** la única petición enviada fue `POST /api/v1/usuarios/yo/contrasena`

#### Scenario: Iniciar sesión con la contraseña nueva
- **DADO** una persona que cambió su contraseña
- **CUANDO** inicia sesión con la nueva
- **ENTONCES** el flujo normal de inicio de sesión funciona

### Requirement: Errores del cambio de contraseña
El sistema SHALL decidir el mensaje por `codigo`. `REGLA_NEGOCIO_VIOLADA` SHALL mostrar `La
contraseña actual es incorrecta` junto al campo de contraseña actual, sin cerrar la sesión ni
redirigir, vaciando solo ese campo y conservando la nueva y la confirmación. `DATOS_INVALIDOS`
con `errores` SHALL mostrarse en `contrasenaActual` o `contrasenaNueva`. Cualquier otro caso,
una clave sin campo o un error de red SHALL mostrar el aviso genérico. Solo el `204` o un 401
real SHALL cerrar la sesión.

#### Scenario: Actual incorrecta
- **DADO** un cambio de contraseña enviado
- **CUANDO** la API responde 422 con código `REGLA_NEGOCIO_VIOLADA`
- **ENTONCES** el campo de contraseña actual muestra `La contraseña actual es incorrecta` y queda
  vacío con el foco puesto en él
- **Y** la nueva y la confirmación conservan lo escrito
- **Y** la sesión se conserva y la persona sigue en el perfil

#### Scenario: El 422 no dispara el cierre de sesión
- **DADO** una sesión vigente
- **CUANDO** la API responde 422 al cambio de contraseña
- **ENTONCES** la sesión no se borra y no hay redirección a `/login`

#### Scenario: La decisión no depende del texto del servidor
- **DADO** un 422 `REGLA_NEGOCIO_VIOLADA` con cualquier `detail`
- **CUANDO** se muestra el error
- **ENTONCES** el mensaje es el fijo del frontend y no aparece el texto `detail`

#### Scenario: El error se quita al editar
- **DADO** el error de contraseña actual mostrado
- **CUANDO** la persona escribe en ese campo
- **ENTONCES** el error desaparece

#### Scenario: Errores de campo del backend
- **DADO** un cambio de contraseña enviado
- **CUANDO** la API responde 400 `DATOS_INVALIDOS` con `errores.contrasenaNueva`
- **ENTONCES** el mensaje se muestra en la contraseña nueva, con el foco en el primer campo con
  error
- **Y** la sesión se conserva

#### Scenario: Clave de error sin campo
- **DADO** un cambio de contraseña enviado
- **CUANDO** la API responde 400 `DATOS_INVALIDOS` con una clave que no es de ningún campo
- **ENTONCES** se muestra el aviso genérico

#### Scenario: Otro error
- **DADO** un cambio de contraseña enviado
- **CUANDO** la API responde 500 o falla la red
- **ENTONCES** se muestra el aviso genérico y la sesión se conserva

#### Scenario: Token vencido
- **DADO** un cambio de contraseña enviado
- **CUANDO** la API responde 401
- **ENTONCES** la sesión se borra y la persona llega a `/login`, sin aviso genérico

### Requirement: Las contraseñas no se conservan ni se registran
El sistema SHALL limpiar los campos de contraseña del formulario tras un envío exitoso, SHALL
vaciar el campo de contraseña actual tras un error de ese campo, y SHALL NOT guardar las
contraseñas fuera del formulario ni escribirlas en la consola.

#### Scenario: Formulario limpio tras el éxito
- **DADO** un cambio de contraseña correcto
- **CUANDO** la API responde 204
- **ENTONCES** los tres campos del formulario quedan vacíos

#### Scenario: Sin escritura en consola
- **DADO** cualquier cambio de contraseña, correcto o rechazado
- **CUANDO** termina
- **ENTONCES** no se llamó a ningún método de `console`

#### Scenario: Sin estado persistente
- **DADO** un cambio de contraseña enviado
- **CUANDO** se inspecciona el almacenamiento local
- **ENTONCES** no contiene ninguna de las contraseñas

### Requirement: Accesibilidad y pantallas estrechas
El sistema SHALL etiquetar cada campo, asociar cada mensaje de error a su campo, poner el foco
en el primer campo con error tras un rechazo del servidor y anunciar los avisos a lectores de
pantalla; ningún estado SHALL depender solo del color. En pantallas estrechas el contenido SHALL
apilarse en una sola columna.

#### Scenario: Etiquetas y errores asociados
- **DADO** el perfil cargado y un campo con error
- **CUANDO** se observa el campo
- **ENTONCES** tiene una etiqueta visible
- **Y** su mensaje de error se anuncia como descripción del campo y se muestra como texto

#### Scenario: Avisos anunciados
- **DADO** un cambio de nombre exitoso o un error genérico
- **CUANDO** se muestra el aviso
- **ENTONCES** se hace en un `MatSnackBar`, que lo anuncia a lectores de pantalla

#### Scenario: Pantalla estrecha
- **DADO** una pantalla de teléfono
- **CUANDO** se observa el perfil
- **ENTONCES** los datos, el formulario de nombre y el de contraseña se apilan en una columna sin
  desplazamiento horizontal

### Requirement: Cerrar sesión sigue en la cabecera
La pantalla de perfil SHALL NOT repetir el botón `Cerrar sesión`: la persona lo tiene en la
cabecera del presupuesto, visible en la misma pantalla.

#### Scenario: Sin botón de cerrar sesión propio
- **DADO** la pantalla de perfil, sin la cabecera del layout
- **CUANDO** se observa su contenido
- **ENTONCES** no hay ningún botón `Cerrar sesión`
