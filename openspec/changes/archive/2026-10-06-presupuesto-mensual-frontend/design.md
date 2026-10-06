# Design

## Context

Ver proposal.md - Why. Estado observado:

- Backend (`com.presupuesto.asignacion`, archivado en `asignacion-backend` y ajustado por
  `transferencias-backend`, que solo cambió cómo se calculan los ingresos, no el contrato):
  - `MesPresupuestoResponse { mes, listoParaAsignar, totalAsignado, totalActividad,
    totalDisponible, grupos }`.
  - `GrupoMesResponse { id, nombre, orden, oculto, categorias }`.
  - `CategoriaMesResponse { categoriaId, nombre, oculta, asignado, actividad, disponible,
    sobregastada }`.
  - `AsignacionActualizadaResponse { categoria, listoParaAsignar }`.
  - Requests: `AsignarRequest { asignado }` y `MoverDineroRequest { origenId, destinoId, monto }`.
  - Códigos de error: `DATOS_INVALIDOS`, `RECURSO_NO_ENCONTRADO`, `CONFLICTO` (409, asignación
    concurrente) y `REGLA_NEGOCIO_VIOLADA`.
- Frontend:
  - `LayoutPresupuestoPage` arma el menú lateral con las rutas hijas que tienen `data.seccion`,
    usando `hija.path` como enlace.
  - Hoy la ruta hija `''` es `InicioPage`, y `RedireccionPresupuestoPage` navega a
    `/presupuestos/{id}`.
  - `shared/formato/milliunits.ts` (`leerMonto`, `aMilliunits`, `deMilliunits`) y
    `shared/validacion/monto.validator.ts` (`montoValido`) ya existen.
  - Los diálogos de cuentas y categorías son el patrón a seguir: hacen la petición, se cierran
    solo con éxito y deciden los errores por `codigo`.

## Goals / Non-Goals

**Goals:**
- Que el mes salga siempre de la fecha local y nunca de `toISOString()`, con funciones puras
  probadas en dos zonas horarias.
- Que editar el asignado se sienta inmediato y nunca deje la vista distinta del backend.

**Non-Goals:**
- Recalcular en el frontend el disponible arrastrado entre meses: lo calcula el backend; el
  frontend solo ajusta de forma optimista la fila que se edita.
- Edición de varias celdas a la vez ni deshacer.

## Decisions

**Funciones de mes puras en `features/presupuesto-mensual/services/mes.ts`.**
- `mesActual(ahora = new Date())` usa `getFullYear()` y `getMonth()`.
- `esMesValido(texto)` acepta `^\d{4}-(0[1-9]|1[0-2])$` entre `2000-01` y `2100-12`.
- `sumarMeses(mes, n)` es aritmética entera sobre año y mes.
- `textoMes(mes, region = regionUsuario())` usa `Intl.DateTimeFormat(region, { month: 'long',
  year: 'numeric' })` sobre `new Date(anio, mes - 1, 1)`, una fecha local que no se corre de mes.
- Constantes `MES_MINIMO` y `MES_MAXIMO`.

Ningún cálculo pasa por UTC. Alternativa descartada: `new Date().toISOString().slice(0, 7)`, que
en Bolivia desde las 20:00 del último día del mes devuelve el mes siguiente.

**Rutas.** Hijas de `presupuestos/:presupuestoId`, en este orden:
`{ path: '', pathMatch: 'full', redirectTo: () => `presupuesto/${mesActual()}` }` (directo al mes:
el router de Angular no encadena dos redirecciones relativas en el mismo nivel, así que `''` →
`presupuesto` → `presupuesto/{mes}` se quedaba en `/presupuestos/{id}`);
`{ path: 'presupuesto', pathMatch: 'full', redirectTo: () => \`presupuesto/${mesActual()}\`,
data: seccion('Presupuesto', 'account_balance_wallet') }` (el menú usa ese `path` como enlace y
`routerLinkActive` no exacto lo marca también en `presupuesto/:mes`);
`{ path: 'presupuesto/:mes', loadComponent: PresupuestoMensualPage }`;
`{ path: 'inicio', ..., data: seccion('Inicio', 'home') }`; y luego cuentas y categorías. El mes
inválido lo resuelve la página: si `:mes` no es válido, navega con `replaceUrl` a
`../{mesActual()}` y no pide nada. `RedireccionPresupuestoPage` no cambia, porque sigue llevando
a `/presupuestos/{id}` y el resto lo hacen las redirecciones.

