# registro-usuarios Specification

## Purpose

Permite que una persona mayor de edad se dé de alta en Cada Peso con sus credenciales y sus datos
personales, validando esos datos antes de crear la cuenta.

## Requirements

### Requirement: Registro de una persona con credenciales y perfil
El sistema SHALL permitir registrarse enviando email, contraseña, nombre, apellido, fecha de
nacimiento y, opcionalmente, teléfono y moneda predeterminada. Si los datos son válidos, el
sistema SHALL crear las credenciales y el perfil juntos, de forma atómica, asignar el rol
`USUARIO` y responder `201` con un token de acceso válido para esa persona.

#### Scenario: Registro exitoso
- **DADO** que no existe ninguna cuenta con el email `ana@ejemplo.com`
- **CUANDO** una persona mayor de edad se registra con ese email y datos válidos
- **ENTONCES** el sistema responde `201` con un token de acceso
- **Y** ese token permite consultar los datos del usuario autenticado, con rol `USUARIO`

#### Scenario: Un fallo al guardar el perfil no deja credenciales huérfanas
- **DADO** un registro con datos válidos
- **CUANDO** falla la creación del perfil después de crear las credenciales
- **ENTONCES** no queda guardada ninguna cuenta con ese email

### Requirement: Valores por defecto y opcionales del registro
Si el registro no incluye moneda predeterminada, el sistema SHALL usar `BOB`. Si no incluye
teléfono, el perfil SHALL quedar sin teléfono.

#### Scenario: Registro sin moneda ni teléfono
- **DADO** un registro válido sin moneda predeterminada ni teléfono
- **CUANDO** la persona se registra
- **ENTONCES** su perfil tiene `BOB` como moneda predeterminada y no tiene teléfono

#### Scenario: Registro con moneda explícita
- **DADO** un registro válido con moneda predeterminada `USD`
- **CUANDO** la persona se registra
- **ENTONCES** su perfil tiene `USD` como moneda predeterminada

### Requirement: Email único sin distinguir mayúsculas ni espacios
El sistema SHALL guardar el email sin espacios al inicio ni al final y en minúsculas, y SHALL
validarlo ya normalizado. El sistema SHALL rechazar con `409` y código `EMAIL_YA_REGISTRADO` un
registro cuyo email, una vez normalizado, ya pertenece a otra cuenta.

#### Scenario: El email se guarda recortado y en minúsculas
- **DADO** un registro válido con email `"  Ana@Ejemplo.COM  "`
- **CUANDO** la persona se registra
- **ENTONCES** el registro se acepta
- **Y** los datos del usuario autenticado muestran el email `ana@ejemplo.com`

#### Scenario: Email ya registrado con otras mayúsculas y espacios
- **DADO** que existe una cuenta con el email `ana@ejemplo.com`
- **CUANDO** alguien intenta registrarse con `" ANA@ejemplo.com"`
- **ENTONCES** el sistema responde `409` con código `EMAIL_YA_REGISTRADO`
- **Y** no se crea ninguna cuenta nueva

### Requirement: Nombre y apellido sin espacios sobrantes
El sistema SHALL guardar el nombre y el apellido sin espacios al inicio ni al final, conservando
sus mayúsculas y sus espacios internos, y SHALL aplicar las longitudes mínima y máxima al valor
ya recortado.

#### Scenario: Nombre y apellido se guardan recortados
- **DADO** un registro válido con nombre `"  María José "` y apellido `" Rojas  "`
- **CUANDO** la persona se registra
- **ENTONCES** los datos del usuario autenticado muestran el nombre `María José` y el apellido
  `Rojas`

#### Scenario: Un nombre que solo cumple la longitud gracias a los espacios
- **DADO** un registro con nombre `"  A  "`
- **CUANDO** la persona intenta registrarse
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y un error en el campo
  `nombre`

### Requirement: La contraseña se guarda exactamente como se envió
El sistema SHALL NOT modificar la contraseña recibida (ni recortarla, ni cambiar sus mayúsculas)
antes de protegerla ni al compararla en el inicio de sesión.

#### Scenario: Los espacios de la contraseña son significativos
- **DADO** una persona registrada con la contraseña `" secreta123 "` (con un espacio al inicio y
  otro al final)
- **CUANDO** inicia sesión con la contraseña `secreta123`, sin los espacios
- **ENTONCES** el sistema responde `401` con código `CREDENCIALES_INVALIDAS`
- **Y** al iniciar sesión con `" secreta123 "` el sistema responde `200`

### Requirement: Validación de los datos del registro
El sistema SHALL rechazar con `400`, código `DATOS_INVALIDOS` y un mensaje por cada campo
inválido un registro que no cumpla: email obligatorio, con formato válido y de máximo 254
caracteres; contraseña de 8 a 72 caracteres y de no más de 72 bytes en UTF-8; nombre y apellido
obligatorios de 2 a 100 caracteres; fecha de nacimiento obligatoria; teléfono de máximo 20
caracteres; moneda predeterminada que sea un código de moneda ISO 4217 existente, escrito
exactamente en mayúsculas (no basta con que tenga tres letras).

#### Scenario: Contraseña demasiado corta
- **DADO** un registro con una contraseña de 7 caracteres
- **CUANDO** la persona intenta registrarse
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y un error en el campo
  `contrasena`

#### Scenario: Contraseña de 72 caracteres que supera 72 bytes
- **DADO** un registro con una contraseña de 72 caracteres que incluye letras con tilde o `ñ`, de
  modo que ocupa más de 72 bytes en UTF-8
