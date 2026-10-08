# Design

## Context

Ver proposal.md - Why. Estado observado:

- Backend (`com.presupuesto.conciliacion`, integrado; spec `conciliacion`), bajo
  `/api/v1/presupuestos/{presupuestoId}/cuentas/{cuentaId}/conciliacion`:
  - `GET /estado?saldoExtracto=&fecha=` (200) → `EstadoConciliacionResponse { cuentaId, fecha,
    saldoExtracto, saldoConciliado, saldoConciliadoAlCorte, diferencia, totalNoConciliadas,
    noConciliadas: TransaccionResponse[] }`. `diferencia = saldoExtracto -
    saldoConciliadoAlCorte`. `noConciliadas` trae **todas** las `NO_CONCILIADA` de la cuenta
    (sin filtrar por fecha), de la más reciente a la más antigua, como máximo 100;
    `totalNoConciliadas` las cuenta todas. No dice cuántas `CONCILIADA` se reconciliarán.
  - `POST` (201) con `CrearConciliacionRequest { saldoExtracto, fecha, crearAjuste, categoriaId }`
    (sin Bean Validation) → `ConciliacionResponse { id, cuentaId, fecha, saldoExtracto, ajuste,
    transaccionAjusteId, cantidadReconciliadas, fechaCreacion }`. Recalcula la diferencia; el
    ajuste es una transacción `CONCILIADA` y aprobada por la diferencia, con beneficiario
    `Ajuste de conciliación`, y entra en `cantidadReconciliadas`. Con diferencia 0 no crea ajuste.
  - `GET` (200) → historial de `ConciliacionResponse`, `fecha` y `id` descendentes, máximo 50.
    `PUT`/`DELETE` responden `405`.
  - Errores, en orden: presupuesto y cuenta `404`; `400 DATOS_INVALIDOS` **sin `errores`** por
    saldo o fecha ausentes o fecha futura (también en `/estado`); `422 REGLA_NEGOCIO_VIOLADA` por
    cuenta cerrada, diferencia sin `crearAjuste`, categoría en cuenta fuera del presupuesto,
    ajuste sin categoría cuando se exige (`enPresupuesto` y (`ajuste < 0` o `TARJETA_CREDITO`)) y
    categoría de pago de tarjeta; `404` por categoría inexistente. Estado e historial funcionan en
    cuentas cerradas.
- Frontend:
  - `CuentasPage` muestra cada cuenta con un menú (`Editar`, `Cerrar` o `Reabrir`) en
    `ng-template matMenuContent let-cuenta`.
  - `TransaccionesPage` lee los filtros de la URL (`cuentaId`, `estado`, `desde`, `hasta`...) con
    `filtros-url.ts` y tiene en el encabezado `Agregar transferencia` y `Agregar transacción`.
  - `CuentaResumen` y `CategoriaResumen` de `transacciones` no tienen `tipo` ni `esPagoTarjeta`;
    como una feature no importa de otra, la conciliación necesita sus propios modelos.
  - Patrón de espera e ignorar respuestas atrasadas: `campo-beneficiario.component.ts`
    (`debounceTime` + `distinctUntilChanged` + `switchMap` + `catchError`).
  - `app-campo-monto` acepta negativos; `aFechaNegocio` arma `yyyy-MM-dd` local.

## Goals / Non-Goals

**Goals:**
- Una feature propia (`features/conciliacion`) con su ruta, para no crecer más la pantalla de
  transacciones y tocar lo mínimo de las pantallas que usan otras personas.
- Validar en el cliente lo que el backend responde como `400` sin `errores` (saldo, fecha y fecha
  futura): esos `400` no se pueden atribuir a un campo y nunca se decide por `detail`.
- La regla de categoría del ajuste en una función pura probada caso por caso.

**Non-Goals:**
- Marcar transacciones como conciliadas desde el asistente (se hace en Transacciones).
- Mostrar de antemano cuántas `CONCILIADA` se reconciliarán: la API no lo da; se muestra después
  con `cantidadReconciliadas`.

## Decisions

**Punto de entrada: ruta propia y enlaces desde Cuentas y Transacciones.** Un diálogo dentro de
Transacciones obligaría a meter la lógica en esa feature y no serviría desde Cuentas; una
pantalla con ruta deja enlazar desde las dos sin importar nada entre features (solo
`routerLink`). La ruta va sin `data: seccion(...)`, así el menú lateral no cambia. Se ubica en
`app.routes.ts` después de `cuentas`:
`{ path: 'cuentas/:cuentaId/conciliacion', loadComponent: ... ConciliacionPage }`.

**Modelos y lectura propios.** `CuentaConciliacion { id, nombre, tipo, enPresupuesto, cerrada }`
leída con `GET .../cuentas/{id}` en `CuentaLecturaService` de la feature, y
`GrupoCategoriasLectura`/`CategoriaLectura` con `esPagoTarjeta` en `CategoriaLecturaService` de
la feature (mismo GET que el de transacciones, a propósito duplicado).

