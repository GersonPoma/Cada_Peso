# Design

## Context

Ver proposal.md - Why. Estado observado:

- Backend (`com.presupuesto.transaccionprogramada`, integrado; spec `transacciones-programadas`),
  bajo `/api/v1/presupuestos/{presupuestoId}/transacciones-programadas`
  (`TransaccionProgramadaController`):
  - `POST` (201) con `CrearProgramadaRequest { cuentaId, fechaInicio, frecuencia, fechaFin,
    monto, categoriaId, beneficiario, memo }` (`@NotNull` en cuenta, fechaInicio, frecuencia y
    monto; `@MontoNoCero`; `@Size` 100 y 500; recorta y pasa vacíos a `null`).
  - `GET` (`?soloActivas`, por defecto `false`) → lista sin paginar, por `proximaFecha`
    ascendente, finalizadas al final, luego id. `GET /{id}`.
  - `PUT /{id}` con `ActualizarProgramadaRequest { monto, categoriaId, beneficiario, memo,
    frecuencia, fechaFin }`; ignora `cuentaId` y `fechaInicio`.
  - `DELETE /{id}` (204); las generadas quedan con `programadaId = null`.
  - `POST /{id}/pausar` y `/{id}/reanudar` (200, idempotentes; reanudar limpia `ultimoError` y
    salta lo pausado).
  - `POST /generar` (200) → `GeneracionResponse { generadas, plantillasConError }`.
  - Respuesta `TransaccionProgramadaResponse { id, cuentaId, fechaInicio, frecuencia, fechaFin,
    monto, categoriaId, beneficiario, memo, activa, proximaFecha, ultimoError, fechaCreacion,
    fechaActualizacion }`.
  - Errores: presupuesto, plantilla, cuenta y categoría `404`; `400 DATOS_INVALIDOS` con
    `errores` (Bean Validation) o sin `errores` (`fechaFin < fechaInicio`, del service); `422`
    por cuenta cerrada o categoría de pago de tarjeta.
  - `ultimoError` es el mensaje en español de la `NegocioException` que falló, o un mensaje fijo
    de error inesperado: es dato para mostrar, no un `ProblemDetail`.
- Frontend:
  - El menú lateral sale de las rutas hijas con `data.seccion`: una sección = una ruta.
  - `features/transacciones/components/dialogo-transaccion.component` ya resuelve
    `Salida`/`Entrada` con `mat-button-toggle`, `app-campo-monto` en positivo, datepicker con
    `aFechaNegocio`/`deFechaNegocio`, `aplicarErroresDeCampos`, `sobreTextoRecortado` y
    `MENSAJE_ERROR_GENERICO`: se sigue el mismo patrón (no se importa: es de otra feature).
  - `app-campo-beneficiario` y su `BeneficiarioLecturaService` viven en `features/transacciones`.
  - `TransaccionResponse` del frontend ya tiene `programadaId`.
  - Lectura de cuentas y de categorías con `esPagoTarjeta`: la hacen `conciliacion` y
    `transacciones` con servicios propios (duplicación a propósito, regla de AGENTS).

## Goals / Non-Goals

**Goals:**
- Tocar un solo archivo compartido (`app.routes.ts`, + su spec).
- Ninguna regla del calendario del backend replicada en el cliente.
- Estado, frecuencia y textos del resultado en funciones puras probadas sin TestBed.

**Non-Goals:**
- Filtrar por `soloActivas` (la lista es corta; el estado se ve en cada fila).
- Mostrar las transacciones generadas por cada plantilla (la lista de transacciones no filtra
  por `programadaId`).

## Decisions

**Ruta y nombre.** Feature `features/transacciones-programadas/` (mismo nombre que el backend) y
ruta corta `programadas` con `data: seccion('Programadas', 'event_repeat')`, después de
`transacciones` en `app.routes.ts`. `loadComponent`, como el resto.

**Sin vista de próximas fechas en el cliente.** Calcular "las próximas 3 fechas" exige el mismo
algoritmo que `CalendarioProgramado` (ocurrencia n desde `fechaInicio`, último día del mes,
bisiestos) y, para una plantilla existente, saber cuál fue la última generada (no viene en la
respuesta). Duplicarlo arriesga mostrar fechas que el generador no usará; la API no ofrece una
vista previa. Se muestra la `proximaFecha` del servidor y, en el diálogo, la regla de fin de mes
como texto (`necesitaAvisoFinDeMes(frecuencia, fechaInicio)`: frecuencia en meses y día ≥ 29).
Si se quiere la vista previa, el camino es un endpoint del backend.

