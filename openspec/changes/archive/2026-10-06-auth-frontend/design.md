# Design

## Context

Ver proposal.md - Why. Estado actual observado en `/frontend`:

- Angular 21 **zoneless** (no hay `zone.js` ni `polyfills`), componentes standalone, TypeScript
  estricto con `noPropertyAccessFromIndexSignature` (los mapas se leen con `valor['clave']`),
  `strictTemplates`, Prettier (`printWidth 100`, `singleQuote`) y tests con Vitest + jsdom.
- `app.config.ts` solo tiene `provideBrowserGlobalErrorListeners()`, `provideRouter(routes)` y
  `...proveerMaterial()`; **no provee `HttpClient`**. `app.routes.ts` exporta `routes = []`.
  `app.html` es solo `<router-outlet />`.
- Disponible de `angular-material`: `proveerMaterial()` (`AdaptadorFechaRegional`,
  textos en español, `mat-icon` con Material Symbols), `aFechaNegocio()` en `shared/fecha/` y
  `regionUsuario()` en `shared/formato/`. `core/api-base-url.ts` exporta `urlBaseApi`
  (`'/api/v1'`, relativa; `proxy.conf.json` reenvía `/api` a `localhost:8080`).
- El contrato del backend (`auth-backend`) que se consume: `POST /auth/registro` y
  `POST /auth/login` devuelven `{ token, tipo, expiraEn }`; `GET /usuarios/yo` devuelve
  `{ email, rol, nombre, apellido, fechaNacimiento, telefono, monedaPredeterminada }`; los
  errores son `ProblemDetail` con `status`, `detail`, `codigo` y, en `DATOS_INVALIDOS` de campos,
  el mapa `errores` (clave = nombre del campo). Códigos usados: `DATOS_INVALIDOS`,
  `CREDENCIALES_INVALIDAS`, `EMAIL_YA_REGISTRADO` y `NO_AUTENTICADO`.
- Verificado en el código instalado: el validador `Validators.email` de Angular limita el total a
  254 caracteres y la parte local a 64; un texto inválido en el datepicker deja el valor del
  control en `null` y marca a la vez `matDatepickerParse` y `required`; `Validators.minLength` y
  `maxLength` ignoran el valor vacío.

## Goals / Non-Goals

**Goals:**
- Primera feature real del frontend, siguiendo la estructura obligatoria de `AGENTS.md` y
  Angular Material como única librería de UI, sin dependencias nuevas.
- Dejar en `core/` y `shared/` las piezas que reutilizarán todas las features (sesión,
  interceptor, guards, validadores, lectura de errores del API, cabecera).
- Que `core/` y `shared/` no importen nada de `features/`.

**Non-Goals:**
- Recuperar contraseña, editar perfil, rutas de presupuesto o cuentas, refresh token y tema
  oscuro.
- Sincronizar la sesión entre pestañas (evento `storage`) ni avisar con un mensaje cuando la
  sesión expira.
- Guardar la URL a la que se quería entrar para volver tras el login.
- Cambios en el backend, en `config.yaml` o en las dependencias (no se ejecuta `npm install`).

## Decisions

**Sesión: `SesionService` en `core/sesion/`, con señales y `localStorage`.**
`@Injectable({ providedIn: 'root' })`. Guarda un solo valor JSON `{ token, expiraEn }` bajo la
clave `cada-peso.sesion` (una sola clave para que el token y su expiración nunca queden
desincronizados). Al construirse lee ese valor: si no existe, no es JSON, no tiene los dos textos
o `Date.parse(expiraEn)` es `NaN` o ya pasó (`<= Date.now()`), borra la clave y arranca sin
sesión. API pública: `sesion` (señal de solo lectura con `{ token, expiraEn } | null`),
`haySesion` (señal calculada), `token()`, `iniciar(token, expiraEn)` y `cerrar()`. Toda lectura y
escritura de `localStorage` va en `try/catch`: si falla (modo privado, almacenamiento bloqueado),
la sesión vive solo en la señal mientras la página siga abierta. La expiración se comprueba al
arrancar (lo pedido) y, durante el uso, el 401 del interceptor cubre un token que vence con la
app abierta. Alternativa descartada: cookie `httpOnly`, que protegería mejor del XSS pero exige
que el backend la emita (hoy devuelve el token en el cuerpo); queda anotado como riesgo.

