# Design

## Context

Ver proposal.md - Why. Estado observado:

- Backend (`com.presupuesto.reporte`, integrado; spec `reportes`), bajo
  `/api/v1/presupuestos/{presupuestoId}/reportes`, todo `GET` (`ReporteController`):
  - `gasto-por-categoria?desde&hasta` → `GastoPorCategoriaResponse { desde, hasta, total,
    grupos: GrupoGastoResponse[] { grupoId, nombre, total, porcentaje, categorias:
    CategoriaGastoResponse[] { categoriaId, nombre, oculta, total, porcentaje } }, sinCategoria:
    { total, porcentaje } }`.
  - `ingresos-gastos?desde&hasta` → `{ desde, hasta, ingresos, gastos, neto, meses: [{ mes,
    ingresos, gastos, neto }] }`.
  - `patrimonio?desde&hasta` → `{ desde, hasta, meses: [{ mes, activos, pasivos, patrimonio }] }`.
  - `cuentas/{cuentaId}/evolucion-saldo?desde&hasta` → `{ cuentaId, nombre, tipo, enPresupuesto,
    cerrada, saldoInicial, desde, hasta, meses: [{ mes, entradas, salidas, saldo }] }`.
  - `metas?desde&hasta` (`hasta` opcional) → `{ desde, hasta, metas: [{ categoriaId, nombre,
    oculta, tipo, monto, necesidad, asignado, gastado, porcentaje: number | null, meses: [{ mes,
    necesidad, asignado, gastado, disponible, faltante, estado, porcentaje: number | null }] }] }`.
    `tipo`: `MONTO_MENSUAL | MONTO_PARA_FECHA | ...` (`TipoMeta`); `estado`: `SOBREGASTADA |
    POSPUESTA | FALTA | FINANCIADA`.
  - `desde`/`hasta` llegan como `String` y los valida el service: `404 RECURSO_NO_ENCONTRADO`
    (presupuesto, cuenta) antes que `400 DATOS_INVALIDOS` (mes mal formado, invertido, ausente o
    más de `reportes.max-meses`, 60 por defecto) sin `errores`. Montos `long` en milésimas;
    porcentajes en centésimas de punto.
- Frontend:
  - El menú lateral lo arma `LayoutPresupuestoPage` con las rutas hijas que tienen
    `data.seccion`: agregar una sección es **una ruta** en `app.routes.ts`, sin tocar el layout.
  - `AGENTS.md` pide `loadComponent` (no `loadChildren`) para las secciones.
  - Meses: `features/presupuesto-mensual/services/mes.ts` (`mesActual` local, `esMesValido`
    2000-01..2100-12, `sumarMeses`, `textoMes`). Lo importa también `app.routes.ts`. Una feature
    no puede importar de otra.
  - `shared/formato/monto.pipe.ts` (`monto`, con `regionUsuario()`), `shared/api/problema-api.ts`
    (`leerProblemaApi`, `CODIGOS_API`, `MENSAJE_ERROR_GENERICO`), `PresupuestoActivoService`
    (`id`, `moneda`).
  - `features/presupuesto-mensual/services/presentacion-meta.ts` ya decide `Faltaron` neutro en
    meses pasados; reportes replica esa regla en su propia función (no puede importarla).
  - `features/transacciones/services/filtros-url.ts` entiende `cuentaId`, `categoriaId`, `desde`
    y `hasta` (`yyyy-MM-dd`): el enlace `Ver transacciones` es viable sin tocar esa pantalla.
  - Sin librería de gráficos. Angular 21.2, Material 21.2, Vitest.

## Goals / Non-Goals

**Goals:**
- Tocar un solo archivo compartido de enrutamiento (`app.routes.ts`) y ninguno del layout.
- Toda la lógica decidible (rango, atajos, URL, porcentajes, escalas, textos de estado) en
  funciones puras probadas sin TestBed.
- Cero dependencias nuevas.

**Non-Goals:**
- Interacción rica en los gráficos (zoom, tooltips flotantes, animaciones): la tabla da el detalle.
- Cachear reportes entre visitas a la pantalla; solo se evita repetir dentro de la misma visita.

