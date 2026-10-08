# Design

## Context

Ver proposal.md - Why. Estado observado:

- Backend (`com.presupuesto.beneficiario`, integrado):
  - `BeneficiarioResponse { id, nombre, categoriaPredeterminadaId }`.
  - `CrearBeneficiarioRequest { nombre, categoriaId }` y `ActualizarBeneficiarioRequest
    { nombre, categoriaId }`; el nombre se recorta y se valida `@NotBlank @Size(max = 100)`.
  - `GET /beneficiarios` (lista ordenada por nombre), `?q=&limite=` (prefijo, `limite` 1 a 50,
    por defecto 10), `GET/PUT /beneficiarios/{id}`, `POST`; sin `DELETE`. `409
    BENEFICIARIO_YA_EXISTE`; `404` para categoría ajena; `422` si la categoría es de pago de
    tarjeta.
  - `TransaccionResponse` ya trae `beneficiarioId` y `programadaId`; el frontend aún no los
    modela.
  - El árbol de categorías trae además `tipo` en el grupo y `esPagoTarjeta`/`cuentaId` en cada
    categoría (de `tarjetas-credito-backend`).
- Frontend:
  - `dialogo-transaccion.component.ts` tiene `beneficiario` como `FormControl<string>`
    `nonNullable` con `sobreTextoRecortado(Validators.maxLength(100))`, un contador y el campo en
    el HTML con `matInput`. El select de categoría usa `grupos` (visibles más las ocultas que la
    transacción ya usa).
  - `tabla-transacciones.component.ts` pinta `t.beneficiario` y recibe mapas de nombres
    (`nombresCuenta`, `nombresCategoria`) desde la página.
  - `transacciones.page.ts` carga cuentas y árbol con `cargar(...)` (`toObservable` +
    `switchMap`) y recarga página y saldos con la señal `recargas`.
  - `CODIGOS_API` no tiene `BENEFICIARIO_YA_EXISTE`.
  - El menú lateral se arma con las rutas hijas que tienen `data: seccion(...)`, en su orden.

## Goals / Non-Goals

**Goals:**
- Autocompletado sin romper el texto libre: el backend sigue siendo quien crea y vincula.
- Que la regla de la categoría recordada viva en una función pura probada caso por caso.
- Respetar el aislamiento entre features: lo de transacciones no importa de beneficiarios.

**Non-Goals:**
- Fusionar o borrar beneficiarios, renombrado automático, sugerencias por ubicación, importar.
- Que el filtro `q` de transacciones busque por el nombre actual del beneficiario: el backend
  busca en el texto guardado, así que una transacción vieja de un beneficiario renombrado solo se
  encuentra por el texto original. Es una limitación aceptada.
- Excluir las categorías de pago del diálogo de transacción (no es parte de este change).

## Decisions

**Campo vacío o al enfocarlo vacío: no se pide nada.** Mostrar los 5 primeros por orden
alfabético no ayuda (no son los más usados) y suma una petición por cada apertura del diálogo.

**`BeneficiarioLecturaService` (`features/transacciones/services/`).**
- `buscar(presupuestoId, q, limite = 10)`: `GET .../beneficiarios?q&limite`.
- `listar(presupuestoId)`: `GET .../beneficiarios` sin parámetros.
- Ambos devuelven `BeneficiarioSugerido { id, nombre, categoriaPredeterminadaId }`, modelo
  propio de la feature.

**`app-campo-beneficiario` (`features/transacciones/components/`).**
- **Entradas:** `control: FormControl<string>` (el del diálogo, con sus validadores) y
  `grupos: GrupoCategoriasResumen[]` (el árbol completo, para el texto secundario). El
  presupuesto sale de `PresupuestoActivoService`.
- **Salida:** `elegido: BeneficiarioSugerido` al elegir una opción (`optionSelected`).
- **Búsqueda:** `control.valueChanges` → `map(trim)` → `debounceTime(250)` →
  `distinctUntilChanged()` → `switchMap(q => q ? buscar(q).pipe(catchError(() => of([]))) :
  of([]))`, expuesto con `toSignal` como `sugerencias`. Una búsqueda cancelada por `switchMap`
  no llega nunca, así que las respuestas atrasadas se ignoran solas.
