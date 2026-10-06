# Spec Delta

## Purpose

Define el formulario de registro del frontend: los campos, las reglas de validación que replican
las del backend con mensajes en español, la mayoría de edad, los límites de la contraseña, el
envío de la fecha de nacimiento sin desfase de zona horaria y el manejo de los errores del API.

## ADDED Requirements

### Requirement: Formulario de registro
El sistema SHALL ofrecer en `/registro` un formulario con los campos email, contraseña, nombre,
apellido, fecha de nacimiento, teléfono (opcional) y moneda predeterminada, con las etiquetas en
español. El botón de enviar SHALL estar deshabilitado mientras el formulario sea inválido o se
esté enviando.

#### Scenario: Etiquetas en español
- **DADO** la pantalla `/registro` recién abierta
- **CUANDO** se observan los campos y el botón
- **ENTONCES** se ven `Email`, `Contraseña`, `Nombre`, `Apellido`, `Fecha de nacimiento`,
  `Teléfono (opcional)`, `Moneda predeterminada` y el botón `Crear cuenta`

#### Scenario: Formulario vacío
- **DADO** el formulario recién abierto
- **CUANDO** se observa el botón de enviar
- **ENTONCES** está deshabilitado

#### Scenario: Botón deshabilitado mientras se envía
- **DADO** un formulario válido enviado, con la respuesta todavía pendiente
- **CUANDO** se observa el botón de enviar
- **ENTONCES** está deshabilitado

#### Scenario: Enlace al inicio de sesión
- **DADO** la pantalla `/registro`
- **CUANDO** la persona pulsa `Inicia sesión`
- **ENTONCES** llega a `/login`

### Requirement: Validación del email
El email SHALL ser obligatorio, tener formato válido y no superar 254 caracteres, medido sin
espacios al inicio ni al final. Un email formado solo por espacios SHALL tratarse como vacío.

#### Scenario: Email vacío o solo espacios
- **DADO** el campo email tocado
- **CUANDO** está vacío o contiene solo espacios
- **ENTONCES** se muestra `El email es obligatorio`

#### Scenario: Email con formato inválido
- **DADO** el email `ana-arroba-ejemplo.com`
- **CUANDO** la persona sale del campo
- **ENTONCES** se muestra `Ingresa un email válido`

#### Scenario: Email con espacios alrededor
- **DADO** el email `"  ana@ejemplo.com  "`
- **CUANDO** se valida el campo
- **ENTONCES** el campo es válido

#### Scenario: Email demasiado largo
- **DADO** un email de 255 caracteres
- **CUANDO** se valida el campo
- **ENTONCES** se muestra `El email no puede superar 254 caracteres`

### Requirement: Validación de la contraseña
La contraseña SHALL ser obligatoria (no puede estar formada solo por espacios), tener al menos 8
caracteres y ocupar como máximo 72 bytes en UTF-8. El sistema SHALL NOT recortar ni modificar la
contraseña en ningún momento.

#### Scenario: Contraseña vacía o solo espacios
- **DADO** el campo de contraseña tocado
- **CUANDO** está vacío o contiene 8 espacios
- **ENTONCES** se muestra `La contraseña es obligatoria`

#### Scenario: Contraseña demasiado corta
- **DADO** la contraseña `1234567`
- **CUANDO** se valida el campo
- **ENTONCES** se muestra `La contraseña debe tener al menos 8 caracteres`

#### Scenario: Contraseña de 72 bytes
- **DADO** una contraseña de 72 caracteres ASCII
- **CUANDO** se valida el campo
- **ENTONCES** el campo es válido

#### Scenario: Caracteres multibyte dentro del límite
- **DADO** una contraseña de 36 letras `ñ`, que ocupan 72 bytes
- **CUANDO** se valida el campo
- **ENTONCES** el campo es válido

#### Scenario: Caracteres multibyte que superan el límite
- **DADO** una contraseña de 72 caracteres formada por una `ñ` y 71 letras `a`, que ocupa 73
  bytes
- **CUANDO** se valida el campo
- **ENTONCES** se muestra `La contraseña no puede ocupar más de 72 bytes (la ñ y las vocales con
  tilde ocupan 2)`

#### Scenario: La contraseña no se recorta
- **DADO** la contraseña `" secreta123 "`, con un espacio al inicio y otro al final
- **CUANDO** la persona envía el formulario
- **ENTONCES** la petición lleva la contraseña `" secreta123 "`, con sus espacios

