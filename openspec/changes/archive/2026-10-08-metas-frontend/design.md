# Design

## Context

Ver proposal.md - Why. Estado observado:

- Backend (`com.presupuesto.meta`, integrado; spec `metas`):
  - `MetaController` bajo `/api/v1/presupuestos/{presupuestoId}`: `PUT/GET/DELETE
    /categorias/{categoriaId}/meta` (200, 200 o 404, 204 o 404) y `GET /metas`.
  - `MetaMesController` bajo `.../meses/{mes}`: `GET /metas?incluirOcultas`,
    `POST /metas/{categoriaId}/posponer` y `/reanudar` (sin cuerpo, idempotentes, responden el
    elemento del mes) y `POST /auto-asignar`.
  - `GuardarMetaRequest { tipo, monto, frecuencia, diaSemana, intervaloDias, fechaInicio,
    fechaObjetivo }`; `TipoMeta` = `MONTO_MENSUAL | MONTO_PARA_FECHA | SALDO_OBJETIVO`;
    `FrecuenciaMeta` = `SEMANAL | MENSUAL | PERSONALIZADA`; `diaSemana` 1 a 7, `intervaloDias`
    2 a 365. Los campos que no aplican se descartan; los que faltan responden `400` con
    `errores` por campo.
  - `MetasMesResponse { mes, totalFaltante, metas }` y `MetaMesResponse { categoriaId, nombre,
    tipo, monto, necesidad, asignado, disponible, faltante, estado }`; `EstadoMeta` =
    `SOBREGASTADA | POSPUESTA | FALTA | FINANCIADA` en ese orden de prioridad. No hay campo
    `pospuesta` ni `metaId`.
  - `AutoAsignarRequest { estrategia, categoriaIds?, simular? }` y `AutoAsignarResponse
    { aplicado, listoParaAsignarAntes, listoParaAsignarDespues, cambios: [{ categoriaId, nombre,
    asignadoAntes, asignadoDespues }] }`. Sin `categoriaIds`, todas las visibles salvo las de
    pago de tarjeta; `[]` responde `400`.
  - Errores: `400 DATOS_INVALIDOS`, `404 RECURSO_NO_ENCONTRADO`, `409 CONFLICTO` (meta cambiada
    al mismo tiempo). **No hay `422`** en esta feature: las categorías ocultas y las de pago de
    tarjeta admiten meta (requisito `Las categorías de pago de tarjeta admiten meta`).
- Frontend (`features/presupuesto-mensual/`):
  - `PresupuestoMensualPage` carga `GET .../meses/{mes}` con `toObservable(peticion)` +
    `switchMap` (`peticion` = presupuesto, mes, `incluirOcultas` y `recargas`), asigna en el
    lugar de forma optimista y abre `DialogoMoverDineroComponent` (`{ data, width: '440px' }`,
    resultado `{ tipo: 'movido', mes } | { tipo: 'recargar' }`).
  - `GrupoMesComponent` pinta cada fila (`div.fila.categoria`, `col-nombre` con el menú
    `more_vert`, `app-celda-asignado`, actividad, disponible) y emite `asignar`, `moverDinero` y
    `cubrirSobregasto`; el menú de la fila tiene `Mover dinero...` y `Cubrir sobregasto`.
  - En pantallas estrechas la grilla pasa a 3 columnas y `.col-nombre` ocupa toda la fila.
  - `CategoriaMesResponse` del frontend no tiene `esPagoTarjeta` ni `cuentaId` (el backend sí).

### Diferencias con el pedido original

- Los tipos se llaman `MONTO_MENSUAL` (con frecuencia), `MONTO_PARA_FECHA` y `SALDO_OBJETIVO`.
- Posponer y reanudar usan `{categoriaId}`, no un `metaId` (no existe en la API).
- La vista previa se pide con `simular: true`, no con un campo `modo`.
- Las categorías de pago de tarjeta no tienen protecciones `422` en metas: el backend las admite.

## Goals / Non-Goals

