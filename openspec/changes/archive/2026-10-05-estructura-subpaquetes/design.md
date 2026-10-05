# Design

## Context

Ver proposal.md - Why. Estado actual observado en `backend/src`:

- `com.presupuesto.auth` (plano): `AuthController`, `AuthService`, `LoginRequest`,
  `RegistroRequest`, `TokenResponse`, `Normalizacion` (package-private) y `RegistroMapper`
  (interfaz MapStruct package-private).
- `com.presupuesto.usuario` (plano): `UsuarioController`, `UsuarioService`, `UsuarioRepository`,
  `PerfilRepository`, `Usuario`, `Perfil`, `Rol`, `UsuarioActualResponse`, `UsuarioMapper`
  (interfaz MapStruct package-private), `MayorDeEdad` y `MayorDeEdadValidator`.
- `com.presupuesto.comun`: `EntidadBase` en la raíz y los subpaquetes `config`, `excepcion`,
  `seguridad` y `validacion`.
- **Dependencia de `comun/` hacia una feature**: en `src/main`, `JwtService` importa
  `usuario.Usuario` y `usuario.Rol`, y `UsuarioAutenticado` importa `usuario.Rol`; en `src/test`,
  `JwtServiceTest` importa `Usuario` y `Rol`, y `SecurityConfigTest` importa `Usuario`.
  `JwtService.emitir(Usuario)` solo lee `getId()` y `getRol()`. Sus llamadores son
  `AuthService` (2 llamadas) y, en tests, `JwtServiceTest` (5), `SecurityConfigTest` (1) y
  `UsuarioActualIntegracionTest` (1).
- `Rol` lo usan además `Usuario` (`@Enumerated(STRING)`), `UsuarioActualResponse`,
  `RegistroIntegracionTest` y `UsuarioPerfilRepositoryTest`.
- Miembros package-private que hoy se usan desde otra clase del mismo paquete y que quedarán en
  subpaquetes distintos: `RegistroMapper` y `UsuarioMapper` (usados por los services) y
  `TokenResponse.desde(TokenEmitido)` (usado por `AuthService`). `Normalizacion` solo la usan
  `RegistroRequest` y `LoginRequest`.
- Tests: `auth` tiene `RegistroRequestTest`, `RegistroIntegracionTest`, `LoginIntegracionTest` y
  `RegistroAtomicidadTest`; los dos últimos usan el helper package-private
  `RegistroIntegracionTest.datosValidosCon(...)`. `usuario` tiene `MayorDeEdadValidatorTest`,
  `UsuarioPerfilRepositoryTest` y `UsuarioActualIntegracionTest`. La suite completa tiene 94
  tests.
- `BackendApplication` está en `com.presupuesto`, así que el escaneo de componentes, de
  entidades JPA y de repositorios de Spring Boot ya cubre cualquier subpaquete.

## Goals / Non-Goals

**Goals:**
- Una estructura interna idéntica y predecible en todas las features, documentada en
  `AGENTS.md` como obligatoria.
- Dependencias en un solo sentido: las features dependen de `comun/`, y ningún archivo de
  `comun/` (main ni test) importa `com.presupuesto.usuario` ni `com.presupuesto.auth`.
- Cero cambios de comportamiento: mismas rutas, respuestas, claims del token, tablas, beans y la
  misma suite de 94 tests con los mismos nombres y aserciones.
- Mantener la visibilidad mínima: solo se hace `public` lo que el cambio de paquete obliga.

**Non-Goals:**
- Renombrar clases o mover lógica entre clases, salvo `Rol` y la firma de
  `JwtService.emitir`.
- Reorganizar `comun/` por capas (ver decisión).
- Introducir herramientas de verificación de arquitectura (ArchUnit u otras): la verificación de
  dependencias es un `grep`.

## Decisions

**Las features dependen de `comun/`, nunca al revés: `Rol` pasa a `comun/seguridad` y
`JwtService.emitir` recibe `(Long id, Rol rol)`.**
`comun/` es infraestructura transversal que todas las features usan; si además conoce a una
feature, cualquier cambio en esa feature puede romper la infraestructura, y una feature que use
`comun/seguridad` podría acabar en un ciclo. Hoy el acoplamiento es pequeño y se corta en dos
puntos:
- `Rol` se mueve a `comun/seguridad`. Es el rol de **seguridad** de la aplicación: viaja en el
  claim `rol` del JWT, en el principal `UsuarioAutenticado` y en la autoridad `ROLE_<rol>`, todos
  en `comun/seguridad`. `Usuario` lo sigue persistiendo con `@Enumerated(STRING)` en la columna
  `rol` (la feature depende de `comun/`, que es el sentido permitido) y `UsuarioActualResponse`
  lo sigue exponiendo; los valores (`USUARIO`, `ADMIN`) no cambian, así que la columna, su
  restricción `CHECK` y el JSON tampoco. Alternativa descartada: dejar `Rol` en `usuario` y que
  `JwtService` reciba el rol como `String`, que perdería el tipo y obligaría a convertir de nuevo
  en `validar`.
