# Tasks

Rutas: producción en `backend/src/main/java/com/presupuesto/`, tests en
`backend/src/test/java/com/presupuesto/`. Comandos desde `backend/` con `.\mvnw.cmd`.

## 1. Validación de contraseña compartida

- [x] 1.1 Crear la anotación `ContrasenaValida` (paquete `com.presupuesto.usuario.validacion`,
  test `ContrasenaValidaTest` en el mismo paquete): compuesta por `@NotBlank`,
  `@Size(min = 8, max = 72)` y `@MaximoBytesUtf8(72)`, sin validador propio y sin
  `@ReportAsSingleViolation`. Verificar con `ContrasenaValidaTest` (vacía, 7/8/72/73
  caracteres, 40 caracteres de 2 bytes, espacios conservados, mensajes iguales a los actuales).
- [x] 1.2 Reemplazar las tres anotaciones de `contrasena` en `RegistroRequest` (paquete
  `com.presupuesto.auth.dto.request`) por `@ContrasenaValida`, en este orden obligatorio:
  (a) primero ampliar `RegistroRequestTest` (mismo paquete) con los tests de los mensajes de
  contraseña (vacía, 7/8/72/73 caracteres, 40 caracteres `ñ` de 2 bytes, campo y mensaje de cada
  violación) y correrlos contra las tres anotaciones originales, sin tocar `RegistroRequest`:
  deben pasar; si alguno falla, se corrige el test, no el código; (b) solo entonces reemplazar
  por `@ContrasenaValida`; (c) volver a correr `RegistroRequestTest` y
  `RegistroIntegracionTest` (`com.presupuesto.auth.controller`) sin modificar ni una aserción
  escrita en (a) ni las existentes: deben seguir pasando.

## 2. Requests del perfil

- [x] 2.1 Crear el record `ActualizarNombreRequest` (paquete
  `com.presupuesto.usuario.dto.request`, test `ActualizarNombreRequestTest` en el mismo
  paquete): `@NotBlank @Size(min = 2, max = 100) String nombre`, recortado en el constructor
  compacto. Verificar con el test: recorte, 1/2/100/101 caracteres, vacío, solo espacios y nulo.
- [x] 2.2 Crear el record `CambiarContrasenaRequest` (paquete
  `com.presupuesto.usuario.dto.request`, test `CambiarContrasenaRequestTest` en el mismo
  paquete): `@NotBlank contrasenaActual`, `@ContrasenaValida contrasenaNueva`, sin modificar
  ningún valor y con `toString()` fijo que no incluya las contraseñas. Verificar con el test:
  faltantes, vacías, límites de la nueva, espacios conservados y que `toString()` no contiene
  ninguna de las dos contraseñas.

## 3. Servicio

- [x] 3.1 Agregar `cambiarNombre` a `UsuarioService` (paquete `com.presupuesto.usuario.service`,
  test `UsuarioServiceTest` en el mismo paquete): `@Transactional`, actualiza `Perfil.nombre` y
  devuelve `UsuarioActualResponse.desde`; usuario inexistente lanza `NoAutenticadoException`
  con `MENSAJE_NO_AUTENTICADO`. Verificar con `UsuarioServiceTest` (nombre guardado y devuelto;
  inexistente → 401).
- [x] 3.2 Agregar `cambiarContrasena` a `UsuarioService` con el orden de la decisión 4 del design
  (inexistente 401, actual de más de 72 bytes o incorrecta 422 con mensaje fijo, nueva igual 422,
  guardar `passwordEncoder.encode`), inyectando `UsuarioRepository` y el `PasswordEncoder` que
  usa `AuthService`. Verificar con `UsuarioServiceTest`: correcta guarda un hash que
  `matches`; incorrecta no guarda; actual `"ñ".repeat(37)` (37 caracteres, 74 bytes UTF-8) no
  llama a `matches`; igual (con `equals`) no guarda; igual con actual incorrecta devuelve el
  mensaje de "actual incorrecta".

## 4. Controller

- [x] 4.1 Agregar `PUT /api/v1/usuarios/yo` (200, `UsuarioActualResponse`) y
  `POST /api/v1/usuarios/yo/contrasena` (204) a `UsuarioController` (paquete
  `com.presupuesto.usuario.controller`), con `@Valid @RequestBody` y
  `@AuthenticationPrincipal UsuarioAutenticado`, sin id de usuario en la URL. Verificar con
  `PerfilIntegracionTest` (paquete `com.presupuesto.usuario.controller`, nuevo): los flujos de
  nombre del spec (éxito con recorte, solo cambia el nombre, vacío/solo espacios/1/101
  caracteres, exactamente 100, cuerpo sin nombre o ilegible), sin reflejar el nombre enviado.
- [x] 4.2 Completar `PerfilIntegracionTest` con los flujos de contraseña del spec, cada test con
  sus propios usuarios de email único: correcta (204, sin cuerpo); login con la nueva
  funciona y con la vieja devuelve `CREDENCIALES_INVALIDAS`; actual incorrecta (422, mensaje
  fijo); actual de `"ñ".repeat(37)` (37 caracteres, 74 bytes; 422 con el mismo mensaje fijo);
  nueva débil (400); nueva igual (422); `Secreta123` distinta
  (204); nueva con espacios; faltan datos y cuerpo ilegible (400); respuestas sin ninguna de las
  contraseñas enviadas; el hash guardado no es el texto plano y valida con el encoder.
- [x] 4.3 Completar `PerfilIntegracionTest` con seguridad y aislamiento: sin token (401
  `NO_AUTENTICADO`) y token de persona inexistente (401) en ambas rutas; Ana no afecta a Beto;
  el token anterior al cambio sigue devolviendo 200 en `GET /api/v1/usuarios/yo`.

## 5. Documentación y comprobaciones finales

- [x] 5.1 Actualizar `AGENTS.md`: agregar `dto/request` a `usuario` en el árbol (y quitar la
  frase "`usuario` no tiene `dto/request`"); sección "Perfil de usuario" con las dos rutas, el
  orden de errores (400, 401, 422 actual incorrecta, 422 igual), `@ContrasenaValida` como regla
  única de contraseña, que el token anterior sigue válido hasta expirar y que no hay limitación
  de intentos (riesgo). Verificar releyendo que cada afirmación coincide con el spec.
- [x] 5.2 Comprobar que ninguna línea nueva registra contraseñas o el nombre:
  `grep -rniE "log\.|logger|System\.out|printStackTrace" src/main/java/com/presupuesto/usuario`
  no debe mostrar nada que imprima los requests; y que el `grep` de dependencias de `comun/`
  de AGENTS.md devuelve cero líneas.
- [x] 5.3 Ejecutar `.\mvnw.cmd test` desde `backend/` y verificar que toda la suite pasa, y
  `openspec validate perfil-usuario-backend --strict` sin errores. Revisar que no hay líneas de
  más de 100 columnas en los archivos nuevos o modificados.
