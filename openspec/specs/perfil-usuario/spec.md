# perfil-usuario Specification

## Purpose

Permite que la persona autenticada gestione su propia cuenta: corregir su nombre y cambiar su
contraseña verificando la actual, sin que ninguno de los dos datos se refleje en errores ni en
registros.

## Requirements

### Requirement: Cambio del propio nombre
El sistema SHALL permitir a la persona autenticada cambiar su nombre con
`PUT /api/v1/usuarios/yo` y SHALL responder `200` con los mismos datos que la consulta de
`GET /api/v1/usuarios/yo`, ya actualizados. El sistema SHALL guardar el nombre sin espacios al
inicio ni al final, conservando mayúsculas y espacios internos, y SHALL aplicar las mismas
longitudes que el registro (2 a 100 caracteres) al valor ya recortado.

#### Scenario: Cambio de nombre exitoso
- **DADO** una persona registrada con nombre `Ana`
- **CUANDO** envía `PUT /api/v1/usuarios/yo` con nombre `"  María José  "`
- **ENTONCES** el sistema responde `200` con nombre `María José`
- **Y** una consulta posterior de sus datos muestra `María José`

#### Scenario: Solo cambia el nombre
- **DADO** una persona con apellido `Rojas`, teléfono y moneda `USD`
- **CUANDO** envía `PUT /api/v1/usuarios/yo` con un nombre válido y, además, los campos
  `apellido`, `email`, `rol` y `monedaPredeterminada` con otros valores
- **ENTONCES** el sistema responde `200`
- **Y** el email, el rol, el apellido, el teléfono y la moneda quedan como estaban

#### Scenario: Nombre vacío o solo espacios
- **DADO** una persona autenticada
- **CUANDO** envía el nombre `""` o `"    "`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y un error en el campo
  `nombre`
- **Y** su nombre no cambia

#### Scenario: Nombre demasiado corto o demasiado largo
- **DADO** una persona autenticada
- **CUANDO** envía el nombre `" A "` (1 carácter tras recortar) o uno de 101 caracteres
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y un error en el campo
  `nombre`
- **Y** su nombre no cambia

#### Scenario: Nombre de exactamente 100 caracteres
- **DADO** una persona autenticada
- **CUANDO** envía un nombre de 100 caracteres rodeado de espacios
- **ENTONCES** el sistema responde `200` y guarda los 100 caracteres sin los espacios

#### Scenario: Cuerpo sin nombre o ilegible
- **DADO** una persona autenticada
- **CUANDO** envía un cuerpo sin el campo `nombre`, vacío o con JSON mal formado
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Cambio de contraseña verificando la actual
El sistema SHALL permitir a la persona autenticada cambiar su contraseña con
`POST /api/v1/usuarios/yo/contrasena`, enviando `contrasenaActual` y `contrasenaNueva`. Si la
actual es correcta y la nueva es válida y distinta, el sistema SHALL guardar el hash de la nueva
con el mismo algoritmo que el registro y SHALL responder `204` sin cuerpo.

#### Scenario: Cambio de contraseña exitoso
- **DADO** una persona registrada con la contraseña `secreta123`
- **CUANDO** envía la contraseña actual `secreta123` y la nueva `otraClave456`
- **ENTONCES** el sistema responde `204` sin cuerpo

#### Scenario: Iniciar sesión con la contraseña nueva
- **DADO** una persona que cambió su contraseña de `secreta123` a `otraClave456`
- **CUANDO** inicia sesión con su email y `otraClave456`
- **ENTONCES** el sistema responde `200` con un token de acceso

#### Scenario: La contraseña anterior deja de funcionar
- **DADO** una persona que cambió su contraseña de `secreta123` a `otraClave456`
- **CUANDO** inicia sesión con su email y `secreta123`
- **ENTONCES** el sistema responde `401` con código `CREDENCIALES_INVALIDAS`

#### Scenario: La contraseña se guarda con hash
- **DADO** una persona que cambió su contraseña a `otraClave456`
- **CUANDO** se inspecciona lo guardado para esa persona
- **ENTONCES** el valor guardado no es `otraClave456` y es un hash válido para ese texto
  generado por el mismo mecanismo que usa el registro