- **CUANDO** la persona intenta registrarse
- **ENTONCES** el sistema responde `400` con un error en el campo `contrasena`, nunca un error
  interno

#### Scenario: Email con formato inválido
- **DADO** un registro con el email `ana-arroba-ejemplo.com`
- **CUANDO** la persona intenta registrarse
- **ENTONCES** el sistema responde `400` con un error en el campo `email`

#### Scenario: Varios campos inválidos a la vez
- **DADO** un registro sin nombre y con un teléfono de 21 caracteres
- **CUANDO** la persona intenta registrarse
- **ENTONCES** el sistema responde `400` con un error en el campo `nombre` y otro en el campo
  `telefono`

#### Scenario: Moneda con el formato correcto pero inexistente
- **DADO** un registro con moneda predeterminada `ZZZ` (tres letras mayúsculas, pero no es un
  código de moneda existente)
- **CUANDO** la persona intenta registrarse
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y un error en el campo
  `monedaPredeterminada`

#### Scenario: Moneda existente escrita en minúsculas
- **DADO** un registro con moneda predeterminada `bob`
- **CUANDO** la persona intenta registrarse
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y un error en el campo
  `monedaPredeterminada`

#### Scenario: Un dato inválido no se informa como regla de negocio
- **DADO** un registro con una contraseña de 7 caracteres
- **CUANDO** la persona intenta registrarse
- **ENTONCES** la respuesta no es `422` ni tiene código `REGLA_NEGOCIO_VIOLADA`, códigos reservados
  para las reglas de negocio

### Requirement: Cuerpo de la petición ilegible
Si el cuerpo de la petición no puede interpretarse (JSON mal formado, un campo con un tipo
incorrecto o una fecha que no existe), el sistema SHALL responder `400` con código
`DATOS_INVALIDOS` y un mensaje genérico, sin mapa `errores` y sin exponer detalles internos del
intérprete de JSON (clases, posiciones ni fragmentos del cuerpo recibido).

#### Scenario: JSON mal formado
- **DADO** un cuerpo de registro con JSON mal formado (por ejemplo, sin la llave de cierre)
- **CUANDO** se envía al registro
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y el mensaje genérico
- **Y** la respuesta no menciona clases, posiciones ni partes del cuerpo recibido

#### Scenario: Fecha de nacimiento que no existe
- **DADO** un registro con fecha de nacimiento `2000-13-01`
- **CUANDO** la persona intenta registrarse
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y el mensaje genérico

### Requirement: Mayoría de edad
El sistema SHALL aceptar el registro solo si la persona tiene 18 años cumplidos en la fecha actual
del sistema, contando como cumplidos los 18 el mismo día del aniversario. Si no los tiene, el
sistema SHALL rechazarlo como un error de validación más: `400`, código `DATOS_INVALIDOS` y
un error en el campo `fechaNacimiento`.

#### Scenario: Cumple 18 hoy
- **DADO** que la fecha actual del sistema es `2026-10-02`
- **CUANDO** se registra una persona nacida el `2008-10-02`
- **ENTONCES** el registro se acepta

#### Scenario: Cumple 18 mañana
- **DADO** que la fecha actual del sistema es `2026-10-02`
- **CUANDO** se registra una persona nacida el `2008-10-03`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y un error en el campo
  `fechaNacimiento`

#### Scenario: Nacida un 29 de febrero, el 28 de febrero de un año no bisiesto
- **DADO** que la fecha actual del sistema es `2026-02-28` (2026 no es bisiesto)
- **CUANDO** se registra una persona nacida el `2008-02-29`
- **ENTONCES** el sistema responde `400` con un error en el campo `fechaNacimiento`, porque todavía
  no cumplió los 18

#### Scenario: Nacida un 29 de febrero, el 1 de marzo de un año no bisiesto
- **DADO** que la fecha actual del sistema es `2026-03-01`
- **CUANDO** se registra una persona nacida el `2008-02-29`
- **ENTONCES** el registro se acepta

#### Scenario: Fecha de nacimiento futura
- **DADO** que la fecha actual del sistema es `2026-10-02`
- **CUANDO** se registra una persona con fecha de nacimiento `2030-01-01`
- **ENTONCES** el sistema responde `400` con un error en el campo `fechaNacimiento`

### Requirement: Presupuesto inicial al registrarse
El sistema SHALL crear, junto con las credenciales y el perfil y en la misma operación atómica,
un presupuesto llamado `Mi presupuesto` cuya moneda sea la moneda predeterminada del perfil
recién creado. Si falla cualquier parte del alta, el sistema SHALL NOT dejar guardado ningún
presupuesto de esa persona.

#### Scenario: Registro con moneda explícita
- **DADO** un registro válido con moneda predeterminada `USD`
- **CUANDO** la persona se registra
- **ENTONCES** al listar sus presupuestos con el token recibido hay uno solo, `Mi presupuesto`,
  con moneda `USD`

#### Scenario: Registro sin moneda
- **DADO** un registro válido sin moneda predeterminada
- **CUANDO** la persona se registra
- **ENTONCES** su único presupuesto es `Mi presupuesto` con moneda `BOB`

#### Scenario: Un fallo al crear el presupuesto no deja un alta parcial
- **DADO** un registro con datos válidos
- **CUANDO** falla la creación del presupuesto inicial
- **ENTONCES** no queda guardada ninguna cuenta ni perfil con ese email
