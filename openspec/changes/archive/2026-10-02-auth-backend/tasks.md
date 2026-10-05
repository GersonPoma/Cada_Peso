# Tasks

## 1. Dependencias y configuración base

- [x] 1.1 En `pom.xml`, reemplazar `io.jsonwebtoken:jjwt-jackson` por `io.jsonwebtoken:jjwt-gson`
      (misma `${jjwt.version}`, scope `runtime`, sin fijar versión de Gson); verificar con
      `.\mvnw.cmd dependency:tree "-Dincludes=com.fasterxml.jackson.core,com.google.code.gson"`
      que ya no aparecen `com.fasterxml.jackson.core:jackson-databind` ni `jackson-core` 2.x
      (`jackson-annotations` puede seguir apareciendo porque Jackson 3 lo usa) y que aparece
      `com.google.code.gson:gson` 2.13.2.
- [x] 1.2 Crear `comun/config/RelojConfig` con un bean `Clock` (`Clock.systemUTC()`) y, en
      `src/test`, `RelojDePrueba` (`extends Clock`, instante modificable, zona UTC) registrado
      como bean `@Primary` en una `@TestConfiguration` compartida; verificar con un test que,
      tras fijar un instante en `RelojDePrueba`, el `Clock` inyectado en el contexto devuelve ese
      instante.
- [x] 1.3 Crear `comun/seguridad/JwtProperties` (`@ConfigurationProperties("jwt")`, record con
      `String secreto` y `Duration expiracion`), habilitarlo con
      `@EnableConfigurationProperties` y agregar a `application.properties`
      `jwt.secreto=${JWT_SECRET:cada-peso-secreto-solo-para-desarrollo-local-no-usar-en-produccion}`
      (66 bytes, solo ASCII) y `jwt.expiracion=24h`; declarar el bean `PasswordEncoder`
      (`BCryptPasswordEncoder`) en `comun/seguridad`; verificar con un test de contexto, sin
      `JWT_SECRET` definido, que `JwtProperties` carga una expiración de 24 h y un secreto por
      defecto de al menos 32 bytes en UTF-8 (256 bits, mínimo de jjwt para HS256), y que el
      `PasswordEncoder` codifica y verifica una contraseña.
- [x] 1.4 Actualizar `AGENTS.md`: `jjwt-gson` en vez de `jjwt-jackson` (con el motivo: no existe
      módulo de jjwt para Jackson 3); la variable de entorno `JWT_SECRET` en la tabla de
      variables de entorno, indicando que su valor por defecto es **solo para desarrollo local**
      (es público, está en el repositorio), que en cualquier otro entorno se debe definir
      `JWT_SECRET` y que debe tener al menos 32 bytes (256 bits, requisito de jjwt para HS256) o
      la aplicación no arranca; y la convención de obtener cualquier "ahora"/"hoy" desde el bean
      `Clock`, nunca con `LocalDate.now()`/`Instant.now()` directos; verificar que el archivo
      cubre los tres puntos.

## 2. Excepciones y códigos de error

- [x] 2.1 Agregar `EMAIL_YA_REGISTRADO`, `CREDENCIALES_INVALIDAS` y `DATOS_INVALIDOS` a
      `CodigoError`; agregar a `ConflictoException` un constructor `(CodigoError, String)`
      conservando el existente; crear `NoAutenticadoException(CodigoError, String)` (401) y su
      `@ExceptionHandler` en `ManejadorGlobalExcepciones`; extraer a una constante compartida el
      mensaje "Se requiere autenticación para acceder a este recurso" y usarla también en
      `AutenticacionEntryPointPersonalizado`; verificar extendiendo
      `ManejadorGlobalExcepcionesTest` (con nuevos endpoints en `ControladorDePrueba`, solo en
      `src/test`) que una `ConflictoException` con `EMAIL_YA_REGISTRADO` responde 409 con ese
      `codigo`, que `NoAutenticadoException` responde 401 con el `codigo` recibido, `timestamp` y
      el `detail` recibido, y que `SecurityConfigTest` sigue pasando con el mismo `detail`.