### Requirement: Validación de nombre y apellido
El nombre y el apellido SHALL ser obligatorios y tener entre 2 y 100 caracteres, medidos sin
espacios al inicio ni al final, y SHALL enviarse sin esos espacios.

#### Scenario: Obligatorios
- **DADO** el nombre o el apellido tocado
- **CUANDO** está vacío o contiene solo espacios
- **ENTONCES** se muestra `El nombre es obligatorio` o `El apellido es obligatorio`

#### Scenario: Longitud medida después de recortar
- **DADO** el nombre `"  A  "`
- **CUANDO** se valida el campo
- **ENTONCES** se muestra `El nombre debe tener al menos 2 caracteres`

#### Scenario: Longitud máxima
- **DADO** un apellido de 101 caracteres
- **CUANDO** se valida el campo
- **ENTONCES** se muestra `El apellido no puede superar 100 caracteres`

#### Scenario: Nombre enviado recortado
- **DADO** el nombre `"  María José "` y el apellido `" Rojas  "`
- **CUANDO** la persona envía el formulario
- **ENTONCES** la petición lleva el nombre `María José` y el apellido `Rojas`

### Requirement: Fecha de nacimiento y mayoría de edad
La fecha de nacimiento SHALL ser obligatoria, elegirse o escribirse en el selector de fechas según
la región detectada, y corresponder a una persona con 18 años cumplidos a la fecha actual del
navegador, contando como cumplidos los 18 el mismo día del aniversario. Un texto que no es una
fecha existente SHALL mostrarse como fecha no válida.

#### Scenario: Fecha obligatoria
- **DADO** el campo de fecha tocado
- **CUANDO** está vacío
- **ENTONCES** se muestra `La fecha de nacimiento es obligatoria`

#### Scenario: Texto que no es una fecha existente
- **DADO** la región `es-BO`
- **CUANDO** la persona escribe `31/02/2003`
- **ENTONCES** se muestra `La fecha no es válida`

#### Scenario: Cumple 18 hoy
- **DADO** que hoy es el 6 de octubre de 2026
- **CUANDO** se elige el 6 de octubre de 2008
- **ENTONCES** la fecha es válida

#### Scenario: Cumple 18 mañana
- **DADO** que hoy es el 6 de octubre de 2026
- **CUANDO** se elige el 7 de octubre de 2008
- **ENTONCES** se muestra `Debe tener 18 años o más`

#### Scenario: Nacida un 29 de febrero
- **DADO** una persona nacida el 29 de febrero de 2008
- **CUANDO** hoy es el 28 de febrero de 2026
- **ENTONCES** se muestra `Debe tener 18 años o más`
- **Y** cuando hoy es el 1 de marzo de 2026 la fecha es válida

#### Scenario: Fecha futura
- **DADO** que hoy es el 6 de octubre de 2026
- **CUANDO** se elige el 1 de enero de 2030
- **ENTONCES** se muestra `Debe tener 18 años o más`

#### Scenario: Fecha escrita según la región
- **DADO** la región `es-BO`
- **CUANDO** la persona escribe `05/03/2003`
- **ENTONCES** la fecha elegida es el 5 de marzo de 2003
- **Y** la petición de registro lleva `fechaNacimiento` igual a `2003-03-05`

### Requirement: Fecha de nacimiento enviada sin desfase de zona horaria
El sistema SHALL enviar la fecha de nacimiento como `yyyy-MM-dd` con el año, mes y día del
calendario local en que la persona la eligió, sin conversión a UTC, de modo que el día enviado sea
el elegido en cualquier zona horaria.

#### Scenario: Zona horaria con desfase negativo
- **DADO** un navegador en la zona horaria `America/New_York`
- **CUANDO** la persona elige el 1 de octubre de 2008 y envía el formulario
- **ENTONCES** la petición lleva `fechaNacimiento` igual a `2008-10-01`

#### Scenario: Zona horaria con desfase positivo
- **DADO** un navegador en la zona horaria `Asia/Tokyo`
- **CUANDO** la persona elige el 1 de octubre de 2008 y envía el formulario
- **ENTONCES** la petición lleva `fechaNacimiento` igual a `2008-10-01`, nunca `2008-09-30`

### Requirement: Teléfono y moneda predeterminada
El teléfono SHALL ser opcional con un máximo de 20 caracteres medidos sin espacios al inicio ni al
final, y SHALL omitirse de la petición si queda vacío. La moneda predeterminada SHALL elegirse de
una lista corta de monedas comunes, con `BOB` seleccionada por defecto.

