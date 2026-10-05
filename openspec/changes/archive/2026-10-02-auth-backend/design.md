# Design

## Context

Estado actual del backend (ver proposal.md - Why para la motivación):

- `SecurityConfig` (`comun/seguridad`) ya es stateless, sin CSRF, permite sin autenticación
  `/actuator/health`, `/api/v1/auth/**` y `/error`, y responde 401/403 con `ProblemDetail` mediante
  `AutenticacionEntryPointPersonalizado` (`NO_AUTENTICADO`) y `AccesoDenegadoHandlerPersonalizado`
  (`ACCESO_DENEGADO`). No hay filtro JWT.
- `ManejadorGlobalExcepciones` traduce `RecursoNoEncontradoException` (404),
  `ConflictoException` (409), `ReglaNegocioException` (422) y `MethodArgumentNotValidException`
  (422, código `REGLA_NEGOCIO_VIOLADA`, mapa `errores` campo → mensaje). `ConflictoException` hoy
  fija siempre `CodigoError.CONFLICTO`. No maneja `HttpMessageNotReadableException`: un JSON mal
  formado termina en el 400 por defecto de Spring (`DefaultHandlerExceptionResolver` → `/error`),
  sin `codigo` ni formato `ProblemDetail`. `ManejadorGlobalExcepcionesTest` cubre 404, 409 y 422
  de negocio, pero no tiene ningún test de errores de validación.
- `CodigoError`: `RECURSO_NO_ENCONTRADO`, `CONFLICTO`, `REGLA_NEGOCIO_VIOLADA`, `NO_AUTENTICADO`,
  `ACCESO_DENEGADO`.
- `pom.xml`: Spring Boot 4.1.1 (Jackson 3.1.5, paquete `tools.jackson`), jjwt 0.12.6 con
  `jjwt-api`, `jjwt-impl` y `jjwt-jackson`. `mvn dependency:tree` muestra que `jjwt-jackson`
  arrastra `com.fasterxml.jackson.core:jackson-databind:2.21.5` (Jackson 2, versión gestionada por
  el `jackson-2-bom` de Boot). En Maven Central no existe ningún módulo de jjwt para Jackson 3; la
  última versión, 0.13.0, también depende de Jackson 2.
- La JVM corre en UTC (`configuracion-inicial`). Los tests de integración existentes usan
  `@SpringBootTest` contra el PostgreSQL local.

## Goals / Non-Goals

**Goals:**
- Dejar operativo el ciclo registro → token → petición autenticada, sin estado en el servidor.
- Publicar en este documento el contrato de la API que consumirá `auth-frontend`.
- Que las reglas dependientes de la fecha (mayoría de edad, expiración del token) sean testeables
  fijando el reloj, sin esperas reales.

**Non-Goals:**
- Refresh token, cierre de sesión en servidor, revocación o lista negra de tokens.
- Recuperación o cambio de contraseña, verificación de email, edición del perfil.
- Endpoints o reglas de autorización específicos de `ADMIN` (el rol existe y viaja en el token,
  pero ninguna ruta lo exige todavía).
- Limitación de intentos de inicio de sesión (rate limiting) o bloqueo de cuentas.
- Normalizar el formato del teléfono (solo se limita la longitud).
- Cualquier cambio en el frontend.

## Decisions

**`jjwt-gson` en lugar de `jjwt-jackson`, manteniendo jjwt 0.12.6.**
`jjwt-jackson` funciona técnicamente junto a Jackson 3 porque los paquetes no chocan
(`com.fasterxml.jackson` vs `tools.jackson`), pero obliga a cargar un segundo Jackson completo
solo para serializar los claims, y nunca usaría el `ObjectMapper` configurado por Spring Boot.
`jjwt-gson` es el otro serializador oficial de jjwt; Gson 2.13.2 ya está gestionado por Spring
Boot 4.1.1, así que no se fija versión a mano. Se reemplaza solo el artefacto `runtime`
(`jjwt-api` y `jjwt-impl` no cambian) y se mantiene 0.12.6 para no mezclar este cambio con una
subida de versión. Alternativas descartadas: mantener `jjwt-jackson` (dos Jackson en el
classpath); escribir un serializador propio para jjwt sobre Jackson 3 (código a mantener sin
necesidad); `jjwt-orgjson` (dependencia nueva no gestionada por Boot).