### Requirement: Contraseña actual incorrecta
El sistema SHALL rechazar con `422` y código `REGLA_NEGOCIO_VIOLADA` un cambio de contraseña
cuya `contrasenaActual` no corresponde a la guardada, con un mensaje fijo que no incluye ninguna
de las dos contraseñas. El sistema SHALL tratar igual una `contrasenaActual` de más de 72 bytes
UTF-8. El sistema SHALL NOT responder `401`, para no cerrar la sesión del cliente.

#### Scenario: Contraseña actual incorrecta
- **DADO** una persona registrada con la contraseña `secreta123`
- **CUANDO** envía la contraseña actual `equivocada1` y una nueva válida
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y el mensaje fijo
  "La contraseña actual es incorrecta"
- **Y** su contraseña sigue siendo `secreta123`

#### Scenario: Contraseña actual de más de 72 bytes
- **DADO** una persona autenticada
- **CUANDO** envía una contraseña actual de 37 caracteres `ñ` (74 bytes UTF-8, que pasa un
  conteo por caracteres pero no por bytes) y una nueva válida
- **ENTONCES** el sistema responde `422` con el mismo código y el mismo mensaje fijo

#### Scenario: El error no refleja lo enviado
- **DADO** una persona autenticada
- **CUANDO** envía una contraseña actual incorrecta `equivocada1` y la nueva `otraClave456`
- **ENTONCES** el cuerpo de la respuesta no contiene `equivocada1` ni `otraClave456`

### Requirement: Contraseña nueva con las reglas del registro
El sistema SHALL exigir a `contrasenaNueva` exactamente las reglas de la contraseña del
registro: no vacía, de 8 a 72 caracteres y de máximo 72 bytes en UTF-8, comparada sin recortar.
Una nueva que las incumple SHALL responder `400` con código `DATOS_INVALIDOS` y un error en el
campo `contrasenaNueva`, sin reflejar su valor. Esas reglas SHALL estar definidas una sola vez y
usarse tanto en el registro como aquí.

#### Scenario: Contraseña nueva demasiado corta
- **DADO** una persona autenticada
- **CUANDO** envía la nueva `corta12` (7 caracteres) con su actual correcta
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y un error en
  `contrasenaNueva`
- **Y** su contraseña no cambia

#### Scenario: Contraseña nueva demasiado larga
- **DADO** una persona autenticada
- **CUANDO** envía una nueva de 73 caracteres, o de 40 caracteres que ocupan más de 72 bytes
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y un error en
  `contrasenaNueva`

#### Scenario: Contraseña nueva con espacios
- **DADO** una persona autenticada
- **CUANDO** envía la nueva `"  clave nueva 9 "` con su actual correcta
- **ENTONCES** el sistema responde `204`
- **Y** puede iniciar sesión con ese texto exacto, con sus espacios

#### Scenario: Las reglas coinciden con las del registro
- **DADO** una contraseña que el registro rechaza por longitud o por bytes
- **CUANDO** se usa como contraseña nueva
- **ENTONCES** el cambio de contraseña también la rechaza con `400`
- **Y** el registro conserva los mismos mensajes de error que antes de este cambio

#### Scenario: El error no refleja la contraseña nueva
- **DADO** una persona autenticada
- **CUANDO** envía la nueva `corta12`
- **ENTONCES** el cuerpo de la respuesta no contiene `corta12`

### Requirement: Contraseña nueva distinta de la actual
El sistema SHALL rechazar con `422` y código `REGLA_NEGOCIO_VIOLADA` un cambio cuya
`contrasenaNueva` es idéntica a `contrasenaActual`, sin guardar nada. Esta comprobación SHALL
hacerse solo después de verificar que la actual es correcta.