**Interceptor y guards funcionales en `core/auth/`, sobre `SesionService`.**
- `sesionInterceptor` (`HttpInterceptorFn`, `core/auth/sesion.interceptor.ts`): si la URL es
  `urlBaseApi` o empieza por `urlBaseApi + '/'` y hay token, clona la petición con
  `Authorization: Bearer <token>`. Si la petición falla con `HttpErrorResponse` 401, es a
  `urlBaseApi` y **no** a `urlBaseApi + '/auth/'`, llama a `sesion.cerrar()` y
  `router.navigateByUrl('/login')`; siempre relanza el error. Se excluye todo `/auth/` (no solo
  `/auth/login`) porque ahí un 401 nunca significa sesión vencida; el registro no devuelve 401,
  así que en la práctica solo importa el login.
- `sesionGuard` (`CanActivateFn`): `true` con sesión; si no, `UrlTree` a `/login`.
- `invitadoGuard` (`CanActivateFn`): `true` sin sesión; si no, `UrlTree` a `/`.
- `app.config.ts` agrega `provideHttpClient(withInterceptors([sesionInterceptor]))`.
- Ninguna de estas piezas importa `AuthService` (viviría en `features/`): solo `SesionService`.

**Validadores de formulario compartidos en `shared/validacion/`.**
Viven en `shared/` (no en `features/auth/`) porque cualquier formulario futuro los necesita y la
estructura obligatoria no tiene una carpeta de validadores dentro de una feature. Sufijo
`.validator.ts` (se agrega a `AGENTS.md`).
- `sobreTextoRecortado(validador)`: aplica un `ValidatorFn` de Angular al valor **recortado**
  (`trim()`) y no modifica el control. Con él se reutilizan los validadores nativos y sus claves de
  error: `sobreTextoRecortado(Validators.required)` rechaza un texto de solo espacios (como
  `@NotBlank`), y `sobreTextoRecortado(Validators.email | minLength(2) | maxLength(100))` miden
  después de recortar. Internamente evalúa el validador sobre un `FormControl` temporal con el
  valor recortado. Los errores conservan sus claves: `required`, `email`, `minlength`,
  `maxlength`.
- `maxBytesUtf8(maximo)`: error `{ maxBytesUtf8: { maximo, actual } }` si
  `new TextEncoder().encode(valor).length > maximo`; vacío o `null` es válido.
- `mayorDeEdad(edadMinima = 18)`: recibe el `Date | null` del datepicker y devuelve
  `{ mayorDeEdad: { edadMinima } }` si no cumple. Calcula con la fecha local de hoy
  (`new Date()`): `edad = anioHoy - anioNacimiento`, menos 1 si `(mesHoy, diaHoy)` es menor que
  `(mesNacimiento, diaNacimiento)`. Da el mismo resultado que `Period.between(...).getYears()` del
  backend, incluido el 29 de febrero (verificado: el 28-02-2026 una persona nacida el 29-02-2008
  tiene 17 y el 01-03-2026 tiene 18). Una fecha futura da una edad menor que 18 y se rechaza.
  `null` o fecha inválida es válido (de eso se encargan `required` y `matDatepickerParse`).
- `trim()` de JavaScript y `strip()` de Java difieren en caracteres raros (p. ej. el espacio de
  no separación); da igual porque el frontend envía el valor ya recortado y el backend lo
  recorta de nuevo sin cambiarlo.

**Errores del API: `shared/api/problema-api.ts` y `shared/formulario/errores-formulario.ts`.**
- `leerProblemaApi(error)` devuelve `{ status, codigo?, detail?, errores? } | null` (`null` si no
  es un `HttpErrorResponse`); un fallo de red (`status 0`) o un cuerpo sin forma de `ProblemDetail`
  da un problema sin `codigo`. Exporta las constantes de códigos usadas y
  `MENSAJE_ERROR_GENERICO = 'No pudimos completar la operación. Inténtalo de nuevo.'`.
  Se decide siempre por `codigo`, nunca por `detail`.