**Dos features: `auth` y `usuario`.**
- `com.presupuesto.usuario`: `Usuario`, `Perfil`, `Rol`, `UsuarioRepository`, `PerfilRepository`,
  `UsuarioController` (`/api/v1/usuarios/yo`), `UsuarioService`, `UsuarioActualResponse`,
  `UsuarioMapper` (MapStruct) y la anotación `@MayorDeEdad` con su validador.
- `com.presupuesto.auth`: `AuthController` (`/api/v1/auth/**`), `AuthService`, `RegistroRequest`,
  `LoginRequest`, `TokenResponse`.
- `comun/seguridad`: `JwtService` (emitir y validar tokens), `FiltroAutenticacionJwt`,
  `JwtProperties`, `UsuarioAutenticado` (principal) y el bean `PasswordEncoder`; la infraestructura
  JWT es transversal (la usarán todas las features protegidas), por eso no vive en `auth`.
- `comun/config`: `RelojConfig` con el bean `Clock`.
- `comun/validacion`: `@MaximoBytesUtf8` y `@MonedaValida`, genéricas, sin lógica de negocio
  (cualquier feature futura con un código de moneda, como las cuentas, reutilizará
  `@MonedaValida`).

**Modelo: `Perfil` es dueño de la relación uno a uno, unidireccional.**
`Perfil` declara `@OneToOne(fetch = LAZY, optional = false)` con
`@JoinColumn(name = "usuario_id", nullable = false, unique = true)`; `Usuario` no tiene referencia
a `Perfil`. Así `Usuario` queda limitado a autenticación (el filtro y el login nunca cargan datos
personales) y se evita el problema conocido de Hibernate de que el lado inverso de un `@OneToOne`
no puede ser realmente `LAZY`. `/usuarios/yo` lee desde `Perfil`:
`PerfilRepository.findByUsuarioId(Long)` con `@EntityGraph(attributePaths = "usuario")`, una sola
consulta con join, necesaria porque `spring.jpa.open-in-view=false`.

Columnas (Hibernate las crea con `ddl-auto=update`):

| Tabla      | Columna                 | Tipo / restricciones                                  |
|------------|-------------------------|-------------------------------------------------------|
| `usuarios` | `email`                 | `varchar(254)`, not null, unique                      |
| `usuarios` | `contrasena`            | `varchar(100)`, not null (hash BCrypt, 60 caracteres) |
| `usuarios` | `rol`                   | `varchar(20)`, not null, `@Enumerated(STRING)`        |
| `perfiles` | `usuario_id`            | bigint, not null, unique, FK → `usuarios.id`          |
| `perfiles` | `nombre`, `apellido`    | `varchar(100)`, not null                              |
| `perfiles` | `fecha_nacimiento`      | `date`, not null                                      |
| `perfiles` | `moneda_predeterminada` | `varchar(3)`, not null                                |
| `perfiles` | `telefono`              | `varchar(20)`, nullable                               |

Más `id`, `fecha_creacion` y `fecha_actualizacion` de `EntidadBase`. `contrasena` usa 100 y no 60
para no tener que migrar la columna si en el futuro se cambia a un `DelegatingPasswordEncoder`
(que antepone `{bcrypt}`). `rol` se inicializa en `USUARIO` con `@Builder.Default`.

**Normalización en el constructor compacto de los records de request, antes de validar.**
`RegistroRequest` y `LoginRequest` normalizan sus campos en el constructor compacto del record,
que Jackson invoca al deserializar, así que Bean Validation ya ve los valores normalizados:
- `email` (registro y login): sin espacios al inicio ni al final y en minúsculas,
  `strip().toLowerCase(Locale.ROOT)`. `Locale.ROOT` evita el problema de la "i" turca, y `strip()`
  recorta también los espacios Unicode, no solo los ASCII que quita `trim()`.
- `nombre` y `apellido` (registro): `strip()`, sin cambiar mayúsculas.
- `telefono` (registro): `strip()`, y `null` si queda vacío.
- `monedaPredeterminada` (registro): `BOB` si llega `null`; no se recorta ni se pasa a mayúsculas
  (`" usd"` o `"usd"` se rechazan con 400 por `@MonedaValida`).