**Carga.** La página lee `:mes` de `paramMap` como señal y usa
`toObservable(computed({ presupuestoId, mes, incluirOcultas, recargas }))` con `switchMap` a
`obtener`. `switchMap` descarta la respuesta atrasada al cambiar de mes. Si el mes pedido es
distinto del que se muestra, la vista pasa a `cargando` (spinner); el interruptor y las recargas
del mismo mes reemplazan los datos sin parpadeo. Los totales de grupo y del mes se calculan
siempre en el frontend sumando las categorías mostradas (con el mes recién cargado coinciden con
los del backend; después de una edición optimista siguen siendo correctos).

**Edición del asignado: celda tonta, página que decide.**
`CeldaAsignadoComponent` recibe el asignado, la moneda, el nombre (para el `aria-label`), si está
guardando y un mensaje de error del servidor, y emite `guardar(milliunits)`. Por dentro:
- **Abrir:** un botón muestra el monto; al pulsarlo (o con Enter, que el botón ya activa) pasa a
  un `input` con `deMilliunits(asignado)` seleccionado (`afterNextRender`).
- **Leer el texto:** se interpreta con `leerMonto`. Vacío es `0`; inválido marca el campo
  (`aria-invalid`, mensaje `Monto inválido`) y no emite.
- **Enter, blur y Tab:** `Enter` y `blur` confirman. `Tab` (sin Shift) hace `preventDefault`,
  confirma y, si el valor era válido, enfoca el botón de la siguiente
  `app-celda-asignado` del documento.
- **Escape** cancela.
- **Sin cambios:** si el valor no cambió, no emite. Una bandera evita que el `blur` que sigue a
  `Enter` vuelva a confirmar.
- **Foco:** tras `Enter` o `Escape` vuelve al botón.

`PresupuestoMensualPage.asignar(categoriaId, valor)`:
1. Ignora la llamada si esa categoría ya tiene un guardado en curso (`Set` en una señal).
2. Aplica el cambio de forma optimista: `asignado = valor`, `disponible += delta`,
   `sobregastada = disponible < 0` y `listoParaAsignar -= delta`.
3. Envía el `PUT`. Con éxito, reemplaza la fila por `respuesta.categoria` y fija
   `listoParaAsignar`.
4. Si falla, restaura la fila anterior, devuelve el `delta` a `listoParaAsignar` y avisa:
   - `400` con `errores.asignado`: el mensaje va a la celda.
   - `CONFLICTO` o `RECURSO_NO_ENCONTRADO`: aviso genérico y recarga.
   - `401`: nada.
   - Cualquier otro: aviso genérico.

La reversión toca solo esa fila y ese delta, así no pisa otra celda que se haya guardado mientras
tanto.

**Componentes de presentación.**
- **`BarraMesComponent`:** mes de entrada, salida `cambiar(mes)`. Botones
  `aria-label="Mes anterior"` y `"Mes siguiente"` deshabilitados en los límites, y `Hoy`.
- **`ResumenListoParaAsignarComponent`:** tres estados con ícono (`check_circle`, `done_all`,
  `error`) y texto. Usa los colores `--mat-sys-primary-container` y `--mat-sys-error-container`.
- **`GrupoMesComponent`:**
  - Encabezado plegable (botón con `expand_more`/`chevron_right` y `aria-expanded`) con la suma
    de sus categorías.
  - Filas en CSS grid de 4 columnas; por debajo de 600 px se apilan y muestran etiquetas.
  - Menú por categoría con `Mover dinero...` y, si está sobregastada, `Cubrir sobregasto`.
  - Disponible: clase `positivo` (`--mat-sys-primary`), neutro o `sobregastado`
    (`--mat-sys-error`, ícono `warning` y texto `Sobregastado` visible).

