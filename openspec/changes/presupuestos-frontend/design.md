# Design

## Context

Ver proposal.md - Why. Estado actual observado en `/frontend`:

- Angular 21 zoneless, standalone + signals, `ChangeDetectionStrategy.OnPush` en todos los
  componentes, Vitest + jsdom (`src/test-setup.ts` fija `TZ`), arneses de Material ya usados en
  `registro.page.spec.ts`. `@angular/cdk` y `@angular/material` 21.2 están instalados, así que
  `BreakpointObserver`, `MatSidenav`, `MatDialog`, `MatSelect`, `MatMenu` y `MatList` se usan sin
  dependencias nuevas. En jsdom no existe `window.matchMedia`: el `MediaMatcher` del CDK cae en un
  matcher que nunca coincide (pantalla ancha), y los tests de pantalla estrecha sustituyen el
  `BreakpointObserver`.
- `app.routes.ts`: `''` (con `sesionGuard`) carga `InicioPage`; `login` y `registro` con
  `invitadoGuard`; `'**'` → `''`. `LoginPage` y `RegistroPage` navegan a `/` al terminar, e
  `invitadoGuard` redirige a `/`: por eso `/` sigue siendo la entrada única de la parte privada.
- `InicioPage` tiene su propia `<app-cabecera>` con el botón "Cerrar sesión" y pide
  `GET /usuarios/yo`. `CabeceraComponent` (`shared/cabecera/`) proyecta su contenido a la
  derecha de la marca `Cada Peso`.
- `SesionService.cerrar()` es el único punto por el que se cierra la sesión: lo llaman el botón
  de la página de inicio y `sesionInterceptor` ante un 401.
- `shared/` ya ofrece `leerProblemaApi`, `CODIGOS_API`, `MENSAJE_ERROR_GENERICO`,
  `mensajeDeError`, `aplicarErroresDeCampos` y `sobreTextoRecortado`.
- Contrato del backend (`presupuestos-backend`, archivado): ver proposal y la spec principal
  `presupuestos`.

## Goals / Non-Goals

**Goals:**
- Una sola fuente del presupuesto actual en `core/`, que las features futuras leen sin importar
  `features/presupuestos`.
- Que agregar una sección nueva (cuentas, categorías...) sea una sola línea en `app.routes.ts`,
  sin tocar el layout.
- Reutilizar las piezas de `shared/` y `core/` existentes; ninguna librería nueva.

**Non-Goals:**
- Cachear la lista de presupuestos entre páginas o pestañas.
- Recordar el último presupuesto usado (por ejemplo en `localStorage`): `/` siempre lleva al
  primero de la lista.
- Mostrar o editar la moneda del perfil.

## Decisions

**`PresupuestoActivoService` en `core/presupuesto-activo/`, con modelo propio.**
`@Injectable({ providedIn: 'root' })` con un `signal<PresupuestoActivo | null>` privado, expuesto
como `presupuesto` (`asReadonly()`), y los métodos `fijar(presupuesto: PresupuestoActivo)` y
`limpiar()`. `PresupuestoActivo` (`{ id: number; nombre: string; moneda: string }`) vive en
`core/presupuesto-activo/presupuesto-activo.model.ts`; el layout convierte su
`PresupuestoResponse` en ese modelo al fijarlo. Así `core/` no importa `features/` y ninguna
feature importa a otra. Alternativa descartada: leer el id de la URL en cada feature
(`paramMap` del padre), que no da la moneda y repite la lógica en cada página.

**Limpieza al cerrar sesión: `SesionService.cerrar()` llama a `PresupuestoActivoService.limpiar()`.**
Como el botón y el interceptor ya pasan por `cerrar()`, basta un punto. Ambos están en `core/`, y
`PresupuestoActivoService` no depende de `SesionService`, así que no hay ciclo. Alternativa
descartada: un `effect` en `PresupuestoActivoService` que observe `haySesion`, porque los efectos
se ejecutan de forma diferida y entre medio una pantalla podría leer el presupuesto anterior.

**La redirección vive en una página (`RedireccionPresupuestoPage`), no en un guard.**
La ruta `''` necesita además mostrar el estado sin presupuestos, el spinner y el error con
"Reintentar", y desde ahí abrir el diálogo de crear: es una pantalla, no solo una decisión. Con
lista no vacía navega con `router.navigate(['/presupuestos', primero.id], { replaceUrl: true })`.
La página incluye `<app-cabecera>` con "Cerrar sesión", porque una persona sin presupuestos no
tendría otra forma de salir. Un 401 no avisa (el interceptor ya cerró la sesión), igual que
`InicioPage`. El aviso de error usa
`snackBar.open(MENSAJE_ERROR_GENERICO, 'Reintentar')` y `onAction()` vuelve a cargar, y la
página muestra además un botón "Reintentar" fijo por si el aviso se cierra.

