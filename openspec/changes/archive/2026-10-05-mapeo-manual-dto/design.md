# Design

## Context

Ver proposal.md - Why. Estado actual observado:

- `pom.xml`: propiedades `mapstruct.version` (1.6.3) y `lombok-mapstruct-binding.version`
  (0.2.0), dependencia `org.mapstruct:mapstruct`, y en el `maven-compiler-plugin` dos
  ejecuciones (`default-compile` y `default-testCompile`), cada una con `annotationProcessorPaths`
  = lombok → lombok-mapstruct-binding → mapstruct-processor.
- `auth/mapper/RegistroMapper`: `Perfil aPerfil(RegistroRequest, Usuario)`, con
  `disableBuilder = true` (MapStruct usa el constructor vacío y los setters de `Perfil`). Su
  único usuario es `AuthService.registrar` (`perfilRepository.save(registroMapper.aPerfil(...))`),
  que lo recibe en su constructor explícito.
- `usuario/mapper/UsuarioMapper`: `UsuarioActualResponse aUsuarioActual(Perfil)`, tomando
  `email` y `rol` de `perfil.getUsuario()`. Su único usuario es `UsuarioService.obtenerActual`
  (`.map(usuarioMapper::aUsuarioActual)`).
- DTO actuales y quién los importa:
  - `auth/dto`: `LoginRequest`, `RegistroRequest`, `Normalizacion` (package-private) y
    `TokenResponse` (con `public static desde(TokenEmitido)`); los importan `AuthController`,
    `AuthService` y `RegistroMapper`. En tests, `RegistroRequestTest` está en `auth.dto`.
  - `usuario/dto`: `UsuarioActualResponse`; lo importan `UsuarioController`, `UsuarioService` y
    `UsuarioMapper`.
- No existe ningún test de los mappers: la suite tiene 94 tests y ninguno los prueba
  directamente (los cubren los tests de integración de registro y de `/usuarios/yo`).
- `AGENTS.md` tiene las secciones "Backend: organización por feature" (árbol, plantilla con
  `mapper`, reglas, `comun/`) y "DTOs y MapStruct" (MapStruct obligatorio, orden de los
  procesadores), y "Nomenclatura" usa como ejemplos `CuentaDto` y `CuentaMapper`.
- `openspec/config.yaml` tiene un `context` general sin la estructura de una feature y una sola
  regla, para `specs`.

## Goals / Non-Goals

**Goals:**
- Quitar MapStruct sin cambiar ningún comportamiento ni ningún test (94, mismos nombres).
- Una estructura de feature única y obligatoria (con `dto/request` y `dto/response` y sin
  `mapper`), documentada en `AGENTS.md` y exigida en la planificación vía `openspec/config.yaml`.

**Non-Goals:**
- Cambiar Lombok, las entidades, los endpoints o las respuestas.
- Introducir otra librería de mapeo, o una verificación automática de la estructura (ArchUnit).
- Reorganizar `comun/` (sigue organizado por responsabilidad, como fijó
  `estructura-subpaquetes`).

## Decisions

**Mapeo manual: `desde(...)` estático en los records de response y builders en los services.**
- Entidad → response: cada record de `dto/response` expone
  `public static <X>Response desde(<Entidad o valor> origen)`. `UsuarioActualResponse.desde(Perfil)`
  arma el record con `perfil.getUsuario().getEmail()`, `perfil.getUsuario().getRol()` y los cinco
  campos del perfil, exactamente los mismos que mapeaba `UsuarioMapper`; `UsuarioService` pasa a
  `.map(UsuarioActualResponse::desde)`. `TokenResponse.desde(TokenEmitido)` ya sigue este patrón.
- Request → entidad: el service construye la entidad con su builder de Lombok. `AuthService`
  crea el `Perfil` con `Perfil.builder().usuario(usuario).nombre(request.nombre())
  .apellido(request.apellido()).fechaNacimiento(request.fechaNacimiento())
  .monedaPredeterminada(request.monedaPredeterminada()).telefono(request.telefono()).build()`,
  como ya hace con `Usuario`. Los campos y valores son los mismos que asignaba `RegistroMapper`
  (que dejaba `id` y fechas en `null`, igual que el builder), así que la fila guardada es idéntica.
  `RegistroMapper` desaparece del constructor de `AuthService`.
- Por qué en el service y no en el request: el request no debe conocer la entidad ni las
  dependencias que haga falta resolver para crearla (aquí, el `Usuario` ya guardado), y el
  service es quien orquesta la transacción.
- Alternativa descartada: mantener MapStruct, por el costo descrito en proposal.md - Why para
  dos mapeos de pocas líneas. Alternativa descartada: constructores de copia en las entidades,
  que acoplarían las entidades a los DTO.

**Quitar MapStruct del `pom.xml`, dejando Lombok en `annotationProcessorPaths`.**
Se eliminan las propiedades `mapstruct.version` y `lombok-mapstruct-binding.version`, la
dependencia `org.mapstruct:mapstruct` y, en las dos ejecuciones del `maven-compiler-plugin`, las
entradas `lombok-mapstruct-binding` y `mapstruct-processor`. Las dos ejecuciones se conservan con
`lombok` como única entrada: con `annotationProcessorPaths` explícito, Maven solo usa los
procesadores listados, y Lombok tiene que seguir ahí para generar getters, builders, etc. Se
verifica con `.\mvnw.cmd dependency:tree` que no queda ningún artefacto `org.mapstruct`.

