# Design

## Context

Ver proposal.md - Why. Estado observado:

- Backend (`com.presupuesto.transaccion`, integrado):
  - `CrearTransferenciaRequest { cuentaOrigenId, cuentaDestinoId, fecha, monto, categoriaId,
    memo }` (`monto` `@Positive`, memo recortado, máximo 500).
  - `ActualizarTransferenciaRequest { fecha, monto, categoriaId, memo }`.
  - `TransferenciaResponse { salida, entrada }`, ambos `TransaccionResponse`.
  - `POST /transferencias` (201), `GET/PUT/DELETE /transferencias/{transaccionId}` con el id de
    cualquiera de las dos patas; 404 si no es transferencia, 422 por cuenta cerrada, pata
    reconciliada o regla de categoría.
- Frontend (`features/transacciones/`):
  - `accionesDe(transaccion, cuentaCerrada)` bloquea `editar` y `borrar` de una pata con
    `Es parte de una transferencia`; el menú de la tabla pinta `Editar` y `Borrar` con esas
    acciones.
  - `DialogoConfirmacionComponent` genérico (`titulo`, `mensaje`, `confirmar`), reutilizable.
  - `DialogoTransaccionComponent` es el patrón a seguir: `gruposCuentas`, `hoy()` local,
    `app-campo-monto` con `mayorQueCero`, `sobreTextoRecortado`, petición propia y cierre con
    `{ tipo: 'guardada' } | { tipo: 'recargar' }`.
  - La página abre los diálogos con `abrir(...)` y recarga con `recargar()` (página y saldos) y
    `recargasListas` (cuentas y categorías).

## Goals / Non-Goals

**Goals:**
- Que la regla de categoría viva en una función pura probada en las cuatro combinaciones.
- No hacer peticiones por fila: todo lo que necesita la tabla sale de la página cargada.

**Non-Goals:**
- Cambiar las cuentas de una transferencia existente (el backend no lo permite).
- Monedas distintas, programadas, conciliación guiada, deshacer.
- Cambiar el lote (ya excluye las patas de `BORRAR` y `CATEGORIZAR`).

## Decisions

**Regla de categoría: función pura** en
`features/transacciones/services/regla-categoria-transferencia.ts`:
`reglaCategoriaTransferencia(origen: CuentaResumen | null, destino: CuentaResumen | null):
'oculta-presupuesto' | 'oculta-seguimiento' | 'obligatoria' | 'opcional' | 'pendiente'`.
`pendiente` mientras falte una cuenta (campo oculto, sin texto). El diálogo la expone como
`computed` sobre señales de origen y destino (`toSignal` de los `valueChanges`) y, con un
`effect` o una suscripción, ajusta el control de categoría: `obligatoria` →
`setValidators(Validators.required)`; otra → sin validadores; `oculta-*` y `pendiente` →
`setValue(null)`. La petición manda `categoriaId` solo en `obligatoria` y `opcional` (en
`opcional`, `null` si no se eligió); en las ocultas la clave no se envía.

**Exclusión mutua de cuentas.** `opcionesOrigen` y `opcionesDestino` son `computed` sobre las
cuentas abiertas, cada una sin la elegida en el otro select, agrupadas igual que en el diálogo
de transacción. Un validador de grupo `origenDistintoDeDestino` respalda la regla (por si llegan
iguales desde la preselección). `Invertir` hace `patchValue({ origen: destino, destino: origen })`
y se oculta al editar.

**Edición.** El diálogo recibe `{ transaccionId | null, cuentaOrigenId | null, cuentas, grupos }`.
Con `transaccionId` pide `obtener` al abrir (señal `cargando`), rellena con
`salida.cuentaId`/`entrada.cuentaId` (controles deshabilitados), `deFechaNegocio(salida.fecha)`,
`entrada.monto`, `salida.categoriaId ?? entrada.categoriaId` y `salida.memo`. Las cuentas
cerradas de una transferencia existente se agregan a las opciones solo para mostrarse.