- `JwtService.emitir(Usuario usuario)` pasa a `emitir(Long id, Rol rol)`, que es exactamente lo
  que usa (`getId()` y `getRol()`); `AuthService` llama a
  `jwtService.emitir(usuario.getId(), usuario.getRol())`. El token resultante es idéntico (`sub`,
  `rol`, `iat`, `exp`). Alternativa descartada: una interfaz en `comun/seguridad`
  (p. ej. `SujetoDeToken`) que `Usuario` implemente; añade un tipo más para dos valores y ata la
  entidad a la seguridad.

La regla se verifica con un `grep` sobre `src/main/java/com/presupuesto/comun` y
`src/test/java/com/presupuesto/comun` que debe devolver **cero** líneas con
`import com.presupuesto.usuario` o `import com.presupuesto.auth`, y queda en `AGENTS.md` como
regla obligatoria.

**Subpaquetes por capa con nombres fijos, creados solo si la feature los necesita.**
Los únicos nombres permitidos dentro de una feature son `controller`, `service`, `repository`,
`entity`, `dto`, `mapper` y `validacion` (en español, como el resto del dominio, y sin tildes). No
quedan clases en la raíz de la feature. Una feature crea solo los subpaquetes que usa; por
ejemplo, `auth` no tiene `entity` ni `repository` porque trabaja sobre las entidades de
`usuario`. Alternativa descartada: organizar todo el backend por capa (`com.presupuesto.service`,
etc.); se descarta porque `AGENTS.md` ya fija la organización por feature y este change solo
ordena el interior de cada una.

Qué va en cada subpaquete:

| Subpaquete   | Contenido                                                                  |
|--------------|----------------------------------------------------------------------------|
| `controller` | `@RestController` de la feature                                            |
| `service`    | `@Service` con la lógica de negocio y las transacciones                    |
| `repository` | Interfaces de Spring Data JPA                                              |
| `entity`     | Entidades JPA (extienden `EntidadBase`) y los enums propios que persisten  |
| `dto`        | Records de request/response y sus helpers package-private                  |
| `mapper`     | Interfaces MapStruct (`@Mapper(componentModel = "spring")`)                |
| `validacion` | Anotaciones de Bean Validation propias de la feature y sus validadores     |

**Destino de cada clase de `src/main`.**

| Clase actual                    | Paquete nuevo                          |
|---------------------------------|----------------------------------------|
| `auth.AuthController`           | `auth.controller`                      |
| `auth.AuthService`              | `auth.service`                         |
| `auth.LoginRequest`             | `auth.dto`                             |
| `auth.RegistroRequest`          | `auth.dto`                             |
| `auth.TokenResponse`            | `auth.dto`                             |
| `auth.Normalizacion`            | `auth.dto` (sigue package-private)     |
| `auth.RegistroMapper`           | `auth.mapper`                          |
| `usuario.UsuarioController`     | `usuario.controller`                   |
| `usuario.UsuarioService`        | `usuario.service`                      |
| `usuario.UsuarioRepository`     | `usuario.repository`                   |
| `usuario.PerfilRepository`      | `usuario.repository`                   |
| `usuario.Usuario`               | `usuario.entity`                       |
| `usuario.Perfil`                | `usuario.entity`                       |
| `usuario.Rol`                   | `comun.seguridad` (ver primera decisión) |
| `usuario.UsuarioActualResponse` | `usuario.dto`                          |
| `usuario.UsuarioMapper`         | `usuario.mapper`                       |
| `usuario.MayorDeEdad`           | `usuario.validacion`                   |
| `usuario.MayorDeEdadValidator`  | `usuario.validacion`                   |

`Normalizacion` va a `dto` y no a un subpaquete propio porque solo la usan los constructores
compactos de `RegistroRequest` y `LoginRequest`: así sigue siendo package-private.

**Cambios de visibilidad y de firma: solo los que exigen el cambio de paquete y la regla de
dependencias.**
- `RegistroMapper` y `UsuarioMapper` pasan a `public interface`: los services que los inyectan
  quedan en otro subpaquete. MapStruct genera la implementación en el mismo paquete del mapper,
  así que no cambia nada más.
- `TokenResponse.desde(TokenEmitido)` pasa a `public static`: lo llama `AuthService`, que queda
  en `auth.service`.
- `JwtService.emitir(Usuario)` pasa a `emitir(Long id, Rol rol)` (primera decisión).
- Todo lo demás conserva su visibilidad y firma actuales. En particular, `Normalizacion` y sus
  métodos siguen package-private, y las constantes `AuthService.MENSAJE_*` y
  `ManejadorGlobalExcepciones.MENSAJE_*` siguen package-private porque nadie fuera de su paquete
  las usa.

**Tests: reflejan el paquete de la clase que prueban; los de HTTP van en `controller`.**