**DTO separados en `dto/request` y `dto/response`.**

| Clase                   | Paquete actual   | Paquete nuevo            | Visibilidad     |
|-------------------------|------------------|--------------------------|-----------------|
| `LoginRequest`          | `auth.dto`       | `auth.dto.request`       | `public`        |
| `RegistroRequest`       | `auth.dto`       | `auth.dto.request`       | `public`        |
| `Normalizacion`         | `auth.dto`       | `auth.dto.request`       | package-private |
| `TokenResponse`         | `auth.dto`       | `auth.dto.response`      | `public`        |
| `UsuarioActualResponse` | `usuario.dto`    | `usuario.dto.response`   | `public`        |
| `RegistroMapper`        | `auth.mapper`    | eliminada                | —               |
| `UsuarioMapper`         | `usuario.mapper` | eliminada                | —               |
| `RegistroRequestTest`   | `auth.dto`       | `auth.dto.request`       | package-private |

Ninguna visibilidad cambia respecto de hoy. `RegistroRequestTest` está en `src/test`.

`Normalizacion` queda junto a los dos requests que la usan, así que sigue package-private. Los
records ya son `public` porque los usan el controller y el service; `desde(...)` es `public`
porque lo llama el service, que está en otro subpaquete. Se mueve todo con `git mv` para
conservar el historial, y se eliminan con `git rm` los mappers y los directorios `mapper`.

**`AGENTS.md`: una sección "Estructura obligatoria de una feature".**
Reemplaza a "Backend: organización por feature" y a "DTOs y MapStruct" (su contenido sobre
records pasa a la nueva sección). Contiene, en este orden:
1. La declaración de que **toda feature nueva (cuentas, categorías, transacciones y las que
   sigan) sigue esta estructura sin excepciones**.
2. El árbol de plantilla:
   ```
   com/presupuesto/<feature>/
   ├── controller/
   ├── service/
   ├── repository/
   ├── entity/
   ├── dto/
   │   ├── request/
   │   └── response/
   └── validacion/
   ```
   y el árbol actual de `auth` y `usuario`. Solo se crean los subpaquetes que la feature
   necesita, y ninguna clase vive en la raíz de la feature.
3. Qué va en cada subpaquete (tabla).
4. El mapeo manual: `desde(...)` en los records de response, builders en los services, sin
   MapStruct ni ninguna otra librería de mapeo.
5. Dónde van los tests: mismo subpaquete que la clase que prueban (incluido
   `dto/request`/`dto/response`), y los de HTTP en `controller`.
6. Las reglas de visibilidad mínima y de dependencias: las features dependen de `comun/`, nunca
   al revés, con el `grep` que lo comprueba.
7. Una checklist para crear una feature nueva.

La subsección de `comun/` y el árbol de `comun/` se conservan tal como quedaron en
`estructura-subpaquetes`. En "Nomenclatura", los ejemplos `CuentaDto` y `CuentaMapper` se
cambian por `CuentaRequest`/`CuentaResponse`. En "EntidadBase y convenciones de Lombok" no cambia
nada (los builders de `@SuperBuilder` son los que usa el mapeo manual).

**`openspec/config.yaml`: contexto y reglas para que la planificación aplique la estructura.**
- Al `context` se le agrega un párrafo breve: estructura obligatoria de una feature
  (`controller`, `service`, `repository`, `entity`, `dto/request`, `dto/response`, `validacion`,
  solo los necesarios, ninguna clase en la raíz), mapeo manual sin MapStruct, `comun/` no importa
  features, y que el detalle está en `AGENTS.md`, sección "Estructura obligatoria de una feature".
- En `rules` se agregan:
  - `design`: toda feature nueva o cambio que cree o mueva clases ubica cada una en su
    subpaquete según la estructura obligatoria de `AGENTS.md` y el design incluye una tabla con
    el paquete completo (`com.presupuesto.<feature>.<subpaquete>`) de cada clase y de su test.
  - `tasks`: cada tarea que crea o mueve una clase indica su paquete completo y el de su test,
    según la estructura obligatoria de `AGENTS.md`.
- Se mantiene el límite de 100 columnas también en `config.yaml`. La validez se comprueba con
  `openspec validate --strict` y con `openspec instructions design|tasks --json`, que deben
  devolver el `context` nuevo y las reglas nuevas (prueba de que el YAML se lee bien).

## Risks / Trade-offs

- [Un campo nuevo en una entidad o un response podría olvidarse en el mapeo manual, cosa que
  MapStruct avisaba con un warning de campo sin mapear] → Los records obligan a pasar todos los
  campos en el constructor canónico, así que `desde(...)` no compila si falta uno; y los tests de
  integración comprueban los campos de cada respuesta.
- [Quitar `mapstruct-processor` mientras quede alguna referencia a `org.mapstruct` rompe la
  compilación] → Se eliminan los dos mappers en la misma tarea y se compila con `clean`.
- [`config.yaml` con YAML inválido haría que OpenSpec ignore el `context` sin error visible] →
  Se verifica explícitamente que `openspec instructions` devuelve el texto nuevo.

## Migration Plan

No aplica: no hay cambios de base de datos, configuración de ejecución ni API. Rollback:
revertir el commit.
