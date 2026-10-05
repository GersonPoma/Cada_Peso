# Proposal

## Why

MapStruct se usa hoy para solo dos mapeos triviales (`RegistroMapper`: request → `Perfil`;
`UsuarioMapper`: `Perfil` → response), y a cambio exige un procesador de anotaciones extra, un
binding con Lombok en un orden exacto de `annotationProcessorPaths`, interfaces que hay que hacer
`public` al cambiar de paquete y clases generadas en `target/` (que ya causaron un fallo
intermitente de la suite al no encontrarse `UsuarioMapperImpl`). Un mapeo manual con métodos
estáticos y builders es más corto, se lee en el mismo archivo y no depende de generación de
código. Al mismo tiempo, `dto/` mezcla lo que entra y lo que sale de la API. Antes de crear las
features de negocio (cuentas, categorías, transacciones) conviene fijar la estructura definitiva
de una feature y hacerla obligatoria, también para los agentes que planifican changes con
OpenSpec.

## What Changes

- **Quitar MapStruct**: eliminar `mapstruct`, `mapstruct-processor` y `lombok-mapstruct-binding`
  del `pom.xml` (dependencia, propiedades de versión y entradas de `annotationProcessorPaths`),
  dejando Lombok como único procesador de anotaciones.
- **Mapeo manual**:
  - `UsuarioMapper` se reemplaza por el método estático `UsuarioActualResponse.desde(Perfil)`.
  - `RegistroMapper` se reemplaza por la construcción de `Usuario` y `Perfil` con sus builders
    dentro de `AuthService`.
  - Se eliminan los subpaquetes `auth/mapper` y `usuario/mapper`.
- **DTO separados en `dto/request` y `dto/response`**:
  - `auth/dto/request`: `LoginRequest`, `RegistroRequest` y `Normalizacion` (sigue
    package-private).
  - `auth/dto/response`: `TokenResponse`.
  - `usuario/dto/response`: `UsuarioActualResponse`.
  - Los records usados desde otro subpaquete son `public`.
  - `RegistroRequestTest` se mueve con `git mv` a `auth/dto/request`.
- **`AGENTS.md`**: nueva sección "Estructura obligatoria de una feature", que reemplaza a
  "Backend: organización por feature" y a "DTOs y MapStruct". Incluye el árbol de plantilla, qué
  va en cada subpaquete, el mapeo manual, dónde van los tests, la regla de dependencias con
  `comun/` y una checklist para crear una feature. Indica explícitamente que **toda feature
  nueva (cuentas, categorías, transacciones y las que sigan) sigue esta estructura sin
  excepciones**. También se corrigen las menciones a `CuentaMapper`/`CuentaDto` en
  "Nomenclatura".
- **`openspec/config.yaml`**: el `context` resume la estructura obligatoria y remite a
  `AGENTS.md`, y `rules` agrega reglas para `design` y `tasks` que obligan a ubicar cada clase en
  su subpaquete y a indicar el paquete de cada clase en el design.

Sin cambios de comportamiento: mismas rutas, mismas respuestas JSON, mismas tablas y la misma
suite de 94 tests (no existe ningún test propio de los mappers, así que no se elimina ninguno).

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. Es un refactor de implementación y documentación sin cambios de comportamiento
observable, por eso el change declara `skip_specs: true`.

## Impact

- **Backend, `pom.xml`**: sin MapStruct; Lombok sigue en `annotationProcessorPaths`.
- **Backend, `src/main`**: se eliminan `RegistroMapper` y `UsuarioMapper`; cambian `AuthService`
  (builders), `UsuarioService` (`UsuarioActualResponse::desde`), `UsuarioActualResponse` (método
  `desde`) y el paquete de los 5 DTO (`LoginRequest`, `RegistroRequest`, `Normalizacion`,
  `TokenResponse`, `UsuarioActualResponse`), con los imports de sus usuarios.
- **Backend, `src/test`**: `RegistroRequestTest` cambia de paquete; ningún test cambia su lógica.
- **Documentación y planificación**: `AGENTS.md` y `openspec/config.yaml`.
- **API, base de datos y frontend**: ninguno.
