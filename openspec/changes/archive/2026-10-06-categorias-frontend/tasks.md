# Tasks

Todas las rutas son desde `frontend/src/app/` y cada test va junto al archivo que prueba. Los
comandos se ejecutan en `frontend/`. No se ejecuta `npm install` ni se agrega ninguna librería.

## 1. Línea base

- [x] 1.1 Ejecutar `npx ng build` y `npx ng test --watch=false` antes de cambiar nada; verificar
      que el build termina sin errores y anotar cuántos tests pasan (369 esperados).

## 2. Modelos, códigos y servicio

- [x] 2.1 Crear los modelos de `features/categorias/models/` listados en `design.md`, con los
      nombres de campo exactos de los records de `com.presupuesto.categoria.dto` (`oculto` en
      grupos, `oculta` en categorías), uno por archivo (interfaces sin test); verificar con
      `npx ng build`.
- [x] 2.2 Agregar `GRUPO_CATEGORIA_YA_EXISTE` y `CATEGORIA_YA_EXISTE` a `CODIGOS_API` en
      `shared/api/problema-api.ts` (spec `shared/api/problema-api.spec.ts`, sin cambios).
- [x] 2.3 Crear `features/categorias/services/categoria.service.ts` (test
      `features/categorias/services/categoria.service.spec.ts`) con las once operaciones de
      `design.md`; verificar con `HttpTestingController` la URL, el método, el parámetro
      `incluirOcultas` y el cuerpo de cada una (`/categorias`, `/grupos-categorias`,
      `/{id}/ocultar`, `/mostrar` y `/mover` con `{ posicion }` o `{ grupoId, posicion }`).

## 3. Reordenamiento puro

- [x] 3.1 Crear `features/categorias/services/reordenamiento.ts` (test
      `features/categorias/services/reordenamiento.spec.ts`) con `posicionGrupo`,
      `posicionCategoria`, `moverGrupo` y `moverCategoria`. Verificar en el test:
      - Grupos: 0→2 da 2, 2→0 da 0 y el mismo lugar da `null`; con un grupo oculto en medio
        (`orden` 0, 2, 3 visibles), 0→2 da 3.
      - Mismo grupo: hacia abajo y hacia arriba sin ocultos; con `A(0) H(1) B(2) C(3)` visibles
        `A, B, C`, A→2 da 3, C→0 da 0, A→1 da 2 y C→1 da 2; el mismo lugar da `null`.
      - Otro grupo: delante de la primera da su `orden`, entre dos da el de la segunda, al
        final da el de la última más uno, en un grupo vacío da 0, y con una oculta al final del
        destino da el de la última visible más uno.
      - `moverGrupo` y `moverCategoria` devuelven un árbol nuevo con el orden esperado (dentro
        del grupo, a otro grupo, a un grupo vacío, con el `grupoId` actualizado) y no mutan el
        árbol recibido.

## 4. Diálogos

- [x] 4.1 Crear `features/categorias/components/dialogo-grupo.component.ts` (+ html, scss; test
      `features/categorias/components/dialogo-grupo.component.spec.ts`): crear hace `POST` con el
      nombre recortado; renombrar muestra el nombre actual y hace `PUT`; nombre vacío y de 101
      caracteres con sus mensajes; botón deshabilitado si es inválido o se envía; 409
      `GRUPO_CATEGORIA_YA_EXISTE` en el nombre; 400 en sus campos; 500 con aviso genérico; el
      diálogo solo se cierra con éxito.
- [x] 4.2 Crear `features/categorias/components/dialogo-categoria.component.ts` (+ html, scss;
      test `features/categorias/components/dialogo-categoria.component.spec.ts`): crear hace
      `POST` con `grupoId`, nombre recortado y nota recortada o `null`; editar muestra nombre y
      nota y hace `PUT` con `{ nombre, nota }` (nota vacía → `null`); nota de 501 caracteres con
      su mensaje y el contador `501/500`; 409 `CATEGORIA_YA_EXISTE` en el nombre; 500 con aviso
      genérico; botón deshabilitado mientras envía.
- [x] 4.3 Crear `features/categorias/components/dialogo-mover-categoria.component.ts` (+ html,
      scss; test `features/categorias/components/dialogo-mover-categoria.component.spec.ts`):
      ofrece los grupos y, para el elegido, `Al principio` y `Después de ...` sin la propia
      categoría; mover al principio y después de la última de otro grupo envía las posiciones
      de la spec; mover a un grupo vacío envía 0; una elección que no cambia nada cierra sin
      llamar; 409 `CATEGORIA_YA_EXISTE` se muestra en el diálogo.

## 5. Página y componente de grupo

- [x] 5.1 Crear `features/categorias/components/grupo-categorias.component.ts` (+ html, scss) y
      `features/categorias/pages/categorias.page.ts` (+ html, scss; test
      `features/categorias/pages/categorias.page.spec.ts`) según `design.md`: carga, interruptor,
      estados, menús, diálogos, ocultar y mostrar, arrastrar y soltar optimista con reversión y
      bloqueo.
- [x] 5.2 En el test de la página (presupuesto activo fijado, `HttpTestingController`, `MatDialog`
      y `MatSnackBar` sustituidos, `cdkDropListDropped` disparado con eventos armados), verificar:
      - Carga, interruptor y estados: spinner; el árbol se pide con `incluirOcultas=false` y
        muestra grupos y categorías en orden con la nota debajo; `Mostrar ocultas` pide
        `incluirOcultas=true` y pinta los ocultos atenuados con `visibility_off`; árbol vacío
        con `Aún no tienes categorías`; error con `Reintentar`; un 401 no avisa.
      - Diálogos y menús: `Agregar grupo`, `Agregar categoría`, `Renombrar`, `Editar` y
        `Mover a...` abren su diálogo con los datos correctos y recargan al cerrarse con
        resultado; ocultar y mostrar grupo y categoría llaman a su endpoint y recargan.
      - Reordenar grupos: soltar un grupo actualiza la vista antes de la respuesta y llama a
        `/mover` con la posición del backend.
      - Reordenar categorías: dentro del grupo y a otro grupo, incluido uno vacío.
      - Sin cambios: soltar en el mismo lugar no llama al backend.
      - Rechazo del backend: un 409 revierte la vista, avisa con el texto de categoría repetida
        y recarga.
      - Movimiento en curso: un segundo soltado se ignora.

## 6. Ruta y menú

- [x] 6.1 Agregar en `app.routes.ts` la ruta hija `categorias` con
      `data: seccion('Categorías', 'category')` (test `app.routes.spec.ts`): entrar con sesión a
      `/presupuestos/3/categorias` muestra el árbol y el enlace `Categorías` en el menú lateral.

## 7. Documentación

- [x] 7.1 Agregar `features/categorias/` al árbol del frontend en `AGENTS.md`; verificar que
      ninguna línea nueva supera 100 columnas.

## 8. Verificación final

- [x] 8.1 Ejecutar `npx ng build` y `npx ng test --watch=false`; todo en verde.
- [x] 8.2 Desde `frontend/src/app`, `grep -rn "features/" core shared` y
      `grep -rnE "from '\.\./\.\./(auth|inicio|presupuestos|cuentas)/" features/categorias`
      devuelven cero líneas, y `npx prettier --check --end-of-line auto` sobre los archivos
      nuevos y modificados no da diferencias.
- [x] 8.3 Ejecutar `openspec validate categorias-frontend --strict` sin errores.
