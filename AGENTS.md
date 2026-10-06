# AGENTS.md

Convenciones del proyecto **presupuesto** (clon de YNAB, presupuesto personal base cero).
Monorepo con `/backend` (Spring Boot 4 + Java 21 + Maven) y `/frontend` (Angular standalone +
signals). Cualquier agente o desarrollador debe leer este archivo antes de tocar el código.

El nombre del producto es **"Cada Peso"**: es el título de la pestaña del navegador
(`frontend/src/index.html`) y el `spring.application.name=cada-peso` del backend. El paquete
raíz (`com.presupuesto`) y la base de datos (`presupuesto`) conservan su nombre técnico.

## Nomenclatura

- El **dominio** (entidades, paquetes de negocio, campos, mensajes de error) se nombra en
  **español**: `Cuenta`, `Categoria`, `Transaccion`, `fechaCreacion`, `saldoActual`.
- Los **sufijos técnicos** (patrones, roles arquitectónicos) se mantienen en **inglés**, pegados
  al nombre en español: `CuentaController`, `CuentaService`, `CuentaRepository`, `CuentaRequest`,
  `CuentaResponse`.
- Nunca se usan **tildes ni la letra ñ** en nombres de clases, métodos, variables, paquetes,
  columnas de base de datos, ni rutas de archivo (ej. `Categoria`, no `Categoría`;
  `anio`, no `año`).
- **Java**: clases en `PascalCase`, métodos/variables en `camelCase`, constantes en
  `UPPER_SNAKE_CASE`, paquetes en minúsculas sin guiones.
- **Base de datos**: tablas y columnas en `snake_case`, en español, sin tildes/ñ
  (ej. `fecha_creacion`, `nombre_categoria`). Hibernate las genera automáticamente a partir de
  los nombres de campo en `camelCase`.
- **Angular**: componentes/servicios/directivas en `PascalCase` con sufijo técnico en inglés
  (`CuentaListComponent`, `CuentaService`), archivos en `kebab-case`
  (`cuenta-list.component.ts`), selectores de componente con prefijo `app-`.

## Estructura obligatoria de una feature

El backend se organiza por **feature de negocio**, no por capa técnica: cada feature es un
paquete bajo `com.presupuesto` y, **dentro** de ella, las clases se reparten en subpaquetes por
capa. La infraestructura transversal, sin lógica de negocio, vive en `com.presupuesto.comun`.

**Toda feature nueva (cuentas, categorías, transacciones y las que sigan) sigue esta estructura
sin excepciones.** Ningún change puede crear una feature con otra organización.

### Plantilla

```
com/presupuesto/<feature>/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
│   ├── request/
│   └── response/
├── evento/
└── validacion/
```

Solo se crean los subpaquetes que la feature necesita, y **ninguna clase** vive en la raíz de la
feature (`com.presupuesto.<feature>`) ni en la raíz de `dto/`.

Árbol actual (`backend/src/main/java/com/presupuesto/`):

```
com/presupuesto/
├── BackendApplication.java
├── comun/
│   ├── EntidadBase.java
│   ├── config/
│   ├── excepcion/
│   ├── seguridad/
│   └── validacion/
├── auth/
│   ├── controller/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   └── service/
├── categoria/
│   ├── controller/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── entity/
│   ├── repository/
│   └── service/
├── cuenta/
│   ├── controller/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── entity/
│   ├── repository/
│   └── service/
├── presupuesto/
│   ├── controller/
│   ├── dto/
│   │   ├── request/
│   │   └── response/
│   ├── entity/
│   ├── evento/
│   ├── repository/
│   └── service/
└── usuario/
    ├── controller/
    ├── dto/
    │   └── response/
    ├── entity/
    ├── repository/
    ├── service/
    └── validacion/
```

`auth` no tiene `entity` ni `repository` porque trabaja con las entidades de `usuario`, y
`usuario` no tiene `dto/request` porque todavía no recibe datos propios. `presupuesto` puede
depender de `usuario` y de `comun`, y `auth` de `presupuesto` (crea el presupuesto inicial al
registrarse); `cuenta` y `categoria` pueden depender de `presupuesto` y de `comun`; nunca al
revés (`presupuesto` y `cuenta` no importan `categoria`).

