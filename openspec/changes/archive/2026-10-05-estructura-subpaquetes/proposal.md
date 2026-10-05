# Proposal

## Why

Las features `auth` (7 clases) y `usuario` (11 clases) tienen todas sus clases planas en un
único paquete: controllers, services, repositorios, entidades, DTOs, mappers y validaciones
mezclados. Con las próximas features (cuentas, categorías, transacciones) cada paquete crecería
igual y sería difícil ubicar una clase. Conviene fijar ahora, con solo dos features que mover, una
estructura interna por capa obligatoria para todas las features, antes de que haya más código que
reorganizar.

Además, `comun/` depende hoy de una feature: `JwtService` y `UsuarioAutenticado`
(`comun/seguridad`) importan `Usuario` y `Rol` de `usuario`. La infraestructura transversal no
debería conocer a ninguna feature; si no se corrige ahora, cada feature nueva puede repetir el
patrón y acabar en dependencias circulares entre `comun/` y las features.

## What Changes

- **Invertir la dependencia de `comun/` hacia `usuario`**: `Rol` se mueve a `comun/seguridad`
  (es el rol de seguridad que viaja en el token y en el principal) y
  `JwtService.emitir(Usuario)` pasa a `JwtService.emitir(Long id, Rol rol)`. Ningún archivo de
  `comun/` (ni de `src/main` ni de `src/test`) importa `com.presupuesto.usuario` ni
  `com.presupuesto.auth`, verificado con un `grep`. Regla documentada en `AGENTS.md`: **las
  features dependen de `comun/`, nunca al revés**.
- Reorganizar cada feature del backend en subpaquetes por capa, creando solo los que la feature
  necesita: `controller`, `service`, `repository`, `entity`, `dto`, `mapper` y `validacion`.
  - `auth` → `controller`, `service`, `dto`, `mapper` (no tiene entidades ni repositorios
    propios: usa los de `usuario`).
  - `usuario` → `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `validacion`.
- Ajustar visibilidades e imports: los mappers MapStruct (`RegistroMapper`, `UsuarioMapper`) y
  `TokenResponse.desde(...)` pasan a `public` porque sus usuarios quedan en otro subpaquete; todo
  lo que puede seguir siendo package-private lo sigue siendo (por ejemplo, `Normalizacion` queda
  junto a los records de request que la usan).
- Mover los tests de `auth` y `usuario` a los subpaquetes que reflejan la clase que prueban,
  cambiando solo `package` e `import`, y la suite sigue teniendo 94 tests con los mismos nombres.
  La única excepción son las llamadas a `JwtService.emitir` en `JwtServiceTest`,
  `SecurityConfigTest` y `UsuarioActualIntegracionTest`, que pasan a la nueva firma con el mismo
  id y rol que antes; ninguna aserción cambia.
- Revisar `comun/`: se mantiene organizado por responsabilidad transversal (`excepcion`,
  `seguridad`, `config`, `validacion` y `EntidadBase` en la raíz), sin subpaquetes por capa,
  porque no es una feature (ver design.md).
- Actualizar `AGENTS.md` con el árbol de carpetas exacto como convención **obligatoria** para
  todas las features futuras, y corregir la sección de organización del backend (hoy dice que el
  filtro JWT está "a implementar" y no menciona `comun/validacion`).

Sin cambios de comportamiento: mismas rutas, mismas respuestas, mismas tablas y columnas, mismos
beans.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. Es un refactor puro de estructura de paquetes sin cambios de comportamiento observable,
por eso el change declara `skip_specs: true`.

## Impact

- **Backend, `src/main`**: `Rol` pasa de `usuario` a `comun/seguridad`; `JwtService.emitir`
  cambia de firma (`AuthService` es su único llamador) y `JwtService` y `UsuarioAutenticado` dejan
  de importar nada de `usuario`; las otras 17 clases de `com.presupuesto.auth` y
  `com.presupuesto.usuario` cambian de paquete, con sus imports actualizados.
- **Backend, `src/test`**: las 7 clases de test de `auth` y `usuario` cambian de paquete e
  imports; `JwtServiceTest` y `SecurityConfigTest` (`comun/seguridad`) dejan de usar `Usuario` y
  llaman a la nueva firma de `emitir`; el resto de los tests de `comun/` y de la raíz no cambian.
- **Base de datos**: ninguno (las entidades declaran `@Table` explícito; Hibernate no ve
  diferencia).
- **API**: ninguno.
- **Documentación**: `AGENTS.md`.
- **Dependencias y frontend**: ninguno.
