# autenticacion Specification

## Purpose

Permite que una persona registrada inicie sesión, obtenga un token de acceso con validez limitada
y lo use para acceder a las rutas protegidas de la API, incluida la consulta de sus propios datos.

## Requirements

### Requirement: Inicio de sesión con credenciales
El sistema SHALL permitir iniciar sesión con email y contraseña. El email SHALL normalizarse igual
que en el registro (sin espacios al inicio ni al final y en minúsculas) antes de compararlo; la
contraseña SHALL compararse tal como se envía. Si las credenciales son correctas, el sistema
SHALL responder `200` con un token de acceso para esa persona.

#### Scenario: Credenciales correctas
- **DADO** una cuenta registrada con el email `ana@ejemplo.com` y la contraseña `secreta123`
- **CUANDO** alguien inicia sesión con `ana@ejemplo.com` y `secreta123`
- **ENTONCES** el sistema responde `200` con un token de acceso de esa cuenta

#### Scenario: Email con mayúsculas y espacios
- **DADO** una cuenta registrada con el email `ana@ejemplo.com` y la contraseña `secreta123`
- **CUANDO** alguien inicia sesión con el email `"  Ana@Ejemplo.COM "` y `secreta123`
- **ENTONCES** el sistema responde `200` con un token de acceso de esa cuenta

#### Scenario: La contraseña se compara con sus espacios
- **DADO** una cuenta registrada con el email `ana@ejemplo.com` y la contraseña `" secreta123 "`
  (con un espacio al inicio y otro al final)
- **CUANDO** alguien inicia sesión con ese email y la contraseña `"secreta123"`, sin espacios
- **ENTONCES** el sistema responde `401` con código `CREDENCIALES_INVALIDAS`
- **Y** al iniciar sesión con ese email y la contraseña `" secreta123 "` el sistema responde
  `200` con un token de acceso de esa cuenta

### Requirement: Credenciales inválidas sin revelar qué falló
Si el email no pertenece a ninguna cuenta o la contraseña es incorrecta, el sistema SHALL
responder `401` con código `CREDENCIALES_INVALIDAS` y exactamente el mismo mensaje en ambos casos,
sin indicar si el email existe.

#### Scenario: Contraseña incorrecta
- **DADO** una cuenta registrada con el email `ana@ejemplo.com`
- **CUANDO** alguien inicia sesión con ese email y una contraseña incorrecta
- **ENTONCES** el sistema responde `401` con código `CREDENCIALES_INVALIDAS`

#### Scenario: Email inexistente
- **DADO** que no existe ninguna cuenta con el email `nadie@ejemplo.com`
- **CUANDO** alguien inicia sesión con ese email
- **ENTONCES** el sistema responde `401` con código `CREDENCIALES_INVALIDAS` y el mismo mensaje
  que ante una contraseña incorrecta

#### Scenario: Contraseña de más de 72 bytes
- **DADO** una cuenta registrada con el email `ana@ejemplo.com`
- **CUANDO** alguien inicia sesión con ese email y una contraseña de más de 72 bytes en UTF-8
- **ENTONCES** el sistema responde `401` con código `CREDENCIALES_INVALIDAS`, nunca un error
  interno

### Requirement: Datos obligatorios del inicio de sesión
El sistema SHALL rechazar con `400`, código `DATOS_INVALIDOS` y un error por campo un
inicio de sesión sin email o sin contraseña.

#### Scenario: Inicio de sesión sin contraseña
- **DADO** una petición de inicio de sesión con email y sin contraseña
- **CUANDO** se envía
- **ENTONCES** el sistema responde `400` con un error en el campo `contrasena`

### Requirement: Token de acceso con validez de 24 horas
El token de acceso emitido por el registro o el inicio de sesión SHALL identificar a la persona y
su rol, SHALL estar firmado por el sistema y SHALL dejar de ser válido 24 horas después de su
emisión. La respuesta SHALL incluir el token, su tipo (`Bearer`) y el instante de expiración. No
existe un mecanismo de renovación: al expirar, la persona debe iniciar sesión de nuevo.

#### Scenario: La respuesta informa la expiración
- **DADO** que el instante actual del sistema es `2026-10-02T12:00:00Z`
- **CUANDO** una persona inicia sesión correctamente
- **ENTONCES** la respuesta incluye el tipo `Bearer` y la expiración `2026-10-03T12:00:00Z`

#### Scenario: Token expirado
- **DADO** un token emitido hace más de 24 horas
- **CUANDO** se usa para acceder a una ruta protegida
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

### Requirement: Acceso a rutas protegidas con el token
El sistema SHALL autenticar cada petición que incluya el header `Authorization: Bearer <token>`
con un token válido. Una petición a una ruta protegida sin token, con un token mal formado, con
firma inválida o expirado, o con un header `Authorization` que no use el prefijo `Bearer ` SHALL
recibir `401` con código `NO_AUTENTICADO`. Las rutas públicas
(registro, inicio de sesión, salud) SHALL seguir accesibles aunque el token enviado sea inválido.

#### Scenario: Token válido
- **DADO** un token emitido por el sistema hace menos de 24 horas
- **CUANDO** se usa en el header `Authorization: Bearer` para acceder a una ruta protegida
- **ENTONCES** la petición se procesa como hecha por la persona dueña del token

#### Scenario: Token alterado
- **DADO** un token emitido por el sistema al que se le modificó cualquier parte
- **CUANDO** se usa para acceder a una ruta protegida
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

#### Scenario: Ruta protegida sin token
- **DADO** una petición sin header `Authorization`
- **CUANDO** se dirige a una ruta protegida
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

#### Scenario: Token válido sin el prefijo Bearer
- **DADO** un header `Authorization` cuyo valor es un token válido emitido por el sistema, pero
  sin el prefijo `Bearer `
- **CUANDO** se usa para acceder a una ruta protegida
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

#### Scenario: Ruta pública con un token inválido
- **DADO** una petición de inicio de sesión con credenciales correctas y un header
  `Authorization: Bearer` con un token inválido
- **CUANDO** se envía
- **ENTONCES** el sistema la procesa normalmente y responde `200` con un token nuevo

### Requirement: Consulta de los datos del usuario autenticado
El sistema SHALL devolver a la persona autenticada su email, su rol y los datos de su perfil
(nombre, apellido, fecha de nacimiento, teléfono y moneda predeterminada). La respuesta SHALL NOT
incluir nunca la contraseña ni su hash. Si el token es válido pero la persona dueña del token ya
no existe, el sistema SHALL responder `401` con código `NO_AUTENTICADO`, igual que ante un token
inválido, para que el cliente cierre la sesión.

#### Scenario: Consulta de los propios datos
- **DADO** una persona registrada como `ana@ejemplo.com`, nombre `Ana`, apellido `Rojas`, nacida
  el `1990-05-20`, sin teléfono y con moneda `BOB`
- **CUANDO** consulta sus propios datos con su token
- **ENTONCES** el sistema responde `200` con esos datos y el rol `USUARIO`
- **Y** la respuesta no contiene ningún campo de contraseña

#### Scenario: Consulta sin token
- **DADO** una petición sin token
- **CUANDO** se consultan los datos del usuario autenticado
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

#### Scenario: Token válido de una persona que ya no existe
- **DADO** un token firmado por el sistema, no expirado, cuya persona ya no existe
- **CUANDO** se consultan los datos del usuario autenticado con ese token
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO` y el mismo mensaje que ante
  una petición sin token, nunca `404`