**Beneficiario como texto libre.** `matInput` con `maxlength` y `sobreTextoRecortado(
Validators.maxLength(100))`. Alternativas descartadas: importar `app-campo-beneficiario` (rompe
la regla de features), copiarlo (versión paralela) o moverlo a `shared/` con su servicio (toca
archivos de `transacciones` que otras personas editan). El backend crea o vincula el beneficiario
al generar, así que no hay pérdida de datos; el autocompletado queda como mejora cuando el campo
se mueva a `shared/` en un change propio.

**Indicador de "programada" en Transacciones: no en este change.** Es viable (el campo ya llega)
pero obliga a editar `tabla-transacciones.component.{html,ts}` y su spec, que son de otra
feature en uso; se documenta como mejora de una línea (ícono `event_repeat` con
`aria-label="Generada por una programada"`).

**Página (`ProgramadasPage`).** Carga con `switchMap` sobre `{ presupuestoId, recargas }`:
`forkJoin([programadas, cuentas, categorias])` → `EstadoPantalla = cargando | listo | error`
(`toSignal`). Mapas `Map<id, nombre>` para cuenta y categoría con `computed`. `Reintentar` y cada
recarga emiten en `recargas`; `switchMap` descarta respuestas atrasadas. Las acciones de fila
usan `ocupadas = signal<Set<number>>` para deshabilitar los botones de la plantilla en curso; la
respuesta de pausar/reanudar reemplaza la fila (`filas.update`) sin recargar todo. Lista con
`mat-table` en pantallas anchas y, bajo 600 px (`BreakpointObserver` del CDK), tarjetas
(`mat-card`) con los mismos datos; ambas leen las mismas `FilaProgramada` armadas por una
función pura.

**Funciones puras** (`services/presentacion-programada.ts`):
- `textoFrecuencia(frecuencia)`.
- `estadoProgramada(p)` → `{ estado: 'error' | 'pausada' | 'finalizada' | 'activa', texto,
  icono, tono }` en el orden de la spec (`error_outline`, `pause_circle`, `event_available`,
  `schedule`).
- `tipoMonto(monto)` → `'salida' | 'entrada'` y `montoAbsoluto(monto)` (enteros).
- `textoResultadoGeneracion(r)` con singular y plural.
- `necesitaAvisoFinDeMes(frecuencia, fechaInicio: Date | null)`.
- `filaProgramada(p, cuentas, categorias)` para la tabla y las tarjetas.

**Diálogo (`DialogoProgramadaComponent`).** `DatosDialogoProgramada { presupuestoId, cuentas,
grupos, original?: TransaccionProgramadaResponse }`. `FormGroup` tipado: `cuentaId`, `tipo`,
`monto` (`required`, `montoValido()`, `min(1)`), `frecuencia`, `fechaInicio`, `fechaFin`,
`categoriaId`, `beneficiario`, `memo`, con validador de grupo `finNoAnteriorAInicio`
(compara `aFechaNegocio` de ambos como texto `yyyy-MM-dd`). En edición, `cuentaId` y
`fechaInicio` quedan `disable()` y se muestran como texto. El cuerpo se arma con funciones puras
`aCrearRequest(valor)` y `aActualizarRequest(valor)` (`monto * signo`, `aFechaNegocio`,
`trim() || null`). Opciones de categoría: grupos y categorías no ocultos, sin `esPagoTarjeta`,
más la categoría actual si estaba oculta. Cuentas: abiertas (en edición, el nombre de la cuenta
original aunque esté cerrada). `enviando = signal(false)` deshabilita `Guardar` y evita doble
envío (`if (enviando()) return`). Cierra con la `TransaccionProgramadaResponse` guardada; la
página recarga.

**Errores** (`services/errores-programada.ts`, sobre `leerProblemaApi`): devuelve
`{ accion: 'campos' | 'mensaje' | 'recargar' | 'generico', ... }`; el diálogo aplica
`aplicarErroresDeCampos` o muestra el mensaje en un `role="alert"` y, con `recargar`, cierra con
`'recargar'`. Mensajes en `services/mensajes-programada.ts`.