**Goals:**
- Todo dentro de `features/presupuesto-mensual`: ningún archivo compartido cambia, para no chocar
  con quien trabaja en paralelo.
- Las reglas de presentación (progreso, texto del estado, mes pasado) en funciones puras
  probadas caso por caso.

**Non-Goals:**
- Sección propia de metas, entrada en el menú lateral o ruta nueva.
- Crear metas desde Categorías.
- Recalcular la necesidad en el cliente: siempre se usa la del backend.

## Decisions

**Dónde se crea la meta: solo en el mes.** Es donde se ve cuánto falta y donde la meta tiene
sentido. Ofrecerla también en Categorías obligaría a duplicar el diálogo en
`features/categorias` (una feature no importa de otra) o a mover un diálogo de negocio a
`shared/`, que no conoce features. Se descarta; si hace falta, otro change puede enlazar desde
Categorías al mes.

**`GET /metas` queda fuera.** La lista no trae datos del mes (ni necesidad ni estado), así que no
reemplaza a `GET /meses/{mes}/metas`, y para editar basta `GET /categorias/{id}/meta`. Un
resumen de todas las metas a lo largo de los meses es el reporte de metas de
`reportes-frontend`.

**Carga.** La página pide en el mismo `switchMap` `forkJoin([mes, metasDelMes.pipe(catchError(
() => of(null)))])` con el mismo `incluirOcultas`: así ambas respuestas son siempre del mismo
pedido y una atrasada se descarta junta. `metas = signal<MetasMesResponse | null>`;
`errorMetas = signal(false)` cuando el segundo valor es `null` (aviso con `Reintentar` →
`recargar()`). La asignación optimista no toca las metas (sus números se refrescan con la
próxima carga); tras asignar en el lugar no se recargan para no deshacer la edición rápida.
`metasPorCategoria = computed(Map<categoriaId, MetaMesResponse>)` se pasa a `app-grupo-mes`.

**Indicador.** `IndicadorMetaComponent` (`app-indicador-meta`, entradas `meta`, `moneda` y
`mesPasado`) debajo del nombre, dentro de `col-nombre`, sin columna nueva: en pantallas
estrechas `col-nombre` ya ocupa toda la fila. Usa `mat-progress-bar` en modo `determinate`. La
función pura `presentacionMeta(meta, mesPasado)` (en `services/presentacion-meta.ts`) devuelve
`{ texto, icono, tono: 'ok' | 'falta' | 'neutro' | 'error', progreso }`:
- `progreso = necesidad === 0 ? 100 : clamp(round(asignado * 100 / necesidad), 0, 100)` en
  enteros (sin coma flotante en el dinero; el porcentaje solo se usa para la barra).
- `FINANCIADA` → `Financiada`, `ok`; `POSPUESTA` → `Pospuesta este mes`, `neutro`;
  `SOBREGASTADA` → `Sobregastada`, `error`; `FALTA` → `Falta {faltante}` (`falta`) o, si
  `mesPasado`, `Faltaron {faltante}` (`neutro`).
`mesPasado = mes < mesActual()` (comparación de `yyyy-MM`). El texto de montos usa `MontoPipe`.

**Meses anteriores a la meta.** El backend aplica la meta vigente a todos los meses porque no
hay historial; mostrar un faltante rojo en meses viejos parecería un error real. Por eso en los
meses pasados `FALTA` va en tono neutro y el diálogo avisa que la meta se aplica a todos los
meses.

**Ocultas.** Siguen al interruptor `Mostrar ocultas` del mes (mismo `incluirOcultas`), así el
`totalFaltante` coincide con lo que se ve.

**Categorías de pago de tarjeta.** Se tratan como cualquier otra (el backend las admite). Solo en
auto-asignar se explica que `Todas las categorías visibles` no las incluye y, en `Elegir
categorías`, se marcan `(pago de tarjeta)` usando `esPagoTarjeta`, que se agrega a
`CategoriaMesResponse`.