**Comunicación entre features: eventos.** Cuando una feature debe avisar a otra sin importarla
(la dependencia iría al revés), publica un evento de Spring y la otra lo escucha. Hoy
`presupuesto` publica `PresupuestoCreadoEvento` (record en `presupuesto/evento`, con el
`Presupuesto` ya guardado) al final de `PresupuestoService.crear(...)` y `crearInicial(...)`, y
`categoria` lo escucha (`CategoriasInicialesListener`, en `categoria/service`) para crear el
árbol inicial de grupos y categorías. El oyente es un `@EventListener` **síncrono**, nunca
asíncrono ni `@TransactionalEventListener`: corre en la transacción del publicador, así que un
fallo revierte presupuesto y árbol juntos (y el usuario, en el registro). `presupuesto` nunca
importa `categoria` ni `cuenta`.

### Qué va en cada subpaquete

| Subpaquete     | Contenido                                                                   |
|----------------|-----------------------------------------------------------------------------|
| `controller`   | `@RestController` de la feature, bajo `/api/v1/<ruta-en-kebab-case>`         |
| `service`      | `@Service` con la lógica de negocio, las transacciones y el mapeo manual     |
| `repository`   | Interfaces de Spring Data JPA                                               |
| `entity`       | Entidades JPA (extienden `EntidadBase`) y los enums propios que persisten   |
| `dto/request`  | Records que entran a la API, con sus validaciones y helpers package-private |
| `dto/response` | Records que salen de la API, cada uno con su método `desde(...)`            |
| `validacion`   | Anotaciones de Bean Validation propias de la feature y sus validadores      |
| `evento`       | Records de eventos que la feature publica para otras (solo si los publica)  |

Los DTO de la API son siempre **Java records**, nunca clases.

### Mapeo manual entre entidades y DTO

El proyecto **no usa MapStruct** ni ninguna otra librería de mapeo; Lombok es el único
procesador de anotaciones del `pom.xml`.

- **Entidad → response**: cada record de `dto/response` tiene un método
  `public static <X>Response desde(<Entidad o valor> origen)` que construye el record (ej.
  `UsuarioActualResponse.desde(Perfil)`, `TokenResponse.desde(TokenEmitido)`). El service lo usa
  directamente: `.map(UsuarioActualResponse::desde)`.
- **Request → entidad**: el service construye la entidad con su builder de Lombok
  (`@SuperBuilder`), por ejemplo `Perfil.builder().usuario(usuario).nombre(request.nombre())...
  .build()` en `AuthService`. El request no conoce las entidades.
- Nunca constructores de copia en las entidades ni clases `*Mapper`.

### Tests

- Cada test va en `src/test`, en el **mismo subpaquete** que la clase que prueba (incluidos
  `dto/request` y `dto/response`; ej. `auth/dto/request/RegistroRequestTest`).
- Los tests de integración HTTP (MockMvc contra un endpoint) van en `controller`.

### Visibilidad y dependencias

- Visibilidad mínima: solo es `public` lo que se usa desde otro subpaquete o desde otra feature;
  lo que solo usa su propio subpaquete queda package-private (ej.
  `auth/dto/request/Normalizacion`).
- **Las features dependen de `comun/`, nunca al revés**: ningún archivo de `comun/` (ni en
  `src/main` ni en `src/test`) importa `com.presupuesto.<feature>`. Si una clase de `comun/`
  necesita datos de una feature, los recibe como parámetros simples (ej.
  `JwtService.emitir(Long id, Rol rol)`, no la entidad `Usuario`). Se comprueba desde
  `backend/src` con `grep -rnE "import com\.presupuesto\.(usuario|auth|presupuesto|cuenta|categoria)" <dir>` para
  `<dir>` = `main/java/com/presupuesto/comun` y `test/java/com/presupuesto/comun` (ampliando la
  alternancia con cada feature nueva); debe devolver cero líneas.

