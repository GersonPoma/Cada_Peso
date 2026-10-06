# Tasks

Todas las rutas son desde `frontend/src/app/` y cada test va junto al archivo que prueba. No se
ejecuta `npm install` ni se agrega ninguna librería.

## 1. Línea base

- [x] 1.1 En `frontend`, ejecutar `npx ng build` y `npx ng test --watch=false` antes de cambiar
      nada; verificar que el build termina sin errores, que `npm ls @angular/material
      @angular/cdk material-symbols` ya los lista y anotar cuántos tests pasan (54 esperados).

## 2. Validadores y utilidades compartidas

- [x] 2.1 Crear `shared/validacion/sobre-texto-recortado.validator.ts` (test
      `shared/validacion/sobre-texto-recortado.validator.spec.ts`) con
      `sobreTextoRecortado(validador)` según `design.md`; verificar que
      `sobreTextoRecortado(Validators.required)` rechaza `''`, `'   '` y `null` y acepta
      `' a '`, que con `Validators.email` acepta `'  ana@ejemplo.com  '` y rechaza
      `'ana-arroba-ejemplo.com'`, que con `minLength(2)` rechaza `'  A  '` y acepta `'  Ab '`, que
      con `maxLength(3)` acepta `'  abc '`, que conserva las claves de error de Angular
      (`required`, `email`, `minlength`, `maxlength`) y que el valor del control no cambia.
- [x] 2.2 Crear `shared/validacion/max-bytes-utf8.validator.ts` (test
      `shared/validacion/max-bytes-utf8.validator.spec.ts`) con `maxBytesUtf8(maximo)`; verificar
      que 72 letras `a` son válidas, que 36 letras `ñ` (72 bytes) son válidas, que una `ñ` y 71
      letras `a` (72 caracteres, 73 bytes) dan `{ maxBytesUtf8: { maximo: 72, actual: 73 } }` y
      que `''` y `null` son válidos.
- [x] 2.3 Crear `shared/validacion/mayor-de-edad.validator.ts` (test
      `shared/validacion/mayor-de-edad.validator.spec.ts`) con `mayorDeEdad(edadMinima = 18)`;
      verificar, con `vi.useFakeTimers({ toFake: ['Date'] })` y en las zonas `America/New_York` y
      `Asia/Tokyo`, los escenarios de `specs/registro-frontend/spec.md`: con hoy el 6 de octubre
      de 2026, nacer el 6 de octubre de 2008 es válido y el 7 de octubre de 2008 y el 1 de enero
      de 2030 dan `{ mayorDeEdad: { edadMinima: 18 } }`; nacer el 29 de febrero de 2008 es
      inválido con hoy el 28 de febrero de 2026 y válido con hoy el 1 de marzo de 2026; `null` y
      un `Date` inválido son válidos.
- [x] 2.4 Crear `shared/api/problema-api.ts` (test `shared/api/problema-api.spec.ts`) con
      `leerProblemaApi(error)`, las constantes de códigos y `MENSAJE_ERROR_GENERICO`; verificar que
      un `HttpErrorResponse` 400 con cuerpo `ProblemDetail` devuelve `status`, `codigo` y
      `errores`, que un fallo de red (`status 0`) y un cuerpo de texto devuelven un problema sin
      `codigo`, que un valor que no es `HttpErrorResponse` devuelve `null` y que el mensaje
      genérico es `No pudimos completar la operación. Inténtalo de nuevo.`.
- [x] 2.5 Crear `shared/formulario/errores-formulario.ts` (test
      `shared/formulario/errores-formulario.spec.ts`) con `mensajeDeError` y
      `aplicarErroresDeCampos`; verificar que `mensajeDeError` respeta el orden del mapa de
      mensajes (con `required` y `matDatepickerParse` a la vez gana el que va primero), pone
      `servidor` primero, acepta mensajes en función y devuelve `null` sin coincidencias; y que
      `aplicarErroresDeCampos` pone `{ servidor }` en los controles con ese nombre, los marca como
      tocados, devuelve las claves sin control, deja el formulario inválido y el error desaparece
      al cambiar el valor de ese control sin afectar a los demás.
