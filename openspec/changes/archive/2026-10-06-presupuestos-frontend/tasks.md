# Tasks

Todas las rutas son desde `frontend/src/app/` y cada test va junto al archivo que prueba. Los
comandos se ejecutan en `frontend/`. No se ejecuta `npm install` ni se agrega ninguna librería, y
no se hace commit.

## 1. Línea base

- [x] 1.1 Ejecutar `npx ng build` y `npx ng test --watch=false` antes de cambiar nada; verificar
      que el build termina sin errores y anotar cuántos tests pasan (54 o más esperados).

## 2. Presupuesto activo y cierre de sesión (`core/`)

- [x] 2.1 Crear `core/presupuesto-activo/presupuesto-activo.model.ts` (interfaz
      `PresupuestoActivo { id, nombre, moneda }`, sin test) y
      `core/presupuesto-activo/presupuesto-activo.service.ts` (test
      `core/presupuesto-activo/presupuesto-activo.service.spec.ts`) con la señal de solo lectura
      `presupuesto`, `fijar(...)` y `limpiar()`; verificar que arranca en `null`, que `fijar`
      expone id, nombre y moneda, que fijar otro lo reemplaza y que `limpiar` vuelve a `null`.
- [x] 2.2 Modificar `core/sesion/sesion.service.ts` para que `cerrar()` llame a
      `PresupuestoActivoService.limpiar()` (test `core/sesion/sesion.service.spec.ts`); agregar un
      caso que fija un presupuesto, cierra la sesión y verifica que no hay presupuesto activo.
- [x] 2.3 Ampliar `core/auth/sesion.interceptor.spec.ts` con un caso: un 401 de
      `/api/v1/presupuestos` deja el presupuesto activo en `null` y navega a `/login`.

## 3. Código de error compartido

- [x] 3.1 Agregar `PRESUPUESTO_YA_EXISTE` a `CODIGOS_API` en `shared/api/problema-api.ts` (test
      `shared/api/problema-api.spec.ts`, sin cambios); verificar con `npx ng build`.

## 4. Modelos y servicio de la feature (`features/presupuestos/`)

- [x] 4.1 Crear las interfaces, una por archivo y sin test:
      `features/presupuestos/models/presupuesto-response.model.ts`
      (`{ id, nombre, moneda, fechaCreacion, fechaActualizacion }`, fechas como texto ISO),
      `features/presupuestos/models/crear-presupuesto-request.model.ts`
      (`{ nombre, moneda? }`), `features/presupuestos/models/actualizar-presupuesto-request.model.ts`
      (`{ nombre }`), `features/presupuestos/models/datos-dialogo-presupuesto.model.ts`
      (`{ modo: 'crear' } | { modo: 'renombrar'; presupuesto: { id, nombre } }`) y
      `features/presupuestos/models/seccion-presupuesto.model.ts` (`{ etiqueta, icono }`);
      verificar con `npx ng build`.
- [x] 4.2 Crear `features/presupuestos/services/presupuesto.service.ts` (test
      `features/presupuestos/services/presupuesto.service.spec.ts`) con `listar()`,
      `crear(solicitud)` y `renombrar(id, solicitud)`; verificar con `HttpTestingController` que
      hacen `GET /api/v1/presupuestos`, `POST /api/v1/presupuestos` con el cuerpo recibido (sin
      `moneda` cuando no viene) y `PUT /api/v1/presupuestos/3` con `{ nombre }`.

## 5. Diálogo de crear y renombrar

- [x] 5.1 Crear `features/presupuestos/components/dialogo-presupuesto.component.ts` (+ `.html`,
      `.scss`; test `features/presupuestos/components/dialogo-presupuesto.component.spec.ts`)
      según `design.md`: título "Nuevo presupuesto"/"Renombrar presupuesto", campo nombre,
      `mat-select` de moneda solo al crear (`Moneda de mi perfil` por defecto, `BOB`, `USD`,
      `EUR`, `ARS`, `BRL`, `CLP`, `PEN`), botones "Cancelar" y "Crear"/"Guardar", envío con
      `trim()`, cierre con el `PresupuestoResponse` solo al tener éxito y errores por `codigo`.
- [x] 5.2 En el test del diálogo (con `MAT_DIALOG_DATA`, un `MatDialogRef` falso,
      `HttpTestingController` y arneses `MatInputHarness`, `MatSelectHarness` y
      `MatButtonHarness`), verificar: crear con `"  Casa "` sin tocar la moneda envía
      `{ nombre: 'Casa' }` sin `moneda`; crear eligiendo `USD` envía `moneda: 'USD'`; renombrar
      muestra el nombre actual, no tiene selector de moneda y hace `PUT` con el nombre nuevo; con
      éxito se cierra con la respuesta; `"   "` muestra "El nombre es obligatorio" y 101
      caracteres "El nombre no puede superar los 100 caracteres", ambos con el botón
      deshabilitado; el botón también está deshabilitado con la respuesta pendiente; un 409
      `PRESUPUESTO_YA_EXISTE` muestra "Ya tienes un presupuesto con ese nombre" en el nombre sin
      cerrar; un 400 `DATOS_INVALIDOS` con `errores.moneda` muestra ese mensaje en la moneda; un
      500 abre el aviso genérico sin cerrar; "Cancelar" cierra sin resultado.

## 6. Página de redirección (`/`)

- [x] 6.1 Crear `features/presupuestos/pages/redireccion-presupuesto.page.ts` (+ `.html`,
      `.scss`; test `features/presupuestos/pages/redireccion-presupuesto.page.spec.ts`) según
      `design.md`: `<app-cabecera>` con "Cerrar sesión", spinner mientras carga, navegación con
      `replaceUrl` al primero, estado vacío con "Crear mi primer presupuesto" y error con aviso
      "Reintentar" y botón fijo "Reintentar".