- `mensajeDeError(errores, mensajes)` devuelve el primer mensaje cuya clave está en los errores del
  control, **en el orden del mapa `mensajes`** (no el del objeto de errores); un mensaje puede ser
  un texto o una función que recibe el valor del error (p. ej. para `minlength`). La clave
  `servidor` (el valor del error es el texto) va siempre primero.
- `aplicarErroresDeCampos(formulario, errores)` pone en cada control con ese nombre el error
  `{ servidor: mensaje }`, lo marca como tocado y devuelve las claves que no corresponden a ningún
  control. Un error manual se borra solo cuando el control revalida al editarse (Angular recalcula
  los errores), así que no hace falta limpiarlo a mano. Mientras ese error exista, el formulario
  es inválido y el botón de enviar queda deshabilitado, como pide el spec. Los nombres de los
  controles de los formularios son los mismos que los campos del backend.
- Los mensajes de `errores` del backend se muestran tal como llegan (decisión pedida); los de las
  reglas estándar de Jakarta Validation pueden venir en el idioma de la JVM, pero solo se ven si
  una regla del backend falla sin que la del frontend lo haya impedido (ver riesgos).
- Zoneless: un error manual puesto desde un `subscribe` no marca la vista por sí solo; cada
  página lo acompaña de un cambio de la señal `enviando`, que sí la marca.

**Modelos que reflejan los DTO, en `models/` de cada feature.**
Interfaces TypeScript con los mismos nombres de campo y de tipo que los DTO del backend:
`LoginRequest`, `RegistroRequest` (con `telefono` y `monedaPredeterminada` opcionales),
`TokenResponse` y `UsuarioActualResponse`. Un archivo por interfaz, sufijo `.model.ts`.

**`AuthService` en `features/auth/services/`.**
`registrar(solicitud)` hace `POST ${urlBaseApi}/auth/registro` e `iniciarSesion(solicitud)` hace
`POST ${urlBaseApi}/auth/login`; ambos devuelven `Observable<TokenResponse>` y con `tap` guardan
la sesión (`sesion.iniciar(token, expiraEn)`). Cerrar la sesión no pasa por aquí: la página de
inicio usa `SesionService` de `core/`, porque una feature no importa de otra.

**Componente `campo-contrasena` en `features/auth/components/`.**
`app-campo-contrasena`, `OnPush`, con las entradas `control` (`FormControl<string>`, obligatoria),
`etiqueta` (por defecto `Contraseña`) y `autocompletar` (`current-password` o `new-password`). Su
plantilla es un `mat-form-field` con `matInput` cuyo `type` alterna entre `password` y `text`
según la señal `visible`, y un `button mat-icon-button matSuffix type="button"` con
`<mat-icon>visibility</mat-icon>` u `<mat-icon>visibility_off</mat-icon>`, `aria-label`
`Mostrar contraseña` / `Ocultar contraseña` y `aria-pressed`. El `mat-error` usa
`mensajeDeError` con los mensajes `servidor`, `required`, `minlength` y `maxBytesUtf8`: en el
login el control solo tiene `required`, así que ninguna regla de longitud se muestra. Recibe el
control por entrada y no implementa `ControlValueAccessor`, para no duplicar el valor.

**Páginas.**
- Ambas páginas de auth: `app-cabecera` y, debajo, un `mat-card` centrado (ancho máximo de
  480 px), formulario con `novalidate`, `ChangeDetectionStrategy.OnPush` y estilos solo con
  tokens `--mat-sys-*`. Señal `enviando`. El botón de enviar tiene
  `[disabled]="formulario.invalid || enviando()"`. Los controles son
  `FormControl<string>` con `nonNullable: true` (la fecha, `FormControl<Date | null>`).
