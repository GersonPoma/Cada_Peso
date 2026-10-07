# Design

## Context

Ver proposal.md - Why. Estado observado:

- Backend (`com.presupuesto.transaccion`, con `transferencias-backend` ya integrado):
  - `TransaccionResponse { id, cuentaId, fecha, monto, categoriaId, beneficiario, memo, estado,
    aprobada, subtransacciones[{ id, categoriaId, monto, memo }], transaccionParId,
    fechaCreacion, fechaActualizacion }`.
  - `PaginaResponse { contenido, pagina, tamano, totalElementos, totalPaginas }`.
  - `LoteResponse { afectadas }` y `SaldoCuentaResponse { cuentaId, saldo, saldoConciliado }`.
  - Requests: `CrearTransaccionRequest { cuentaId, fecha, monto, categoriaId, beneficiario,
    memo, aprobada, subtransacciones }`, `ActualizarTransaccionRequest` (igual sin `cuentaId` ni
    `aprobada`), `SubTransaccionRequest { categoriaId, monto, memo }` (`categoriaId` opcional),
    `CambiarEstadoRequest { estado }`, `MoverCuentaRequest { cuentaId }` y
    `LoteRequest { ids, operacion, categoriaId }`.
  - Filtros de la lista: `cuentaId`, `categoriaId`, `desde`, `hasta`, `estado`, `soloSinAprobar`
    y `q`, más `page` y `size`.
  - Según la spec `transacciones`, `CATEGORIZAR` en lote falla con divididas, reconciliadas y
    patas, y `BORRAR` con reconciliadas y patas.
- Frontend:
  - `shared/formato/milliunits.ts` (`leerMonto`, `aMilliunits`, `deMilliunits`), `aFechaNegocio()`,
    `AdaptadorFechaRegional` y los pipes `monto` y `fecha`.
  - El patrón de páginas (`toObservable` + `switchMap` + recargas) y de diálogos (petición propia,
    cierre solo con éxito, errores por `codigo`) de cuentas, categorías y presupuesto mensual.

## Goals / Non-Goals

**Goals:**
- Una calculadora exacta y pura, probada sin DOM, y un campo de monto reutilizable.
- Que las reglas de qué acción admite cada transacción vivan en funciones puras probadas.

**Non-Goals:**
- Paréntesis en la calculadora y otros operadores.
- Seleccionar filas a través de varias páginas.
- Autocompletado de beneficiario, y crear o editar transferencias.

## Decisions

**`evaluarMonto(texto, region = regionUsuario())` en `shared/calculadora/evaluar-monto.ts`.**
1. Tokeniza recorriendo el texto (sin espacios). Los operadores son `+ - * /`. Un `-` al principio
   o después de un operador es signo unario (solo uno); `+` unario no se acepta, así `5++2` es
   inválido. Un operando es la secuencia máxima de caracteres que no son operadores, y cualquier
   otro carácter (letras, paréntesis) lo vuelve inválido.
2. Cada operando se lee con `leerMonto(operando, region)` del módulo de dinero, que respeta los
   separadores de la región y rechaza más de 3 decimales. Se convierte a la fracción exacta
   `BigInt(milesimas) / 1000n`.
3. Se evalúa con dos pasadas sobre fracciones de `BigInt` (`{ n, d }` reducidas con MCD): primero
   `*` y `/` de izquierda a derecha, luego `+` y `-`. Dividir por cero devuelve `null`.
4. Redondea `n * 1000 / d` a entero con la mitad alejada de cero (`2 * |resto| >= |d|`) y devuelve
   `null` si no es `Number.isSafeInteger`.

No usa `eval`, `Function`, `parseFloat` ni `Number` con decimales. Alternativa descartada:
escalar cada operando a milésimas y dividir enteros en cada paso, que redondea en medio del
cálculo (`10/3*3` daría 9,999).

**`app-campo-monto` en `shared/calculadora/campo-monto.component.ts`.**
- **Entradas:** `control: FormControl<number | null>` (milésimas), `etiqueta` y `requerido`.
- **Texto interno:** un `FormControl<string>` que arranca en `deMilliunits(control.value)` y se
  sincroniza cuando el control cambia desde fuera y el campo no tiene el foco.
- **Al confirmar** (`blur` o `Enter`):
  - Vacío pone `null`.
  - Inválido deja el texto y marca `{ montoInvalido }`.
  - Válido pone el valor y reescribe el texto con `deMilliunits`.
