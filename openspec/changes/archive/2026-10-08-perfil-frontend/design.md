# Design

## Context

Estado observado en el código (motivación en proposal.md):

- **La cabecera real vive en `LayoutPresupuestoPage`** (`features/presupuestos/pages/
  layout-presupuesto.page.html`), que proyecta sus acciones dentro de `<app-cabecera>`. El
  componente compartido `CabeceraComponent` solo pone la marca `Cada Peso` y un `ng-content`.
- **El engranaje no abre ajustes de idioma ni de formato**: su etiqueta es `Gestionar
  presupuestos` y abre un `mat-menu` con `Nuevo presupuesto` y `Renombrar presupuesto`. No existe
  ninguna pantalla de idioma, formato ni moneda: la región se detecta sola con `regionUsuario()`.
  Es el caso (b) del pedido, no el (a).
- **El menú lateral se arma solo** con las rutas hijas que declaran `data: seccion(...)`; quien
  agrega un enlace edita `app.routes.ts`, no el layout.
- **Cerrar sesión** hoy es `SesionService.cerrar()` (borra almacenamiento y token y vacía el
  presupuesto activo) más `router.navigateByUrl('/login')`, en `LayoutPresupuestoPage
  .cerrarSesion()` y en el interceptor. No hay función compartida para el par.
- **El interceptor** cierra la sesión **solo ante un 401** de `/api/v1` que no sea
  `/api/v1/auth/`. `POST /api/v1/usuarios/yo/contrasena` no es de `/auth/`: un 401 real la cierra
  (correcto) y un 422 no.
- **`InicioPage`** llama a `GET /usuarios/yo` cada vez que se crea (no hay caché): el saludo se
  actualiza al volver a abrirla.
- **El campo de contraseña** (`app-campo-contrasena`, con mostrar/ocultar y `autocomplete`) está
  en `features/auth/components/` y las reglas de contraseña y nombre del registro están escritas
  dentro de `registro.page.ts`. AGENTS.md prohíbe que una feature importe de otra.
- **Compañero en paralelo**: `conciliacion-frontend` agrega una ruta hija en `app.routes.ts`
  justo después de `cuentas` y toca `cuentas.page.*` y `transacciones.page.*`; `metas-frontend` no
  declara archivos compartidos. Ninguno toca el layout, `core/` ni `shared/`.
- **El backend** (`perfil-usuario`) existe solo en el commit local `8135e83`, no en `origin`.

## Goals / Non-Goals

**Goals:**
- Pantalla de perfil con cambio de nombre y de contraseña, reutilizando lo que ya existe.
- Tocar el mínimo de archivos compartidos, todos listados abajo.
- Nada de contraseñas fuera del formulario, de logs ni de consola.

**Non-Goals:**
- Cambiar correo o moneda, eliminar cuenta, recuperar contraseña, invalidar tokens, límite de
  intentos, foto de perfil, elegir idioma o formato.
- Cambiar `AuthService`, `SesionService`, el interceptor o los guards.

## Decisions

### 1. Punto de entrada: botón nuevo junto al engranaje (opción b)
Un `<a mat-icon-button routerLink="perfil" aria-label="Mi perfil">` con `account_circle`,
entre el engranaje y `Cerrar sesión`. **Por qué no en el menú del engranaje**: ese menú es de
gestión de presupuestos (y su botón se llama así); mezclar el perfil lo vuelve ambiguo para un
lector de pantalla. **Por qué ícono y no el nombre**: mostrar el nombre obliga a cargar el
perfil en el layout (cambio en su `.ts`, otra dependencia y otro estado). `RouterLink` y
`MatIconButton` ya están importados en el layout, así que el cambio es solo `.html` (+ su
spec). Riesgo de ancho en teléfonos: ver Riesgos.

### 2. Ruta hija del layout, sin `seccion`, al final
`{ path: 'perfil', loadComponent: ... }` después de `beneficiarios`, sin `data: seccion(...)`
(no entra al menú). Queda lejos del punto donde `conciliacion-frontend` inserta su ruta (tras
`cuentas`), así que git fusiona ambas sin conflicto. **Consecuencia**: el perfil solo se alcanza
dentro de un presupuesto; una persona sin presupuestos (que ve `/`) aún no lo tiene. Alternativa
rechazada: ruta de primer nivel `/perfil`: perdería la cabecera, el presupuesto activo y el
cierre de sesión, y obligaría a una segunda cabecera.