**Diálogo `DialogoMoverDineroComponent`.**
- **Datos:** `{ mes: MesPresupuestoResponse, origenId?, destinoId?, monto? }`.
- **Opciones:** grupos visibles con sus categorías visibles en `mat-optgroup`.
- **Validaciones** (validadores que leen al hermano y se reevalúan al cambiar el origen):
  - `destinoId` distinto de `origenId`: `El destino debe ser distinto del origen`.
  - `monto` obligatorio, `montoValido()` y mayor que 0: `El monto debe ser mayor que 0`.
  - `monto` no mayor al disponible del origen: `Supera el disponible del origen`.
- **Ayuda:** `Disponible: {monto}` del origen.
- **Resultado:** `{ tipo: 'movido', mes }` o `{ tipo: 'recargar' }` (404).
- **Errores:** `REGLA_NEGOCIO_VIOLADA` como mensaje del diálogo (`El origen no tiene suficiente
  disponible`); `DATOS_INVALIDOS` con `aplicarErroresDeCampos` (los controles se llaman
  `origenId`, `destinoId` y `monto`, como el request); `RECURSO_NO_ENCONTRADO` con aviso y
  cierre `recargar`; el resto con el aviso genérico.

**Modelos.** Un archivo por interfaz: `mes-presupuesto-response`, `grupo-mes-response`,
`categoria-mes-response`, `asignacion-actualizada-response`, `asignar-request`,
`mover-dinero-request`, `datos-dialogo-mover-dinero` y `resultado-mover-dinero`.

### Archivos nuevos o modificados

Rutas desde `frontend/src/app/`; prefijo `features/presupuesto-mensual/` abreviado `pm/`.

| Archivo | Estado | Test |
|---|---|---|
| `pm/models/*.model.ts` (8 archivos) | nuevo | (interfaces) |
| `pm/services/mes.ts` | nuevo | `pm/services/mes.spec.ts` |
| `pm/services/mes-presupuesto.service.ts` | nuevo | `pm/services/mes-presupuesto.service.spec.ts` |
| `pm/components/barra-mes.component.ts` (+ html, scss) | nuevo | `pm/components/barra-mes.component.spec.ts` |
| `pm/components/resumen-listo-para-asignar.component.ts` (+ html, scss) | nuevo | `pm/components/resumen-listo-para-asignar.component.spec.ts` |
| `pm/components/celda-asignado.component.ts` (+ html, scss) | nuevo | `pm/components/celda-asignado.component.spec.ts` |
| `pm/components/grupo-mes.component.ts` (+ html, scss) | nuevo | `pm/components/grupo-mes.component.spec.ts` |
| `pm/components/dialogo-mover-dinero.component.ts` (+ html, scss) | nuevo | `pm/components/dialogo-mover-dinero.component.spec.ts` |
| `pm/pages/presupuesto-mensual.page.ts` (+ html, scss) | nuevo | `pm/pages/presupuesto-mensual.page.spec.ts` |
| `shared/api/problema-api.ts` | modificado | sin cambios en su spec |
| `app.routes.ts` | modificado | `app.routes.spec.ts` |

## Risks / Trade-offs

- [`redirectTo` como función depende de la hora del momento] → Es justo lo pedido: "Hoy" se
  evalúa cada vez que se entra sin mes.
- [Optimismo en `disponible`] → Solo suma el delta del asignado, que es exacto para el mes
  editado; la respuesta del backend reemplaza la fila enseguida.
- [El aviso de un 409 concurrente recarga el mes completo] → Es raro y así la vista vuelve a la
  verdad del backend.

## Migration Plan

Solo frontend. Los enlaces guardados a `/presupuestos/{id}` siguen funcionando (redirigen). Las
URLs viejas que mostraban el inicio en `/presupuestos/{id}` ahora abren el presupuesto mensual.