**El layout carga su propia lista y valida el id contra ella.**
`LayoutPresupuestoPage` pide `listar()` una vez al crearse y lee `:presupuestoId` de
`ActivatedRoute.paramMap` (el componente se reutiliza al cambiar solo el parámetro, así que no
basta un `snapshot`). Cada vez que hay lista y parámetro, busca el id: si está, llama a
`presupuestoActivo.fijar(...)`; si no está (no existe, es ajeno o no es un número), navega a `/`
con `replaceUrl`. No se consulta `GET /presupuestos/{id}` porque la lista ya hace falta para el
selector y el 404 queda cubierto al no encontrarlo. El `<router-outlet />` de las secciones solo
se pinta cuando el presupuesto activo coincide con el id de la URL, para que ninguna sección
futura arranque con el presupuesto anterior. Mientras carga muestra un spinner; un error de carga
se trata igual que en la redirección (aviso con "Reintentar").
Alternativa descartada: compartir la lista entre la redirección y el layout con un caché en el
servicio; ahorra una petición en la entrada pero añade invalidación, y la lista es pequeña.

**Selector, gestión y cierre de sesión en la cabecera.**
Dentro de `<app-cabecera>` (contenido proyectado): el botón de hamburguesa (solo en pantalla
estrecha), un `mat-select` con los presupuestos (`aria-label="Presupuesto"`) cuyo
`selectionChange` navega a `/presupuestos/{id}`, un `mat-icon-button` "Gestionar presupuestos"
(`aria-label`, ícono `settings`) que abre un `mat-menu` con "Nuevo presupuesto" y "Renombrar
presupuesto", y el `mat-button` "Cerrar sesión" (llama a `sesion.cerrar()` y navega a `/login`).
El menú agrupa las dos acciones para no saturar la cabecera en pantallas estrechas.

**Secciones del menú lateral declaradas en las rutas hijas.**
Cada ruta hija de `presupuestos/:presupuestoId` lleva `data: { seccion: { etiqueta, icono } }`.
El layout arma los enlaces de `mat-nav-list` leyendo `ActivatedRoute.routeConfig.children` y
quedándose con las que tienen `seccion` (enlace relativo con `routerLink` y
`routerLinkActive`). La interfaz `SeccionPresupuesto` vive en
`features/presupuestos/models/seccion-presupuesto.model.ts` y `app.routes.ts` la usa al tiparlas
(`app.routes.ts` no es `core/` ni `shared/`, así que puede importar de una feature). Hoy solo
existe `{ path: '', ..., data: { seccion: { etiqueta: 'Inicio', icono: 'home' } } }`; cuentas
agregará `{ path: 'cuentas', loadComponent: ..., data: { seccion: {...} } }` en una línea.
Alternativa descartada: una lista fija de enlaces en el layout, que obligaría a tocar dos
archivos por feature.

**Menú lateral adaptable con `BreakpointObserver`.**
`toSignal(breakpointObserver.observe([Breakpoints.XSmall, Breakpoints.Small]))` da `estrecha`
(menos de 960 px). Ancha: `mode="side"` y `opened`. Estrecha: `mode="over"`, cerrado, se abre con
el botón de hamburguesa (`aria-label="Abrir menú"`, ícono `menu`) y se cierra al pulsar un
enlace. Se usa el `BreakpointObserver` del CDK y no `matchMedia` directo para poder sustituirlo
en los tests.

**Un solo diálogo, `DialogoPresupuestoComponent`, que hace la petición.**
Se abre con `MatDialog.open(DialogoPresupuestoComponent, { data, width: '400px' })` y
`data: DatosDialogoPresupuesto` = `{ modo: 'crear' }` o
`{ modo: 'renombrar'; presupuesto: { id, nombre } }` (interfaz en
`features/presupuestos/models/datos-dialogo-presupuesto.model.ts`). El diálogo llama él mismo a
`crear`/`renombrar` y solo se cierra con éxito (`dialogRef.close(respuesta)`), para poder
mostrar los errores sin perder lo escrito; quien lo abre recibe el `PresupuestoResponse` en
`afterClosed()` (o `undefined` si se canceló). Formulario reactivo:
- `nombre`: `sobreTextoRecortado(Validators.required)` y
  `sobreTextoRecortado(Validators.maxLength(100))`, mensajes con `mensajeDeError` (`required` →
  "El nombre es obligatorio", `maxlength` → "El nombre no puede superar los 100 caracteres"). Se
  envía con `trim()`.
- `moneda` (solo en modo crear): `FormControl<string | null>` con valor inicial `null`, opciones
  `Moneda de mi perfil` (`null`), `BOB`, `USD`, `EUR`, `ARS`, `BRL`, `CLP`, `PEN` (constante
  `MONEDAS` en el componente). Con `null` la petición omite `moneda`.
- Botón "Crear"/"Guardar" deshabilitado con `formulario.invalid || enviando()`; "Cancelar" cierra
  sin resultado. Mientras se envía, `disableClose`.
- Errores: `PRESUPUESTO_YA_EXISTE` → `setErrors({ servidor: 'Ya tienes un presupuesto con ese
  nombre' })` en `nombre` (se muestra con `mensajeDeError`, que prioriza `servidor`);
  `DATOS_INVALIDOS` con `errores` → `aplicarErroresDeCampos` (aviso genérico si queda alguna
  clave sin control); otro → aviso genérico. `CODIGOS_API` incorpora `PRESUPUESTO_YA_EXISTE`.