- **Pista:** una señal muestra `= {resultado}` mientras el texto tiene un operador después del
  primer carácter y se puede evaluar.
- **Errores:** el campo de texto refleja los errores del control externo (`required`, los de quien
  lo use) o `montoInvalido`, y los muestra en `mat-error` con mensajes por clave. Quien lo usa
  puede pasar mensajes propios (`mensajes`).

**Servicios de lectura propios.** `CuentaLecturaService.listar(presupuestoId)` (con
`incluirCerradas=true`) y `CategoriaLecturaService.arbol(presupuestoId)` (con
`incluirOcultas=true`) devuelven modelos propios (`CuentaResumen`, `GrupoCategoriasResumen` y
`CategoriaResumen`) con solo los campos que usa la pantalla. No se importa nada de
`features/cuentas` ni de `features/categorias`.

**Funciones puras de la feature** (`features/transacciones/services/`):
- **`acciones-transaccion.ts`:** `accionesDe(transaccion, cuentaCerrada)` devuelve, por acción
  (`editar`, `duplicar`, `mover`, `aprobar`, `estado` y `borrar`), `{ visible, habilitada,
  motivo }` según las reglas de la spec. `filtrarLote(transacciones, operacion)` devuelve
  `{ aplicables, omitidas }`.
- **`filtros-url.ts`:** `leerFiltros(queryParamMap)` y `aQueryParams(filtros)`. Leen y escriben
  `cuentaId`, `categoriaId`, `desde`, `hasta`, `estado`, `sinAprobar` (`1`), `q`, `pagina` y
  `tamano`, y descartan valores inválidos (números no positivos, fechas que no son `yyyy-MM-dd`,
  estados desconocidos, tamaños fuera de 10/20/50/100). También `hayFiltros(filtros)`.

**Página.**
- **Filtros desde la URL:** lee los filtros de `queryParamMap` como señal; la URL es la única
  fuente de verdad.
- **Cambios de filtro:** el componente de filtros emite `Filtros` y la página navega con
  `router.navigate([], { queryParams })` en la página 0, sin `pagina`, y limpia la selección.
- **Paginador:** emite `PageEvent`, que se traduce a `pagina` y `tamano` en la URL.
- **Carga:** `toObservable(computed({ presupuestoId, filtros, recargas }))` con `switchMap` a
  `listar`. Los saldos van aparte con su propio `switchMap` sobre `{ presupuestoId, recargas }`.
  Cuentas y categorías se cargan una vez y otra más con `recargarListas()`.

**Filtros (`FiltrosTransaccionesComponent`).**
- **Formulario:** recibe los filtros actuales, las cuentas y los grupos de categorías. Su
  formulario reactivo se reinicia con `{ emitEvent: false }` cuando cambian desde la URL.
- **Búsqueda:** `q` emite con `debounceTime(300)` y `distinctUntilChanged`; el resto de los
  controles emiten al momento.
- **Fechas:** `mat-datepicker` (adaptador regional); la `Date` se convierte con `aFechaNegocio()`
  y la URL se vuelve a leer con `new Date(anio, mes - 1, dia)`.

**Tabla (`TablaTransaccionesComponent`).**
- **Columnas:** `mat-table` con `seleccion`, `fecha` (pipe `fecha`), `cuenta`, `beneficiario`
  (con insignia `Transferencia`), `categoria`, `memo`, `salida`, `entrada`, `estado`,
  `aprobacion` y `acciones`.
- **Menú por fila:** muestra el motivo de cada acción deshabilitada como texto secundario dentro
  del ítem y como `matTooltip`.
- **Pantallas estrechas:** CSS a menos de 600 px apila las celdas.
- **Datos:** recibe las transacciones, mapas de nombres de cuentas y categorías, las cuentas
  cerradas, la moneda y la selección; emite cada acción.

**Diálogos.**
- **`DialogoTransaccionComponent`:**
  - **Datos:** `{ modo: 'crear' } | { modo: 'editar', transaccion }`, más las cuentas y el árbol.
  - **Monto:** el campo es positivo y lleva un `mat-button-toggle-group` de tipo. Al editar, el
    tipo sale del signo y el monto del valor absoluto.
  - **Fecha:** por defecto `new Date()` en hora local; al editar sale de `new Date(y, m - 1, d)`.
  - **División:** cuando está activa, `categoriaId` se deshabilita y las partes viven en un
    `FormArray` que maneja `EditorDivisionComponent`.
  - **Resultado:** `{ tipo: 'guardada' } | { tipo: 'recargar' }`.