**Página (`ConciliacionPage`).** `cuentaId` de la ruta (`toSignal(paramMap)`). Carga con
`switchMap` sobre `{ presupuestoId, cuentaId, recargas }`: `forkJoin([cuenta, categorias])`
(error `404` → aviso y `router.navigate(['..'])` a Cuentas; otro → estado `error` con
`Reintentar`) y, aparte, el historial (`switchMap` sobre `{..., recargasHistorial}` con su propio
estado `cargando | listo | error`, para que un error del historial no tape el asistente).

**Asistente.** Formulario `saldoExtracto: FormControl<number | null>` (`required`), `fecha:
FormControl<Date | null>` (`required` + validador `noFutura` que compara con la fecha local de
hoy), `crearAjuste: FormControl<boolean>` y `categoriaId: FormControl<number | null>`. La consulta
del estado:
`merge(saldo.valueChanges, fecha.valueChanges).pipe(startWith(null), map(() => valorSiValido()),
debounceTime(300), distinctUntilChanged(iguales), switchMap(v => v ? servicio.estado(...).pipe(
map(e => ({ v, e })), catchError(() => of({ v, error: true }))) : of(null)))` en `toSignal`. La
vista solo usa el estado si su `v` coincide con los valores actuales, así nunca se muestra una
diferencia de otros datos; `Reintentar` re-emite con un `Subject`. La función pura
`textoDiferencia(diferencia)` devuelve `{ texto, tono }`.

**No conciliadas hasta la fecha.** `noConciliadasHasta(estado)` cuenta en `estado.noConciliadas`
las de `fecha <= estado.fecha` (comparando `yyyy-MM-dd`) y devuelve `{ cantidad, minimo }`, con
`minimo` verdadero si `noConciliadas.length < totalNoConciliadas` (la lista se recorta a 100
desde la más reciente, así que puede faltar alguna antigua). El enlace usa `routerLink` a
`['/presupuestos', id, 'transacciones']` con `queryParams { cuentaId, estado: 'NO_CONCILIADA',
hasta }`, que `filtros-url.ts` ya entiende.

**Regla del ajuste: función pura** `reglaCategoriaAjuste(cuenta, diferencia)` en
`services/regla-categoria-ajuste.ts`:
`'sin-ajuste'` (diferencia 0) | `'no-admite'` (`!enPresupuesto`) | `'obligatoria'`
(`enPresupuesto` y (`diferencia < 0` o `tipo === 'TARJETA_CREDITO'`)) | `'opcional'`. Igual que
`exigeCategoria` del backend. Una suscripción ajusta `categoriaId`: `obligatoria` →
`Validators.required`; `sin-ajuste` y `no-admite` → `setValue(null)` y sin validadores. Las
opciones de categoría excluyen las ocultas (y grupos ocultos) y las de pago de tarjeta (`422`
en el backend).

**Cuándo se puede reconciliar.** `puedeReconciliar = computed(...)`: cuenta abierta, formulario
válido, estado vigente cargado y (`diferencia === 0` o `crearAjuste`); si no, el botón queda
deshabilitado con su texto. Es el reflejo en el cliente del `422` por diferencia sin ajuste.

**Confirmación y envío.** `DialogoConfirmarConciliacionComponent` (propio, con resumen y aviso
de irreversibilidad; `cdkFocusInitial` en `Cancelar`). Al confirmar,
`ConciliacionService.crear` con `crearAjuste` y `categoriaId` solo si la regla lo admite; señal
`enviando` deshabilita el botón (un doble clic no duplica el ajuste y, aun así, el backend
recalcula). `resultado = signal<ConciliacionResponse | null>` muestra lo que devolvió el `POST`;
después `recargas` del estado (re-emitiendo el `Subject`) y del historial. La pantalla de
Transacciones vuelve a pedir página y saldos al entrar, así que no hace falta avisarle.

**Errores.** Por `codigo`, como en `transacciones-frontend`: `DATOS_INVALIDOS` con `errores` →
`aplicarErroresDeCampos`, sin `errores` → mensaje `Revisa el saldo y la fecha del extracto`;
`REGLA_NEGOCIO_VIOLADA` → `MENSAJE_REGLA_CONCILIACION` en el asistente; `RECURSO_NO_ENCONTRADO`
→ aviso y recarga completa (cuenta, categorías, estado e historial); otro → aviso genérico.

**Cuenta cerrada.** Se decide con `cuenta.cerrada`: sin asistente, con el aviso y el historial
(el backend lo permite). En Cuentas, el menú de una cerrada ofrece `Ver conciliaciones`; en
Transacciones no hay botón para una cuenta cerrada (`cuentasCerradas` ya existe en la página).

**Cambios en otras pantallas (mínimos).**
- `features/cuentas/pages/cuentas.page.html`: `Conciliar` (abiertas) y `Ver conciliaciones`
  (cerradas) como `mat-menu-item` con `routerLink`; `cuentas.page.ts` importa `RouterLink`.