**Moneda por defecto: "Moneda de mi perfil" en vez de precargar un código.**
El pedido original era preseleccionar la moneda del perfil si se conoce y, si no, `BOB`. Saberla
exigiría que `features/presupuestos` pida `GET /usuarios/yo` con un modelo duplicado (no puede
importar `features/inicio`), y si la moneda del perfil no está entre las siete opciones (por
ejemplo `MXN`) el `BOB` de respaldo crearía el presupuesto en una moneda que la persona no eligió.
Omitir `moneda` delega en el backend, que ya aplica la del perfil, sin petición extra y siempre
correcto. Decisión confirmada por el usuario al planificar el change.

**Tras crear o renombrar, el layout actualiza su lista en memoria.**
Crear: inserta el nuevo en la lista, la reordena por nombre (`localeCompare` con
`sensitivity: 'base'`, igual que el backend) y navega a `/presupuestos/{id}`; como ya está en la
lista, la validación del id lo encuentra sin volver a pedirla. Renombrar: reemplaza el elemento,
reordena y vuelve a `fijar` el activo con el nombre nuevo. La redirección, tras crear el primero,
solo navega (el layout cargará la lista al entrar).

**`InicioPage` sin cabecera ni cierre de sesión.**
Pasa a ser la sección por defecto del layout: se quitan `<app-cabecera>`, el botón, `cerrarSesion`
y las inyecciones de `SesionService` y `Router`. El resto (saludo, spinner, error) no cambia.

### Archivos nuevos o modificados

Rutas desde `frontend/src/app/`.

| Archivo | Estado | Test |
|---|---|---|
| `core/presupuesto-activo/presupuesto-activo.model.ts` | nuevo | (interfaz, sin test) |
| `core/presupuesto-activo/presupuesto-activo.service.ts` | nuevo | `core/presupuesto-activo/presupuesto-activo.service.spec.ts` |
| `core/sesion/sesion.service.ts` | modificado | `core/sesion/sesion.service.spec.ts` |
| `shared/api/problema-api.ts` | modificado | `shared/api/problema-api.spec.ts` (sin cambios) |
| `features/presupuestos/models/presupuesto-response.model.ts` | nuevo | (interfaz, sin test) |
| `features/presupuestos/models/crear-presupuesto-request.model.ts` | nuevo | (interfaz, sin test) |
| `features/presupuestos/models/actualizar-presupuesto-request.model.ts` | nuevo | (interfaz, sin test) |
| `features/presupuestos/models/datos-dialogo-presupuesto.model.ts` | nuevo | (interfaz, sin test) |
| `features/presupuestos/models/seccion-presupuesto.model.ts` | nuevo | (interfaz, sin test) |
| `features/presupuestos/services/presupuesto.service.ts` | nuevo | `features/presupuestos/services/presupuesto.service.spec.ts` |
| `features/presupuestos/components/dialogo-presupuesto.component.ts` (+ `.html`, `.scss`) | nuevo | `features/presupuestos/components/dialogo-presupuesto.component.spec.ts` |
| `features/presupuestos/pages/redireccion-presupuesto.page.ts` (+ `.html`, `.scss`) | nuevo | `features/presupuestos/pages/redireccion-presupuesto.page.spec.ts` |
| `features/presupuestos/pages/layout-presupuesto.page.ts` (+ `.html`, `.scss`) | nuevo | `features/presupuestos/pages/layout-presupuesto.page.spec.ts` |
| `features/inicio/pages/inicio.page.ts` (+ `.html`) | modificado | `features/inicio/pages/inicio.page.spec.ts` |
| `app.routes.ts` | modificado | `app.routes.spec.ts` |

## Risks / Trade-offs

- [Dos `GET /presupuestos` al entrar por `/` (redirección y layout)] → Lista pequeña y sin
  paginación; si molesta, un caché en `PresupuestoService` se agrega sin cambiar las specs.
- [Una sección futura podría leer `PresupuestoActivoService` antes de que el layout lo fije] →
  El `router-outlet` solo se pinta cuando el activo coincide con la URL.
- [El menú lateral lee `routeConfig.children`, que con `loadChildren` sería `undefined`] → Las
  secciones se declaran con `loadComponent` directamente en `app.routes.ts`; si alguna vez se
  usa `loadChildren`, la sección se declara igual en el padre con su `data`.
- [Sin `matchMedia` en jsdom, un test que olvide sustituir el `BreakpointObserver` siempre ve
  pantalla ancha] → Los tests de pantalla estrecha proveen un `BreakpointObserver` falso
  controlado por un `Subject`.
- [La moneda del perfil no se ve en el diálogo] → La opción se llama "Moneda de mi perfil" y la
  moneda real se ve en el selector tras crear; reversible solo en el diálogo.

## Migration Plan

Solo frontend, sin datos persistidos nuevos (el presupuesto activo vive en memoria). Una sesión
ya iniciada sigue válida: al recargar, `/` lleva al primer presupuesto. Rollback: revertir el
change.