- [x] 2.6 Crear `shared/cabecera/cabecera.component.ts`, `cabecera.component.html` y
      `cabecera.component.scss` (test `shared/cabecera/cabecera.component.spec.ts`) con
      `app-cabecera` (`mat-toolbar` con `Cada Peso` y `<ng-content />`); verificar que se renderiza
      un `mat-toolbar` con el texto `Cada Peso` y que un botón proyectado aparece dentro de la
      barra.

## 3. Sesión, interceptor y guards en `core/`

- [x] 3.1 Crear `core/sesion/sesion.service.ts` (test `core/sesion/sesion.service.spec.ts`) con
      `SesionService` según `design.md`; verificar con `localStorage` real de jsdom y `Date`
      simulada los escenarios de `specs/autenticacion-frontend/spec.md`: un token que vence dentro
      de 2 horas restaura la sesión; uno vencido, un texto que no es JSON, un JSON incompleto y
      una `expiraEn` inválida dejan sin sesión y borran la clave `cada-peso.sesion`;
      `iniciar(token, expiraEn)` guarda el valor y cambia `haySesion()` y `token()`; `cerrar()`
      borra la clave; y con un `localStorage` que lanza al leer y al escribir la sesión funciona
      en memoria sin errores.
- [x] 3.2 Crear `core/auth/sesion.interceptor.ts` (test `core/auth/sesion.interceptor.spec.ts`)
      con `sesionInterceptor`; verificar con `HttpTestingController` que con sesión una petición a
      `/api/v1/usuarios/yo` lleva `Authorization: Bearer abc`, que sin sesión y hacia otras URL
      (`/otra/ruta`, `https://ejemplo.com/api/v1/x`) no lleva el header, que un 401 en
      `/api/v1/usuarios/yo` borra la sesión, navega a `/login` y relanza el error, que un 401 en
      `/api/v1/auth/login` con una sesión previa no la borra ni navega, y que un 500 no la borra.
- [x] 3.3 Crear `core/auth/sesion.guard.ts` y `core/auth/invitado.guard.ts` (tests
      `core/auth/sesion.guard.spec.ts` y `core/auth/invitado.guard.spec.ts`) con `sesionGuard` e
      `invitadoGuard`; verificar que `sesionGuard` devuelve `true` con sesión y un `UrlTree` a
      `/login` sin ella, y que `invitadoGuard` devuelve `true` sin sesión y un `UrlTree` a `/` con
      ella.
- [x] 3.4 Modificar `app.config.ts` para agregar `provideHttpClient(withInterceptors(
      [sesionInterceptor]))` y crear `app.config.spec.ts`; verificar, con `TestBed` configurado con
      `appConfig.providers` más `provideHttpClientTesting()` al final, que con sesión una petición
      a `/api/v1/usuarios/yo` lleva el token y que un 401 en esa petición borra la sesión.

## 4. Feature `auth`

- [x] 4.1 Crear los modelos `features/auth/models/login-request.model.ts`,
      `features/auth/models/registro-request.model.ts` y
      `features/auth/models/token-response.model.ts` (interfaces sin lógica, sin test propio) con
      los campos del contrato de `design.md` (`telefono` y `monedaPredeterminada` opcionales en el
      registro); verificar que `npx ng build` termina sin errores.
- [x] 4.2 Crear `features/auth/services/auth.service.ts` (test
      `features/auth/services/auth.service.spec.ts`) con `AuthService.registrar` e
      `iniciarSesion`; verificar con `HttpTestingController` que `registrar` hace
      `POST /api/v1/auth/registro` y `iniciarSesion` hace `POST /api/v1/auth/login` con el cuerpo
      recibido, que al responder guardan en `SesionService` el `token` y la `expiraEn` y que ante
      un error no guardan sesión.