### 3. Reutilizar subiendo a `shared/` (sin copias)
Una feature no puede importar de otra, así que `features/perfil` no puede usar
`features/auth/components/campo-contrasena`. En vez de duplicarlo:
- **Mover** `CampoContrasenaComponent` (ts, html, scss, spec) a `shared/campo-contrasena/`. Su
  único consumo previo es `login.page.ts` y `registro.page.ts`: solo cambia su ruta de importación.
  Gana una entrada opcional `mensajesExtra: MensajesDeError` (se mezcla con los suyos) para que
  la confirmación diga `Las contraseñas no coinciden` y la nueva `La contraseña nueva debe ser
  distinta de la actual` sin meter textos de perfil en el componente.
- **Extraer** de `registro.page.ts` a `shared/validacion/` los validadores de contraseña nueva
  (`sobreTextoRecortado(required)`, `minLength(8)`, `maxBytesUtf8(72)`) y de nombre
  (`required`, `minLength(2)`, `maxLength(100)` sobre texto recortado) con los mensajes del
  nombre. `RegistroPage` los usa en lugar de sus copias y no cambia su comportamiento (sus
  specs actuales son la red). El de nombre se extrae también para que la regla 2 a 100 no
  quede escrita en dos sitios.
- Alternativa rechazada: dejar el campo en `auth` y copiarlo en `perfil` (versión paralela).

### 4. Servicio y modelos propios en `features/perfil`
`PerfilService` (`obtener`, `cambiarNombre`, `cambiarContrasena`) y sus modelos. Se duplica el
modelo de lectura de `features/inicio` (siete campos): es la duplicación pequeña y a propósito
que AGENTS.md prevé entre features. **El nombre en Inicio no necesita estado compartido**:
Inicio vuelve a consultar al abrirse. Por eso `core/` no se toca.

### 5. Cierre de sesión tras el 204, sin duplicar lógica
Tras el `204`, en este orden: `sesion.cerrar()`, `router.navigateByUrl('/login')` y, **solo si
la navegación resuelve en `true`**, `snackBar.open('Contraseña cambiada, inicia sesión de
nuevo', 'Cerrar', { duration: 8000 })`. Se reutiliza `SesionService.cerrar()` (toda la lógica de
borrar sesión y presupuesto activo); la navegación es la misma de una línea del layout y del
interceptor, y no se crea un ayudante nuevo para no tocar `core/`.
**Por qué el aviso va al final**: así solo se muestra si la persona llegó de verdad a `/login`
(si la navegación falla o se cancela, no se le dice "inicia sesión de nuevo" estando en otra
pantalla) y un lector de pantalla lo anuncia ya en la pantalla nueva. **No** es por miedo a que el
aviso muera con el formulario: `MatSnackBar` es un servicio de raíz (nadie lo vuelve a proveer en
un componente) y se dibuja en el overlay global, así que destruir el formulario o el perfil no
lo cierra. Tampoco hace falta tocar la pantalla de login ni pasar parámetros por la URL.
Limitación documentada: el aviso desaparece a los 8 s y no se repite al recargar. Alternativa
rechazada: leerlo en `LoginPage` por `state` o `queryParam` (toca otro archivo compartido para
un aviso que se pierde igual al refrescar).
**Limitación del servidor**: el token viejo sigue válido hasta 24 h; este cierre es solo local.

### 6. Errores por `codigo`, nunca por `detail`
| Respuesta del cambio de contraseña | Resultado |
|---|---|
| `204` | Decisión 5. |
| `422` `REGLA_NEGOCIO_VIOLADA` | Error `servidor` en `contrasenaActual`: `La contraseña actual es incorrecta`; se vacía ese campo, se conservan nueva y confirmación, foco en la actual. No cierra sesión. |
| `400` `DATOS_INVALIDOS` con `errores` | `aplicarErroresDeCampos` en `contrasenaActual` / `contrasenaNueva`; foco en el primer campo con error. Claves sin campo: aviso genérico. |
| `401` | Lo maneja el interceptor (sesión cerrada y `/login`); la pantalla no avisa. |
| Otro código, sin cuerpo o red | Aviso genérico; todo lo escrito se conserva. |