- `contrasena` (registro y login): **nunca se modifica**; los espacios o mayúsculas forman parte
  de la contraseña, y una contraseña registrada con un espacio final solo se acepta en el login
  con ese mismo espacio.

Un `null` se deja como `null` (no se normaliza) para que lo rechacen `@NotBlank`/`@NotNull`.
Normalizar antes de validar hace que `"  ana@ejemplo.com  "` pase `@Email`, y que `"  A  "` falle
`@Size(min = 2)` por tener un solo carácter real. Alternativa descartada: normalizar en el
service, después de validar, que rechazaría con 400 un email válido rodeado de espacios y
aceptaría un nombre de un solo carácter rodeado de espacios.

**Moneda: anotación propia `@MonedaValida` contra `java.util.Currency`, no `@Pattern`.**
`@Pattern(regexp = "[A-Z]{3}")` solo comprueba la forma y aceptaría códigos inexistentes como
`ZZZ`, que el sistema guardaría como moneda predeterminada de la persona. `@MonedaValida`
(`comun/validacion`, mensaje "No es un código de moneda ISO 4217 válido") acepta solo los
códigos de `Currency.getAvailableCurrencies()`. Su validador construye una sola vez, en un campo
`static final`, un `Set<String>` inmutable con los `getCurrencyCode()` de esas monedas, y cada
validación es una búsqueda en ese conjunto. La comparación distingue mayúsculas: los códigos del
JDK están en mayúsculas, así que `bob` se rechaza aunque `BOB` exista (coherente con no
normalizar este campo). `null` es válido, como en el resto de restricciones; en la práctica
nunca llega `null`, porque el constructor compacto aplica `BOB` antes de validar. Se descartó
`Currency.getInstance(codigo)` dentro de un `try/catch`: usa excepciones para el flujo normal y
acepta los mismos códigos, sin ventaja. Con el JDK 21 del proyecto el conjunto tiene 230 códigos:
incluye `BOB` y `USD`, y no incluye `ZZZ` ni `bob`.

**Email duplicado: comprobación previa más restricción única como red de seguridad.**
`registrar` comprueba `existsByEmail` y lanza `ConflictoException(EMAIL_YA_REGISTRADO, ...)`. Como
dos registros simultáneos pueden pasar ambos la comprobación, el `Usuario` se guarda con
`saveAndFlush` y una `DataIntegrityViolationException` se traduce a la misma
`ConflictoException`. `ConflictoException` gana un constructor `(CodigoError, String)`; el
existente `(String)` sigue usando `CONFLICTO`.

**Registro atómico.**
`AuthService.registrar` es `@Transactional`: crea `Usuario`, luego `Perfil`, y emite el token. Si
cualquier paso lanza una excepción, se revierte todo (escenario de credenciales huérfanas de
`registro-usuarios`).

**Mayoría de edad: `@MayorDeEdad` con `Clock` inyectado en el validador.**
`MayorDeEdadValidator implements ConstraintValidator<MayorDeEdad, LocalDate>` recibe el `Clock`
por constructor; Spring Boot crea los validadores con `SpringConstraintValidatorFactory`, que
inyecta beans. Regla: `null` es válido (de eso se encarga `@NotNull`) y, si no,
`Period.between(fechaNacimiento, LocalDate.now(clock)).getYears() >= 18`. `Period.between` cuenta
el aniversario como cumplido y trata el 29 de febrero como cumplido el 1 de marzo en años no
bisiestos (el 28 de febrero de 2026 da 17 años, 11 meses y 30 días). Una fecha futura da años
negativos y se rechaza. El mensaje propio es "Debe tener 18 años o más" (en español, a diferencia
de los mensajes por defecto de Jakarta Validation, porque lo escribimos nosotros). Su fallo es un
`FieldError` más, así que lo traduce el manejador de validación (400 `DATOS_INVALIDOS`, ver más
abajo) sin código propio (decisión del usuario: no se crea `MENOR_DE_EDAD`).

`RelojConfig` declara `Clock.systemUTC()`. Esto es coherente con la JVM en UTC; como "hoy" se
calcula en UTC, en Bolivia (UTC-4) una persona que cumple 18 puede ser aceptada desde las 20:00
del día anterior (ver Risks). La convención para el resto del proyecto es obtener cualquier
"ahora"/"hoy" desde este bean, nunca con `LocalDate.now()` ni `Instant.now()` directos.