**Menú de la fila.** `GrupoMesComponent` gana la entrada `metas` (mapa) y las salidas
`editarMeta`, `posponerMeta` y `reanudarMeta` (con la categoría). El menú agrega
`Agregar meta`/`Editar meta` y, con meta, `Posponer este mes`/`Reanudar este mes`. Con
`SOBREGASTADA` se ofrecen ambas: el backend no dice si está pospuesta y las dos son idempotentes.

**Diálogo de meta.** `DialogoMetaComponent` recibe `{ categoriaId, nombre, tieneMeta }`; con meta
pide `obtener` (señal `cargando`; `404` → aviso y cierre `recargar`). Formulario: `tipo`,
`monto` (`app-campo-monto`, `Validators.required` y mayor que 0), `frecuencia`, `diaSemana`,
`intervaloDias` (`Validators.min(2)`, `max(365)`, entero), `fechaInicio`, `fechaObjetivo`
(datepicker con `AdaptadorFechaRegional`). Una suscripción a `tipo` y `frecuencia` habilita solo
los controles del tipo (los demás `disable()`, así no validan ni se envían) y la función pura
`aGuardarMetaRequest(valor)` (en `services/guardar-meta.ts`) arma el cuerpo con solo los campos
del tipo y las fechas con `aFechaNegocio`. `Quitar meta` abre `DialogoConfirmacionMetaComponent`
(propio de la feature: no se importa el de transacciones) y envía `DELETE`. Resultado:
`{ tipo: 'guardada' } | { tipo: 'quitada' } | { tipo: 'recargar' }`; cualquiera → `recargar()`.

**Errores.** Por `codigo`, como en `transacciones-frontend`: `DATOS_INVALIDOS` con
`aplicarErroresDeCampos`; `REGLA_NEGOCIO_VIOLADA` (no esperado, por robustez) y `CONFLICTO` como
mensaje en el diálogo; `RECURSO_NO_ENCONTRADO` aviso + cierre `recargar`; otro, aviso genérico.
`CONFLICTO` se agrega a la lógica del diálogo usando `CODIGOS_API.CONFLICTO`, que ya existe.

**Posponer y reanudar.** La página llama a `MetaService` y en `next` o `error` (`404` con aviso
`La categoría ya no tiene meta`) hace `recargar()`. Mientras la petición está en curso, las
acciones de esa fila quedan deshabilitadas (señal `metasEnCurso: ReadonlySet<number>`).

**Auto-asignar.** Botón `Auto-asignar` junto a `Mostrar ocultas`, deshabilitado sin datos del
mes. `DialogoAutoAsignarComponent` recibe `{ mes, categorias }` (las del mes cargado con
`esPagoTarjeta` y si tienen meta). Formulario: `estrategia` (obligatoria), `alcance`
(`'todas' | 'elegidas'`), `elegidas` (`FormControl<number[]>`). Validador del grupo: con
`elegidas` y lista vacía → error `sinCategorias`. `previa = signal<AutoAsignarResponse | null>`
que se vacía con cualquier `valueChanges`. `Vista previa` → `simular: true`; `Aplicar` → mismo
cuerpo (`categoriaIds` solo con `elegidas`) con `simular: false`, y cierra con
`{ tipo: 'aplicado', cambios: n }`; la página avisa `Se actualizaron {n} categorías` con la
respuesta real y recarga. La vista previa es informativa: si el mes cambió en medio, lo aplicado
es lo que diga la respuesta. Botones deshabilitados mientras se envía.

**Accesibilidad y foco.** `MatDialog` lleva el foco al primer campo (`cdkFocusInitial` en
`Tipo` y `Estrategia`) y lo restaura al cerrar (`restoreFocus` por defecto). Estados con texto.

**Mensajes.**
- `MENSAJE_CONFLICTO_META = 'La meta cambió al mismo tiempo; vuelve a guardar'`
- `MENSAJE_META_INEXISTENTE = 'La categoría o su meta ya no existe. Actualizamos el mes.'`
- `MENSAJE_SIN_CATEGORIAS = 'Elige al menos una categoría'`

