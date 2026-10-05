# Proposal

## Why

Cada Peso todavía no tiene usuarios: `SecurityConfig` exige autenticación en todo lo que no sea
`/api/v1/auth/**`, `/actuator/health` o `/error`, pero no existe ningún filtro que valide un
token, así que cualquier ruta protegida devuelve 401 (fail-closed, previsto desde
`fundacion-proyecto`). Antes de construir cualquier feature de negocio con datos por usuario
(cuentas, categorías, transacciones) hace falta que una persona pueda registrarse, iniciar sesión
y autenticar sus peticiones con un JWT.

## What Changes

- **Entidades `Usuario` y `Perfil`** (ambas extienden `EntidadBase`), separadas a propósito:
  - `Usuario` (tabla `usuarios`) solo para autenticación: `email` obligatorio, único, máximo 254
    caracteres y guardado recortado y en minúsculas; `contrasena` hasheada con BCrypt; `rol`
    como enum `Rol`
    (`USUARIO`, `ADMIN`) guardado como texto, `USUARIO` por defecto.
  - `Perfil` (tabla `perfiles`) con los datos personales: `nombre` y `apellido` obligatorios
    (2 a 100 caracteres), `fechaNacimiento` obligatoria (`LocalDate`), `monedaPredeterminada`
    obligatoria (código ISO 4217, `BOB` por defecto) y `telefono` opcional (máximo 20). Relación
    uno a uno definida en `Perfil` (`usuario_id` único, clave foránea, carga `LAZY`).
- **Mayoría de edad**: anotación de validación propia `@MayorDeEdad` (18 años o más, el día en que
  se cumplen 18 se acepta), calculada respecto de "hoy" obtenido de un bean `Clock` inyectado para
  que los tests puedan fijar la fecha. Su fallo es un error de validación de campo más
  (`fechaNacimiento`), con la misma respuesta que cualquier otro.
- **Endpoints**:
  - `POST /api/v1/auth/registro`: crea `Usuario` y `Perfil` en una sola transacción y devuelve un
    token.
  - `POST /api/v1/auth/login`: devuelve un token si las credenciales son correctas, con el mismo
    mensaje genérico si el email no existe o la contraseña es incorrecta.
  - `GET /api/v1/usuarios/yo` (protegido): devuelve email, rol y datos del perfil del usuario
    autenticado, nunca la contraseña. Si el token es válido pero el usuario ya no existe,
    responde 401 `NO_AUTENTICADO` (igual que un token inválido) para que el frontend cierre la
    sesión.
- **Validaciones de entrada** con `@Size` en los DTOs y `length` en las columnas: email con
  formato válido (`@Email`) y máximo 254; contraseña de 8 a 72 caracteres y, además, de no más de
  72 bytes en UTF-8 (el límite real de BCrypt); nombre y apellido de 2 a 100; teléfono máximo 20;
  moneda predeterminada validada con una anotación propia `@MonedaValida`, que acepta solo
  códigos existentes en `java.util.Currency.getAvailableCurrencies()` (por ejemplo, rechaza
  `ZZZ`), en lugar de comprobar solo que tenga tres letras mayúsculas.
- **Normalización de la entrada**, antes de validar: el email se recorta (trim) y se pasa a
  minúsculas tanto en el registro como en el login, así que `"  Ana@Ejemplo.COM "` inicia sesión
  en la cuenta `ana@ejemplo.com`; nombre y apellido se guardan recortados. La contraseña nunca se
  modifica.
- **JWT**: firmado con HS256 usando el secreto de la variable de entorno `JWT_SECRET`, con un
  valor por defecto de al menos 32 bytes (256 bits, el mínimo que jjwt exige para HS256) que
  solo sirve para desarrollo local; cualquier otro entorno define `JWT_SECRET`. Expiración de 24
  horas, con el id y el rol del usuario en los claims, sin refresh token. Filtro JWT registrado
  en `SecurityConfig` que autentica cada petición con el header `Authorization: Bearer <token>`.
- **Nuevos `CodigoError`**: `EMAIL_YA_REGISTRADO` (409), `CREDENCIALES_INVALIDAS` (401) y
  `DATOS_INVALIDOS` (400). No se crea un código propio para la mayoría de edad.