- **Resaltado:** cada opción se parte en `{ coincide, resto }` cortando el nombre en el largo del
  texto buscado (`nombre.slice(0, q.length)` en `<strong>`), sin `innerHTML`.
- **Pista de nuevo:** `computed` que es verdadero si el texto recortado no está vacío y ninguna
  sugerencia tiene `nombre.toLocaleLowerCase() === texto.toLocaleLowerCase()`. Antes de que
  llegue la primera respuesta se considera nuevo (no hay coincidencias conocidas).
- **Contador y errores:** el componente muestra `largo/100` y el `mat-error` de `maxlength` y del
  error `servidor` (`mensajeDeError`).
- **Teclado y accesibilidad:** el teclado (flechas, `Enter`, `Escape`) lo da `MatAutocomplete`
  con `autoActiveFirstOption` desactivado para no reemplazar el texto libre al pulsar `Enter`;
  el input lleva `aria-label="Beneficiario"`.
- Zoneless y `OnPush`: todo el estado de la vista son señales (`sugerencias`, `textoActual` con
  `toSignal`).

**Categoría recordada: función pura** `categoriaRecordada(contexto)` en
`features/transacciones/services/categoria-recordada.ts`:
`({ creando, dividida, categoriaPristine, categoriaActual, sugerida, idsExistentes }) =>
number | null`. Devuelve el id a poner o `null` si no se cumple alguna de las cuatro condiciones.
El diálogo la llama en `(elegido)`; si devuelve un id, hace `categoriaId.setValue(id)` sin
marcarlo como `dirty` (sigue `pristine` hasta que la persona lo cambie), pone la señal
`categoriaSugerida` a `true` y una suscripción a `categoriaId.valueChanges` la vuelve a `false`
cuando el valor difiere del sugerido. Las ocultas: `grupos` del diálogo pasa a ser un `computed`
que incluye también la categoría sugerida, para que el `mat-select` la pueda mostrar.

**Reintento ante 409.** En `enviar()`, la petición es
`peticion().pipe(retry({ count: 1, delay: (e) => esBeneficiarioDuplicado(e) ? of(0) :
throwError(() => e) }))`. Así solo se reintenta ese código, una vez y con el mismo cuerpo. Si
el error final es `BENEFICIARIO_YA_EXISTE`, se pone `{ servidor: 'Ya existe un beneficiario con
ese nombre' }` en el control `beneficiario`.

**Nombre vinculado en la tabla.** La página carga `beneficiarios` con `listar` mediante su helper
`cargar`, disparado por `presupuestoId` y una señal `recargasBeneficiarios` que sube tras un
guardado del diálogo (resultado `guardada` o `recargar`). Un error deja el mapa vacío (la columna
cae al texto). La tabla recibe `nombresBeneficiario: ReadonlyMap<number, string>` y usa
`beneficiario(t) = (t.beneficiarioId != null && mapa.get(t.beneficiarioId)) || t.beneficiario ||
''`.

**Feature `features/beneficiarios/`.**
- `BeneficiarioService`: `listar`, `crear`, `actualizar` (sin `obtener`: la pantalla edita con
  los datos de la fila).
- `CategoriaLecturaService` propio de la feature (mismo GET que el de transacciones, con modelos
  propios `GrupoCategoriasLectura` y `CategoriaLectura` que incluyen `esPagoTarjeta`, para
  excluir del select las categorías de pago, que el backend rechaza con 422).
- `BeneficiariosPage`: `forkJoin` de lista y árbol con `switchMap` sobre una señal de recarga;
  estado `cargando | listo | error`; buscador con `FormControl` + `debounceTime(250)` → señal
  `filtro`; `filtrados = computed(...)` con `startsWith` en minúsculas (`toLocaleLowerCase`).
