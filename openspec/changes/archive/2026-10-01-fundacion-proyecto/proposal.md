# Proposal

## Why

El proyecto es un clon de YNAB (presupuesto personal base cero) y hoy no existe ningún código ni configuración: ni backend, ni frontend, ni convenciones documentadas. Antes de implementar cualquier capacidad de negocio (auth, cuentas, categorías, transacciones) se necesita una base técnica común — estructura de monorepo, convenciones de nomenclatura, configuración de OpenSpec y esqueletos de backend/frontend que arranquen y se conecten entre sí — para que los cambios futuros sean consistentes y no repitan decisiones de bajo nivel.

## What Changes

- Crear `.gitignore` único en la raíz del monorepo (Java/Maven, Node/Angular, IDEs, `.env`); sin `.gitignore` ni `HELP.md` dentro de `/backend`.
- Crear `.editorconfig` en la raíz con límite de línea de 100 columnas para todo el monorepo (UTF-8, LF, indentación de 4 espacios en Java y 2 espacios en TS/HTML/SCSS/JSON, `max_line_length=100`), y configurar Prettier en `/frontend` con `printWidth: 100` y `singleQuote: true`.
- Generar `/backend` con Spring Initializr: Maven, Java 21, Spring Boot 4 (última estable), packaging jar, configuración en `.properties`, `groupId=com.presupuesto`, `artifactId=backend`, paquete base `com.presupuesto`. Dependencias de Initializr: Spring Web, Spring Security, Spring Data JPA, PostgreSQL Driver, Lombok, Validation, Spring Boot Actuator.
- Agregar manualmente a `pom.xml`: MapStruct, `lombok-mapstruct-binding`, jjwt (`api`, `impl`, `jackson`), y dependencias de test `spring-boot-starter-test` y `spring-security-test`.
- Crear la estructura de paquetes base del backend por feature (`comun/excepcion`, `comun/seguridad`, `comun/config`) y la clase `EntidadBase` (`@MappedSuperclass`) con id, auditoría de fechas y convenciones Lombok descritas en las convenciones del proyecto.
- Configurar `application.properties` con conexión a PostgreSQL vía las variables de entorno `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, las cinco con valor por defecto local (`localhost`, `5432`, `presupuesto`, `postgres`, `admin` respectivamente), `spring.jpa.hibernate.ddl-auto=update`, `spring.jpa.open-in-view=false`, sin Flyway/Liquibase.
- Configurar el manejo global de excepciones (`ManejadorGlobalExcepciones`) devolviendo `ProblemDetail` (RFC 9457), y la jerarquía base de excepciones (`NegocioException`, `RecursoNoEncontradoException`, `ConflictoException`, `ReglaNegocioException`) con su enum `CodigoError`.
- Crear `SecurityConfig` en `comun/seguridad` con un `SecurityFilterChain` *stateless* (sin sesión, CSRF deshabilitado) que permite sin autenticación `/actuator/health` y `/api/v1/auth/**`, y exige autenticación en el resto de rutas; el filtro JWT y los endpoints de login/registro se implementan en el change de `auth`.
- Generar `/frontend` con Angular CLI (routing, estilos SCSS, sin SSR) y crear la estructura de carpetas `core/`, `shared/`, `features/`.
- Configurar `environments` del frontend con la URL base de la API como ruta relativa (`/api/v1`, no absoluta) y el proxy de desarrollo de Angular (`proxy.conf.json`, referenciado desde `angular.json` en `serve.options.proxyConfig`, mapeando `/api` → `http://localhost:8080`) para que las peticiones lleguen al backend sin configurar CORS.
- Crear `AGENTS.md` en la raíz documentando todas las convenciones del proyecto (nomenclatura, estructura backend/frontend, reglas de dinero, seguridad, API, variables de entorno de conexión a PostgreSQL, estilo de código, tests, commits), y `CLAUDE.md` que lo importe con `@AGENTS.md`.
- Completar `openspec/config.yaml` con `context` (stack, convenciones y límite de línea de 100 columnas) y `rules` por artefacto (specs con escenarios Dado/Cuando/Entonces para cambios futuros).
- Verificar que el backend arranca en el puerto 8080 conectado a PostgreSQL con `/actuator/health` respondiendo `UP`, y que el frontend arranca con `ng serve` en el puerto 4200 sin errores.

Este change es de **fundación técnica** (scaffolding, convenciones, configuración de herramientas): no introduce ni modifica comportamiento de negocio observable por el usuario final, por lo que no declara capacidades de especificación (`skip_specs: true`). Las capacidades de negocio (autenticación, cuentas, categorías, transacciones) se especificarán en changes posteriores que se apoyen en esta base.

## Capabilities

Sin capacidades nuevas ni modificadas — este change está marcado `skip_specs: true` en `.openspec.yaml` porque es trabajo de tooling/infraestructura (estructura de repo, convenciones, configuración de build) sin comportamiento de negocio que especificar. Los requisitos de negocio futuros (p. ej. `auth`, `cuenta`, `categoria`, `transaccion`) se definirán en sus propios changes con sus propias specs.

## Impact

- **Repositorio**: nuevo `.gitignore` y `.editorconfig` en la raíz (límite de línea de 100 columnas para todo el monorepo); nuevos directorios `/backend` y `/frontend`; nuevos `AGENTS.md` y `CLAUDE.md` en la raíz.
- **Backend**: proyecto Maven nuevo en `/backend` (Spring Boot 4, Java 21) con dependencias de seguridad, persistencia, validación, mapeo (MapStruct) y JWT (jjwt); incluye `SecurityConfig` stateless (sin filtro JWT ni login — eso es del change `auth`); sin endpoints de negocio todavía, solo esqueleto, configuración común y Actuator.
- **Frontend**: proyecto Angular nuevo en `/frontend` con estructura `core/`, `shared/`, `features/`, configuración de entornos con URL relativa (`/api/v1`), Prettier (`printWidth: 100`, `singleQuote: true`) y conexión al backend vía el proxy de desarrollo de Angular (`angular.json` → `proxy.conf.json`), sin CORS.
- **Base de datos**: requiere una instancia de PostgreSQL local/accesible vía las variables de entorno `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` (valores por defecto: `localhost`/`5432`/`presupuesto`/`postgres`/`admin`); el esquema se gestiona por Hibernate (`ddl-auto=update`), sin migraciones todavía.
- **OpenSpec**: `openspec/config.yaml` queda con `context` y `rules` que condicionarán cómo se redactan proposals, specs, design y tasks en todos los changes futuros del proyecto.
- **Dependencias externas**: ninguna breaking change (proyecto greenfield).
