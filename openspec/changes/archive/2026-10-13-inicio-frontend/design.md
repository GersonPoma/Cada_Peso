# Design

## Context

Ver proposal.md - Why. Estado observado leyendo el código y las specs archivadas:

- `InicioPage` (`features/inicio/pages`) pide `GET /usuarios/yo` y pinta `Hola, <nombre>`; su spec
  existente (4 `it`) **no fija presupuesto activo** y afirma que, tras responder, no hay
  `mat-progress-spinner` ni ningún `button`, y que `backend.verify()` no deja peticiones abiertas.
  Las tarjetas se pintan solo con presupuesto activo, así que esos `it` no cambian.
- Las rutas y respuestas reales (leídas del backend y de las specs archivadas):

| Fuente | Ruta bajo `/api/v1/presupuestos/{id}` | Lo que se usa |
|---|---|---|
| Mes | `GET /meses/{mes}?incluirOcultas=true` | `listoParaAsignar`, `grupos[].categorias[].sobregastada` |
| Cuentas | `GET /cuentas` (abiertas) | `id`, `enPresupuesto`, `cerrada` |
| Saldos | `GET /transacciones/saldos` | `cuentaId`, `saldo` |
| Metas | `GET /meses/{mes}/metas` | `metas[].estado` |
| Sin aprobar | `GET /transacciones?soloSinAprobar=true&page=0&size=1` | `totalElementos` |
| Ingresos y gastos | `GET /reportes/ingresos-gastos?desde={mes}&hasta={mes}` | `ingresos`, `gastos`, `neto` |
| Perfil (existente) | `GET /api/v1/usuarios/yo` | `nombre` |

- `Cuentas` calcula `Total en el presupuesto` en el cliente: suma el `saldo` de las filas con
  `!cerrada && enPresupuesto` (`cuentas.page.ts`, `total`). Es la única suma del dominio que `Inicio`
  repite.
- AGENTS.md: una feature no importa de otra feature; `core/` y `shared/` sí se reutilizan
  (`PresupuestoActivoService`, `mesActual()`, `textoMes()`, pipe `monto`, `leerProblemaApi`).

## Goals / Non-Goals

**Goals:** un resumen de seis tarjetas independientes, con 7 peticiones en paralelo, sin ningún
cálculo nuevo del dominio y sin tocar archivos compartidos ni pantallas enlazadas.

**Non-Goals:** gráficos, patrimonio, personalización, notificaciones, un endpoint nuevo de
resumen en el backend, refresco automático al cambiar de mes con la pantalla abierta.

## Decisions

### D1. Servicios y modelos propios, sin importar otras features
`features/inicio/services/resumen-lectura.service.ts` (solo GET) con modelos mínimos en `models/`,
como `CuentaLecturaService` de importación. Duplicación pequeña y a propósito (AGENTS.md).

### D2. Una fuente por petición, cargadores independientes
`services/cargador-tarjeta.ts` expone `crearCargador(peticion)`: dado un `() => Observable<T>`
y el presupuesto activo, devuelve las señales `estado` (`cargando | listo | error`), `datos` y
`recargar()`. Usa `toObservable` + `switchMap` sobre `{presupuestoId, recargas}` (patrón de
`CuentasPage`): una respuesta vieja se descarta al cambiar de presupuesto o al reintentar, y
`takeUntilDestroyed` cancela al salir. Un error de una fuente no afecta a las otras.
`ResumenInicioStore` (provisto en el componente `InicioPage`) crea los seis cargadores.

### D3. Siete peticiones, seis tarjetas
```
InicioPage (store)
  +-- perfil ............. GET /usuarios/yo ......................... saludo        (1)
  +-- mes ................ GET /meses/{mes}?incluirOcultas=true ..... listo + sobreg. (1)
  +-- saldo .............. GET /cuentas + GET /transacciones/saldos .. saldo total  (2)
  +-- metas .............. GET /meses/{mes}/metas ................... metas         (1)
  +-- sinAprobar ......... GET /transacciones?...size=1 ............. sin aprobar   (1)
  +-- ingresosGastos ..... GET /reportes/ingresos-gastos ............ ingresos      (1)
                                                                          total:     7
```
Las dos tarjetas del mes comparten una petición y un `Reintentar` (repite solo `GET /meses`). La
tarjeta de saldo une `cuentas` y `saldos` con `forkJoin`: si una falla, falla la tarjeta.

### D4. Saldo total con la regla de Cuentas y un test de contrato
`services/resumen-inicio.ts` (funciones puras): `saldoTotalDelPresupuesto(cuentas, saldos)` suma,
como enteros, `saldo` de las cuentas con `!cerrada && enPresupuesto`. Para que no diverja de
`Cuentas` sin que una feature importe a la otra, **el mismo conjunto de datos** (corriente
`1200000`, tarjeta `-150000`, seguimiento `600000`, cerrada `300000`, total `1050000`) se usa en un
`it` nuevo de `resumen-inicio.spec.ts` y en un `it` nuevo (solo se agrega) de `cuentas.page.spec.ts`.
*Alternativa*: mover la regla a `shared/` y que `CuentasPage` la use; descartada: tocaría una
pantalla y un spec existente para deduplicar 3 líneas.

