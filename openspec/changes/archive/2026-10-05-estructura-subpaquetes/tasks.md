# Tasks

## 1. Línea base

- [x] 1.1 Con JDK 21, ejecutar `.\mvnw.cmd clean test` antes de mover nada y guardar en el
      scratchpad la lista ordenada de tests ejecutados (`NombreSimpleDeLaClase#metodo` de cada
      `testcase` de `target/surefire-reports/TEST-*.xml`); verificar que son 94 tests y que todos
      pasan.

## 2. Dependencias: `comun/` no depende de ninguna feature

- [x] 2.1 Con `git mv`, mover `usuario/Rol.java` a `comun/seguridad/Rol.java` y actualizar su
      `package`; cambiar `JwtService.emitir(Usuario)` por `emitir(Long id, Rol rol)` (mismo
      `sub`, claim `rol`, `iat` y `exp`); cambiar las 2 llamadas de `AuthService` a
      `jwtService.emitir(usuario.getId(), usuario.getRol())`; actualizar el import de `Rol` en
      `Usuario`, `UsuarioActualResponse`, `UsuarioAutenticado`, `RegistroIntegracionTest` y
      `UsuarioPerfilRepositoryTest`; en los tests, adaptar solo las llamadas a `emitir` como
      indica `design.md` (`JwtServiceTest` con las constantes `ID_USUARIO = 42L` y
      `ROL_USUARIO = Rol.USUARIO`, `SecurityConfigTest` con `emitir(1L, Rol.USUARIO)`,
      `UsuarioActualIntegracionTest` con `emitir(Long.MAX_VALUE, Rol.USUARIO)`), sin tocar
      ninguna aserción; verificar que `.\mvnw.cmd clean test` pasa con 94 tests.
- [x] 2.2 Verificar que ningún archivo de `comun/` importa una feature: desde `backend/src`,
      `grep -rnE "import com\.presupuesto\.(usuario|auth)" <dir>` con `<dir>` igual a
      `main/java/com/presupuesto/comun` y después a `test/java/com/presupuesto/comun` debe
      devolver cero líneas en ambos casos (código de salida 1 de `grep`).

## 3. Feature `usuario`

- [x] 3.1 Con `git mv`, mover las 10 clases restantes de `src/main/java/com/presupuesto/usuario`
      a `controller`, `service`, `repository`, `entity`, `dto`, `mapper` y `validacion` según la
      tabla de `design.md`; actualizar su `package` y sus imports; hacer `public` la interfaz
      `UsuarioMapper`; actualizar los imports de `Usuario`, `Perfil`, `PerfilRepository`,
      `UsuarioRepository` y `MayorDeEdad` en `auth` (todavía plano); verificar que
      `src/main/java/com/presupuesto/usuario` ya no contiene ningún archivo `.java` en su raíz y
      que `.\mvnw.cmd clean compile` termina sin errores.
- [x] 3.2 Con `git mv`, mover los 3 tests de `usuario` según la tabla de tests de `design.md`
      (`MayorDeEdadValidatorTest` → `usuario.validacion`, `UsuarioPerfilRepositoryTest` →
      `usuario.repository`, `UsuarioActualIntegracionTest` → `usuario.controller`) cambiando solo
      `package` e imports; actualizar los imports de las clases de `usuario` en los tests de
      `auth`; verificar que `.\mvnw.cmd clean test` pasa con 94 tests.

## 4. Feature `auth`

- [x] 4.1 Con `git mv`, mover las 7 clases de `src/main/java/com/presupuesto/auth` a
      `controller`, `service`, `dto` y `mapper` según la tabla de `design.md`; actualizar su
      `package` y sus imports; hacer `public` la interfaz `RegistroMapper` y el método
      `TokenResponse.desde(TokenEmitido)`; dejar `Normalizacion` y sus métodos package-private
      en `auth.dto`; verificar que `src/main/java/com/presupuesto/auth` ya no contiene ningún
      archivo `.java` en su raíz y que `.\mvnw.cmd clean compile` termina sin errores.
- [x] 4.2 Con `git mv`, mover los 4 tests de `auth` según la tabla de tests de `design.md`
      (`RegistroRequestTest` → `auth.dto`; `RegistroIntegracionTest`, `LoginIntegracionTest` y
      `RegistroAtomicidadTest` → `auth.controller`) cambiando solo `package` e imports, sin
      cambiar la visibilidad del helper `RegistroIntegracionTest.datosValidosCon(...)`; verificar
      que `.\mvnw.cmd clean test` pasa con 94 tests.

## 5. Documentación

- [x] 5.1 Reescribir la sección "Backend: organización por feature" de `AGENTS.md` con: el árbol
      exacto de `design.md`; la plantilla de una feature con los siete subpaquetes y qué va en
      cada uno; la regla **obligatoria** para toda feature futura (solo esos siete nombres de
      subpaquete y solo los que la feature necesite, ninguna clase en la raíz de la feature,
      mínima visibilidad: `public` solo lo que se usa desde otro subpaquete); que los tests
      reflejan el subpaquete de la clase que prueban y los de HTTP van en `controller`; que
      `comun/` se organiza por responsabilidad (`config`, `excepcion`, `seguridad`,
      `validacion` y `EntidadBase` en la raíz), no por capa; y la regla **las features dependen
      de `comun/`, nunca al revés**, con el `grep` de la 2.2 para comprobarla y la aclaración de
      que `comun/seguridad/Rol` es solo el rol de autorización de la aplicación (los roles del
      dominio de una feature van en su `entity`). Corregir en esa misma sección la descripción
      desactualizada de `comun/seguridad` ("filtro JWT (a implementar...)") y agregar
      `comun/validacion`; verificar que el texto cubre cada punto, que ninguna línea supera 100
      columnas y que el árbol coincide con el resultado de listar los directorios de
      `src/main/java/com/presupuesto`.

## 6. Verificación integral

- [x] 6.1 Ejecutar `.\mvnw.cmd clean test` y verificar que pasan 94 tests y que la lista ordenada
      `NombreSimpleDeLaClase#metodo` es idéntica a la guardada en la 1.1; repetir el `grep` de la
      2.2 (cero líneas); verificar con `git diff -M` que cada archivo de test movido o editado
      solo tiene cambios en líneas `package` e `import`, salvo las llamadas a `emitir` (y sus
      constantes en `JwtServiceTest`) descritas en `design.md`, y que en `src/main` los únicos
      cambios fuera de `package`/`import` son los tres cambios de visibilidad y la nueva firma de
      `JwtService.emitir` con sus 2 llamadas en `AuthService`; ejecutar
      `openspec validate estructura-subpaquetes --strict` y verificar que es válido.
- [x] 6.2 Con PostgreSQL corriendo, levantar el backend (`.\mvnw.cmd spring-boot:run`) y con
      `curl` registrar un usuario, iniciar sesión y consultar `/api/v1/usuarios/yo`; verificar
      que las respuestas son las mismas que antes (201, 200 y 200 con los siete campos y `rol`
      `USUARIO`), que el payload del token sigue teniendo `sub` y `rol`, y borrar después el
      usuario de prueba.