- **`EditorDivisionComponent`:**
  - Recibe el `FormArray` de partes (`categoriaId`, `monto` en milésimas y `memo`), el monto
    total en milésimas y las categorías.
  - Agrega y quita partes dentro de 2 a 20.
  - Calcula la suma con enteros y muestra `Falta asignar {x}`, `Sobra {x}` o `Las partes suman el
    monto`.
  - El diálogo valida la suma con un validador del `FormArray` que compara con el monto.
- **`DialogoMoverCuentaComponent`:** cuentas abiertas sin la actual; hace el `POST` y se cierra
  con la transacción movida.
- **`DialogoConfirmacionComponent`:** genérico de la feature (`{ titulo, mensaje, confirmar }`);
  devuelve `true` al confirmar. Lo usan borrar y el aviso de filas omitidas en el lote.
- **`BarraLoteComponent`:** recibe las transacciones seleccionadas y las categorías; emite
  `aprobar`, `categorizar(categoriaId)`, `borrar` y `limpiar`. La página aplica `filtrarLote`,
  confirma y llama al lote.

**Ruta.** `{ path: 'transacciones', loadComponent: TransaccionesPage, data: seccion(
'Transacciones', 'receipt_long') }`, entre `cuentas` y `categorias`.

### Archivos nuevos o modificados

Rutas desde `frontend/src/app/`; `tx/` abrevia `features/transacciones/`.

| Archivo | Estado | Test |
|---|---|---|
| `shared/calculadora/evaluar-monto.ts` | nuevo | `shared/calculadora/evaluar-monto.spec.ts` |
| `shared/calculadora/campo-monto.component.ts` (+ html, scss) | nuevo | `shared/calculadora/campo-monto.component.spec.ts` |
| `tx/models/*.model.ts` | nuevo | (interfaces) |
| `tx/services/transaccion.service.ts` | nuevo | `tx/services/transaccion.service.spec.ts` |
| `tx/services/cuenta-lectura.service.ts` | nuevo | `tx/services/lectura.service.spec.ts` |
| `tx/services/categoria-lectura.service.ts` | nuevo | `tx/services/lectura.service.spec.ts` |
| `tx/services/acciones-transaccion.ts` | nuevo | `tx/services/acciones-transaccion.spec.ts` |
| `tx/services/filtros-url.ts` | nuevo | `tx/services/filtros-url.spec.ts` |
| `tx/components/filtros-transacciones.component.ts` (+ html, scss) | nuevo | `tx/components/filtros-transacciones.component.spec.ts` |
| `tx/components/tabla-transacciones.component.ts` (+ html, scss) | nuevo | `tx/components/tabla-transacciones.component.spec.ts` |
| `tx/components/dialogo-transaccion.component.ts` (+ html, scss) | nuevo | `tx/components/dialogo-transaccion.component.spec.ts` |
| `tx/components/editor-division.component.ts` (+ html, scss) | nuevo | (cubierto por el test del diálogo) |
| `tx/components/dialogo-mover-cuenta.component.ts` (+ html) | nuevo | `tx/components/dialogo-mover-cuenta.component.spec.ts` |
| `tx/components/dialogo-confirmacion.component.ts` (+ html) | nuevo | (cubierto por el test de la página) |
| `tx/components/barra-lote.component.ts` (+ html, scss) | nuevo | `tx/components/barra-lote.component.spec.ts` |
| `tx/pages/transacciones.page.ts` (+ html, scss) | nuevo | `tx/pages/transacciones.page.spec.ts` |
| `app.routes.ts` | modificado | `app.routes.spec.ts` |

## Risks / Trade-offs

- [Duplicación de modelos de cuentas y categorías] → Es pequeña y a propósito: mantiene las
  features independientes.
- [El lote solo opera sobre la página visible] → El tamaño máximo es 100, que coincide con el
  límite del backend.
- [La calculadora rechaza paréntesis] → Se documenta; es el alcance pedido.

## Migration Plan

Solo frontend. Rollback: revertir el change.