- [x] 4.3 Crear `features/auth/components/campo-contrasena.component.ts`, `.html` y `.scss` (test
      `features/auth/components/campo-contrasena.component.spec.ts`); verificar los escenarios de
      mostrar y ocultar: por defecto el `input` es `type="password"` con el ícono `visibility` y la
      etiqueta `Mostrar contraseña`; al pulsar es `type="text"` con `visibility_off` y
      `Ocultar contraseña` y el valor `secreta123` no cambia; al pulsar otra vez vuelve a
      ocultarse; con un control con `required`, `minLength(8)` y `maxBytesUtf8(72)` tocado y
      vacío o inválido muestra los mensajes de `design.md`; y con un control que solo tiene
      `required` no muestra ninguna regla de longitud.
- [x] 4.4 Crear `features/auth/pages/login.page.ts`, `.html` y `.scss` (test
      `features/auth/pages/login.page.spec.ts`) con `LoginPage`; verificar con
      `proveerMaterial()`, `HttpTestingController` y un `Router` simulado los escenarios de
      inicio de sesión de `specs/autenticacion-frontend/spec.md`: campos obligatorios con sus
      mensajes y botón deshabilitado; email `abc` y contraseña `x` dejan el formulario válido sin
      mensajes de formato ni de longitud; el email `"  Ana@Ejemplo.COM "` y la contraseña
      `" secreta123 "` viajan como `ana@ejemplo.com` y `" secreta123 "`; el botón se deshabilita
      mientras la petición está pendiente; una respuesta correcta guarda la sesión y navega a `/`;
      `CREDENCIALES_INVALIDAS` muestra `Email o contraseña incorrectos` como mensaje general sin
      marcar campos; `DATOS_INVALIDOS` con `errores.email` lo muestra en el campo; un 500 y un
      error de red muestran el aviso genérico del `MatSnackBar`; y el enlace `Regístrate` apunta
      a `/registro`.
- [x] 4.5 Crear `features/auth/pages/registro.page.ts`, `.html` y `.scss` (test
      `features/auth/pages/registro.page.spec.ts`) con el formulario, las validaciones y la
      construcción de la petición de `design.md`; verificar con `proveerMaterial()` y
      `navigator.language` simulado los escenarios de `specs/registro-frontend/spec.md`: etiquetas
      en español y botón deshabilitado con el formulario vacío; los mensajes de email (vacío, solo
      espacios, formato, 255 caracteres, espacios alrededor válidos), contraseña (vacía, 8
      espacios, 7 caracteres, 72 letras `a`, 36 letras `ñ`, `ñ` más 71 `a`), nombre y apellido
      (vacío, `"  A  "`, 101 caracteres) y teléfono (21 caracteres, 20 con espacios alrededor);
      fecha obligatoria, `31/02/2003` en `es-BO` como `La fecha no es válida` y los casos de
      mayoría de edad con `Date` simulada; y el cuerpo de la petición: email recortado y en
      minúsculas, contraseña `" secreta123 "` intacta, nombre y apellido recortados, `telefono`
      recortado y omitido si queda vacío, `monedaPredeterminada` `BOB` por defecto y `USD` si se
      elige, y `fechaNacimiento` `2008-10-01` al elegir el 1 de octubre de 2008 en
      `America/New_York` y en `Asia/Tokyo`, y `2003-03-05` al escribir `05/03/2003` en `es-BO`.
- [x] 4.6 Completar `features/auth/pages/registro.page.ts` y `.html` (mismo test,
      `features/auth/pages/registro.page.spec.ts`) con el envío y los errores del API; verificar:
      un 201 guarda la sesión, navega a `/` y no llama a `/api/v1/auth/login`; `DATOS_INVALIDOS`
      con `errores` de `contrasena` y `fechaNacimiento` muestra cada mensaje en su campo y deja el
      botón deshabilitado, y al editar un campo desaparece su error sin afectar al otro; una clave
      de `errores` sin campo y un `DATOS_INVALIDOS` sin `errores` muestran el aviso genérico;
      `EMAIL_YA_REGISTRADO` muestra `Ya existe una cuenta con ese email` en el campo email; un 500
      y un error de red muestran el aviso genérico, conservan lo escrito y vuelven a habilitar el
      botón; y el enlace `Inicia sesión` apunta a `/login`.

## 5. Feature `inicio`