- **BREAKING (sin consumidores todavía): errores de validación con 400 `DATOS_INVALIDOS`.** Hoy
  `ManejadorGlobalExcepciones` responde a `MethodArgumentNotValidException` con 422
  `REGLA_NEGOCIO_VIOLADA`, mezclando datos mal formados con reglas de negocio. Pasa a responder
  400 `DATOS_INVALIDOS` con el mapa `errores` (incluidos `@MayorDeEdad` y `@MaximoBytesUtf8`), y
  422 `REGLA_NEGOCIO_VIOLADA` queda reservado para `ReglaNegocioException`. Ningún endpoint ni
  frontend existente depende del 422 de validación.
- **Cuerpo ilegible**: `ManejadorGlobalExcepciones` maneja también
  `HttpMessageNotReadableException` (JSON mal formado, tipo incorrecto, fecha imposible) con 400
  `DATOS_INVALIDOS` en formato `ProblemDetail` y un mensaje genérico, sin exponer el detalle del
  parser. Aplica a todos los endpoints, no solo a los de autenticación.
- **Dependencia JWT**: `jjwt-jackson` se reemplaza por `jjwt-gson`. Verificado que `jjwt-jackson`
  (0.12.6, y también la última 0.13.0) solo soporta Jackson 2 y arrastra
  `com.fasterxml.jackson.core:jackson-databind` 2.x al classpath junto al Jackson 3
  (`tools.jackson`) de Spring Boot 4; no existe un módulo de jjwt para Jackson 3.
- **Contrato de la API documentado en `design.md`** (endpoints, DTOs con campos, longitudes y
  validaciones, códigos HTTP y `CodigoError` de cada error) como referencia para el change
  `auth-frontend`.
- Actualizar `AGENTS.md`: `jjwt-gson` en vez de `jjwt-jackson`, variable `JWT_SECRET`, filtro JWT
  ya implementado, nueva excepción 401, la distinción 400 `DATOS_INVALIDOS` (datos mal formados)
  vs. 422 `REGLA_NEGOCIO_VIOLADA` (reglas de negocio) y la convención de obtener "hoy" desde el
  bean `Clock`.

Sin cambios en el frontend.

## Capabilities

### New Capabilities

- `registro-usuarios`: alta de una persona en el sistema con sus credenciales y su perfil
  personal, incluyendo las reglas de validación de los datos y la mayoría de edad.
- `autenticacion`: inicio de sesión con credenciales, emisión y validación del token de acceso,
  protección de las rutas que requieren sesión y consulta de los datos del usuario autenticado.

### Modified Capabilities

Ninguna (la única capacidad existente, `formato-regional`, no cambia).

## Impact

- **Backend, código nuevo**: paquetes de feature `auth` (controller, service, DTOs de registro,
  login y token) y `usuario` (`Usuario`, `Perfil`, `Rol`, repositorios, controller de
  `/usuarios/yo`, DTO de respuesta, mapper MapStruct, `@MayorDeEdad`); en `comun/seguridad`, el
  servicio de JWT, el filtro JWT, sus propiedades de configuración y el bean `PasswordEncoder`; en
  `comun/config`, el bean `Clock`.
- **Backend, código modificado**: `SecurityConfig` (registro del filtro JWT), `CodigoError`,
  `ConflictoException` (aceptar un `CodigoError` específico), nueva excepción 401 y
  `ManejadorGlobalExcepciones` (manejo de la excepción 401, validación con 400 `DATOS_INVALIDOS`
  y nuevo manejo de `HttpMessageNotReadableException`) con sus tests, `application.properties`
  (secreto y expiración del JWT).
- **API existente**: los errores de validación de cualquier endpoint pasan de 422 a 400; un cuerpo
  ilegible pasa del 400 por defecto de Spring Boot a un 400 `ProblemDetail` del proyecto.
- **Base de datos**: tablas nuevas `usuarios` y `perfiles` creadas por Hibernate
  (`ddl-auto=update`).
- **Dependencias**: `jjwt-jackson` → `jjwt-gson` (misma versión de jjwt; Gson ya gestionado por
  Spring Boot).
- **Configuración**: variable de entorno nueva `JWT_SECRET`.
- **Documentación**: `AGENTS.md`.
- **Frontend**: ninguno.