`REGLA_NEGOCIO_VIOLADA` cubre dos casos del backend (actual incorrecta y nueva igual a la
actual) con el mismo código. Como no se lee `detail`, el frontend **impide el segundo antes de
enviar** (validador `distinta de la actual`, igual de exacto que el `equals` del backend), de
modo que un 422 solo puede ser la actual incorrecta. Para el nombre: `DATOS_INVALIDOS`
`errores.nombre` en el campo, el resto genérico. Verificado en el interceptor: ningún camino de
este change dispara el cierre salvo el `204` y un 401 real.

### 7. Contraseñas: solo en el formulario
Los tres controles de contraseña viven solo en el `FormGroup` del componente (nunca en una señal,
servicio ni almacenamiento); el cuerpo de la petición se arma en el momento del envío y se
descarta. Se vacían tras el éxito (`reset`) y el campo actual tras su error. Sin `console.*`.
`autocomplete`: `current-password`, `new-password`, `new-password`. Un campo oculto de solo
lectura con el correo (`autocomplete="username"`, `aria-hidden`, `tabindex="-1"`) permite que
los gestores de contraseñas asocien la nueva contraseña a la cuenta correcta.

### 8. Nombre de la moneda
`nombreDeMoneda(codigo)` en `shared/formato/nombre-moneda.ts`: `Intl.DisplayNames([
regionUsuario()], { type: 'currency' })`, primera letra en mayúscula, resultado `CODIGO · Nombre`.
Si `of()` lanza (`RangeError`) o devuelve el mismo código, o el navegador no tiene
`DisplayNames`, devuelve solo el código. Es una función pura, sin pipe nuevo.

### 9. Formularios (componentes de presentación)
- `FormularioNombreComponent`: entrada `nombre` actual; salida `guardado(perfil)`. Valida con las
  reglas compartidas; `mat-hint` `n/100` con el largo recortado; botón deshabilitado si inválido,
  igual al actual (recortado) o enviando; `PUT`; aviso `Nombre actualizado`.
- `FormularioContrasenaComponent`: tres `app-campo-contrasena`; validadores de grupo en
  `cambio-contrasena.validator.ts` (`coincideCon`, `distintaDe`) con revalidación cruzada al
  cambiar el campo vecino; botón deshabilitado si inválido o enviando.
- `DatosPerfilComponent`: lista de definición (`<dl>`) de solo lectura.
- `PerfilPage`: carga, estados (`cargando` / `listo` / `error` con `Reintentar` y `role="alert"`),
  tarjeta única en una columna (`max-width` 560 px) para pantallas estrechas.
- Foco: tras un rechazo del servidor, `host.querySelector('input.ng-invalid')?.focus()`; los
  errores van en `mat-error` (asociados al campo por Material) y los avisos en `MatSnackBar`.
- Accesibilidad extra: título `h1` `Mi perfil`; el contador no es el único indicador de límite
  (el mensaje de error repite la regla).

## Tablas de archivos

Rutas desde `frontend/src/app/`. Cada archivo nuevo lleva su `*.spec.ts` al lado.

### Archivos nuevos o movidos

| Archivo | Test |
|---|---|
| `features/perfil/pages/perfil.page.ts` (+ `.html`, `.scss`) | `perfil.page.spec.ts` |
| `features/perfil/components/formulario-nombre.component.ts` (+ `.html`, `.scss`) | `formulario-nombre.component.spec.ts` |
| `features/perfil/components/formulario-contrasena.component.ts` (+ `.html`, `.scss`) | `formulario-contrasena.component.spec.ts` |
| `features/perfil/components/datos-perfil.component.ts` (+ `.html`, `.scss`) | `datos-perfil.component.spec.ts` |
| `features/perfil/services/perfil.service.ts` | `perfil.service.spec.ts` |
| `features/perfil/services/cambio-contrasena.validator.ts` | `cambio-contrasena.validator.spec.ts` |
| `features/perfil/models/perfil-response.model.ts` | (interfaz, sin test) |
| `features/perfil/models/cambiar-nombre-request.model.ts` | (interfaz, sin test) |
| `features/perfil/models/cambiar-contrasena-request.model.ts` | (interfaz, sin test) |
| `shared/campo-contrasena/campo-contrasena.component.ts` (+ `.html`, `.scss`), **movido** desde `features/auth/components/` | `campo-contrasena.component.spec.ts` (movido, más `mensajesExtra`) |
| `shared/validacion/contrasena-nueva.validator.ts` | `contrasena-nueva.validator.spec.ts` |
| `shared/validacion/nombre-persona.validator.ts` | `nombre-persona.validator.spec.ts` |
| `shared/formato/nombre-moneda.ts` | `nombre-moneda.spec.ts` |