## Decisions

**Pestañas en una sola ruta, no rutas hijas.** Una ruta `reportes` con `ReportesPage` y un
`mat-tab-group` con `<ng-template matTabContent>` (el contenido de la pestaña se crea solo al
elegirla, así cada reporte carga al verse). Rutas hijas obligarían a `loadChildren` o a cinco
entradas más en `app.routes.ts` (archivo que otras personas editan en paralelo) y repartirían el
selector de rango entre rutas. La pestaña, el rango y la cuenta viven en query params
(`reporte`, `desde`, `hasta`, `cuentaId`) escritos con `router.navigate([], { queryParams,
queryParamsHandling: 'merge', replaceUrl: true })`: recargar o compartir conserva todo, y el
historial no se llena con cada cambio. `replaceUrl` también al normalizar una URL inválida.

**`mes.ts` a `shared/fecha/`.** Se mueve el contenido (y su spec) a `shared/fecha/mes.ts` y
`features/presupuesto-mensual/services/mes.ts` queda como `export * from
'../../../shared/fecha/mes';`. Así no hay una segunda versión de las funciones de meses, reportes
no importa otra feature, y ni `app.routes.ts` ni los archivos de `presupuesto-mensual` cambian
sus imports (menos conflictos con quienes trabajan en paralelo). Alternativa descartada:
duplicar `mesActual`/`sumarMeses` en reportes (versión paralela que podría divergir en la regla
de la hora local).

**Rango: funciones puras** en `features/reportes/services/rango-reporte.ts`:
- `RangoMeses { desde: string; hasta: string }`, `MAXIMO_MESES = 60`.
- `contarMeses(desde, hasta)` (entero, inclusivo), `validarRango(desde, hasta)` →
  `null | 'invertido' | 'excede-maximo' | 'mal-formado'`.
- `atajo(tipo, mesLocal)` para `este-mes | ultimos-3 | ultimos-6 | ultimos-12 | este-anio`,
  con `sumarMeses` de `shared/fecha/mes.ts`; `RANGO_POR_DEFECTO = 'ultimos-6'`.
- `rangoDesdeUrl(params, mesLocal)` → rango válido o el de por defecto (con un flag
  `normalizado` para reescribir la URL); `primerDia(mes)` y `ultimoDia(mes)` (`yyyy-MM-dd`, con
  aritmética entera de calendario, sin `Date` UTC) para el enlace a transacciones.
- `textoRango(rango, region)` con `textoMes`.
El servidor puede tener un máximo menor (configurable): su `400` se muestra con el aviso del
rango; la interfaz no intenta descubrir el máximo.

**Selector de mes: dos `mat-select` (mes y año) por extremo**, en
`components/selector-rango.component`. El `mat-datepicker` con `startView="multi-year"` exigiría
formatos propios de `MAT_DATE_FORMATS` y un input de texto que `AdaptadorFechaRegional`
interpreta como fecha con día; los `mat-select` son accesibles, no admiten valores mal formados y
no tocan `core/material`. Meses con nombre de `Intl` según la región; años 2000 a 2100. El
componente emite `rangoCambiado` solo con un rango válido y muestra el error de validación en un
`mat-error`/`role="alert"`. Los atajos son `mat-stroked-button` (`mat-button-toggle` no aplica
porque el rango manual no corresponde a ningún atajo).

**Datos por pestaña.** `ReporteService` (`services/reporte.service.ts`) con un método por
endpoint, que arma `HttpParams` `desde`/`hasta`. Cada pestaña es un componente
(`gasto-reporte`, `ingresos-gastos-reporte`, `patrimonio-reporte`, `saldo-cuenta-reporte`,
`metas-reporte`) con entradas `presupuestoId`, `rango` (y `moneda`) como signals, y un
`toSignal(toObservable(computed(...)).pipe(distinctUntilChanged(iguales), switchMap(p =>
servicio.x(p).pipe(map(listo), catchError(err => of(error(err))), startWith(cargando)))))`.
`switchMap` cancela la petición anterior (el `HttpClient` aborta al desuscribir) y descarta su
respuesta. `Reintentar` re-emite un `Subject` combinado con los parámetros. Como
`matTabContent` destruye el contenido al salir, se usa `preserveContent` en el `mat-tab-group`
para que volver a una pestaña con el mismo rango no repita la petición (los componentes siguen
vivos pero solo los visibles se crearon). Estado tipado `EstadoCarga<T> = { tipo: 'cargando' } |
{ tipo: 'listo'; datos: T } | { tipo: 'error'; aviso: AvisoError }`.

