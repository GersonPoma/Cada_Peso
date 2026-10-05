# Tasks

## 1. Línea base

- [x] 1.1 Con JDK 21, ejecutar `.\mvnw.cmd clean test` antes de cambiar nada y guardar en el
      scratchpad la lista ordenada `NombreSimpleDeLaClase#metodo` de los `testcase` de
      `target/surefire-reports/TEST-*.xml`; verificar que son 94 tests y que todos pasan.

## 2. Mapeo manual y salida de MapStruct

- [x] 2.1 Agregar a `UsuarioActualResponse` (`com.presupuesto.usuario.dto`, todavía sin mover) el
      método `public static UsuarioActualResponse desde(Perfil perfil)` con los siete campos que
      mapeaba `UsuarioMapper` (`email` y `rol` desde `perfil.getUsuario()`); cambiar
      `UsuarioService` a `.map(UsuarioActualResponse::desde)` y quitarle el campo
      `UsuarioMapper`; en `AuthService.registrar`, construir el `Perfil` con `Perfil.builder()`
      (usuario, nombre, apellido, fechaNacimiento, monedaPredeterminada, telefono) y quitar
      `RegistroMapper` de su constructor; eliminar con `git rm`
      `com.presupuesto.auth.mapper.RegistroMapper` y `com.presupuesto.usuario.mapper.UsuarioMapper`
      (y los directorios `auth/mapper` y `usuario/mapper`); verificar que
      `.\mvnw.cmd clean test` pasa con 94 tests.
- [x] 2.2 En `pom.xml`, eliminar las propiedades `mapstruct.version` y
      `lombok-mapstruct-binding.version`, la dependencia `org.mapstruct:mapstruct` y, en las
      ejecuciones `default-compile` y `default-testCompile` del `maven-compiler-plugin`, las
      entradas `lombok-mapstruct-binding` y `mapstruct-processor`, dejando `lombok` como única
      entrada de `annotationProcessorPaths`; verificar que
      `.\mvnw.cmd dependency:tree "-Dincludes=org.mapstruct"` no lista ningún artefacto, que
      `grep -rni mapstruct backend/pom.xml backend/src` no devuelve nada y que
      `.\mvnw.cmd clean test` pasa con 94 tests.

## 3. DTO en `dto/request` y `dto/response`

- [x] 3.1 Con `git mv`, mover `LoginRequest`, `RegistroRequest` y `Normalizacion` a
      `com.presupuesto.auth.dto.request`, `TokenResponse` a `com.presupuesto.auth.dto.response` y
      `UsuarioActualResponse` a `com.presupuesto.usuario.dto.response`, según la tabla de
      `design.md`; actualizar su `package` y los imports de `AuthController`, `AuthService`,
      `UsuarioController` y `UsuarioService`; mantener `Normalizacion` y sus métodos
      package-private; verificar que no queda ningún `.java` directamente en `auth/dto` ni en
      `usuario/dto` y que `.\mvnw.cmd clean compile` termina sin errores.
- [x] 3.2 Con `git mv`, mover `RegistroRequestTest` a `com.presupuesto.auth.dto.request` en
      `src/test`, cambiando solo su `package` e imports; verificar que `.\mvnw.cmd clean test`
      pasa con 94 tests.

## 4. Documentación y planificación

- [x] 4.1 En `AGENTS.md`, reemplazar las secciones "Backend: organización por feature" y "DTOs y
      MapStruct" por la sección "Estructura obligatoria de una feature" con los siete puntos de
      `design.md` (declaración de que toda feature nueva —cuentas, categorías, transacciones y las
      que sigan— la sigue sin excepciones; árbol de plantilla y árbol actual de `auth` y
      `usuario`; qué va en cada subpaquete; mapeo manual con `desde(...)` y builders, sin
      MapStruct; ubicación de los tests; visibilidad mínima y regla de dependencias con `comun/`
      y su `grep`; checklist para crear una feature nueva), conservando la subsección de
      `comun/`; cambiar en "Nomenclatura" los ejemplos `CuentaDto`/`CuentaMapper` por
      `CuentaRequest`/`CuentaResponse`; verificar que `grep -ni mapstruct AGENTS.md` solo
      devuelve la línea que dice que no se usa, que el árbol actual coincide con los directorios
      de `backend/src/main/java/com/presupuesto` y que ninguna línea supera 100 columnas.
- [x] 4.2 En `openspec/config.yaml`, agregar al `context` el resumen de la estructura obligatoria
      de una feature con la referencia a `AGENTS.md`, y en `rules` las reglas de `design` y
      `tasks` descritas en `design.md`, sin superar 100 columnas; verificar que
      `openspec instructions design --change mapeo-manual-dto --json` y
      `openspec instructions tasks --change mapeo-manual-dto --json` devuelven el `context` con el
      párrafo nuevo y sus `rules` nuevas, y que `openspec validate mapeo-manual-dto --strict` es
      válido.

## 5. Verificación integral

- [x] 5.1 Ejecutar `.\mvnw.cmd clean test` y verificar que pasan 94 tests con la lista ordenada
      idéntica a la de la 1.1 (no se elimina ningún test: no había tests propios de los
      mappers); repetir el `grep` de dependencias de `comun/` (cero líneas) y el de `mapstruct`
      sobre `backend/pom.xml` y `backend/src` (cero líneas); revisar con `git diff -M` que en
      `src/test` solo cambian `package` e imports de `RegistroRequestTest`; ejecutar
      `openspec validate mapeo-manual-dto --strict`.
- [x] 5.2 Con PostgreSQL corriendo, levantar el backend (`.\mvnw.cmd spring-boot:run`) y con
      `curl` registrar un usuario (con teléfono y moneda `USD`, para cubrir todos los campos del
      `Perfil`), iniciar sesión y consultar `/api/v1/usuarios/yo`; verificar 201, 200 y 200 con los
      siete campos y los valores enviados, y borrar después el usuario de prueba.