- `login.page.ts` (`/login`): `email` con `sobreTextoRecortado(Validators.required)` y
  `contrasena` con `sobreTextoRecortado(Validators.required)` (no se recorta, solo se rechaza un
  valor vacío o de solo espacios). Envía `{ email: email.trim().toLowerCase(), contrasena }`. En
  `CREDENCIALES_INVALIDAS` guarda el texto `Email o contraseña incorrectos` en la señal
  `errorGeneral`, que se muestra en un `<p role="alert">` con el color de error; se borra al
  volver a enviar. Con `DATOS_INVALIDOS` aplica `aplicarErroresDeCampos`; con cualquier otro
  error, aviso genérico. Éxito: `router.navigateByUrl('/')`. Enlace `¿No tienes cuenta?
  Regístrate` con `routerLink`.
- `registro.page.ts` (`/registro`), con estas reglas:

| Control                | Validadores (sobre el texto recortado salvo indicación)         |
|------------------------|------------------------------------------------------------------|
| `email`                | `required`, `maxlength(254)`, `email`                            |
| `contrasena`           | `required` (recortado); `minLength(8)` y `maxBytesUtf8(72)` sin tocar |
| `nombre`, `apellido`   | `required`, `minlength(2)`, `maxlength(100)`                     |
| `fechaNacimiento`      | `Validators.required` y `mayorDeEdad()` (sobre el `Date`)        |
| `telefono`             | `maxlength(20)`                                                  |
| `monedaPredeterminada` | `Validators.required`, valor inicial `BOB`                       |

  Mensajes (en este orden de prioridad por control): email → `servidor`, `required` (`El email
  es obligatorio`), `maxlength` (`El email no puede superar 254 caracteres`; va antes que `email`
  porque un texto de más de 254 también falla el formato), `email` (`Ingresa un email válido`);
  contraseña → `servidor`, `required` (`La contraseña es obligatoria`), `minlength` (`La
  contraseña debe tener al menos 8 caracteres`), `maxBytesUtf8` (`La contraseña no puede ocupar
  más de 72 bytes (la ñ y las vocales con tilde ocupan 2)`); nombre y apellido → `servidor`,
  `required` (`El nombre es obligatorio` / `El apellido es obligatorio`), `minlength` (`El nombre
  debe tener al menos 2 caracteres`), `maxlength` (`El nombre no puede superar 100
  caracteres`); fecha → `servidor`, `matDatepickerParse` (`La fecha no es válida`; antes que
  `required` porque un texto inválido deja el valor en `null` y marca ambos), `required` (`La
  fecha de nacimiento es obligatoria`), `mayorDeEdad` (`Debe tener 18 años o más`); teléfono →
  `servidor`, `maxlength` (`El teléfono no puede superar 20 caracteres`).
  La fecha usa `mat-datepicker` con `startView="multi-year"` (para llegar rápido a un año de
  nacimiento) y `mat-datepicker-toggle`; la moneda usa `mat-select` con `BOB` (Boliviano), `USD`,
  `EUR`, `ARS`, `BRL`, `CLP` y `PEN`, definidas como constante de la página y todas válidas para
  `@MonedaValida`. La petición se arma así: `email` recortado y en minúsculas; `contrasena`
  tal cual; `nombre` y `apellido` recortados; `fechaNacimiento: aFechaNegocio(fecha)`;
  `telefono` recortado y **omitido** si queda vacío; `monedaPredeterminada` tal cual. Errores:
  `DATOS_INVALIDOS` con `errores` → `aplicarErroresDeCampos` (las claves sin campo, o un
  `DATOS_INVALIDOS` sin `errores`, dan el aviso genérico); `EMAIL_YA_REGISTRADO` →
  `aplicarErroresDeCampos` con `{ email: 'Ya existe una cuenta con ese email' }`; el resto, aviso
  genérico. Todo error devuelve `enviando` a `false`. Enlace `¿Ya tienes cuenta? Inicia sesión`.