**Acciones de la pata.** `accionesDe` gana un tercer parámetro opcional `par: TransaccionResponse
| undefined` y `cuentaParCerrada: boolean`, y las acciones nuevas `editarTransferencia` y
`borrarTransferencia`:
- Para una pata, `editar` y `borrar` quedan `oculta` y las de transferencia se calculan:
  reconciliada (pata o par) → `bloqueada('Una de las transacciones está reconciliada')`; cuenta o
  cuenta par cerrada → `bloqueada('La cuenta está cerrada')`; si no, `disponible`.
- Para una transacción normal, `editarTransferencia` y `borrarTransferencia` quedan `oculta`.
La tabla calcula `pares = computed(() => new Map(transacciones().map(t => [t.id, t])))` y le
pasa a `accionesDe` el par y si su cuenta está cerrada. `TipoAccion` gana
`'editarTransferencia' | 'borrarTransferencia'`; el menú muestra `Editar transferencia` y
`Borrar transferencia` con `matTooltip` y el texto del motivo.

**Descripción de la pata.** En la tabla, `descripcion(t)`: si es pata, busca el par en `pares`;
con par → `Transferencia a/desde {nombresCuenta.get(par.cuentaId)}` según el signo de `t.monto`;
sin par → `Transferencia`. Si no es pata, el beneficiario como hasta ahora (o lo que defina
`beneficiarios-frontend` si se aplica antes).

**Página.** `agregarTransferencia()` abre el diálogo con `cuentaOrigenId` = filtro de cuenta si
está abierta. `manejarAccion` atiende `editarTransferencia` (abre el diálogo con el id de la
pata) y `borrarTransferencia` (confirmación y `TransferenciaService.borrar(id, t.id)` vía
`ejecutar`). Resultado `guardada` → `recargar()`; `recargar` → además `recargasListas`. El foco
vuelve solo: `MatDialog` restaura el foco al elemento que lo tenía al abrir (el botón de la fila
o el de agregar), con `restoreFocus` por defecto; un test lo comprueba.

**Mensajes.** `MENSAJE_REGLA_TRANSFERENCIA = 'No se pudo guardar: una de las cuentas está
cerrada, una de las transacciones está reconciliada o la categoría no corresponde.'`

### Archivos (desde `frontend/src/app/`)

| Archivo nuevo o modificado | Test |
|---|---|
| `features/transacciones/models/crear-transferencia-request.model.ts` (nuevo) | — (interfaz) |
| `features/transacciones/models/actualizar-transferencia-request.model.ts` (nuevo) | — (interfaz) |
| `features/transacciones/models/transferencia-response.model.ts` (nuevo) | — (interfaz) |
| `features/transacciones/models/datos-dialogos-transacciones.model.ts` (+`DatosDialogoTransferencia`) | — |
| `features/transacciones/services/transferencia.service.ts` (nuevo) | `features/transacciones/services/transferencia.service.spec.ts` |
| `features/transacciones/services/regla-categoria-transferencia.ts` (nuevo) | `features/transacciones/services/regla-categoria-transferencia.spec.ts` |
| `features/transacciones/services/acciones-transaccion.ts` | `features/transacciones/services/acciones-transaccion.spec.ts` |
| `features/transacciones/components/dialogo-transferencia.component.ts` (+ html, scss; nuevo) | `features/transacciones/components/dialogo-transferencia.component.spec.ts` |
| `features/transacciones/components/tabla-transacciones.component.ts` (+ html) | `features/transacciones/components/tabla-transacciones.component.spec.ts` |
| `features/transacciones/pages/transacciones.page.ts` (+ html) | `features/transacciones/pages/transacciones.page.spec.ts` |

## Risks / Trade-offs

- **El estado de la pata par fuera de la página no se conoce** → solo se bloquea con lo que hay
  en la página; si la otra pata está reconciliada o su cuenta cerrada, el backend responde 422 y
  el diálogo (o el aviso de la página) lo muestra.
- **Conflicto con `beneficiarios-frontend`** en la columna de beneficiario y la tabla → el que se
  aplique segundo integra ambos: la descripción de una pata gana sobre el beneficiario (las
  transferencias no llevan beneficiario).
- **Campo que aparece y desaparece** → al ocultarse se vacía para no enviar una categoría vieja;
  la persona tiene que volver a elegirla si regresa a una combinación que la pide.
