# Proposal

## Why

Hoy una persona puede registrarse, iniciar sesión y consultar sus datos
(`GET /api/v1/usuarios/yo`), pero no puede corregir su nombre ni cambiar una contraseña que
quedó expuesta o que quiere renovar. Es el mínimo de autogestión de una cuenta, y el frontend
de perfil necesita estos endpoints antes de poder construirse.

## What Changes

- **`PUT /api/v1/usuarios/yo`**: cambia el nombre de la persona autenticada. Mismo recorte y
  misma longitud (2 a 100 caracteres) que el nombre del registro. Responde `200` con
  `UsuarioActualResponse`.
- **`POST /api/v1/usuarios/yo/contrasena`**: recibe `contrasenaActual` y `contrasenaNueva` y
  responde `204`. La contraseña actual incorrecta es `422` con mensaje fijo; la nueva igual a la
  actual es `422`; faltan datos o la nueva incumple las reglas del registro, `400`.
- **Validación de contraseña compartida**: las reglas de la contraseña del registro (no vacía,
  8 a 72 caracteres, máximo 72 bytes UTF-8) pasan a una única anotación de Bean Validation que
  usan el registro y el cambio de contraseña. Los mensajes de error del registro no cambian.
- El usuario siempre es el del token (`@AuthenticationPrincipal`): ninguna URL ni cuerpo lleva
  un id de usuario.
- Sin cambios de esquema. Sin cambios en el registro ni en el login más allá de la anotación
  compartida.
- Se documenta que el token emitido antes del cambio sigue siendo válido hasta expirar (JWT
  stateless) y que hoy el login **no** limita intentos fallidos (ver design, riesgos).

Fuera de alcance: cambiar el correo, eliminar la cuenta, invalidar tokens emitidos,
recuperación de contraseña, limitación de intentos (se deja como riesgo documentado) y cambios
en el frontend.

## Capabilities

### New Capabilities
- `perfil-usuario`: la persona autenticada edita su propio nombre y cambia su contraseña,
  verificando la actual, con errores que no reflejan lo que envió.

### Modified Capabilities

Ninguna. Las reglas de contraseña del registro (`registro-usuarios`) y la consulta de datos
(`autenticacion`) no cambian de comportamiento: la anotación compartida es un refactor interno
cubierto por los tests existentes.

## Impact

- **Backend**: `usuario` gana `dto/request` (dos records) y dos métodos en `UsuarioService`;
  `UsuarioController` gana dos rutas; `usuario/validacion` gana `@ContrasenaValida`, que usa
  también `auth/dto/request/RegistroRequest`.
- **API**: dos rutas nuevas bajo `/api/v1/usuarios/yo`, ambas protegidas por la regla
  `anyRequest().authenticated()` existente (sin cambios en `SecurityConfig`).
- **Base de datos**: ninguno; se actualizan `perfiles.nombre` y `usuarios.contrasena`.
- **Documentación**: `AGENTS.md` (árbol de `usuario`, sección de perfil y nota del token).