### Checklist para crear una feature nueva

1. Crear `com.presupuesto.<feature>` sin ninguna clase en su raíz.
2. Entidades en `entity`, extendiendo `EntidadBase`, con
   `@Getter @Setter @SuperBuilder @NoArgsConstructor @AllArgsConstructor` y `@Table` explícito.
3. Repositorios en `repository` (Spring Data JPA).
4. Requests en `dto/request` como records con Bean Validation (y normalización en el constructor
   compacto si hace falta); responses en `dto/response` como records con `desde(...)`.
5. Validaciones propias de la feature en `validacion`; las genéricas van en `comun/validacion`.
6. Service en `service`: crea entidades con builders, devuelve responses con `desde(...)`,
   obtiene "hoy"/"ahora" del bean `Clock` y lanza las excepciones de `comun/excepcion`.
7. Controller en `controller`, bajo `/api/v1/<ruta-en-kebab-case>`, con `@Valid` en los requests
   y `@AuthenticationPrincipal UsuarioAutenticado` si necesita el usuario.
8. Tests en el subpaquete de cada clase; los de HTTP en `controller`.
9. Visibilidad mínima y el `grep` de dependencias de `comun/` (agregando la feature nueva a la
   alternancia) en cero líneas.
10. En el `design.md` del change, una tabla con el paquete completo de cada clase nueva o movida
    y de su test.

### `comun/`: organizado por responsabilidad, no por capa

`comun/` no es una feature: cada subpaquete es una responsabilidad transversal y agrupa clases
de varios tipos que funcionan juntas. No se divide en `controller`/`service`/`dto`.

- `comun/EntidadBase`: superclase común de todas las entidades JPA (en la raíz de `comun/`).
- `comun/config`: configuración transversal (bean `Clock` en `RelojConfig`, futuros CORS, etc.).
- `comun/excepcion`: jerarquía de excepciones, `CodigoError`, `ManejadorGlobalExcepciones`.
- `comun/seguridad`: `SecurityConfig`, `FiltroAutenticacionJwt`, `JwtService`,
  `JwtProperties`, `UsuarioAutenticado`, `TokenEmitido`, `Rol` y los
  `AuthenticationEntryPoint`/`AccessDeniedHandler` personalizados. `Rol` es **solo** el rol de
  autorización de la aplicación (viaja en el token y en el principal); los roles o estados del
  dominio de una feature van en el `entity` de esa feature.
- `comun/validacion`: anotaciones de Bean Validation genéricas, sin lógica de negocio
  (`@MaximoBytesUtf8`, `@MonedaValida`) y sus validadores.

## `EntidadBase` y convenciones de Lombok

Toda entidad JPA extiende `EntidadBase` (`@MappedSuperclass`), que aporta:

- `id` (`Long`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`).
- `fechaCreacion`/`fechaActualizacion` (`Instant`, `@CreationTimestamp`/`@UpdateTimestamp`).
- `equals`/`hashCode` escritos **a mano** (basados solo en `id`, `hashCode` constante por clase):
  nunca generados por Lombok, porque rompen con proxies de Hibernate (lazy loading) y con
  colecciones antes de persistir la entidad.

Las entidades usan `@Getter @Setter @SuperBuilder @NoArgsConstructor @AllArgsConstructor` de
Lombok (las subclases de `EntidadBase` también deben usar `@SuperBuilder`, nunca `@Builder`).

Cuando una entidad tiene **campos derivados** de otro (ej. `Presupuesto.nombreNormalizado`, que es
`nombre` en minúsculas), se bloquea el setter de ambos con `@Setter(AccessLevel.NONE)` y solo se
cambian mediante un método de la entidad (ej. `Presupuesto.renombrar(...)`), para que nunca
queden desincronizados. Al crear la entidad, el service rellena ambos con el builder usando el
mismo helper (ej. `Presupuesto.normalizar(...)`).

## Excepciones y `ProblemDetail`

Las respuestas de error de la API siguen **RFC 9457** (`ProblemDetail`) de forma centralizada:

- Jerarquía de excepciones de negocio en `comun/excepcion`: `NegocioException` (abstracta),
  `RecursoNoEncontradoException` (404), `ConflictoException` (409), `ReglaNegocioException`
  (422), `DatosInvalidosException` (400) y `NoAutenticadoException` (401). Cada una lleva un `CodigoError`; `ConflictoException`
  y `NoAutenticadoException` lo reciben como parámetro para indicar el motivo concreto (ej.
  `EMAIL_YA_REGISTRADO`, `CREDENCIALES_INVALIDAS`).
- `ManejadorGlobalExcepciones` (`@RestControllerAdvice`) traduce esa jerarquía — y los errores de
  validación (`MethodArgumentNotValidException`) y de cuerpo ilegible
  (`HttpMessageNotReadableException`) — a `ProblemDetail`, con las propiedades `codigo` (enum
  `CodigoError`) y `timestamp`, sin exponer stack traces.
- Qué código usar:
  - **400 `DATOS_INVALIDOS`**: la petición es inválida por sí misma. Incluye cualquier fallo de
    Bean Validation (con el mapa `errores` campo → mensaje), también de anotaciones propias como
    `@MayorDeEdad`, `@MaximoBytesUtf8` o `@MonedaValida`, y el cuerpo ilegible (JSON mal
    formado, cuerpo vacío, tipo incorrecto, fecha imposible), sin `errores`. Lo que solo el
    service puede comprobar (ej. una `posicion` fuera de rango) lanza `DatosInvalidosException`,
    también 400 `DATOS_INVALIDOS` y sin `errores`.
  - **422 `REGLA_NEGOCIO_VIOLADA`** (`ReglaNegocioException`): la petición es válida, pero el
    negocio no la permite en el estado actual del sistema.
  - **409** (`ConflictoException`): la petición choca con un recurso existente (ej. email ya
    registrado).
- El 400 por cuerpo ilegible usa siempre el mensaje fijo "El cuerpo de la petición no es
  válido" y **nunca** expone el mensaje ni la causa de la excepción: el de Jackson incluye
  clases internas, posiciones y fragmentos del cuerpo recibido.
- Un 401 que decide un controller o service (ej. login con credenciales incorrectas) se lanza
  como `NoAutenticadoException` y lo emite el `@RestControllerAdvice`. El 401 `NO_AUTENTICADO` de
  una petición sin token válido a una ruta protegida lo sigue emitiendo el
  `AuthenticationEntryPoint` (ver siguiente punto); ambos usan el mismo mensaje,
  `NoAutenticadoException.MENSAJE_NO_AUTENTICADO`.
- `SecurityConfig` **no** usa `ManejadorGlobalExcepciones` para los errores de autenticación y
  autorización, porque las excepciones de los filtros de seguridad se lanzan antes del
  dispatcher de Spring MVC y el `@RestControllerAdvice` nunca las ve. En su lugar, configura un
  `AuthenticationEntryPoint` propio (401) y un `AccessDeniedHandler` propio (403), ambos en
  `comun/seguridad`, que construyen manualmente el mismo formato `ProblemDetail`
  (`codigo`, `timestamp`, sin stack trace) usando los códigos `CodigoError.NO_AUTENTICADO` y
  `CodigoError.ACCESO_DENEGADO` respectivamente. Todos los códigos de error de la API — vengan
  del dispatcher o de los filtros de seguridad — viven en el único enum `CodigoError`.

## Dinero

El dinero se representa siempre como `long` en **milésimas** (ej. 1.000 = 1 unidad monetaria),
nunca como `double`/`float`/`BigDecimal`. Esta convención aplica desde la entidad hasta el DTO.
En el frontend, el pipe de moneda de `shared/` es responsable de convertir milésimas a la
representación visible para el usuario.

## Fechas: `LocalDate` vs `Instant`

- Las **fechas de negocio** sin hora (fecha de una transacción, mes del presupuesto) se modelan
  como `LocalDate` y viajan en JSON como `yyyy-MM-dd`.
- Los **momentos exactos** de auditoría (`fechaCreacion`, `fechaActualizacion`, etc.) se modelan
  como `Instant` y viajan como ISO-8601 en UTC con sufijo `Z`.
- La JVM del backend corre en UTC (`TimeZone.setDefault` en `main()`,
  `hibernate.jdbc.time_zone=UTC` y `-Duser.timezone=UTC` en Surefire para los tests). Solo el
  frontend convierte un `Instant` a la zona horaria del usuario, y únicamente al mostrarlo.
- Cualquier "ahora" u "hoy" del backend se obtiene del bean `Clock` (`comun/config/RelojConfig`,
  `Clock.systemUTC()`) inyectado, con `Instant.now(clock)` o `LocalDate.now(clock)`; **nunca** con
  `LocalDate.now()`, `Instant.now()` ni equivalentes sin reloj. Así los tests fijan la fecha con
  `RelojDePrueba` (`src/test`, registrado como `@Primary` vía `@Import(RelojDePruebaConfig.class)`)
  en vez de depender del día en que se ejecutan. La única excepción es el `timestamp` informativo
  de las respuestas de error (`ProblemDetail`), que no participa en ninguna regla.

## Formato regional (frontend)

- La región de formato se detecta automáticamente con `regionUsuario()`
  (`shared/formato/region-usuario.ts`), que lee `navigator.language` y usa `en-US` como
  fallback si no hay valor o no es válido. El usuario nunca elige región a mano.
- Todo monto o fecha visible para el usuario se formatea **obligatoriamente** con los pipes
  propios de `shared/formato/`: `monto` (milésimas + código de moneda ISO 4217) y `fecha`
  (`Instant` convertido a la hora local; `LocalDate` sin conversión de zona horaria, para que
  nunca se corra un día). Ambos se basan en `Intl.NumberFormat`/`Intl.DateTimeFormat`.
- Nunca se usan los pipes nativos `date`/`number`/`currency` de Angular, ni
  `registerLocaleData`, ni un `LOCALE_ID` fijo.

## Validación de formularios e idioma de los mensajes

- Los formularios del frontend validan con `Validators` propios de Angular (nativos o funciones
  custom) que replican las reglas de validación del `Request` del backend, y muestran sus
  propios mensajes en español escritos a mano, sin depender de mensajes que vengan del backend.
- El backend sigue validando con Jakarta Validation (`@Valid`, `@NotNull`, `@Size`, etc.) como
  límite de seguridad autoritativo. Sus mensajes por defecto usan el idioma de la JVM de cada
  máquina, sin configuración, y son solo un respaldo porque el frontend valida y muestra sus
  propios mensajes en español.
- Las excepciones de negocio propias (`NegocioException` y subclases, con su `CodigoError`)
  mantienen sus mensajes en español.

## Seguridad: JWT

- La API es **stateless** (`SessionCreationPolicy.STATELESS`, sin sesión, CSRF deshabilitado:
  no aplica a una API sin cookies de sesión).
- La librería de JWT es `io.jsonwebtoken` (**jjwt**: `jjwt-api`, `jjwt-impl`, `jjwt-gson`), no
  Spring Security OAuth2 ni Spring Authorization Server. Se usa `jjwt-gson` y no `jjwt-jackson`
  porque no existe un módulo de jjwt para Jackson 3 (el de Spring Boot 4, paquete
  `tools.jackson`): `jjwt-jackson` solo soporta Jackson 2 y metería un segundo Jackson en el
  classpath. La versión de Gson la gestiona Spring Boot.
- `SecurityConfig` permite sin autenticación `/actuator/health`, `/api/v1/auth/**` (registro y
  login) y `/error`, y exige autenticación (`anyRequest().authenticated()`) en el resto.
- El token se envía en el header `Authorization: Bearer <token>`. Lo emite `JwtService`
  (`comun/seguridad`) al registrarse o iniciar sesión: firmado con HS256, `sub` = id del usuario,
  claim `rol`, 24 h de validez (`jwt.expiracion`) y **sin refresh token** (al expirar hay que
  iniciar sesión de nuevo).
- `FiltroAutenticacionJwt` (`comun/seguridad`) valida el token en cada petición y, si es válido,
  autentica con el principal `UsuarioAutenticado(id, rol)` y la autoridad `ROLE_<rol>`, sin
  consultar la base de datos. Si no hay token, o es inválido o expiró, no autentica: en una ruta
  protegida responde el `AuthenticationEntryPoint` (401 `NO_AUTENTICADO`). **No es un bean**:
  se crea con `new` dentro de `SecurityConfig`, porque Spring Boot registraría cualquier bean
  `Filter` también como filtro de servlet, fuera de la cadena de seguridad.
- Los controllers obtienen el usuario autenticado con
  `@AuthenticationPrincipal UsuarioAutenticado usuario`, nunca leyendo el token a mano.

## Recursos de negocio cuelgan de un presupuesto

Todo recurso de negocio (cuentas, categorías, transacciones y los que sigan) pertenece a un
**presupuesto**, que a su vez pertenece a una persona. Por eso:

- Sus URLs son `/api/v1/presupuestos/{presupuestoId}/<recurso>` (ej.
  `/api/v1/presupuestos/{presupuestoId}/cuentas`), nunca `/api/v1/<recurso>` a secas.
- **Cada operación** del controller, de lectura o de escritura, valida primero la pertenencia con
  `PresupuestoService.obtenerDelUsuario(presupuestoId, usuario.id())`, que devuelve el
  presupuesto o lanza `RecursoNoEncontradoException`. Un presupuesto inexistente o de otra
  persona responde siempre **404**, nunca 403, para no revelar qué ids existen.
- Los repositorios de esos recursos consultan siempre acotados por el presupuesto validado, y un
  recurso de otro presupuesto también responde 404.

## Variables de entorno

`application.properties` lee la conexión a PostgreSQL y el secreto del JWT vía estas variables
de entorno, todas con valor por defecto local (para que cualquier desarrollador con PostgreSQL
local estándar levante el backend sin configuración adicional):

| Variable       | Default                                                               |
|----------------|-----------------------------------------------------------------------|
| `DB_HOST`      | `localhost`                                                           |
| `DB_PORT`      | `5432`                                                                |
| `DB_NAME`      | `presupuesto`                                                         |
| `DB_USER`      | `postgres`                                                            |
| `DB_PASSWORD`  | `admin`                                                               |
| `JWT_SECRET`   | `cada-peso-secreto-solo-para-desarrollo-local-no-usar-en-produccion`  |

El valor por defecto de `JWT_SECRET` es **solo para desarrollo local**: es público (está en el
repositorio), así que cualquier otro entorno debe definir `JWT_SECRET` con un secreto propio. Debe
tener **al menos 32 bytes** en UTF-8 (256 bits, el mínimo que jjwt exige para HS256); con un
secreto más corto la aplicación no arranca.

El esquema lo gestiona Hibernate (`spring.jpa.hibernate.ddl-auto=update`), sin Flyway/Liquibase
por ahora; `spring.jpa.open-in-view=false`. Cualquier entorno distinto del local (y los changes
futuros) debe reutilizar estos mismos nombres de variable, no inventar otros.

## Convenciones de API REST

- Todas las rutas cuelgan de `/api/v1`.
- Las rutas se escriben en **español, en kebab-case** (ej. `/api/v1/categorias`,
  `/api/v1/cuentas/{id}/transacciones`).

## Configuración

- Toda la configuración de Spring Boot vive en `application.properties` (nunca YAML).
- Sin Flyway/Liquibase en esta etapa: el esquema lo gestiona Hibernate (`ddl-auto=update`),
  decisión explícita y revisable si el proyecto introduce CI/CD.

## Comandos de Maven

Los comandos de Maven del proyecto usan siempre el wrapper del repositorio: `.\mvnw.cmd` en
Windows y `./mvnw` en Linux o macOS (desde `backend/`, ej. `.\mvnw.cmd test`). Nunca `mvn`
directamente, para que todos usen la misma versión de Maven. El build requiere JDK 21.

## Tests

- Dependencias de test: `spring-boot-starter-test` + `spring-security-test` (equivalentes
  modulares de Spring Boot 4 cuando una slice de test específica —p. ej. `@WebMvcTest`— lo
  requiera, como `spring-boot-starter-webmvc-test`).
- Las entidades o controllers que existan únicamente para probar infraestructura común (como la
  de este change de fundación) viven solo en `src/test`, nunca en `src/main`, para que Hibernate
  (`ddl-auto=update`) no les cree tablas en PostgreSQL.
- Los tests de integración de infraestructura transversal (p. ej. el de
  `ManejadorGlobalExcepciones` o el de `SecurityConfig`) se conservan de forma permanente como
  parte de la suite.

## Angular Material (única librería de UI)

**Angular Material es la única librería de UI del frontend.** No se agrega ninguna otra librería
de componentes (PrimeNG, Bootstrap, Tailwind UI, etc.) ni de íconos (Font Awesome, SVGs sueltos,
etc.), en ningún change.

- **Componentes**: botones (`mat-button` y variantes), inputs y formularios con `mat-form-field`
  + `matInput` + `mat-error`, selects, datepicker, diálogos (`MatDialog`), tablas (`mat-table` +
  `mat-paginator`) y snackbars son siempre de Material. Los mensajes de validación en español
  (ver "Validación de formularios") van dentro de `mat-error`.
- **Íconos**: solo con `<mat-icon>` y los nombres de **Material Symbols Outlined**. La fuente se
  instala desde npm (`material-symbols`) y se empaqueta en el build; no se usa Google Fonts.
- **Tema**: Material 3 en `src/styles.scss`, solo modo claro, con color principal `#2E7D32` (el
  del favicon). Las paletas de `src/_theme-colors.scss` se generan con
  `ng generate @angular/material:theme-color` y no se editan a mano. Los colores de los
  componentes salen de los tokens `--mat-sys-*`, nunca de valores hex sueltos.
- **Configuración global** en `core/material/`, registrada una sola vez con `proveerMaterial()`
  en `app.config.ts`: `AdaptadorFechaRegional`, `MAT_DATE_LOCALE` (igual a `regionUsuario()`),
  textos internos en español (`PaginadorIntl`, `CalendarioIntl`) y la fuente de `mat-icon`. Todo
  componente de Material nuevo que tenga textos internos se traduce ahí el día que se empieza a
  usar.
- **Fechas del datepicker**: `AdaptadorFechaRegional` es el único `DateAdapter`; interpreta lo
  escrito a mano según el orden de día, mes y año de la región y trata una fecha inexistente
  (ej. `31/02/2003`) como inválida. Nunca se registra otro adapter ni se usa `Date.parse` para
  fechas escritas por el usuario.
- **Conversión al backend**: el `Date` del datepicker se convierte a `yyyy-MM-dd` (`LocalDate`)
  **solo** con `aFechaNegocio()` de `shared/fecha/`; nunca con `toISOString()`, `toJSON()` ni
  `getUTC*()`, que corren la fecha un día según la zona horaria.

## Estructura obligatoria del frontend

`src/app` se organiza en tres carpetas, y **toda feature nueva (cuentas, categorías,
transacciones y las que sigan) sigue esta estructura sin excepciones**, igual que en el backend:

```
src/app/
├── app.ts, app.config.ts, app.routes.ts ...
├── core/
│   ├── api-base-url.ts
│   ├── auth/
│   ├── material/
│   └── sesion/
├── shared/
│   ├── api/
│   ├── cabecera/
│   ├── fecha/
│   ├── formato/
│   ├── formulario/
│   └── validacion/
└── features/
    ├── auth/
    │   ├── components/
    │   ├── models/
    │   ├── pages/
    │   └── services/
    ├── inicio/
    │   ├── models/
    │   ├── pages/
    │   └── services/
    └── <feature>/
        ├── pages/
        ├── components/
        ├── services/
        └── models/
```

- `core/`: servicios singleton, interceptores y configuración transversal del frontend (hoy,
  la URL base de la API, `auth/` con el interceptor y los guards, `sesion/` con el servicio de
  sesión y `material/`, la configuración global de Angular Material).
- `shared/`: componentes, pipes, directivas y utilidades reutilizables entre features (hoy,
  `formato/` con los pipes de monto y fecha, `fecha/` con `aFechaNegocio()`, `api/` con la lectura
  de errores del API, `cabecera/`, `formulario/` con los mensajes de error y `validacion/` con los
  validadores).
- `features/<feature>/`: una carpeta por feature de negocio (`cuentas/`, `categorias/`,
  `transacciones/`, etc.), con solo las subcarpetas que necesite:
  - `pages/`: componentes enrutables, uno por ruta.
  - `components/`: componentes de presentación de la feature (no enrutables).
  - `services/`: acceso a la API de la feature con `HttpClient`.
  - `models/`: interfaces que reflejan los DTO de request y response del backend.
- **Dependencias**: `core/` y `shared/` nunca importan nada de `features/`; una feature puede
  importar de `core/` y `shared/`, pero no de otra feature.
- **Nombres de archivo** en `kebab-case` con el sufijo técnico: `*.page.ts`, `*.component.ts`,
  `*.service.ts`, `*.model.ts`, `*.pipe.ts`, `*.guard.ts`, `*.interceptor.ts`,
  `*.validator.ts`. Los tests `*.spec.ts` van junto al archivo que
  prueban.

Angular se usa con **standalone components + signals**, sin NgModules.

La URL base de la API en `environments` es siempre **relativa** (`/api/v1`), nunca absoluta: el
proxy de desarrollo de Angular (`proxy.conf.json`, referenciado en
`angular.json` → `architect.serve.options.proxyConfig`) reenvía `/api` → `http://localhost:8080`
como same-origin, evitando configurar CORS en el backend.

## Sesión y autenticación del frontend

- El token vive solo en `SesionService` (`core/sesion/`), guardado en `localStorage` bajo la clave
  `cada-peso.sesion` junto con `expiraEn`. Un token vencido al arrancar se descarta.
- `sesionInterceptor` agrega `Authorization: Bearer` solo a las peticiones a `/api/v1`; un 401 de
  cualquier ruta que no sea `/api/v1/auth/**` cierra la sesión y lleva a `/login`.
- `sesionGuard` protege las rutas privadas e `invitadoGuard` las de `/login` y `/registro`.
- `core/` y `shared/` nunca importan `features/`: por eso el interceptor y los guards usan
  `SesionService` y no `AuthService`.
- La contraseña nunca se recorta ni se modifica; email, nombre y apellido se envían recortados
  (el email, además, en minúsculas).
- Los errores del API se interpretan por `codigo` (`leerProblemaApi`), nunca por `detail`.
- Las fechas del registro se envían con `aFechaNegocio()`.

## Estilo de código

- Límite de **100 columnas** en todo el monorepo, impuesto por el `.editorconfig` de la raíz
  (único `.editorconfig` del proyecto; no debe haber otro en `/backend` ni `/frontend`).
- Indentación: **4 espacios en Java**, **2 espacios en TS/HTML/SCSS/JSON**. Fin de línea LF,
  codificación UTF-8.
- En `/frontend`, Prettier (`printWidth: 100`, `singleQuote: true`) complementa al
  `.editorconfig`; su configuración vive en una única parte (la clave `"prettier"` de
  `package.json` o `.prettierrc`, nunca ambas a la vez).
- No hay un formateador equivalente para Java en esta etapa (Spotless/Checkstyle quedan como
  mejora futura si se agrega CI).

## Commits

Commits convencionales (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`, etc.) con el
mensaje **en español** (ej. `feat: agregar endpoint de creación de categorías`).