**Contraseña: `@Size(min = 8, max = 72)` más `@MaximoBytesUtf8(72)`.**
BCrypt solo usa los primeros 72 bytes, y `BCryptPasswordEncoder` de Spring Security 7 lanza
`IllegalArgumentException` al codificar más de 72 bytes, lo que hoy acabaría en un 500. `@Size`
cuenta caracteres (`ñ` o `á` ocupan 2 bytes en UTF-8), así que se agrega la anotación propia
`@MaximoBytesUtf8(72)` (mensaje "No puede ocupar más de 72 bytes"). En el login no se aplica
ninguna de las dos: si la contraseña recibida supera 72 bytes, `AuthService` responde
directamente como credenciales inválidas sin llamar a BCrypt, porque ninguna contraseña
registrada puede ser tan larga.

**Errores de validación: 400 `DATOS_INVALIDOS`, separado de las reglas de negocio (422).**
Se agrega `CodigoError.DATOS_INVALIDOS` y `ManejadorGlobalExcepciones.manejarValidacion` pasa a
responder `MethodArgumentNotValidException` con 400 `DATOS_INVALIDOS`, el mismo `detail` ("Uno o
más campos no son válidos") y el mismo mapa `errores`; esto incluye `@MayorDeEdad`,
`@MaximoBytesUtf8` y `@MonedaValida`, que son restricciones de Bean Validation como cualquier
otra. 422
`REGLA_NEGOCIO_VIOLADA` queda reservado para `ReglaNegocioException`: reglas que dependen del
estado del sistema y que el cliente no puede comprobar solo con el formulario. El criterio es:
si la petición es inválida por sí misma (formato, longitud, obligatoriedad, una regla sobre un
único campo), es 400; si es válida pero el negocio no la permite en el estado actual, es 422 (o
409 si es un conflicto con un recurso existente, como el email duplicado). Así el frontend puede
tratar un 400 como "corrige el formulario" y un 422 como un error de negocio que se muestra tal
cual. El cambio es incompatible respecto del 422 actual, pero todavía ningún endpoint de negocio
ni el frontend dependen de él. Alternativa descartada: mantener 422 para todo, porque mezcla en
un mismo código errores que el frontend debe tratar distinto.

**Cuerpo ilegible: `HttpMessageNotReadableException` → 400 `DATOS_INVALIDOS` sin detalles.**
`ManejadorGlobalExcepciones` agrega un `@ExceptionHandler(HttpMessageNotReadableException.class)`
que responde 400 `DATOS_INVALIDOS` con un `detail` fijo ("El cuerpo de la petición no es
válido"), sin mapa `errores`. Cubre JSON mal formado, un cuerpo vacío, un tipo incorrecto (texto
donde va un objeto) y una fecha imposible como `2000-13-01`, porque Jackson falla al deserializar
antes de llegar a Bean Validation. Nunca se usa `excepcion.getMessage()` ni la causa: el mensaje
de Jackson incluye nombres de clases internas, la posición del error y fragmentos del cuerpo
recibido. El handler del `@RestControllerAdvice` tiene prioridad sobre
`DefaultHandlerExceptionResolver`, así que la respuesta ya no pasa por `/error`. Alternativa
descartada: extender `ResponseEntityExceptionHandler`, que maneja de una vez todas las
excepciones de Spring MVC, pero con su propio formato y sin `codigo`, lo que obligaría a
sobrescribir varios métodos para unificarlo; basta con manejar esta excepción, que es la única
que el cliente provoca en el flujo normal.

**Excepción 401 propia: `NoAutenticadoException`, usada por el login y por `/usuarios/yo`.**
Se agrega a la jerarquía de `comun/excepcion` una `NoAutenticadoException(CodigoError, String)`
(401) y su `@ExceptionHandler` en `ManejadorGlobalExcepciones`. Aquí sí aplica el
`@RestControllerAdvice`, porque estos 401 se deciden en un controller o service y no en un filtro
de seguridad. Se usa en dos casos:
- Login fallido: código `CREDENCIALES_INVALIDAS`, mensaje fijo "Email o contraseña incorrectos".
- `/usuarios/yo` con un token válido de un usuario que ya no existe: código `NO_AUTENTICADO` y el
  mismo mensaje que el `AuthenticationEntryPoint` ("Se requiere autenticación para acceder a este
  recurso"), para que el frontend no distinga este caso de un token inválido y cierre la sesión.
  El mensaje se define una sola vez (constante compartida) para que ambas respuestas no
  diverjan.

Una sola clase con el código como parámetro evita crear una excepción por cada motivo de 401,
igual que `ConflictoException` con `EMAIL_YA_REGISTRADO`.

**Credenciales inválidas con tiempo de respuesta parejo.**
Ante email inexistente, contraseña incorrecta o contraseña de más de 72 bytes, `AuthService`
lanza `NoAutenticadoException(CREDENCIALES_INVALIDAS, ...)` con el mismo mensaje. Para no
revelar por el tiempo de respuesta si un email existe, cuando el email no existe `AuthService`
igualmente ejecuta
`passwordEncoder.matches` contra un hash ficticio generado una vez al arrancar. Alternativa
descartada: usar el `AuthenticationManager` de Spring Security con un `UserDetailsService`;
añade varias clases y su `BadCredentialsException` habría que traducirla igual a nuestro
`ProblemDetail`, sin aportar nada para un único flujo de login.

**JWT: HS256 con `JWT_SECRET` en texto plano, sub = id, claim `rol`, 24 h.**
- `application.properties`:
  `jwt.secreto=${JWT_SECRET:cada-peso-secreto-solo-para-desarrollo-local-no-usar-en-produccion}`
  y `jwt.expiracion=24h`, leídos con `@ConfigurationProperties("jwt")` en el record
  `JwtProperties(String secreto, Duration expiracion)`. El valor por defecto ocupa 66 bytes en
  UTF-8 (solo ASCII), por encima del mínimo de 32 bytes (256 bits) que jjwt exige para HS256. Es
  solo para desarrollo local; cualquier otro entorno define `JWT_SECRET`.
- La clave es `Keys.hmacShaKeyFor(secreto.getBytes(UTF_8))` y se firma con el algoritmo fijado
  explícitamente, `signWith(clave, Jwts.SIG.HS256)`; sin fijarlo, jjwt elige el algoritmo según
  la longitud de la clave (con 66 bytes elegiría HS512), y el algoritmo cambiaría al cambiar el
  secreto. jjwt lanza `WeakKeyException` si el secreto tiene menos de 32 bytes, así que un
  `JWT_SECRET` débil hace fallar el arranque (fail-fast) en vez de emitir tokens inseguros: la
  clave se construye una sola vez al crear `JwtService`, no en cada petición. Se usa texto plano
  y no Base64 para que la variable de entorno sea fácil de definir.
- Claims: `sub` = id del usuario (string), `rol` = nombre del enum, `iat` y `exp` = `iat` + 24 h.
  `iat`/`exp` se calculan con el `Clock`, y el parser recibe un `io.jsonwebtoken.Clock` que
  adapta ese mismo `Clock` (`() -> Date.from(clock.instant())`), así que los tests de expiración
  avanzan el reloj en vez de esperar.
- No se guarda nada en el servidor: el filtro confía en `sub` y `rol` sin consultar la base de
  datos. Un cambio de rol se refleja en el siguiente login (como máximo 24 h después).

**Filtro JWT creado dentro de `SecurityConfig`, no como bean.**
`FiltroAutenticacionJwt extends OncePerRequestFilter`. Si el header `Authorization` empieza con
`Bearer `, valida el token con `JwtService`; si es válido, coloca en el `SecurityContext` un
`UsernamePasswordAuthenticationToken` autenticado cuyo principal es el record
`UsuarioAutenticado(Long id, Rol rol)` y su autoridad `ROLE_<rol>`. Si no hay header, o el token es
inválido o expiró (`JwtException`, `IllegalArgumentException`), no autentica y deja seguir la
cadena: en una ruta protegida responde el entry point existente (401 `NO_AUTENTICADO`), y en una
ruta pública la petición se procesa normalmente. Se registra con
`addFilterBefore(filtro, UsernamePasswordAuthenticationFilter.class)` y se instancia con `new`
dentro de `SecurityConfig`: si fuese un bean, Spring Boot lo registraría además como filtro de
servlet y se ejecutaría fuera de la cadena de seguridad. Los controllers obtienen el usuario con
`@AuthenticationPrincipal UsuarioAutenticado`.

**`/usuarios/yo` con un usuario inexistente responde 401 `NO_AUTENTICADO`.**
Si el token es válido pero ya no existe un `Perfil` para ese id (por ejemplo, se vació la base de
datos local, o en el futuro se borra la cuenta), `UsuarioService` lanza
`NoAutenticadoException(NO_AUTENTICADO, ...)`. El token ya no representa a nadie, así que la
sesión es inválida y el frontend debe tratarlo igual que un token expirado: descartar el token y
volver al login. Alternativa descartada: 404 `RECURSO_NO_ENCONTRADO`, que dejaría al frontend con
una sesión "válida" para un usuario inexistente y obligaría a tratar un caso más. El filtro no
consulta la base de datos para detectarlo en todas las rutas (ver la decisión del JWT): solo
`/usuarios/yo` lo detecta, porque es la ruta que el frontend usa para cargar la sesión.

**Tests con un reloj de prueba mutable y rollback.**
Los tests registran un `RelojDePrueba` (`extends Clock`, instante modificable) como bean
`@Primary` en una `@TestConfiguration` compartida. Así se reutiliza un solo contexto de Spring
para distintas fechas, mientras que con un `Clock.fixed` por clase haría falta un contexto por
fecha. Los tests de integración con MockMvc se anotan con `@Transactional` para que cada test
revierta lo que inserta en el PostgreSQL local. La excepción es el test de atomicidad, que
necesita que la transacción del service se revierta por sí sola: no es `@Transactional` y limpia
sus datos al terminar.

## Contrato de la API

Referencia para `auth-frontend`. Todos los cuerpos son JSON; las fechas `LocalDate` viajan como
`yyyy-MM-dd` y los `Instant` como ISO-8601 en UTC con sufijo `Z`. Los nombres de campo son
exactamente los indicados (sin tildes ni `ñ`).

### Formato de error (`ProblemDetail`, `application/problem+json`)

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Uno o más campos no son válidos",
  "instance": "/api/v1/auth/registro",
  "codigo": "DATOS_INVALIDOS",
  "timestamp": "2026-10-02T12:00:00Z",
  "errores": { "contrasena": "...", "fechaNacimiento": "Debe tener 18 años o más" }
}
```

Errores comunes a todos los endpoints con cuerpo:

| HTTP | `codigo`                | `errores` | Cuándo                                            |
|------|-------------------------|-----------|---------------------------------------------------|
| 400  | `DATOS_INVALIDOS`       | sí        | Falla una validación de campo                     |
| 400  | `DATOS_INVALIDOS`       | no        | JSON ilegible, tipo incorrecto o fecha imposible  |
| 422  | `REGLA_NEGOCIO_VIOLADA` | no        | Regla de negocio (ningún endpoint de este change) |

`detail` fijo de cada 400: "Uno o más campos no son válidos" (validación de campo) y "El cuerpo
de la petición no es válido" (cuerpo ilegible).

`errores` solo aparece en los 400 por validación de campos y tiene un mensaje por campo inválido;
si un campo viola varias reglas, aparece un solo mensaje para ese campo. El 400 por cuerpo
ilegible nunca incluye detalles del intérprete de JSON. Los mensajes de las reglas estándar
(`@NotBlank`, `@NotNull`, `@Size`, `@Email`) están en el idioma de la JVM; los de las anotaciones
propias (`@MayorDeEdad`, `@MaximoBytesUtf8`, `@MonedaValida`) están en español. Todos son solo un
respaldo: el frontend debe validar y mostrar sus propios mensajes. Ningún endpoint de este
change produce 422. El frontend debe decidir según `status` y `codigo`, nunca según el texto de
`detail`.

### `POST /api/v1/auth/registro` (público)

Request `RegistroRequest` (todos los campos son string en el JSON; obligatorios los que llevan
`@NotBlank`/`@NotNull`):

| Campo                  | Validaciones                                                    |
|------------------------|-----------------------------------------------------------------|
| `email`                | `@NotBlank`, `@Email`, `@Size(max = 254)`                       |
| `contrasena`           | `@NotBlank`, `@Size(min = 8, max = 72)`, `@MaximoBytesUtf8(72)` |
| `nombre`               | `@NotBlank`, `@Size(min = 2, max = 100)`                        |
| `apellido`             | `@NotBlank`, `@Size(min = 2, max = 100)`                        |
| `fechaNacimiento`      | formato `yyyy-MM-dd`; `@NotNull`, `@MayorDeEdad`                |
| `telefono`             | opcional; `@Size(max = 20)`                                     |
| `monedaPredeterminada` | opcional; `@MonedaValida` (código ISO 4217 existente)           |

Normalización, aplicada **antes** de validar: `email` sin espacios al inicio ni al final y en
minúsculas; `nombre` y `apellido` sin espacios al inicio ni al final (las longitudes se miden
después de recortar); `telefono` recortado, y `null` si queda vacío; `monedaPredeterminada`
ausente o `null` se guarda como `BOB`. `contrasena` nunca se modifica (ni se recorta).
`@MayorDeEdad` exige 18 años cumplidos a la fecha del servidor (UTC).

Respuestas:

| HTTP | `codigo`              | Cuándo                                                          |
|------|-----------------------|-----------------------------------------------------------------|
| 201  | —                     | Registro creado; cuerpo `TokenResponse`                         |
| 400  | `DATOS_INVALIDOS`     | Falla una validación (mayoría de edad incluida) o cuerpo ilegible |
| 409  | `EMAIL_YA_REGISTRADO` | El email, sin distinguir mayúsculas, ya tiene cuenta            |

### `POST /api/v1/auth/login` (público)

Request `LoginRequest`:

| Campo        | Validaciones                                 |
|--------------|----------------------------------------------|
| `email`      | `@NotBlank`; se recorta y compara en minúsculas |
| `contrasena` | `@NotBlank` (sin `@Size` ni límite de bytes)    |

`email` se normaliza igual que en el registro (sin espacios al inicio ni al final y en
minúsculas), así que `"  Ana@Ejemplo.COM "` inicia sesión en la cuenta `ana@ejemplo.com`.
`contrasena` se compara tal como llega, sin recortar. Una `contrasena` de más de 72 bytes
responde como credenciales inválidas.

Respuestas:

| HTTP | `codigo`                 | Cuándo                                         |
|------|--------------------------|------------------------------------------------|
| 200  | —                        | Credenciales correctas; cuerpo `TokenResponse` |
| 400  | `DATOS_INVALIDOS`        | Falta `email` o `contrasena`, o cuerpo ilegible |
| 401  | `CREDENCIALES_INVALIDAS` | Email inexistente o contraseña incorrecta      |

El 401 usa el mismo `detail` ("Email o contraseña incorrectos") en los dos casos.

### `TokenResponse` (registro y login)

| Campo      | Tipo             | Ejemplo / significado                                    |
|------------|------------------|----------------------------------------------------------|
| `token`    | string           | JWT para el header `Authorization: Bearer <token>`       |
| `tipo`     | string           | Siempre `"Bearer"`                                       |
| `expiraEn` | string `Instant` | `"2026-10-03T12:00:00Z"`: emisión + 24 h; no hay refresh |

### `GET /api/v1/usuarios/yo` (protegido)

Header obligatorio: `Authorization: Bearer <token>`. Sin cuerpo de request.

Response `UsuarioActualResponse`:

| Campo                  | Tipo                | Notas                   |
|------------------------|---------------------|-------------------------|
| `email`                | string              | En minúsculas           |
| `rol`                  | string              | `"USUARIO"` o `"ADMIN"` |
| `nombre`               | string              |                         |
| `apellido`             | string              |                         |
| `fechaNacimiento`      | string `yyyy-MM-dd` |                         |
| `telefono`             | string o `null`     |                         |
| `monedaPredeterminada` | string ISO 4217     |                         |

Nunca incluye `contrasena` ni el hash, ni el `id` interno.

Respuestas:

| HTTP | `codigo`         | Cuándo                                                             |
|------|------------------|--------------------------------------------------------------------|
| 200  | —                | Cuerpo `UsuarioActualResponse`                                     |
| 401  | `NO_AUTENTICADO` | Sin token, token mal formado, con firma inválida o expirado        |
| 401  | `NO_AUTENTICADO` | Token válido de un usuario que ya no existe (mismo `detail`)       |

En todos los 401 de esta ruta el `detail` es "Se requiere autenticación para acceder a este
recurso": el frontend no necesita distinguir los casos y siempre debe cerrar la sesión.

### Uso del token en cualquier ruta protegida

Cualquier ruta fuera de `/api/v1/auth/**`, `/actuator/health` y `/error` exige el header
`Authorization: Bearer <token>`; sin él, o con un token inválido o expirado, responde 401
`NO_AUTENTICADO`. Al recibir ese 401 el frontend debe descartar el token y pedir login de nuevo.

## Risks / Trade-offs

- [Un token robado es válido hasta 24 h y no se puede revocar] → Aceptado para esta etapa (sin
  refresh ni lista negra, fuera de alcance). La expiración corta y HTTPS en producción lo acotan.
- ["Hoy" se calcula en UTC: en Bolivia (UTC-4) el día en que se cumplen 18 empieza a contar desde
  las 20:00 del día anterior] → Aceptado: la diferencia es de horas y no permite registrar a nadie
  con menos de 17 años y 364 días. Si hiciera falta exactitud por zona, se calcularía con la zona
  enviada por el cliente en un change futuro.
- [El valor por defecto de `JWT_SECRET` es público (está en el repositorio)] → Solo sirve para
  desarrollo local; `AGENTS.md` indica que cualquier otro entorno debe definir `JWT_SECRET` con
  al menos 32 bytes. Un secreto más corto hace fallar el arranque. Si un entorno olvida definirlo,
  arrancaría con el valor público: se acepta en esta etapa porque no hay perfiles de Spring para
  distinguir entornos (ver `configuracion-inicial`).
- [El filtro confía en el `rol` del token sin consultar la base de datos] → Aceptado: no hay
  rutas de `ADMIN` todavía; un cambio de rol se aplica en el siguiente login.
- [Pasar los errores de validación de 422 a 400 cambia el contrato de todos los endpoints] →
  Ningún endpoint de negocio ni el frontend existen todavía; `AGENTS.md` documenta la distinción
  para que los changes siguientes la respeten desde el principio.
- [Solo se maneja `HttpMessageNotReadableException`; otras excepciones de Spring MVC (método no
  permitido, tipo de contenido no soportado, parámetro de ruta mal tipado) siguen saliendo con el
  formato por defecto de Spring Boot] → Aceptado: el cliente no las provoca en el flujo normal.
  Se pueden unificar en un change transversal si aparecen.
- [Un usuario inexistente con un token válido solo se detecta en `/usuarios/yo`; en otras rutas
  protegidas la petición se procesaría] → Aceptado mientras no haya borrado de usuarios; las
  features futuras que consulten datos del usuario fallarán al no encontrarlos. Si se agrega el
  borrado de cuentas, se revisará si el filtro debe comprobar la existencia.
- [`@MonedaValida` acepta todo lo que el JDK conoce, incluidos códigos especiales que no son una
  moneda de uso cotidiano (`XXX` "sin moneda", `XTS` "de prueba", metales como `XAU`), y el
  conjunto puede variar entre versiones del JDK] → Aceptado: son códigos ISO 4217 reales que
  `Intl.NumberFormat` del frontend sabe formatear, y el frontend ofrecerá una lista cerrada de
  monedas en el formulario. Si hiciera falta restringirlos, se filtrarían en el validador en un
  change futuro.
- [Sin limitación de intentos, el login permite fuerza bruta] → Fuera de alcance; BCrypt
  (coste 10) encarece cada intento. Se puede agregar en un change de endurecimiento.
- [Las pruebas de integración escriben en el PostgreSQL local] → `@Transactional` con rollback
  por test; el test de atomicidad limpia explícitamente sus datos.

## Migration Plan

1. Desplegar el backend: Hibernate crea `usuarios` y `perfiles` (`ddl-auto=update`); no hay datos
   previos que migrar.
2. Definir `JWT_SECRET` (al menos 32 bytes) en cualquier entorno distinto del local.
3. Rollback: volver a la versión anterior. Las tablas nuevas pueden quedar sin uso o borrarse a
   mano (`perfiles` antes que `usuarios`, por la clave foránea).