#### Scenario: Teléfono demasiado largo
- **DADO** un teléfono de 21 caracteres
- **CUANDO** se valida el campo
- **ENTONCES** se muestra `El teléfono no puede superar 20 caracteres`

#### Scenario: Teléfono de 20 caracteres con espacios alrededor
- **DADO** un teléfono de 20 caracteres con un espacio al inicio y otro al final
- **CUANDO** se valida el campo
- **ENTONCES** el campo es válido

#### Scenario: Teléfono vacío no se envía
- **DADO** el teléfono vacío o con solo espacios
- **CUANDO** la persona envía el formulario
- **ENTONCES** la petición no incluye el campo `telefono`

#### Scenario: Teléfono enviado recortado
- **DADO** el teléfono `" +591 70000000 "`
- **CUANDO** la persona envía el formulario
- **ENTONCES** la petición lleva el teléfono `+591 70000000`

#### Scenario: Moneda por defecto
- **DADO** el formulario recién abierto
- **CUANDO** se observa la moneda predeterminada
- **ENTONCES** está seleccionada `BOB`
- **Y** al enviar sin cambiarla la petición lleva `monedaPredeterminada` igual a `BOB`

#### Scenario: Moneda elegida
- **DADO** el selector de moneda
- **CUANDO** la persona elige `USD`
- **ENTONCES** la petición lleva `monedaPredeterminada` igual a `USD`

### Requirement: Datos enviados en el registro
El sistema SHALL enviar el email sin espacios al inicio ni al final y en minúsculas, y SHALL
guardar la sesión y llevar a la persona a `/` cuando el registro es exitoso, sin pasar por el
inicio de sesión.

#### Scenario: Email enviado normalizado
- **DADO** el email `"  Ana@Ejemplo.COM "`
- **CUANDO** la persona envía el formulario
- **ENTONCES** la petición lleva el email `ana@ejemplo.com`

#### Scenario: Registro exitoso
- **DADO** un formulario válido
- **CUANDO** la API responde 201 con un token
- **ENTONCES** la sesión se guarda
- **Y** la persona llega a `/`
- **Y** no se hace ninguna petición a `/api/v1/auth/login`

### Requirement: Errores del API en el registro
El sistema SHALL mostrar los errores del API según su código: `DATOS_INVALIDOS` con mapa
`errores` en el campo correspondiente, `EMAIL_YA_REGISTRADO` en el campo email, y cualquier otro
caso con el aviso genérico. Un error mostrado en un campo SHALL impedir enviar hasta que la
persona edite ese campo, y SHALL desaparecer al editarlo.

#### Scenario: Errores de campo del backend
- **DADO** un formulario enviado
- **CUANDO** la API responde 400 con código `DATOS_INVALIDOS` y `errores` con las claves
  `contrasena` y `fechaNacimiento`
- **ENTONCES** el mensaje de cada clave aparece en su campo
- **Y** el botón de enviar queda deshabilitado

#### Scenario: El error de un campo se quita al editarlo
- **DADO** un error del backend mostrado en el campo email
- **CUANDO** la persona edita el email
- **ENTONCES** el error del servidor desaparece de ese campo
- **Y** los errores de otros campos no editados siguen visibles

#### Scenario: Clave de error sin campo
- **DADO** un formulario enviado
- **CUANDO** la API responde 400 `DATOS_INVALIDOS` con una clave de `errores` que no corresponde
  a ningún campo
- **ENTONCES** se muestra el aviso genérico en un `MatSnackBar`

#### Scenario: Cuerpo rechazado sin detalle de campos
- **DADO** un formulario enviado
- **CUANDO** la API responde 400 `DATOS_INVALIDOS` sin `errores`
- **ENTONCES** se muestra el aviso genérico

#### Scenario: Email ya registrado
- **DADO** un formulario enviado
- **CUANDO** la API responde 409 con código `EMAIL_YA_REGISTRADO`
- **ENTONCES** el campo email muestra `Ya existe una cuenta con ese email`

#### Scenario: Otro error
- **DADO** un formulario enviado
- **CUANDO** la API responde 500 o la petición falla por red
- **ENTONCES** se muestra el aviso genérico
- **Y** el formulario conserva lo escrito y el botón de enviar vuelve a habilitarse