- [x] 5.1 Crear `features/inicio/models/usuario-actual-response.model.ts` (interfaz sin lógica,
      sin test propio) y `features/inicio/services/usuario-actual.service.ts` (test
      `features/inicio/services/usuario-actual.service.spec.ts`) con
      `UsuarioActualService.obtener()`; verificar que hace `GET /api/v1/usuarios/yo` y devuelve
      el cuerpo tipado con los siete campos del contrato.
- [x] 5.2 Crear `features/inicio/pages/inicio.page.ts`, `.html` y `.scss` (test
      `features/inicio/pages/inicio.page.spec.ts`) con `InicioPage`; verificar los escenarios de
      la página de inicio: mientras la petición está pendiente se ve un `mat-progress-spinner` y
      no hay saludo; al responder el nombre `Ana` se ve `Hola, Ana`; el botón `Cerrar sesión`
      (dentro de la cabecera) borra la sesión del almacenamiento y navega a `/login`; un 500
      muestra el aviso genérico y `No pudimos cargar tus datos.` sin cerrar la sesión; y un 401
      no muestra ningún aviso (lo gestiona el interceptor).

## 6. Rutas

- [x] 6.1 Modificar `app.routes.ts` con las rutas `''` (`sesionGuard`), `login` y `registro`
      (`invitadoGuard`) con `loadComponent` y `**` hacia `''`, y crear `app.routes.spec.ts`;
      verificar con `RouterTestingHarness`, `appConfig.providers` y `provideHttpClientTesting()`
      los escenarios de rutas de `specs/autenticacion-frontend/spec.md`: sin sesión, `/` y una ruta
      desconocida terminan en `/login`; con sesión, `/` muestra la página de inicio y `/login` y
      `/registro` terminan en `/`; sin sesión, `/login` y `/registro` se muestran sin redirección;
      y que las tres páginas muestran la cabecera `Cada Peso`.

## 7. Documentación

- [x] 7.1 Actualizar `AGENTS.md` con lo descrito en `design.md`: los sufijos `*.guard.ts`,
      `*.interceptor.ts` y `*.validator.ts` en las convenciones de nombres de archivo del
      frontend, el árbol de "Estructura obligatoria del frontend" con `core/auth`, `core/sesion`,
      `shared/{api,cabecera,formulario,validacion}`, `features/auth` y `features/inicio`, y la
      sección "Sesión y autenticación del frontend" con las reglas de `design.md`; verificar que
      el árbol coincide con los directorios de `src/app`, que el texto cubre cada punto y que
      ninguna línea supera 100 columnas.

## 8. Verificación integral

- [x] 8.1 En `frontend`, ejecutar `npx ng build` y `npx ng test --watch=false`; verificar que el
      build termina sin errores y que pasan los tests de la 1.1 más los nuevos; ejecutar
      `npx prettier --check` sobre los archivos creados o modificados (los de `core/`, `shared/`,
      `features/`, `app.config.*` y `app.routes.*`; `app.ts`, `app.spec.ts` ya tenían formato
      distinto y no se tocan); verificar con `grep -rn "features/" src/app/core src/app/shared`
      que `core/` y `shared/` no importan de `features/` (cero líneas); verificar con
      `git status` que `package.json` y `package-lock.json` no cambiaron; y ejecutar
      `openspec validate auth-frontend --strict`.
- [x] 8.2 Prueba de punta a punta con PostgreSQL corriendo: levantar el backend
      (`.\mvnw.cmd spring-boot:run` con JDK 21) y `npx ng serve`; con `curl` a través del proxy
      (`http://localhost:4200/api/v1`) verificar que un registro responde 201 con `token`, que
      `GET /usuarios/yo` con el token responde 200 y sin él 401; después, en un navegador (con las
      herramientas de Chrome si están disponibles; si no, pidiéndoselo a la persona), registrar un
      usuario con teléfono y moneda `USD`, ver `Hola, {nombre}`, recargar la página y seguir con
      sesión, cerrar sesión y llegar a `/login`, comprobar que un login con contraseña incorrecta
      muestra `Email o contraseña incorrectos`, que `/registro` con sesión redirige a `/` y que
      un token inválido guardado a mano en `localStorage` lleva a `/login` al cargar `/`; borrar
      el usuario de prueba y detener ambos servidores.
