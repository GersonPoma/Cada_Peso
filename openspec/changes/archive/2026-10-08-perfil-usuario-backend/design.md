# Design

## Context

Estado observado en el código (ver proposal.md para la motivación):

- `usuario` solo tiene `GET /api/v1/usuarios/yo` (`UsuarioController`, `UsuarioService.obtenerActual`).
  `Perfil.nombre` y `Usuario.contrasena` tienen setter de Lombok y no hay campos derivados, así
  que no hace falta un método de entidad.
- Las reglas de la contraseña viven como tres anotaciones en `RegistroRequest.contrasena`
  (`@NotBlank @Size(min = 8, max = 72) @MaximoBytesUtf8(72)`). `Normalizacion` (recorte) es
  package-private en `auth/dto/request`. `auth` ya importa `usuario.validacion.MayorDeEdad`, y
  `usuario` no puede importar `auth`: lo compartido debe vivir en `usuario` o en `comun`.
- `AuthService` hashea con el `PasswordEncoder` (BCrypt), compara con `matches` y rechaza antes
  las contraseñas de más de 72 bytes UTF-8 (límite real de BCrypt) con el mismo error que una
  contraseña incorrecta.
- El interceptor del frontend cierra la sesión ante **cualquier 401** fuera de `/api/v1/auth/**`.
  Por eso "contraseña actual incorrecta" no puede ser 401.
- `ManejadorGlobalExcepciones` ya cubre 400 (validación con mapa `errores` campo → mensaje, sin
  valores recibidos; cuerpo ilegible con mensaje fijo), 422 (`ReglaNegocioException`) y 401.
- **No existe limitación de intentos** en el login ni en ningún otro punto (se buscó en
  `src/main`: no hay contador, bloqueo ni rate limiter).
- Los records generan `toString()` con todos sus campos, así que un request con contraseña la
  mostraría en cualquier log que lo imprima. `LoginRequest` y `RegistroRequest` ya tienen esa
  exposición latente (hoy nadie los registra).

## Goals / Non-Goals

**Goals:**
- Dos operaciones sobre el propio perfil, con el id tomado siempre del token.
- Reutilizar, sin copiar, las reglas de contraseña del registro.
- Nada del cuerpo (contraseñas, nombre) en respuestas de error ni en logs.

**Non-Goals:**
- Cambiar correo, eliminar cuenta, recuperar contraseña, invalidar tokens.
- Implementar limitación de intentos (se documenta como riesgo; ver abajo).
- Tocar el frontend o el esquema.
- Cambiar el comportamiento observable del registro o del login.

## Decisions

### 1. Anotación compuesta `@ContrasenaValida` en `usuario/validacion`
Una anotación de Bean Validation compuesta por `@NotBlank`, `@Size(min = 8, max = 72)` y
`@MaximoBytesUtf8(72)`, sin validador propio (`@Constraint(validatedBy = {})`) y **sin**
`@ReportAsSingleViolation`, de modo que cada violación conserva su mensaje actual y los
mensajes del registro no cambian. `RegistroRequest.contrasena` y
`CambiarContrasenaRequest.contrasenaNueva` la usan.
- *Por qué en `usuario/validacion` y no en `comun/validacion`*: la regla es de credenciales de
  usuario (no genérica) y `auth` ya depende de `usuario.validacion`.
- *Único cambio en `comun`*: para poder componerla, `@MaximoBytesUtf8` (`comun/validacion`) añade
  `ElementType.ANNOTATION_TYPE` a su `@Target`; sin eso `@ContrasenaValida` no compila. No cambia
  su comportamiento ni sus mensajes y `comun` sigue sin importar ninguna feature.
- *Alternativas*: copiar las tres anotaciones (rechazado: es lo que se pidió evitar y puede
  desincronizarse); constantes compartidas (no garantizan que se apliquen igual).

### 2. Requests como records en `usuario/dto/request`
`ActualizarNombreRequest(@NotBlank @Size(min = 2, max = 100) String nombre)` recorta en el
constructor compacto (`nombre == null ? null : nombre.strip()`), igual que el registro, así
la validación ve el valor recortado. `CambiarContrasenaRequest(@NotBlank String contrasenaActual,
@ContrasenaValida String contrasenaNueva)` no modifica nunca las contraseñas.
- *Recorte*: `Normalizacion` es package-private de `auth` y `usuario` no puede importar `auth`;
  mover la clase a `comun` o hacerla pública para un `strip()` sería más cambio que beneficio.
  Se repite la expresión de una línea y un test fija que ambos requests recortan igual.
- `contrasenaActual` solo exige `@NotBlank`: sin `@Size` para no revelar por validación qué
  longitudes son plausibles; la longitud la resuelve el servicio (decisión 4).