#### Scenario: Nueva igual a la actual
- **DADO** una persona registrada con la contraseña `secreta123`
- **CUANDO** envía `secreta123` como actual y como nueva
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA`
- **Y** su contraseña sigue siendo `secreta123`

#### Scenario: Igual con otra capitalización
- **DADO** una persona registrada con la contraseña `secreta123`
- **CUANDO** envía `secreta123` como actual y `Secreta123` como nueva
- **ENTONCES** el sistema responde `204`

#### Scenario: Nueva igual a una actual incorrecta
- **DADO** una persona registrada con la contraseña `secreta123`
- **CUANDO** envía `equivocada1` como actual y como nueva
- **ENTONCES** el sistema responde `422` por contraseña actual incorrecta, no por "igual"

### Requirement: Datos obligatorios del cambio de contraseña
El sistema SHALL responder `400` con código `DATOS_INVALIDOS` cuando falte `contrasenaActual` o
`contrasenaNueva`, o cuando el cuerpo sea ilegible, antes de verificar nada contra lo guardado.

#### Scenario: Falta la contraseña actual
- **DADO** una persona autenticada
- **CUANDO** envía solo `contrasenaNueva`
- **ENTONCES** el sistema responde `400` con un error en `contrasenaActual`

#### Scenario: Falta la contraseña nueva
- **DADO** una persona autenticada
- **CUANDO** envía solo `contrasenaActual`
- **ENTONCES** el sistema responde `400` con un error en `contrasenaNueva`

#### Scenario: Contraseñas vacías o cuerpo ilegible
- **DADO** una persona autenticada
- **CUANDO** envía contraseñas `""`, un cuerpo vacío o un JSON mal formado
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Solo el propio perfil
El sistema SHALL identificar a la persona únicamente por su token en ambas operaciones. Sin
token, con token inválido o expirado, o con un token de una persona que ya no existe, el sistema
SHALL responder `401` con código `NO_AUTENTICADO` y el mismo mensaje que las demás rutas
protegidas, sin modificar nada.

#### Scenario: Cambio de nombre sin token
- **DADO** una petición sin token
- **CUANDO** se envía `PUT /api/v1/usuarios/yo` con un nombre válido
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

#### Scenario: Cambio de contraseña sin token
- **DADO** una petición sin token
- **CUANDO** se envía `POST /api/v1/usuarios/yo/contrasena` con datos válidos
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

#### Scenario: Token de una persona que ya no existe
- **DADO** un token válido cuya persona ya no existe
- **CUANDO** se usa en cualquiera de las dos operaciones
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`, nunca `404`

#### Scenario: Dos personas no se afectan
- **DADO** dos personas registradas, Ana y Beto
- **CUANDO** Ana cambia su nombre y su contraseña
- **ENTONCES** el nombre y la contraseña de Beto no cambian

### Requirement: Los tokens emitidos siguen válidos tras cambiar la contraseña
El sistema SHALL NOT invalidar los tokens ya emitidos al cambiar la contraseña, porque la
autenticación es sin estado; cualquier token anterior SHALL seguir siendo válido hasta su
expiración (24 horas). El cambio de contraseña SHALL NOT emitir un token nuevo.

#### Scenario: El token anterior sigue funcionando
- **DADO** una persona con un token emitido antes de cambiar su contraseña
- **CUANDO** cambia su contraseña y luego consulta `GET /api/v1/usuarios/yo` con ese mismo token
- **ENTONCES** el sistema responde `200`

### Requirement: Las contraseñas no se exponen
El sistema SHALL NOT incluir `contrasenaActual`, `contrasenaNueva` ni el hash guardado en
ninguna respuesta, mensaje de error ni registro (log) de la aplicación, y SHALL NOT reflejar el
nombre enviado en los mensajes de error.

#### Scenario: Respuestas de éxito y de error sin contraseñas
- **DADO** cualquier cambio de contraseña, exitoso o rechazado
- **CUANDO** se examina la respuesta
- **ENTONCES** no contiene ninguna de las contraseñas enviadas ni el hash guardado

#### Scenario: La representación en texto de la petición no contiene la contraseña
- **DADO** una petición de cambio de contraseña con valores concretos
- **CUANDO** se convierte a texto (por ejemplo, para un log)
- **ENTONCES** el texto no contiene ninguna de las dos contraseñas

#### Scenario: Nombre no reflejado en errores
- **DADO** una persona autenticada
- **CUANDO** envía un nombre inválido de 101 caracteres con un texto reconocible
- **ENTONCES** el cuerpo del error no contiene ese texto