**Errores** en `services/errores-reporte.ts`: `avisoError(error, contexto)` con
`leerProblemaApi`: `DATOS_INVALIDOS` → `MENSAJE_RANGO_INVALIDO`; `RECURSO_NO_ENCONTRADO` →
`MENSAJE_CUENTA_INEXISTENTE` (saldo; la pestaña recarga cuentas y quita `cuentaId`) o
`MENSAJE_PRESUPUESTO_INEXISTENTE` (resto, con `Recargar` que hace `location.reload()`); otro →
`MENSAJE_ERROR_GENERICO` con `Reintentar`. Nunca se lee `detail`. Los avisos van en un contenedor
`role="status"`/`aria-live="polite"` (errores `role="alert"`).

**Porcentajes y escalas, en enteros** (`services/formato-reporte.ts`):
- `textoPorcentaje(centesimas, region)`: `Intl.NumberFormat(region, { style: 'percent',
  minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(centesimas / 10000)`; la única
  división es la conversión a texto al mostrar (igual que el pipe `monto`). `null` →
  `Sin necesidad`.
- `proporcion(valor, maximo, escala)` → `Math.floor((valor * escala) / maximo)` con enteros
  (escala 1000 para anchos en por mil y coordenadas SVG enteras); los montos en milésimas caben
  holgadamente en `Number.MAX_SAFE_INTEGER` multiplicados por 1000 para cualquier presupuesto
  realista (hasta 9·10¹² milésimas).
- `escalaVertical(valores)` → `{ minimo, maximo }` enteros que incluyen el 0, y `marcas` de eje
  redondeadas a 1, 2 o 5 × 10ⁿ milésimas.
- `presentacionEstadoMeta(estado, mes, mesActual)` → `{ texto, icono, tono }` (`Faltaron` neutro
  en meses pasados), mismas palabras que presupuesto mensual.

**Gráficos: SVG propio, sin librería.** Verificado en npm el 2026-10-08: `ng2-charts@11.0.0`
exige `@angular/core >= 22`; la última compatible con Angular 21 es `ng2-charts@10.0.0`, con
`chart.js@4.5.1` (≈ 70 KB gzip completo, ≈ 45 KB gzip registrando solo barras y líneas, más
≈ 3 KB de ng2-charts), que además dibuja en `<canvas>` (sin texto accesible propio: habría que
duplicar la tabla igual) y fija colores en JS fuera de los tokens `--mat-sys-*`. Los gráficos
pedidos son tres formas simples (barras horizontales de proporción, barras agrupadas por mes y
líneas por mes, ≤ 60 puntos) que en SVG se dibujan con unas 100 líneas cada uno, con colores por
CSS (tokens de Material), patrones `<pattern>` para no depender del color, `viewBox` +
`width: 100%` para adaptarse al ancho y `role="img"` + `aria-label`. Costo: 0 dependencias; los
componentes van en el chunk diferido de la ruta `reportes` (`loadComponent`), estimado en
≈ 15-25 KB minificados (sin gzip) para toda la feature, contra ≈ 45-70 KB gzip de Chart.js. Si
algún día hacen falta tooltips, zoom o muchos puntos, se revisa.
Componentes (`components/`): `grafico-barras-horizontales` (filas con barra en `div` y ancho en
por mil; no necesita SVG), `grafico-barras-mensuales` (series `{ etiqueta, valores, patron }` y
una línea opcional para el neto o el saldo) y `grafico-lineas` (varias series con trazos
`solid`/`dashed`/`dotted` y marcadores distintos, línea del cero). Cada uno recibe la
descripción accesible; las tablas las arma cada pestaña.