- [x] 6.2 En su test verificar: con la petición pendiente se ve el spinner; con un presupuesto
      (id `7`) navega a `['/presupuestos', 7]` con `replaceUrl: true`; con `casa` (3) y `Viajes`
      (1) navega al 3; con lista vacía no navega y muestra el botón; el botón abre el diálogo en
      modo crear y, si se cierra con id `9`, navega al 9 (con `MatDialog` sustituido); un 500 abre
      `MENSAJE_ERROR_GENERICO` con la acción "Reintentar" y reintentar vuelve a pedir la lista; un
      401 no abre aviso; "Cerrar sesión" borra la sesión y navega a `/login`.

## 7. Layout del presupuesto (`/presupuestos/:presupuestoId`)

- [x] 7.1 Crear `features/presupuestos/pages/layout-presupuesto.page.ts` (+ `.html`, `.scss`;
      test `features/presupuestos/pages/layout-presupuesto.page.spec.ts`) según `design.md`:
      carga de la lista, validación del id con `paramMap`, `fijar` del activo, `router-outlet`
      solo con el activo correcto, cabecera con hamburguesa, `mat-select` de presupuestos,
      `mat-menu` "Gestionar presupuestos" (nuevo y renombrar) y "Cerrar sesión", y `mat-sidenav`
      con las secciones leídas de `routeConfig.children` y modo según `BreakpointObserver`.
- [x] 7.2 En su test (con `RouterTestingHarness` y rutas de prueba con una sección hija,
      `HttpTestingController`, arneses `MatSelectHarness`/`MatSidenavHarness` y un
      `BreakpointObserver` falso con `Subject`), verificar: en `/presupuestos/1` con `Casa` (3,
      `BOB`) y `Viajes` (1, `USD`) el activo es `{ id: 1, nombre: 'Viajes', moneda: 'USD' }` y el
      selector muestra `Viajes`; elegir `Casa` lleva a `/presupuestos/3` y cambia el activo; un
      id `99` o `abc` navega a `/`; el contenido de la sección no se pinta antes de fijar el
      activo; crear desde el menú con respuesta id `9` agrega el presupuesto al selector y lleva
      a `/presupuestos/9`; renombrar actualiza el selector y el activo sin cambiar la URL; un 500
      al cargar abre el aviso con "Reintentar"; "Cerrar sesión" deja sin sesión ni activo y
      navega a `/login`; en pantalla ancha el sidenav está abierto en modo `side` sin botón de
      menú, y en estrecha está cerrado en modo `over`, el botón "Abrir menú" lo abre y pulsar el
      enlace `Inicio` lo cierra.

## 8. Inicio y rutas

- [x] 8.1 Modificar `features/inicio/pages/inicio.page.ts` y `.html` (test
      `features/inicio/pages/inicio.page.spec.ts`): quitar `<app-cabecera>`, el botón "Cerrar
      sesión", `cerrarSesion()` y las inyecciones que sobren; en el test, reemplazar el caso de
      cerrar sesión por uno que verifica que la página no tiene ningún botón "Cerrar sesión" ni
      `app-cabecera`, y mantener los demás casos pasando.
- [x] 8.2 Modificar `app.routes.ts` (test `app.routes.spec.ts`): `''` (`pathMatch: 'full'`,
      `sesionGuard`) → `RedireccionPresupuestoPage`; `presupuestos/:presupuestoId` (`sesionGuard`)
      → `LayoutPresupuestoPage` con `children: [{ path: '', loadComponent: InicioPage,
      data: { seccion: { etiqueta: 'Inicio', icono: 'home' } } }]` tipado con
      `SeccionPresupuesto`; `login`, `registro` y `'**'` sin cambios.
- [x] 8.3 Actualizar `app.routes.spec.ts`: con sesión, `/` pide `/api/v1/presupuestos` (con
      `Bearer abc`), al responder `[{ id: 3, ... }]` termina en `/presupuestos/3`, el layout vuelve
      a pedir la lista, inicio pide `/api/v1/usuarios/yo` y se ve `Cada Peso` una sola vez y
      `Hola, Ana`; `/login` y `/registro` con sesión terminan en `/presupuestos/3`; sin sesión,
      `/presupuestos/3` termina en `/login`; los casos sin sesión existentes siguen igual.

## 9. Documentación

- [x] 9.1 Actualizar `AGENTS.md`: en el árbol del frontend agregar `core/presupuesto-activo/` y
      `features/presupuestos/` (con `components/`, `models/`, `pages/` y `services/`), describir
      `presupuesto-activo/` en la lista de `core/` y agregar la regla de que las features obtienen
      el presupuesto actual de `PresupuestoActivoService` (nunca de otra feature) y declaran su
      enlace del menú lateral con `data.seccion` en su ruta hija; verificar que ninguna línea
      supera 100 columnas.

## 10. Verificación final

- [x] 10.1 Ejecutar `npx ng build` y `npx ng test --watch=false`; verificar que el build no da
      errores y que pasan todos los tests (los de la línea base más los nuevos).
- [x] 10.2 Verificar las dependencias del frontend desde `frontend/src/app`:
      `grep -rn "features/" core shared` devuelve cero líneas, y también
      `grep -rnE "from '\.\./\.\./(auth|inicio)/" features/presupuestos` y
      `grep -rn "from '\.\./\.\./presupuestos/" features/auth features/inicio`; ejecutar
      `npx prettier --check "src/**/*.{ts,html,scss}"` sin diferencias.
- [x] 10.3 Ejecutar `openspec validate presupuestos-frontend --strict` sin errores.