- `CambiarContrasenaRequest` **sobrescribe `toString()`** con un texto fijo sin los campos, y un
  test lo verifica. No se corrige `LoginRequest`/`RegistroRequest` aquí (cambio ajeno al
  alcance); se anota como riesgo.
- Jackson ignora propiedades desconocidas (`FAIL_ON_UNKNOWN_PROPERTIES` desactivado por Spring
  Boot), así que `email`, `rol` o `apellido` en el cuerpo del `PUT` no hacen nada: el record
  solo conoce `nombre`.

### 3. Rutas y respuestas
`PUT /api/v1/usuarios/yo` → `200` `UsuarioActualResponse` (el `desde(Perfil)` existente).
`POST /api/v1/usuarios/yo/contrasena` → `204` con `@ResponseStatus(HttpStatus.NO_CONTENT)` y
retorno `void`. Ambos reciben `@Valid @RequestBody` y `@AuthenticationPrincipal
UsuarioAutenticado`; no hay `@PathVariable` de usuario. Sin cambios en `SecurityConfig`:
`anyRequest().authenticated()` ya las protege.

### 4. Lógica en `UsuarioService`
- `cambiarNombre(Long usuarioId, ActualizarNombreRequest)`: `@Transactional`; carga
  `PerfilRepository.findByUsuarioId` (ya trae el usuario), `setNombre`, guarda y devuelve
  `UsuarioActualResponse.desde`. Si no existe, `NoAutenticadoException` con
  `MENSAJE_NO_AUTENTICADO`, igual que `obtenerActual`.
- `cambiarContrasena(Long usuarioId, CambiarContrasenaRequest)`: `@Transactional`; carga el
  `Usuario` por id (mismo 401 si no existe). Orden fijo:
  1. Validación de formato → 400 (antes del servicio, por Bean Validation).
  2. Usuario inexistente → 401.
  3. Si `contrasenaActual` ocupa más de 72 bytes UTF-8 o `passwordEncoder.matches` es falso →
     `ReglaNegocioException("La contraseña actual es incorrecta")` (422, mensaje fijo como
     constante).
  4. Si `contrasenaNueva.equals(contrasenaActual)` →
     `ReglaNegocioException("La contraseña nueva debe ser distinta de la actual")` (422). Va
     después del paso 3, para no confirmar nada con una actual falsa.
  5. `usuario.setContrasena(passwordEncoder.encode(contrasenaNueva))`.
  Se inyecta el mismo bean `PasswordEncoder` que usa `AuthService`; `UsuarioService` pasa de
  `@RequiredArgsConstructor` con un campo a tener tres (`PerfilRepository`,
  `UsuarioRepository`, `PasswordEncoder`).
- *Por qué 422 y no 401/403 para la actual incorrecta*: el interceptor cierra la sesión con
  cualquier 401; un error de tipeo no debe sacar a la persona. 422 es la "petición válida pero
  el negocio no la permite" de AGENTS.md.
- *Comparación "igual"*: `String.equals` sobre el texto plano, exacto (sin recortar ni
  ignorar mayúsculas). Comparar hashes no sirve (BCrypt tiene sal).
- *Concurrencia*: dos cambios simultáneos terminan con el último que escribe; no hay `@Version`
  en `EntidadBase` y el daño es nulo (la persona queda con una de las dos contraseñas que ella
  eligió). No se agrega bloqueo.

### 5. Tokens y limitación de intentos
- El `204` no emite token y no se invalida ninguno: el JWT es stateless y sin refresh token
  (AGENTS.md). Un token robado sigue sirviendo hasta 24 h aunque se cambie la contraseña. Se
  documenta en AGENTS.md y en el spec (escenario "El token anterior sigue funcionando").
- No hay limitación de intentos en el login, así que **tampoco se agrega aquí** (crearla solo
  para este endpoint sería un mecanismo nuevo y a medias). Queda como riesgo documentado.

## Tabla de paquetes y tests

Raíz de producción: `backend/src/main/java/com/presupuesto/`; de tests:
`backend/src/test/java/com/presupuesto/`.

| Clase | Paquete | Test | Paquete del test |
|---|---|---|---|
| `ContrasenaValida` (nueva, anotación compuesta) | `com.presupuesto.usuario.validacion` | `ContrasenaValidaTest` (nuevo) | `com.presupuesto.usuario.validacion` |
| `ActualizarNombreRequest` (nueva, record) | `com.presupuesto.usuario.dto.request` | `ActualizarNombreRequestTest` (nuevo) | `com.presupuesto.usuario.dto.request` |
| `CambiarContrasenaRequest` (nueva, record) | `com.presupuesto.usuario.dto.request` | `CambiarContrasenaRequestTest` (nuevo) | `com.presupuesto.usuario.dto.request` |
| `UsuarioService` (modificada: 2 métodos, 2 dependencias) | `com.presupuesto.usuario.service` | `UsuarioServiceTest` (nuevo, unitario) | `com.presupuesto.usuario.service` |
| `UsuarioController` (modificada: 2 rutas) | `com.presupuesto.usuario.controller` | `PerfilIntegracionTest` (nuevo, MockMvc) | `com.presupuesto.usuario.controller` |
| `RegistroRequest` (modificada: usa `@ContrasenaValida`) | `com.presupuesto.auth.dto.request` | `RegistroRequestTest` (existente, se amplía) | `com.presupuesto.auth.dto.request` |
| `MaximoBytesUtf8` (modificada: `@Target` admite `ANNOTATION_TYPE`) | `com.presupuesto.comun.validacion` | `MaximoBytesUtf8ValidatorTest` (existente, sin cambios) | `com.presupuesto.comun.validacion` |