- `features/transacciones/pages/transacciones.page.html`: botón `mat-stroked-button`
  `Conciliar` con `routerLink` en `div.botones-encabezado`, visible si `filtros().cuentaId` es
  una cuenta abierta; `transacciones.page.ts` importa `RouterLink` y expone `cuentaConciliable`
  (`computed`).

**Mensajes.**
- `MENSAJE_REGLA_CONCILIACION = 'No se pudo reconciliar: la cuenta está cerrada, falta el ajuste
  o su categoría no corresponde.'`
- `MENSAJE_CUENTA_INEXISTENTE = 'La cuenta ya no existe.'`
- `MENSAJE_REFERENCIA_INEXISTENTE = 'La cuenta o la categoría ya no existe. Actualizamos los
  datos.'`

### Archivos (desde `frontend/src/app/`)

| Archivo nuevo o modificado | Test |
|---|---|
| `features/conciliacion/models/estado-conciliacion-response.model.ts` (nuevo) | — (interfaz) |
| `features/conciliacion/models/transaccion-no-conciliada.model.ts` (nuevo; lo que se usa de `TransaccionResponse`) | — (interfaz) |
| `features/conciliacion/models/crear-conciliacion-request.model.ts` (nuevo) | — (interfaz) |
| `features/conciliacion/models/conciliacion-response.model.ts` (nuevo) | — (interfaz) |
| `features/conciliacion/models/cuenta-conciliacion.model.ts` (nuevo; con `TipoCuentaConciliacion`) | — (interfaz) |
| `features/conciliacion/models/grupo-categorias-lectura.model.ts` y `categoria-lectura.model.ts` (nuevos) | — (interfaz) |
| `features/conciliacion/models/datos-dialogo-confirmar-conciliacion.model.ts` (nuevo) | — (interfaz) |
| `features/conciliacion/services/conciliacion.service.ts` (nuevo) | `features/conciliacion/services/conciliacion.service.spec.ts` |
| `features/conciliacion/services/cuenta-lectura.service.ts` y `categoria-lectura.service.ts` (nuevos) | `features/conciliacion/services/lectura.service.spec.ts` |
| `features/conciliacion/services/regla-categoria-ajuste.ts` (nuevo) | `features/conciliacion/services/regla-categoria-ajuste.spec.ts` |
| `features/conciliacion/services/presentacion-conciliacion.ts` (nuevo; `textoDiferencia`, `noConciliadasHasta`, `noFutura`) | `features/conciliacion/services/presentacion-conciliacion.spec.ts` |
| `features/conciliacion/components/dialogo-confirmar-conciliacion.component.ts` (+ html, scss; nuevo) | `features/conciliacion/components/dialogo-confirmar-conciliacion.component.spec.ts` |
| `features/conciliacion/components/historial-conciliaciones.component.ts` (+ html, scss; nuevo) | `features/conciliacion/components/historial-conciliaciones.component.spec.ts` |
| `features/conciliacion/pages/conciliacion.page.ts` (+ html, scss; nuevo) | `features/conciliacion/pages/conciliacion.page.spec.ts` |
| `app.routes.ts` (ruta `cuentas/:cuentaId/conciliacion`, sin `seccion`) | `app.routes.spec.ts` |
| `features/cuentas/pages/cuentas.page.ts` (+ html) | `features/cuentas/pages/cuentas.page.spec.ts` |
| `features/transacciones/pages/transacciones.page.ts` (+ html) | `features/transacciones/pages/transacciones.page.spec.ts` |

Archivos compartidos tocados: solo `app.routes.ts` (una ruta hija, sin entrada en el menú). El
layout del menú lateral, `core/` y `shared/` no cambian. Fuera de `src/app`, solo `AGENTS.md`.

## Risks / Trade-offs

- **`noConciliadas` recortada a 100** → el conteo hasta la fecha puede quedarse corto; se dice
  `al menos {n}` cuando la API recortó, y el enlace lleva a la lista completa filtrada.
- **La diferencia cambia entre el estado y el envío** → el backend recalcula; se muestra el
  resultado real del `POST`. Si con el ajuste marcado la diferencia pasa a 0, el backend no crea
  ajuste y el resultado lo refleja.
- **El tipo de la cuenta cambia en otra sesión** → la regla del cliente podría no exigir la
  categoría; el backend responde `422` y se muestra el mensaje sin perder lo escrito.
- **Conflictos en `cuentas.page` y `transacciones.page`** con quienes trabajan en paralelo → los
  cambios son una entrada del menú y un botón; quien aplique segundo integra.
- **Fecha futura por diferencia de zona horaria** → el validador usa la fecha local; si el
  servidor (UTC) aún no llegó a ese día, responde `400` sin `errores` y se muestra `Revisa el
  saldo y la fecha del extracto`.