| Test actual                            | Paquete nuevo        |
|----------------------------------------|----------------------|
| `auth.RegistroRequestTest`             | `auth.dto`           |
| `auth.RegistroIntegracionTest`         | `auth.controller`    |
| `auth.LoginIntegracionTest`            | `auth.controller`    |
| `auth.RegistroAtomicidadTest`          | `auth.controller`    |
| `usuario.MayorDeEdadValidatorTest`     | `usuario.validacion` |
| `usuario.UsuarioPerfilRepositoryTest`  | `usuario.repository` |
| `usuario.UsuarioActualIntegracionTest` | `usuario.controller` |

Los tres tests de integración de `auth` se prueban a través de `AuthController` y comparten el
helper package-private `RegistroIntegracionTest.datosValidosCon(...)`; al quedar juntos en
`auth.controller`, el helper no necesita volverse `public`. Los tests de `comun/` y de la raíz se
quedan donde están.

En los tests solo cambian las líneas `package` e `import`, con una única excepción acotada: las
llamadas a `emitir` pasan a la nueva firma con **el mismo id y el mismo rol** que antes, sin
cambiar ninguna aserción ni ningún nombre de test:
- `JwtServiceTest`: el campo `usuario` (id `42L`, rol por defecto `USUARIO`) se reemplaza por
  las constantes `ID_USUARIO = 42L` y `ROL_USUARIO = Rol.USUARIO`, y las 5 llamadas pasan a
  `emitir(ID_USUARIO, ROL_USUARIO)`; deja de importar `Usuario`.
- `SecurityConfigTest`: `emitirToken()` pasa a `jwtService.emitir(1L, Rol.USUARIO)` en lugar de
  construir un `Usuario` con id `1L`; deja de importar `Usuario`.
- `UsuarioActualIntegracionTest`: el token del usuario inexistente pasa a
  `jwtService.emitir(Long.MAX_VALUE, Rol.USUARIO)` en lugar de construir un `Usuario` con id
  `Long.MAX_VALUE`.

**Mover con `git mv`.**
Cada archivo se mueve con `git mv` y después se edita su `package` e imports, para que Git
registre un renombrado y conserve el historial de cada clase. Alternativa descartada: crear
archivos nuevos y borrar los viejos, que pierde el historial con `git log --follow`.

**`comun/` se mantiene organizado por responsabilidad, no por capa.**
`comun/` no es una feature: agrupa infraestructura transversal, cada subpaquete es una
responsabilidad (`excepcion`, `seguridad`, `config`, `validacion`) y ya contiene clases de varios
tipos que funcionan juntas (por ejemplo, en `seguridad`, el filtro, el servicio JWT, la
configuración, sus records y ahora `Rol`). Repartirlas en `controller`/`service`/`dto` separaría
piezas que se leen y cambian juntas, sin ganar nada: ninguna de ellas es lógica de negocio.
`EntidadBase` se queda en la raíz de `comun/` por ser la única superclase compartida por todas
las entidades; un subpaquete `comun/entity` con una sola clase no aporta. Alternativa descartada:
aplicar la misma estructura por capa en `comun/`, por lo anterior.

**Árbol de carpetas y regla de dependencias en `AGENTS.md` como convención obligatoria.**
La sección "Backend: organización por feature" pasa a mostrar el árbol exacto actual y la
plantilla de una feature, y declara obligatorio para cualquier feature nueva: subpaquetes solo
con esos siete nombres, ninguna clase en la raíz de la feature, tests en el mismo subpaquete que
la clase que prueban (HTTP en `controller`), `comun/` organizado por responsabilidad y la regla
**las features dependen de `comun/`, nunca al revés** (ningún archivo de `comun/` importa una
feature), con el `grep` que la comprueba. El árbol que se documenta:

```
backend/src/main/java/com/presupuesto/
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
│   ├── mapper/
│   └── service/
└── usuario/
    ├── controller/
    ├── dto/
    ├── entity/
    ├── mapper/
    ├── repository/
    ├── service/
    └── validacion/
```

## Risks / Trade-offs

- [`Rol` en `comun/seguridad` es un tipo que persiste una entidad de `usuario`; si una feature
  futura necesita roles propios del negocio, no debe agregarlos aquí] → `Rol` es solo el rol de
  autorización de la aplicación; `AGENTS.md` lo deja dicho al documentar la regla. Roles del
  dominio de otra feature van en el `entity` de esa feature.
- [Un import olvidado o una visibilidad insuficiente rompe la compilación] → Se detecta en
  `.\mvnw.cmd test`, que compila todo antes de ejecutar; no puede llegar a ejecutarse con un
  error de paquete.
- [MapStruct deja implementaciones generadas en `target/` con el paquete viejo] → Se ejecuta
  `.\mvnw.cmd clean test`, que regenera todo desde cero.
- [Una feature futura podría crear un subpaquete con otro nombre, dejar clases en la raíz o
  hacer que `comun/` importe una feature] → La convención queda en `AGENTS.md` como obligatoria,
  con el `grep` de dependencias; una verificación automática (ArchUnit) es una posible mejora
  futura, fuera de alcance.

## Migration Plan

No aplica: no hay cambios de base de datos (mismos valores de `rol`, misma columna), de
configuración ni de API. Rollback: revertir el commit.