`UsuarioActualResponse`, `Perfil`, `Usuario`, los repositorios, `SecurityConfig` y
`ManejadorGlobalExcepciones` no cambian. `usuario` gana el subpaquete `dto/request` (AGENTS.md
decía "todavía no recibe datos propios"). La comprobación de dependencias de `comun/` sigue en
cero líneas: el único cambio en `comun` no añade ningún import.

## Estrategia de pruebas

- **Unitarios** (`UsuarioServiceTest`, Mockito con `BCryptPasswordEncoder` real o simulado):
  nombre actualizado y devuelto; usuario inexistente → 401 en ambos métodos; contraseña correcta
  guarda un hash distinto del texto y que `matches`; actual incorrecta → 422, no guarda; actual
  de `"ñ".repeat(37)` (37 caracteres, 74 bytes UTF-8) → 422 sin llamar a `matches` con ella;
  nueva igual → 422 y no guarda; nueva igual
  con actual incorrecta → gana el mensaje de "actual incorrecta".
- **Requests**: `ActualizarNombreRequestTest` (recorte, 1/2/100/101 caracteres, vacío, nulo);
  `CambiarContrasenaRequestTest` (faltantes, 7/8/72/73 caracteres, 40 caracteres de 2 bytes,
  espacios conservados, `toString()` sin las contraseñas); `ContrasenaValidaTest` (los mismos
  casos sobre la anotación y los mismos mensajes que antes); `RegistroRequestTest` (mensajes de
  contraseña sin cambio).
- **Integración** (`PerfilIntegracionTest`, `@SpringBootTest` + MockMvc + `@Transactional` como
  `UsuarioActualIntegracionTest`): cada test registra sus propios usuarios con un email único
  por test, de modo que no dependen entre sí ni del orden. Cubre: cambio de nombre y consulta
  posterior; solo cambia el nombre aunque el cuerpo traiga otros campos; nombre vacío, de solo
  espacios, corto y de 101 caracteres (400, sin reflejar el valor); contraseña correcta (204);
  login con la nueva funciona y con la vieja falla (`CREDENCIALES_INVALIDAS`); actual
  incorrecta (422, mensaje fijo, sin contraseñas en el cuerpo); actual de `"ñ".repeat(37)`
  (74 bytes, 37 caracteres) con el mismo 422 y el mismo mensaje; nueva débil (400); nueva igual
  (422); faltan datos (400); token anterior sigue válido; Ana no afecta a Beto; sin token (401)
  y token de persona inexistente (401) en ambas rutas.

## Risks / Trade-offs

- **[Sin límite de intentos]** Quien tenga un token válido (robado o de una sesión abierta)
  puede probar contraseñas actuales contra este endpoint, y cualquiera puede hacerlo contra el
  login. → Mitigación parcial: BCrypt es lento por diseño y el token dura 24 h. Se propone un
  change aparte con limitación por usuario/IP para login y cambio de contraseña (conviene un
  único mecanismo).
- **[Token anterior sigue válido]** Cambiar la contraseña no expulsa a un atacante con un token
  ya robado. → Documentado; solucionarlo exige estado (versión de credenciales en el token o
  lista de revocación), fuera de alcance.
- **[Cambio de 422 vs 401]** Un cliente que trate 422 como error genérico debe mostrar el
  mensaje fijo. → El frontend lo interpretará por `codigo` y mensaje en el change de perfil.
- **[`toString()` de requests existentes]** `LoginRequest` y `RegistroRequest` imprimirían la
  contraseña si alguien los registra. → No se registran hoy; se deja anotado para un change
  futuro y se evita en el request nuevo.
- **[Recorte duplicado]** La expresión de recorte existe en dos paquetes. → Un test comprueba
  que ambos requests recortan igual.
- **[Modificar `RegistroRequest`]** Toca un camino crítico. → La anotación compuesta conserva
  los mensajes (sin `@ReportAsSingleViolation`) y los tests de registro existentes más el test
  ampliado son la red.

## Migration Plan

Sin migración de datos ni de esquema; se despliega con el backend. Reversión: revertir el
commit; las contraseñas ya cambiadas siguen siendo hashes BCrypt válidos.