- `DialogoBeneficiarioComponent`: recibe `{ beneficiario | null, grupos }`, hace la petición y
  solo se cierra con éxito (`{ tipo: 'guardado' } | { tipo: 'recargar' }`), como los demás
  diálogos. Nombre con `Validators.required` sobre `sobreTextoRecortado(...)` y
  `sobreTextoRecortado(Validators.maxLength(100))`; la opción `Ninguna` es `[value]="null"`.
- Ruta hija `beneficiarios` en `app.routes.ts`, después de `categorias`, con
  `data: seccion('Beneficiarios', 'storefront')`.

### Archivos (desde `frontend/src/app/`)

| Archivo nuevo o modificado | Test |
|---|---|
| `features/transacciones/models/beneficiario-sugerido.model.ts` (nuevo) | — (interfaz) |
| `features/transacciones/models/transaccion-response.model.ts` (+`beneficiarioId`, `programadaId`) | — |
| `features/transacciones/services/beneficiario-lectura.service.ts` (nuevo) | `features/transacciones/services/lectura.service.spec.ts` |
| `features/transacciones/services/categoria-recordada.ts` (nuevo) | `features/transacciones/services/categoria-recordada.spec.ts` |
| `features/transacciones/components/campo-beneficiario.component.ts` (+ html, scss; nuevo) | `features/transacciones/components/campo-beneficiario.component.spec.ts` |
| `features/transacciones/components/dialogo-transaccion.component.ts` (+ html) | `features/transacciones/components/dialogo-transaccion.component.spec.ts` |
| `features/transacciones/components/tabla-transacciones.component.ts` (+ html) | `features/transacciones/components/tabla-transacciones.component.spec.ts` |
| `features/transacciones/pages/transacciones.page.ts` (+ html) | `features/transacciones/pages/transacciones.page.spec.ts` |
| `features/beneficiarios/models/beneficiario-response.model.ts` | — (interfaz) |
| `features/beneficiarios/models/crear-beneficiario-request.model.ts` | — (interfaz) |
| `features/beneficiarios/models/actualizar-beneficiario-request.model.ts` | — (interfaz) |
| `features/beneficiarios/models/categoria-lectura.model.ts` | — (interfaz) |
| `features/beneficiarios/models/grupo-categorias-lectura.model.ts` | — (interfaz) |
| `features/beneficiarios/models/datos-dialogo-beneficiario.model.ts` | — (interfaz) |
| `features/beneficiarios/services/beneficiario.service.ts` | `features/beneficiarios/services/beneficiario.service.spec.ts` |
| `features/beneficiarios/services/categoria-lectura.service.ts` | `features/beneficiarios/services/categoria-lectura.service.spec.ts` |
| `features/beneficiarios/pages/beneficiarios.page.ts` (+ html, scss) | `features/beneficiarios/pages/beneficiarios.page.spec.ts` |
| `features/beneficiarios/components/dialogo-beneficiario.component.ts` (+ html, scss) | `features/beneficiarios/components/dialogo-beneficiario.component.spec.ts` |
| `shared/api/problema-api.ts` (+`BENEFICIARIO_YA_EXISTE`) | `shared/api/problema-api.spec.ts` |
| `app.routes.ts` (ruta `beneficiarios`) | `features/presupuestos/pages/layout-presupuesto.page.spec.ts` |

## Risks / Trade-offs

- **`Enter` con una opción activa reemplaza el texto** → sin `autoActiveFirstOption`, `Enter`
  solo elige si la persona bajó con las flechas; si no, envía el formulario con su texto.
- **La lista completa de beneficiarios para la tabla puede crecer** → son nombres cortos y se
  pide una vez por entrada a la pantalla y tras cada guardado; si crece mucho, se cambiará por
  un nombre embebido en la respuesta (backend).
- **Doble 409 simultáneo** → solo un reintento; el segundo error se muestra en el campo para que
  la persona vuelva a guardar.
- **Filtro de texto de transacciones** → busca en el texto guardado, no en el nombre actual
  (limitación documentada en Non-Goals).