**Confirmación de borrado.** `DialogoConfirmarBorradoComponent` propio (texto con las
transacciones conservadas, `cdkFocusInitial` en `Cancelar`), como en `conciliacion`.

**Generar ahora.** `generando = signal(false)`; resultado en `MatSnackBar` con
`politeness: 'polite'` y además en un `role="status"` visible hasta la próxima acción; luego
`recargas.next()`.

### Archivos (desde `frontend/src/app/`)

| Archivo nuevo o modificado | Test |
|---|---|
| `features/transacciones-programadas/models/transaccion-programada-response.model.ts` (nuevo; con `FrecuenciaProgramada`) | — (interfaz) |
| `features/transacciones-programadas/models/crear-programada-request.model.ts` (nuevo) | — (interfaz) |
| `features/transacciones-programadas/models/actualizar-programada-request.model.ts` (nuevo) | — (interfaz) |
| `features/transacciones-programadas/models/generacion-response.model.ts` (nuevo) | — (interfaz) |
| `features/transacciones-programadas/models/cuenta-lectura.model.ts` (nuevo) | — (interfaz) |
| `features/transacciones-programadas/models/grupo-categorias-lectura.model.ts` y `categoria-lectura.model.ts` (nuevos, con `esPagoTarjeta`) | — (interfaz) |
| `features/transacciones-programadas/models/datos-dialogo-programada.model.ts` (nuevo) | — (interfaz) |
| `features/transacciones-programadas/services/transaccion-programada.service.ts` (nuevo) | `features/transacciones-programadas/services/transaccion-programada.service.spec.ts` |
| `features/transacciones-programadas/services/cuenta-lectura.service.ts` y `categoria-lectura.service.ts` (nuevos) | `features/transacciones-programadas/services/lectura.service.spec.ts` |
| `features/transacciones-programadas/services/presentacion-programada.ts` (nuevo) | `features/transacciones-programadas/services/presentacion-programada.spec.ts` |
| `features/transacciones-programadas/services/formulario-programada.ts` (nuevo; `aCrearRequest`, `aActualizarRequest`, `finNoAnteriorAInicio`, `opcionesCategoria`) | `features/transacciones-programadas/services/formulario-programada.spec.ts` |
| `features/transacciones-programadas/services/errores-programada.ts` (nuevo) | `features/transacciones-programadas/services/errores-programada.spec.ts` |
| `features/transacciones-programadas/services/mensajes-programada.ts` (nuevo; constantes) | — (constantes) |
| `features/transacciones-programadas/components/dialogo-programada.component.ts` (+ html, scss) | `features/transacciones-programadas/components/dialogo-programada.component.spec.ts` |
| `features/transacciones-programadas/components/dialogo-confirmar-borrado.component.ts` | `features/transacciones-programadas/components/dialogo-confirmar-borrado.component.spec.ts` |
| `features/transacciones-programadas/pages/programadas.page.ts` (+ html, scss) | `features/transacciones-programadas/pages/programadas.page.spec.ts` |
| `app.routes.ts` (ruta `programadas` con `seccion('Programadas', 'event_repeat')`) | `app.routes.spec.ts` |

**Archivos compartidos tocados, exactamente:** `app.routes.ts` y `app.routes.spec.ts`. No
cambian `LayoutPresupuestoPage`, `core/`, `shared/` ni otras features. Fuera de `src/app`, solo
`AGENTS.md`.

## Risks / Trade-offs

- **Conflicto en `app.routes.ts`** con otros changes en curso → una entrada en las hijas; quien
  integre segundo ordena.
- **`ultimoError` en texto del backend** → es el mensaje de negocio en español pensado para la
  persona; si es el genérico de error inesperado, la sugerencia de revisar cuenta y categoría
  sigue valiendo.
- **Sin autocompletado de beneficiario** → riesgo de escribir variantes del mismo nombre; el
  backend normaliza al vincular y la pantalla de beneficiarios permite fusionar.
- **Estado leído antes de que corra el generador** → una plantilla con `proximaFecha` pasada se
  ve `Activa` hasta la siguiente generación; `Generar ahora` lo resuelve al momento.
- **Cuenta cerrada después de crear la plantilla** → en edición la cuenta se muestra igual
  (solo lectura); guardar cualquier cambio puede dar `422` y se explica en el diálogo.

## Migration Plan

Solo frontend. Revertir es quitar la ruta y la carpeta de la feature.