- `inicio.page.ts` (`/`): `UsuarioActualService.obtener()` (`GET ${urlBaseApi}/usuarios/yo`) en
  la inicialización, con `takeUntilDestroyed`. Señales `usuario`, `cargando` y `errorCarga`.
  Mientras carga muestra `mat-progress-spinner` indeterminado; luego `Hola, {{ nombre }}`. Un
  botón `Cerrar sesión` (proyectado en la cabecera) llama a `sesion.cerrar()` y
  `router.navigateByUrl('/login')`. Si falla con un estado distinto de 401 muestra el aviso
  genérico y el texto `No pudimos cargar tus datos.`; con 401 no hace nada (el interceptor ya
  cerró la sesión y redirigió).

**Cabecera compartida en `shared/cabecera/`.**
`app-cabecera`: `mat-toolbar` con el texto `Cada Peso` (estilo del token `--mat-sys-primary`),
un espaciador y `<ng-content />` para acciones (el botón de cerrar sesión del inicio). Está en
`shared/` porque la usan dos features.

**Rutas con carga perezosa.**
`app.routes.ts`: `''` (`pathMatch: 'full'`, `sesionGuard`, `InicioPage`), `login` y `registro`
(`invitadoGuard`, `LoginPage` y `RegistroPage`), todas con `loadComponent: () => import(...)`, y
`**` con `redirectTo: ''` (sin sesión, el guard del inicio lo lleva a `/login`).

**Estrategia de tests.**
Vitest con `TestBed` zoneless (`await fixture.whenStable()` tras cada acción), arneses de
Material (`@angular/material/*/testing` y `@angular/cdk/testing`, ya instalados) donde simplifican
y `HttpTestingController` con `provideHttpClient()` + `provideHttpClientTesting()` para verificar
el cuerpo y los headers de cada petición. Los tests de página usan `proveerMaterial()` y fijan
`navigator.language` con `vi.stubGlobal`. Las fechas relativas a "hoy" usan
`vi.useFakeTimers({ toFake: ['Date'] })` (solo `Date`, para no frenar la estabilidad de
Angular) y los de zona horaria cambian `process.env['TZ']` por `describe`, restaurando
`America/New_York` al terminar (como `shared/fecha/fecha-negocio.spec.ts`). `app.routes.spec.ts`
usa `RouterTestingHarness` con las rutas reales; `app.config.spec.ts` comprueba con
`appConfig.providers` que una petición a `/api/v1` lleva el token y que un 401 cierra la sesión.

**`AGENTS.md`.**
Se agregan a los sufijos de archivo `*.guard.ts`, `*.interceptor.ts` y `*.validator.ts`; el
árbol de "Estructura obligatoria del frontend" se actualiza con `core/auth`, `core/sesion`,
`shared/{api,cabecera,formulario,validacion}`, `features/auth` y `features/inicio`; y una sección
nueva "Sesión y autenticación del frontend" fija las reglas: el token vive solo en
`SesionService`; `core/` y `shared/` no importan `features/`; la contraseña nunca se recorta;
email, nombre y apellido se envían recortados (el email en minúsculas); los errores del API se
interpretan por `codigo`; y las fechas del registro se envían con `aFechaNegocio()`.

### Archivos del change (rutas desde `src/app/`)

**`core/sesion/`** (nuevos)

| Archivo              | Test                      |
|----------------------|---------------------------|
| `sesion.service.ts`  | `sesion.service.spec.ts`  |

**`core/auth/`** (nuevos)

| Archivo                   | Test                           |
|---------------------------|--------------------------------|
| `sesion.interceptor.ts`   | `sesion.interceptor.spec.ts`   |
| `sesion.guard.ts`         | `sesion.guard.spec.ts`         |
| `invitado.guard.ts`       | `invitado.guard.spec.ts`       |

**`shared/validacion/`** (nuevos)

| Archivo                            | Test                                    |
|------------------------------------|-----------------------------------------|
| `sobre-texto-recortado.validator.ts` | `sobre-texto-recortado.validator.spec.ts` |
| `max-bytes-utf8.validator.ts`      | `max-bytes-utf8.validator.spec.ts`      |
| `mayor-de-edad.validator.ts`       | `mayor-de-edad.validator.spec.ts`       |

**`shared/api/`, `shared/formulario/` y `shared/cabecera/`** (nuevos)