### D5. Contar, no calcular
`contarSobregastadas(mes)` cuenta `sobregastada === true` en todas las categorías (también las
ocultas: `incluirOcultas=true`, porque `listoParaAsignar` ya las incluye). `contarMetasQueFaltan`
cuenta `estado === 'FALTA'`. **Hueco**: no hay monto en la tarjeta de metas porque `totalFaltante`
suma también las `SOBREGASTADA`; mostrarlo sería otra cuenta distinta de la de la tarjeta. Si se
quiere, el backend deberá exponer el faltante de las `FALTA`.

### D6. Estados vacíos con la cifra que ya llega
- Sin cuentas: `GET /cuentas` vacío (sin pedir nada más).
- Mes sin movimientos: `ingresos`, `gastos` y `neto` en cero. **Hueco**: no hay forma barata de
  saber si el presupuesto tiene transacciones en total sin una octava petición, así que "mes sin
  movimientos" no equivale a "sin transacciones".

### D7. Enlaces
`routerLink` a rutas existentes (no se editan esas pantallas): `/presupuestos/:id/presupuesto/:mes`,
`.../cuentas`, `.../transacciones?sinAprobar=1` y `.../reportes?reporte=ingresos-gastos&desde&hasta`.
`Agregar cuenta` y `Agregar transacción` llevan a `Cuentas` y `Transacciones`; no abren diálogos
(una feature no importa los de otra).

### D8. El mes se fija al abrir
`mesActual()` se evalúa al crear la página. Con la pantalla abierta al pasar de mes no se refresca:
se actualiza al reabrir. Los tests fijan el reloj en al menos dos zonas horarias.

### D9. Tarjeta como componente de presentación
`app-tarjeta-resumen` recibe el título, el `estado`, y proyecta el contenido; pinta carga
(`mat-progress-spinner` con `aria-label`), error (`role="alert"` y `Reintentar`) y vacío. Cada
tarjeta es una región con `aria-labelledby`. Rejilla CSS `auto-fit` que se apila a 390 px.

## Archivos y tests (rutas desde `src/app/`)

| Archivo | Cambio | Test |
|---|---|---|
| `features/inicio/pages/inicio.page.{ts,html,scss}` | **se modifica**: compone el saludo con el mes y las seis tarjetas; las tarjetas solo con presupuesto activo | `inicio.page.spec.ts` (los 4 `it` actuales sin cambios; `it` nuevos) |
| `features/inicio/models/mes-resumen.model.ts` | nuevo | (sin lógica) |
| `features/inicio/models/cuenta-resumen.model.ts` | nuevo (`cuentas` y `saldos`) | (sin lógica) |
| `features/inicio/models/metas-resumen.model.ts` | nuevo | (sin lógica) |
| `features/inicio/models/ingresos-gastos-resumen.model.ts` | nuevo | (sin lógica) |
| `features/inicio/models/pagina-resumen.model.ts` | nuevo (`totalElementos`) | (sin lógica) |
| `features/inicio/services/resumen-lectura.service.ts` | nuevo (6 `GET`) | `resumen-lectura.service.spec.ts` |
| `features/inicio/services/cargador-tarjeta.ts` | nuevo (estado, reintento, descarte) | `cargador-tarjeta.spec.ts` |
| `features/inicio/services/resumen-inicio.store.ts` | nuevo (los 6 cargadores) | `resumen-inicio.store.spec.ts` |
| `features/inicio/services/resumen-inicio.ts` | nuevo (funciones puras) | `resumen-inicio.spec.ts` |
| `features/inicio/services/mensajes-inicio.ts` | nuevo (textos fijos) | `mensajes-inicio.spec.ts` |
| `features/inicio/components/tarjeta-resumen.component.{ts,html,scss}` | nuevo | `tarjeta-resumen.component.spec.ts` |
| `features/cuentas/pages/cuentas.page.spec.ts` | **solo se agrega** un `it` (test de contrato D4) | el mismo archivo |
| `AGENTS.md` (raíz) | sección "Inicio (frontend)" | (documentación) |

Archivos compartidos (`core/`, `shared/`, `app.routes.ts`): **ninguno** se modifica.

## Risks / Trade-offs

- **Siete peticiones al abrir.** Son `GET` ligeros y en paralelo; el mes con ocultas y las
  transacciones con `size=1` son lo más grande/barato respectivamente. Un resumen en un solo
  endpoint sería mejor y queda como mejora del backend.
- **El saldo total repite una regla** (D4); el test de contrato avisa si divergen, pero no la
  comparte en código.
- **Las ocultas cuentan como sobregastadas** y el enlace lleva al mes **sin** `Mostrar ocultas`:
  la persona puede no ver la categoría que cuenta la tarjeta. Queda anotado; no se cambia esa
  pantalla.
- **Mes fijo al abrir** (D8).
