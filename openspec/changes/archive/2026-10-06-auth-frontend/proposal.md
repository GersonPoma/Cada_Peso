# Proposal

## Why

El backend de autenticación ya está terminado (`auth-backend`: registro, login, token JWT y
`GET /api/v1/usuarios/yo`), pero el frontend todavía no tiene pantallas ni sesión: no provee
`HttpClient`, no tiene rutas ni forma de guardar el token. Sin esto no se puede usar la API ni
construir ninguna feature de negocio, porque todas dependen de que haya una persona con sesión.
Angular Material y la conversión de fechas (`angular-material`) ya están listos, así que ahora se
puede construir la primera feature real siguiendo la estructura obligatoria del frontend.

## What Changes

- **Sesión en `core/`**: `SesionService` guarda el token y su `expiraEn` en `localStorage`, expone
  el estado de sesión como señal y permite cerrarla; si el token guardado ya expiró al arrancar la
  app, se considera sin sesión y se limpia.
- **Interceptor y guards en `core/auth/`**: el interceptor agrega `Authorization: Bearer <token>`
  solo a las peticiones a `/api/v1` y, ante un 401 de cualquier petición autenticada (no de
  `/auth`), limpia la sesión y redirige a `/login`; un guard protege las rutas con sesión y otro
  deja pasar solo a invitados (con sesión redirige al inicio). Usan `SesionService`, nunca
  `AuthService`, porque `core/` no importa de `features/`.
- **Feature `auth`** (`features/auth/`): `AuthService` (registro y login, guarda la sesión),
  modelos que reflejan los DTO del backend, componente reutilizable de campo de contraseña con
  botón mostrar/ocultar, y las páginas `/login` y `/registro` (carga perezosa con
  `loadComponent`).
- **Feature `inicio`** (`features/inicio/`): página protegida en `/` que llama a
  `GET /api/v1/usuarios/yo`, muestra "Hola, {nombre}" y permite cerrar sesión.
- **Formularios reactivos tipados** con `mat-form-field`, `matInput` y `mat-error`, mensajes en
  español y las reglas del backend replicadas: email, contraseña (mínimo 8 caracteres y máximo
  72 bytes en UTF-8, sin recortar nunca), nombre y apellido (2 a 100, tras recortar), fecha de
  nacimiento (obligatoria y mayor de 18 años, enviada con `aFechaNegocio()`), teléfono opcional y
  moneda con `mat-select` (por defecto `BOB`). El botón de enviar se deshabilita mientras el
  formulario sea inválido o se esté enviando.
- **Errores del API por `codigo`**: `DATOS_INVALIDOS` en el campo correspondiente,
  `CREDENCIALES_INVALIDAS` como mensaje general del login, `EMAIL_YA_REGISTRADO` en el campo
  email y cualquier otro error con un `MatSnackBar` genérico.
- **Piezas compartidas en `shared/`** (las usarán las features futuras): validadores de
  formulario (`shared/validacion/`), lectura de errores del API y de formulario
  (`shared/api/`, `shared/formulario/`) y la cabecera con el nombre "Cada Peso"
  (`shared/cabecera/`, `mat-toolbar`).
- **`AGENTS.md`**: nuevos sufijos de archivo (`.guard.ts`, `.interceptor.ts`, `.validator.ts`),
  árbol del frontend actualizado y una sección sobre la sesión y la autenticación.

Sin cambios en el backend, sin dependencias nuevas (no se ejecuta `npm install`) y sin tocar
`openspec/config.yaml`.

## Capabilities

### New Capabilities

- `autenticacion-frontend`: sesión de la persona en el navegador (guardado, restauración y
  expiración), token en las peticiones, cierre de sesión ante un 401, rutas protegidas y para
  invitados, inicio de sesión, campo de contraseña con mostrar/ocultar, página de inicio,
  cabecera y manejo de errores del API por código.
- `registro-frontend`: formulario de registro con sus validaciones por campo, mayoría de edad,
  límite de 72 bytes de la contraseña, envío de la fecha sin desfase de zona horaria, datos
  enviados y errores del API en el registro.

### Modified Capabilities

Ninguna. `formato-regional` e `idioma-interfaz` no cambian: las nuevas specs se apoyan en ellas
(selector de fechas por región, conversión a `yyyy-MM-dd`, textos de Material en español) sin
contradecirlas.

## Impact

- **Frontend, `src/app/core/`**: `sesion/` y `auth/` nuevos.
- **Frontend, `src/app/shared/`**: `validacion/`, `api/`, `formulario/` y `cabecera/` nuevos.
- **Frontend, `src/app/features/`**: `auth/` e `inicio/` nuevos.
- **Frontend, raíz de `src/app/`**: `app.config.ts` (`provideHttpClient` con el interceptor) y
  `app.routes.ts` (rutas con carga perezosa y guards).
- **Documentación**: `AGENTS.md`.
- **Backend, dependencias y `config.yaml`**: ninguno.