**Tablas.** `mat-table` dentro de un contenedor con `overflow-x: auto` y `max-width: 100%`;
columnas numéricas alineadas a la derecha con `font-variant-numeric: tabular-nums`. En metas, una
tarjeta (`mat-expansion-panel`) por meta con sus totales y la tabla de meses dentro.

**Cuentas para el saldo.** `CuentaLecturaService` propio (`GET .../cuentas?incluirCerradas=true`)
con `CuentaReporte { id, nombre, tipo, enPresupuesto, cerrada }`, duplicación pequeña y a
propósito (regla de AGENTS). El selector agrupa abiertas y cerradas y marca
`Fuera del presupuesto`.

**Enlace a transacciones.** `routerLink` a `['/presupuestos', id, 'transacciones']` con
`queryParams { categoriaId | cuentaId, desde: primerDia(rango.desde), hasta:
ultimoDia(rango.hasta) }`. No se ofrece para `Sin categoría` (la lista no filtra "sin
categoría"). Nota: la lista filtra por la categoría de la transacción; una división muestra la
transacción completa.

**Mensajes** (constantes en `services/mensajes-reporte.ts`): `MENSAJE_RANGO_INVALIDO`,
`MENSAJE_CUENTA_INEXISTENTE`, `MENSAJE_PRESUPUESTO_INEXISTENTE`, `MENSAJE_SIN_MOVIMIENTOS =
'Sin movimientos en este rango'`, `NOTA_SALDO_INICIAL`, `NOTA_HISTORIAL_METAS`,
`NOTA_PAGOS_TARJETA`, `NOTA_PATRIMONIO`.

### Archivos (desde `frontend/src/app/`)

| Archivo nuevo o modificado | Test |
|---|---|
| `shared/fecha/mes.ts` (nuevo; contenido movido de `presupuesto-mensual`) | `shared/fecha/mes.spec.ts` (movido) |
| `features/presupuesto-mensual/services/mes.ts` (modificado: reexporta) | — (cubierto por `shared/fecha/mes.spec.ts`; se borra `mes.spec.ts` viejo) |
| `features/reportes/models/gasto-por-categoria-response.model.ts` (nuevo) | — (interfaz) |
| `features/reportes/models/ingresos-gastos-response.model.ts` (nuevo) | — (interfaz) |
| `features/reportes/models/patrimonio-response.model.ts` (nuevo) | — (interfaz) |
| `features/reportes/models/evolucion-saldo-response.model.ts` (nuevo, con `TipoCuentaReporte`) | — (interfaz) |
| `features/reportes/models/cumplimiento-metas-response.model.ts` (nuevo, con `EstadoMetaReporte`, `TipoMetaReporte`) | — (interfaz) |
| `features/reportes/models/cuenta-reporte.model.ts` (nuevo) | — (interfaz) |
| `features/reportes/models/rango-meses.model.ts` (nuevo; `RangoMeses`, `TipoAtajo`, `PestanaReporte`) | — (interfaz) |
| `features/reportes/models/estado-carga.model.ts` (nuevo; `EstadoCarga<T>`, `AvisoError`) | — (interfaz) |
| `features/reportes/services/reporte.service.ts` (nuevo) | `features/reportes/services/reporte.service.spec.ts` |
| `features/reportes/services/cuenta-lectura.service.ts` (nuevo) | `features/reportes/services/cuenta-lectura.service.spec.ts` |
| `features/reportes/services/rango-reporte.ts` (nuevo) | `features/reportes/services/rango-reporte.spec.ts` |
| `features/reportes/services/formato-reporte.ts` (nuevo) | `features/reportes/services/formato-reporte.spec.ts` |
| `features/reportes/services/errores-reporte.ts` (nuevo) | `features/reportes/services/errores-reporte.spec.ts` |
| `features/reportes/services/mensajes-reporte.ts` (nuevo; constantes) | — (constantes) |
| `features/reportes/services/carga-reporte.ts` (nuevo; operador `cargarReporte` con `switchMap`, reintento y estados) | `features/reportes/services/carga-reporte.spec.ts` |
| `features/reportes/components/selector-rango.component.ts` (+ html, scss) | `features/reportes/components/selector-rango.component.spec.ts` |
| `features/reportes/components/estado-reporte.component.ts` (+ html, scss; carga, vacío, error con `Reintentar`) | `features/reportes/components/estado-reporte.component.spec.ts` |
| `features/reportes/components/grafico-barras-horizontales.component.ts` (+ html, scss) | `features/reportes/components/grafico-barras-horizontales.component.spec.ts` |
| `features/reportes/components/grafico-barras-mensuales.component.ts` (+ html, scss) | `features/reportes/components/grafico-barras-mensuales.component.spec.ts` |
| `features/reportes/components/grafico-lineas.component.ts` (+ html, scss) | `features/reportes/components/grafico-lineas.component.spec.ts` |
| `features/reportes/components/gasto-reporte.component.ts` (+ html, scss) | `features/reportes/components/gasto-reporte.component.spec.ts` |
| `features/reportes/components/ingresos-gastos-reporte.component.ts` (+ html, scss) | `features/reportes/components/ingresos-gastos-reporte.component.spec.ts` |
| `features/reportes/components/patrimonio-reporte.component.ts` (+ html, scss) | `features/reportes/components/patrimonio-reporte.component.spec.ts` |
| `features/reportes/components/saldo-cuenta-reporte.component.ts` (+ html, scss) | `features/reportes/components/saldo-cuenta-reporte.component.spec.ts` |
| `features/reportes/components/metas-reporte.component.ts` (+ html, scss) | `features/reportes/components/metas-reporte.component.spec.ts` |
| `features/reportes/pages/reportes.page.ts` (+ html, scss) | `features/reportes/pages/reportes.page.spec.ts` |
| `app.routes.ts` (ruta `reportes` con `seccion('Reportes', 'bar_chart')`) | `app.routes.spec.ts` |

**Archivos compartidos tocados, exactamente:** `app.routes.ts` y `app.routes.spec.ts` (una ruta
y un caso), `shared/fecha/mes.ts` y `shared/fecha/mes.spec.ts` (nuevos por movimiento),
`features/presupuesto-mensual/services/mes.ts` (pasa a una línea) y el borrado de
`features/presupuesto-mensual/services/mes.spec.ts`. No cambian `LayoutPresupuestoPage`, `core/`
ni el resto de `shared/`. Fuera de `src/app`, solo `AGENTS.md`.

## Risks / Trade-offs

- **Conflicto en `app.routes.ts`** con metas, conciliación o perfil en paralelo → es una entrada
  en el arreglo de hijas; quien integre segundo resuelve el orden (Reportes va después de
  `beneficiarios`).
- **Conflicto en `presupuesto-mensual/services/mes.ts`** si alguien lo edita en paralelo → se
  avisa en el commit; quien edite después lo hace en `shared/fecha/mes.ts`.
- **Máximo del servidor menor que 60** → la interfaz no lo conoce; el `400` muestra el aviso del
  rango y la persona acorta el rango.
- **Montos enormes en las escalas** → `valor * 1000` supera `MAX_SAFE_INTEGER` sobre 9·10¹²
  milésimas (9 mil millones de unidades); se documenta en `proporcion` y se prueba el límite.
- **Gráficos propios sin tooltips** → toda cifra está en la tabla; los puntos llevan `<title>`
  con mes y monto para quien pasa el mouse.
- **`preserveContent` mantiene vivas las pestañas visitadas** → como mucho cinco componentes con
  sus datos; al cambiar el rango, las ocultas también piden (solo las ya visitadas). Mitigación:
  cada pestaña recibe `activa` y solo pide si está activa o ya lo estaba con ese rango.
- **La meta vigente se aplica a meses anteriores a su creación** → `Faltaron` neutro y la nota;
  no se puede corregir en el cliente.

## Migration Plan

Solo frontend, sin datos ni backend. Revertir es quitar la ruta y la carpeta; `mes.ts` puede
quedarse en `shared/` sin efecto.
