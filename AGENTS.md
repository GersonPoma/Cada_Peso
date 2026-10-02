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
  al nombre en español: `CuentaController`, `CuentaService`, `CuentaRepository`, `CuentaDto`,
  `CuentaMapper`.
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

## Backend: organización por feature

El backend se organiza por **feature de negocio**, no por capa técnica. Cada feature
(`cuenta`, `categoria`, `transaccion`, `auth`, etc.) es un paquete bajo `com.presupuesto` que
agrupa su propio controller, service, repository, entidad, DTOs y mapper. La infraestructura
transversal, sin lógica de negocio, vive en `com.presupuesto.comun`:

- `comun/excepcion`: jerarquía de excepciones, `CodigoError`, `ManejadorGlobalExcepciones`.
- `comun/seguridad`: `SecurityConfig`, filtro JWT (a implementar en el change de `auth`),
  `AuthenticationEntryPoint`/`AccessDeniedHandler` personalizados.
- `comun/config`: configuración transversal adicional (CORS, beans compartidos, etc.).
- `comun/EntidadBase`: superclase común de todas las entidades JPA.

## `EntidadBase` y convenciones de Lombok

Toda entidad JPA extiende `EntidadBase` (`@MappedSuperclass`), que aporta:

- `id` (`Long`, `@GeneratedValue(strategy = GenerationType.IDENTITY)`).
- `fechaCreacion`/`fechaActualizacion` (`Instant`, `@CreationTimestamp`/`@UpdateTimestamp`).
- `equals`/`hashCode` escritos **a mano** (basados solo en `id`, `hashCode` constante por clase):
  nunca generados por Lombok, porque rompen con proxies de Hibernate (lazy loading) y con
  colecciones antes de persistir la entidad.

Las entidades usan `@Getter @Setter @SuperBuilder @NoArgsConstructor @AllArgsConstructor` de
Lombok (las subclases de `EntidadBase` también deben usar `@SuperBuilder`, nunca `@Builder`).

## DTOs y MapStruct

- Los DTOs de entrada/salida de la API se modelan como **Java records**, no como clases.
- El mapeo entidad ↔ DTO se hace con **MapStruct** (`@Mapper(componentModel = "spring")`), nunca
  a mano ni con constructores de copia.
- El `pom.xml` configura `annotationProcessorPaths` del `maven-compiler-plugin` en el orden
  **lombok → lombok-mapstruct-binding → mapstruct-processor**: si no se agrega el binding en ese
  orden exacto, Lombok genera los getters/setters después de que MapStruct los necesita y el
  mapeo falla en tiempo de compilación.

## Excepciones y `ProblemDetail`

Las respuestas de error de la API siguen **RFC 9457** (`ProblemDetail`) de forma centralizada:

- Jerarquía de excepciones de negocio en `comun/excepcion`: `NegocioException` (abstracta),
  `RecursoNoEncontradoException` (404), `ConflictoException` (409), `ReglaNegocioException`
  (422). Cada una lleva un `CodigoError`.
- `ManejadorGlobalExcepciones` (`@RestControllerAdvice`) traduce esa jerarquía — y los errores de
  validación (`MethodArgumentNotValidException`) — a `ProblemDetail`, con las propiedades
  `codigo` (enum `CodigoError`) y `timestamp`, sin exponer stack traces.
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
- La librería de JWT es `io.jsonwebtoken` (**jjwt**: `jjwt-api`, `jjwt-impl`, `jjwt-jackson`), no
  Spring Security OAuth2 ni Spring Authorization Server.
- `SecurityConfig` permite sin autenticación `/actuator/health` y `/api/v1/auth/**`, y exige
  autenticación (`anyRequest().authenticated()`) en el resto. El filtro de validación de JWT y
  los endpoints de login/registro se implementan en el change de `auth`; hasta entonces,
  cualquier ruta protegida devuelve 401 (fail-closed, comportamiento esperado).

## Variables de entorno de conexión a PostgreSQL

`application.properties` lee la conexión vía estas cinco variables de entorno, las cinco con
valor por defecto local (para que cualquier desarrollador con PostgreSQL local estándar levante
el backend sin configuración adicional):

| Variable       | Default        |
|----------------|----------------|
| `DB_HOST`      | `localhost`    |
| `DB_PORT`      | `5432`         |
| `DB_NAME`      | `presupuesto`  |
| `DB_USER`      | `postgres`     |
| `DB_PASSWORD`  | `admin`        |

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

## Estructura del frontend

`src/app` se organiza en tres carpetas:

- `core/`: servicios singleton, interceptores, configuración transversal del frontend.
- `shared/`: componentes, pipes y directivas reutilizables entre features (incluye el pipe de
  dinero en milésimas).
- `features/`: un subdirectorio por feature de negocio (`cuentas/`, `categorias/`,
  `transacciones/`, etc.), cada uno con sus propios componentes standalone.

Angular se usa con **standalone components + signals**, sin NgModules.

La URL base de la API en `environments` es siempre **relativa** (`/api/v1`), nunca absoluta: el
proxy de desarrollo de Angular (`proxy.conf.json`, referenciado en
`angular.json` → `architect.serve.options.proxyConfig`) reenvía `/api` → `http://localhost:8080`
como same-origin, evitando configurar CORS en el backend.

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