| Archivo                                  | Test                         |
|------------------------------------------|------------------------------|
| `shared/api/problema-api.ts`             | `problema-api.spec.ts`       |
| `shared/formulario/errores-formulario.ts` | `errores-formulario.spec.ts` |
| `shared/cabecera/cabecera.component.{ts,html,scss}` | `cabecera.component.spec.ts` |

**`features/auth/models/` y `features/auth/services/`** (nuevos)

| Archivo                       | Test                                |
|-------------------------------|-------------------------------------|
| `models/login-request.model.ts`    | — (interfaz sin lógica)        |
| `models/registro-request.model.ts` | — (interfaz sin lógica)        |
| `models/token-response.model.ts`   | — (interfaz sin lógica)        |
| `services/auth.service.ts`         | `services/auth.service.spec.ts` |

**`features/auth/components/` y `features/auth/pages/`** (nuevos; `.*` son `.ts`, `.html`, `.scss`)

| Archivo                                  | Test                         |
|------------------------------------------|------------------------------|
| `components/campo-contrasena.component.*` | `campo-contrasena.component.spec.ts` |
| `pages/login.page.*`                     | `login.page.spec.ts`         |
| `pages/registro.page.*`                  | `registro.page.spec.ts`      |

Los tests de `components/` y `pages/` van en el mismo directorio que el archivo que prueban.

**`features/inicio/`** (nuevos)

| Archivo                                   | Test                                  |
|-------------------------------------------|---------------------------------------|
| `models/usuario-actual-response.model.ts` | — (interfaz sin lógica)               |
| `services/usuario-actual.service.ts`      | `services/usuario-actual.service.spec.ts` |
| `pages/inicio.page.{ts,html,scss}`        | `pages/inicio.page.spec.ts`           |

**Raíz de `src/app/`** (modificados y nuevos)

| Archivo                         | Test                  |
|---------------------------------|-----------------------|
| `app.config.ts` (modificado)    | `app.config.spec.ts` (nuevo) |
| `app.routes.ts` (modificado)    | `app.routes.spec.ts` (nuevo) |

## Risks / Trade-offs

- [Guardar el token en `localStorage` lo expone a un XSS] → Angular escapa las plantillas, no hay
  librerías de UI de terceros ni `innerHTML`, y el token vence a las 24 h; pasar a una cookie
  `httpOnly` exigiría un change de backend.
- [Los mensajes de `errores` del backend se muestran tal cual y los de reglas estándar de
  Jakarta Validation pueden estar en el idioma de la JVM] → El frontend replica las reglas, así
  que normalmente se detiene antes; solo aparecerían si el backend rechaza algo que el frontend
  aceptó (por ejemplo, un email que el `@Email` de Hibernate rechaza y el de Angular acepta). Si
  se vuelve frecuente, se traducen por clave de campo en un change aparte.
- ["Hoy" en el frontend es la fecha local del navegador y en el backend es la fecha UTC] → En el
  borde de un cumpleaños número 18 puede haber una diferencia de horas; el backend manda: su 400
  se muestra en el campo `fechaNacimiento`.
- [El validador `email` de Angular y `@Email` de Hibernate no son idénticos] → Cubierto por la
  regla anterior; el backend es la autoridad.
- [Con zoneless, un error puesto a mano desde un `subscribe` no refresca la vista] → Cada página
  cambia la señal `enviando` en el mismo `subscribe`, y un test verifica que el mensaje aparece.
- [La sesión no se sincroniza entre pestañas: cerrar sesión en una deja la otra con token
  borrado en el almacenamiento pero vigente en memoria] → La siguiente petición de la otra
  pestaña (que ya no lleva token en una recarga, o que da 401) la saca; sincronizarlas queda fuera
  de alcance.
- [Un 401 de `/auth/` no cierra sesión, así que un token vencido en el login no molesta] →
  Intencional (ver spec); el invitado nunca tiene sesión válida allí por el guard.

## Migration Plan

No aplica: no hay datos ni cambios de API. Rollback: revertir el commit. Los datos de sesión en
`localStorage` (`cada-peso.sesion`) quedan inertes si se revierte.