- [x] 2.2 Cambiar `manejarValidacion` de `ManejadorGlobalExcepciones` para que
      `MethodArgumentNotValidException` responda 400 `DATOS_INVALIDOS` (mismo `detail` "Uno o más
      campos no son válidos" y mismo mapa `errores`), dejando 422 `REGLA_NEGOCIO_VIOLADA` solo
      para `ReglaNegocioException`; agregar un `@ExceptionHandler(HttpMessageNotReadableException)`
      que responda 400 `DATOS_INVALIDOS` con el `detail` fijo "El cuerpo de la petición no es
      válido", sin `errores` y sin usar el mensaje ni la causa de la excepción; verificar en
      `ManejadorGlobalExcepcionesTest`, contra un endpoint `POST` de prueba con un DTO `@Valid`
      (en `ControladorDePrueba`), que un campo inválido responde 400 `DATOS_INVALIDOS` con el
      campo en `errores`; que un JSON mal formado, un cuerpo vacío y una fecha `2000-13-01`
      responden 400 `DATOS_INVALIDOS` con el `detail` fijo y un cuerpo que no contiene nombres de
      clases (`com.`, `tools.jackson`), "line" ni "column"; y que el test existente de
      `ReglaNegocioException` sigue respondiendo 422 `REGLA_NEGOCIO_VIOLADA`.
- [x] 2.3 Actualizar la sección de excepciones de `AGENTS.md`: la nueva `NoAutenticadoException`
      (401, con el `CodigoError` como parámetro) y que el 401 que decide un controller o service
      lo emite el `@RestControllerAdvice`, mientras que el 401 `NO_AUTENTICADO` de una petición
      sin token válido lo sigue emitiendo el `AuthenticationEntryPoint`; la distinción 400
      `DATOS_INVALIDOS` (la petición es inválida por sí misma: validación de campos, incluidas
      anotaciones propias como `@MayorDeEdad`, o cuerpo ilegible) vs. 422
      `REGLA_NEGOCIO_VIOLADA` (petición válida que el negocio no permite en el estado actual) vs.
      409 (conflicto con un recurso existente); y que el cuerpo ilegible nunca expone el detalle
      del parser; verificar que el texto cubre los tres puntos.

## 3. Validaciones propias

- [x] 3.1 Crear `comun/validacion/MaximoBytesUtf8` (anotación con atributo `value`, mensaje "No
      puede ocupar más de {value} bytes") y su validador (`null` es válido); verificar con tests
      unitarios que 72 caracteres ASCII se aceptan, 72 caracteres con al menos una `ñ` se
      rechazan, 36 caracteres `ñ` (72 bytes) se aceptan y `null` se acepta.
- [x] 3.2 Crear `usuario/MayorDeEdad` (mensaje "Debe tener 18 años o más") y
      `MayorDeEdadValidator` con el `Clock` inyectado por constructor, aplicando
      `Period.between(fechaNacimiento, LocalDate.now(clock)).getYears() >= 18` y aceptando `null`;
      verificar con tests unitarios del validador con reloj fijo: hoy `2026-10-02`, nacida
      `2008-10-02` se acepta y `2008-10-03` se rechaza; hoy `2026-02-28`, nacida `2008-02-29` se
      rechaza; hoy `2026-03-01`, nacida `2008-02-29` se acepta; una fecha futura se rechaza.
- [x] 3.3 Crear `comun/validacion/MonedaValida` (mensaje "No es un código de moneda ISO 4217
      válido") y su validador, que compara contra un `Set<String>` `static final` inmutable con
      los `getCurrencyCode()` de `Currency.getAvailableCurrencies()`, distinguiendo mayúsculas y
      aceptando `null`; verificar con tests unitarios que `BOB` y `USD` se aceptan, que `ZZZ`,
      `bob`, `" USD"` y `""` se rechazan, y que `null` se acepta.

## 4. Modelo de datos

- [x] 4.1 Crear `usuario/Rol` (`USUARIO`, `ADMIN`), `usuario/Usuario` (tabla `usuarios`:
      `email` 254 único y no nulo, `contrasena` 100 no nula, `rol` `@Enumerated(STRING)` 20 no
      nulo con `@Builder.Default` `USUARIO`) y `usuario/Perfil` (tabla `perfiles`: `nombre` y
      `apellido` 100, `fechaNacimiento`, `monedaPredeterminada` 3, `telefono` 20 nullable y
      `@OneToOne(fetch = LAZY, optional = false)` con `@JoinColumn(name = "usuario_id",
      nullable = false, unique = true)`), ambas con `@SuperBuilder` y extendiendo `EntidadBase`;
      crear `UsuarioRepository` (`findByEmail`, `existsByEmail`) y `PerfilRepository`
      (`findByUsuarioId` con `@EntityGraph(attributePaths = "usuario")`); verificar con un test
      de integración `@Transactional` que un `Usuario` nuevo tiene rol `USUARIO`, que guardar dos
      usuarios con el mismo email lanza `DataIntegrityViolationException`, y que
      `findByUsuarioId` devuelve el perfil con su usuario accesible tras limpiar el
      `EntityManager`.

## 5. Servicio JWT

- [x] 5.1 Crear `comun/seguridad/JwtService` con `emitir(Usuario)` (sub = id, claim `rol`, `iat`
      y `exp` desde el `Clock`, `exp` = `iat` + `jwt.expiracion`, clave
      `Keys.hmacShaKeyFor(secreto UTF-8)` construida una sola vez en el constructor, firma con
      `signWith(clave, Jwts.SIG.HS256)`) que devuelve el token y su instante de expiración, y
      `validar(String)` que devuelve un `UsuarioAutenticado(Long id, Rol rol)` usando un parser
      con `io.jsonwebtoken.Clock` adaptado del `Clock`; verificar con tests usando
      `RelojDePrueba`: un token recién emitido se valida con su id y rol, y su header tiene
      `alg` = `HS256` aunque el secreto por defecto tenga 66 bytes; un token con un carácter
      alterado lanza `JwtException`; el mismo token tras avanzar el reloj 24 h y 1 s lanza
      `ExpiredJwtException`; un secreto de 31 bytes hace fallar la construcción de `JwtService`
      con `WeakKeyException` y uno de exactamente 32 bytes se acepta.

## 6. Filtro JWT en la cadena de seguridad

- [x] 6.1 Crear `comun/seguridad/FiltroAutenticacionJwt` (`OncePerRequestFilter`, sin anotación
      de bean) que, con un header `Authorization: Bearer <token>` válido, coloca en el
      `SecurityContext` un `UsernamePasswordAuthenticationToken` autenticado con principal
      `UsuarioAutenticado` y autoridad `ROLE_<rol>`, y que ante cualquier token inválido o
      expirado no autentica y continúa la cadena; registrarlo en `SecurityConfig` con `new` y
      `addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`; verificar extendiendo
      `SecurityConfigTest` contra la ruta protegida de prueba `/api/v1/prueba` que un token
      válido emitido por `JwtService` responde 200, y que sin token, con un token alterado, con
      un token expirado (avanzando `RelojDePrueba`) y con un header sin el prefijo `Bearer `
      responde 401 con `codigo` `NO_AUTENTICADO`.
      **Nota (corrección detectada en la 10.2):** `AutenticacionEntryPointPersonalizado` (401) y
      `AccesoDenegadoHandlerPersonalizado` (403), heredados de `fundacion-proyecto`, escribían el
      `ProblemDetail` sin charset y el contenedor lo enviaba como `ISO-8859-1`; un cliente que
      decodifica el JSON como UTF-8 (navegador, Angular) recibía `autenticaci�n`. Se agregó
      `response.setCharacterEncoding("UTF-8")` en ambos, verificado con
      `RespuestasSeguridadUtf8Test` (los dos handlers: `Content-Type` con `charset=UTF-8` y
      `detail` decodificado como UTF-8 igual al original, incluida la `ó` del 401) y con un test
      en `SecurityConfigTest` contra la cadena real; los tres fallan sin la corrección.
- [x] 6.2 Actualizar la sección "Seguridad: JWT" de `AGENTS.md`: el filtro ya está implementado
      (no es un bean, se crea en `SecurityConfig`), el principal es `UsuarioAutenticado` y se
      obtiene en los controllers con `@AuthenticationPrincipal`, y el token lleva `sub` = id y
      claim `rol` con 24 h de validez y sin refresh; eliminar la frase de que las rutas
      protegidas devuelven siempre 401 hasta el change de `auth`; verificar que el texto lo
      refleja.

## 7. Registro

- [x] 7.1 Crear en `auth` el record `RegistroRequest` con las validaciones exactas del contrato
      de `design.md` (incluidas `@MaximoBytesUtf8(72)`, `@MayorDeEdad` y `@MonedaValida`, sin
      `@Pattern`) y un constructor
      compacto que normaliza antes de validar: `email` con `strip().toLowerCase(Locale.ROOT)`,
      `nombre` y `apellido` con `strip()`, `telefono` con `strip()` y `null` si queda vacío,
      `monedaPredeterminada` `null` → `BOB`, los `null` se dejan como `null`, y `contrasena` sin
      tocar; verificar con tests unitarios del record (construyéndolo directamente y validándolo
      con un `Validator`) que `"  Ana@Ejemplo.COM  "` queda `ana@ejemplo.com` y pasa `@Email`,
      que `"  María José "` queda `María José`, que `"  A  "` falla `@Size(min = 2)` en `nombre`,
      que `" secreta123 "` se conserva idéntica y que un `email` `null` se rechaza con
      `@NotBlank` sin lanzar `NullPointerException`.
- [x] 7.2 Crear en `auth` el record `TokenResponse(token, tipo, expiraEn)`,
      `AuthService.registrar` (`@Transactional`: recibe el request ya normalizado; comprueba
      `existsByEmail`; guarda el `Usuario` con `saveAndFlush` traduciendo
      `DataIntegrityViolationException` a `ConflictoException(EMAIL_YA_REGISTRADO, ...)`; guarda
      el `Perfil`; emite el token) y `AuthController` con `POST /api/v1/auth/registro` (201);
      verificar con tests de integración MockMvc `@Transactional` los escenarios de
      `specs/registro-usuarios/spec.md`: registro exitoso (201, `tipo` `Bearer`, token usable en
      `/api/v1/prueba`), valores por defecto (`BOB`, sin teléfono, comprobado en la base de
      datos), moneda explícita `USD` aceptada, monedas `ZZZ` y `bob` rechazadas (400
      `DATOS_INVALIDOS` con `errores.monedaPredeterminada`), email con espacios y mayúsculas
      guardado recortado y en minúsculas, nombre y apellido guardados recortados (comprobado
      en la base de datos), el nombre `"  A  "` rechazado, la contraseña `" secreta123 "`
      guardada de modo que el login solo funciona con los espacios (el hash verifica
      `" secreta123 "` y no `secreta123`), email duplicado con otras mayúsculas y espacios (409
      `EMAIL_YA_REGISTRADO`, sin cuenta nueva), cada caso de validación (400
      `DATOS_INVALIDOS` con el campo esperado en `errores`, nunca 422, incluidos 72 caracteres de
      más de 72 bytes y varios campos inválidos a la vez), el cuerpo ilegible (JSON mal formado y
      fecha `2000-13-01`: 400 `DATOS_INVALIDOS` con el `detail` genérico) y los cinco escenarios
      de mayoría de edad fijando `RelojDePrueba` (rechazo: 400 `DATOS_INVALIDOS` con
      `errores.fechaNacimiento`).
- [x] 7.3 Verificar la atomicidad del registro con un test de integración no `@Transactional`
      que reemplaza `PerfilRepository` por un `@MockitoBean` que lanza una excepción al guardar:
      el registro falla y no queda ningún `Usuario` con ese email en la base de datos; el test
      borra cualquier dato que cree al terminar.

## 8. Inicio de sesión

- [x] 8.1 Crear el record `LoginRequest` (`@NotBlank` en `email` y `contrasena`, con un
      constructor compacto que normaliza `email` igual que `RegistroRequest` y no toca
      `contrasena`) y `AuthService.login` (si la contraseña supera 72 bytes en UTF-8 lanza
      `NoAutenticadoException(CREDENCIALES_INVALIDAS, "Email o contraseña incorrectos")` sin
      llamar a BCrypt; si el email no existe ejecuta
      `passwordEncoder.matches` contra un hash ficticio generado al arrancar y lanza la misma
      excepción; si la contraseña no coincide, la misma excepción; si coincide, emite el token)
      y `POST /api/v1/auth/login` (200) en `AuthController`; verificar con tests de integración
      MockMvc `@Transactional` los escenarios de `specs/autenticacion/spec.md` sobre login:
      credenciales correctas (200), email `"  Ana@Ejemplo.COM "` con mayúsculas y espacios
      aceptado (200, token de la cuenta `ana@ejemplo.com`), contraseña registrada con espacios
      rechazada sin ellos (401) y aceptada con ellos (200), contraseña incorrecta y
      email inexistente (401 `CREDENCIALES_INVALIDAS` con el mismo `detail` en ambos casos),
      contraseña de más de 72 bytes (401, no 500), login sin contraseña (400 `DATOS_INVALIDOS`
      con error en `contrasena`), `expiraEn` igual al instante de `RelojDePrueba` + 24 h, y login
      correcto con un header `Authorization: Bearer` inválido (200).

## 9. Usuario autenticado

- [x] 9.1 Crear `usuario/UsuarioActualResponse` (record: `email`, `rol`, `nombre`, `apellido`,
      `fechaNacimiento`, `telefono`, `monedaPredeterminada`), `UsuarioMapper` (MapStruct,
      `Perfil` → `UsuarioActualResponse` tomando `email` y `rol` de `perfil.usuario`),
      `UsuarioService.obtenerActual(Long)` (transacción de solo lectura, `findByUsuarioId`,
      `NoAutenticadoException(NO_AUTENTICADO, <constante compartida con el entry point>)` si no
      existe) y `UsuarioController` con `GET /api/v1/usuarios/yo` usando
      `@AuthenticationPrincipal UsuarioAutenticado`; verificar con tests de integración MockMvc
      `@Transactional` que, tras registrar a Ana, `/yo` con su token responde 200 con
      exactamente los siete campos y valores esperados y sin ningún campo `contrasena`; que sin
      token responde 401 `NO_AUTENTICADO`; y que con un token válido de un id inexistente
      (emitido con `JwtService`) responde 401 `NO_AUTENTICADO` con exactamente el mismo `detail`
      que la petición sin token (nunca 404).

## 10. Verificación integral

- [x] 10.1 Ejecutar `.\mvnw.cmd test` con JDK 21 y verificar que toda la suite (la existente y la
      nueva) pasa; ejecutar `openspec validate auth-backend --strict` y verificar que el change es
      válido.
- [x] 10.2 Con PostgreSQL corriendo, levantar el backend (`.\mvnw.cmd spring-boot:run`) y, con
      `curl`, registrar un usuario, iniciar sesión y llamar a `/api/v1/usuarios/yo` con el token
      obtenido; verificar que las respuestas coinciden con el contrato de `design.md`, que las
      tablas `usuarios` y `perfiles` existen con las columnas y longitudes de `design.md`
      (`\d usuarios` y `\d perfiles` en `psql`), y que la contraseña está guardada como hash
      BCrypt; borrar después el usuario de prueba.