### Archivos (desde `frontend/src/app/`)

| Archivo nuevo o modificado | Test |
|---|---|
| `features/presupuesto-mensual/models/meta-response.model.ts` (nuevo; `TipoMeta`, `FrecuenciaMeta`) | — (interfaz) |
| `features/presupuesto-mensual/models/guardar-meta-request.model.ts` (nuevo) | — (interfaz) |
| `features/presupuesto-mensual/models/metas-mes-response.model.ts` (nuevo; `MetaMesResponse`, `EstadoMeta`) | — (interfaz) |
| `features/presupuesto-mensual/models/auto-asignar.model.ts` (nuevo; request, response, `EstrategiaAutoAsignar`) | — (interfaz) |
| `features/presupuesto-mensual/models/datos-dialogos-metas.model.ts` (nuevo; datos y resultados) | — (interfaz) |
| `features/presupuesto-mensual/models/categoria-mes-response.model.ts` (+`esPagoTarjeta`, `cuentaId`) | — |
| `features/presupuesto-mensual/services/meta.service.ts` (nuevo) | `features/presupuesto-mensual/services/meta.service.spec.ts` |
| `features/presupuesto-mensual/services/presentacion-meta.ts` (nuevo) | `features/presupuesto-mensual/services/presentacion-meta.spec.ts` |
| `features/presupuesto-mensual/services/guardar-meta.ts` (nuevo) | `features/presupuesto-mensual/services/guardar-meta.spec.ts` |
| `features/presupuesto-mensual/components/indicador-meta.component.ts` (+ html, scss; nuevo) | `features/presupuesto-mensual/components/indicador-meta.component.spec.ts` |
| `features/presupuesto-mensual/components/dialogo-meta.component.ts` (+ html, scss; nuevo) | `features/presupuesto-mensual/components/dialogo-meta.component.spec.ts` |
| `features/presupuesto-mensual/components/dialogo-confirmacion-meta.component.ts` (nuevo, plantilla en línea) | `features/presupuesto-mensual/components/dialogo-confirmacion-meta.component.spec.ts` |
| `features/presupuesto-mensual/components/dialogo-auto-asignar.component.ts` (+ html, scss; nuevo) | `features/presupuesto-mensual/components/dialogo-auto-asignar.component.spec.ts` |
| `features/presupuesto-mensual/components/grupo-mes.component.ts` (+ html, scss) | `features/presupuesto-mensual/components/grupo-mes.component.spec.ts` |
| `features/presupuesto-mensual/pages/presupuesto-mensual.page.ts` (+ html, scss) | `features/presupuesto-mensual/pages/presupuesto-mensual.page.spec.ts` |

Archivos compartidos tocados: ninguno (`app.routes.ts`, el layout del menú lateral, `core/` y
`shared/` no cambian). Fuera de `src/app`, solo `AGENTS.md`.

## Risks / Trade-offs

- **Pospuesta y sobregastada no se distinguen** → con `SOBREGASTADA` se ofrecen posponer y
  reanudar (idempotentes); el texto del estado es `Sobregastada`, que es lo urgente.
- **Una petición más por carga del mes** → se piden en paralelo con `forkJoin`; si las metas
  fallan, el mes sigue usable.
- **La asignación en el lugar no refresca el indicador al instante** → el indicador se actualiza
  en la próxima carga (cambio de mes, recarga u otra operación); se acepta para no recargar el
  mes en cada edición rápida. Si molesta, un change posterior puede recalcular solo las metas
  tras asignar.
- **Meses anteriores a la meta** → se muestran en tono neutro con la aclaración en el diálogo; el
  backend no tiene historial para hacerlo mejor.
- **Conflicto con otros changes en `presupuesto-mensual`** (por ejemplo, tarjetas de crédito) →
  los cambios se limitan a agregar entradas y ramas del menú; quien aplique segundo integra.