### Archivos existentes que se tocan (la lista completa)

| Archivo | Cambio | Riesgo con el compañero |
|---|---|---|
| `app.routes.ts` | Una ruta hija `perfil` al final de `children`. | Bajo: él inserta tras `cuentas`. |
| `app.routes.spec.ts` | Un test de la ruta `perfil`, entre los de `beneficiarios` y `transacciones`. | Bajo: es el archivo donde él agrega tests de rutas; se fusiona salvo que toque ese mismo punto. |
| `features/presupuestos/pages/layout-presupuesto.page.html` | Un botón `Mi perfil`. | Nulo: ninguno de sus changes lo toca. |
| `features/presupuestos/pages/layout-presupuesto.page.spec.ts` | Un test del acceso. | Nulo. |
| `features/auth/pages/registro.page.ts` | Importa el campo movido y usa las reglas compartidas. | Nulo. |
| `features/auth/pages/login.page.ts` | Solo la ruta de importación del campo. | Nulo. |
| `features/inicio/pages/inicio.page.spec.ts` | Un test: consulta el nombre en cada apertura. | Nulo. |
| `AGENTS.md` | Árbol de `shared/` y `features/`, sección de perfil. | Posible: él también edita `AGENTS.md`; se resuelve a mano. |

**No se tocan**: `AuthService`, `SesionService`, el interceptor, los guards,
`PresupuestoActivoService`, el menú lateral (`secciones`), el selector de presupuesto, el
`.ts` del layout, `CabeceraComponent` y `login.page.html` / `registro.page.html` (usan el
mismo selector `app-campo-contrasena`). Si el apply descubre que hace falta tocar
`AuthService` o `SesionService`, se detiene y se avisa.

## Estrategia de pruebas

Vitest + `TestBed` + `provideHttpClientTesting`, como `login.page.spec.ts`: se simulan
`Router.navigateByUrl` y `MatSnackBar.open`, se escribe en los inputs y se verifican petición,
cuerpo y mensajes. Cada escenario del spec es al menos un test; además:
- el interceptor real (`provideHttpClient(withInterceptors([sesionInterceptor]))`) en la prueba
  de `PerfilPage` o del formulario de contraseña, para probar que el 422 no cierra la sesión y el
  401 sí;
- `console` espiada (`vi.spyOn`) para el escenario de no registrar contraseñas;
- relojes/regiones fijados para `nombre-moneda` (`es-BO`, `en-US`) como en `region-usuario.spec.ts`.
La verificación visual en 360 px y con lector de pantalla se hace a mano al final.

## Risks / Trade-offs

- **[Backend sin publicar]** Los endpoints están en un commit local. → Publicar el backend antes
  de integrar; el frontend se prueba con `HttpTestingController` sin él.
- **[Ancho de la cabecera en teléfonos]** `.acciones` no hace `wrap`: menú + selector de 200 px +
  engranaje + `Mi perfil` + `Cerrar sesión` puede desbordar a 360 px. → Verificarlo en el apply;
  si desborda, ajuste mínimo en `layout-presupuesto.page.scss` (se agrega a la lista de archivos
  tocados y se avisa).
- **[Aviso de cambio de contraseña efímero]** El snackbar dura 8 s. → Aceptado (decisión 5).
- **[422 de dos causas]** Si el backend añade otra `REGLA_NEGOCIO_VIOLADA` en este endpoint, el
  mensaje sería engañoso. → La spec del backend define solo dos; la segunda se previene antes.
- **[Refactor de registro]** Toca la pantalla de registro. → Sus specs actuales pasan sin
  cambios de aserciones y se corren antes y después, como en el change de backend.
- **[Persona sin presupuestos sin perfil]** Solo se llega desde el layout. → Aceptado (decisión 2).

## Migration Plan

Sin datos ni configuración nuevos. Reversión: revertir el commit; la sesión y los endpoints no
cambian.
