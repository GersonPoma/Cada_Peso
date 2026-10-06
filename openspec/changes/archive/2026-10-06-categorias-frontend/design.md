# Design

## Context

Ver proposal.md - Why. Estado observado:

- Backend (`com.presupuesto.categoria`, archivado en `categorias-backend`):
  - `GET .../categorias?incluirOcultas` devuelve `List<GrupoCategoriaConCategoriasResponse>` con
    `{ id, nombre, orden, oculto, categorias }`, y cada categoría es un `CategoriaResponse`
    `{ id, grupoId, nombre, orden, oculta, nota, fechaCreacion, fechaActualizacion }`. Ojo:
    `oculto` en el grupo y `oculta` en la categoría.
  - Las operaciones de grupo responden `GrupoCategoriaResponse`
    `{ id, nombre, orden, oculto, fechaCreacion, fechaActualizacion }`, y las de categoría,
    `CategoriaResponse`.
  - Requests: `CrearGrupoCategoriaRequest { nombre }`, `ActualizarGrupoCategoriaRequest
    { nombre }`, `MoverGrupoCategoriaRequest { posicion }`, `CrearCategoriaRequest { grupoId,
    nombre, nota }`, `ActualizarCategoriaRequest { nombre, nota }` y `MoverCategoriaRequest
    { grupoId, posicion }`. El backend recorta nombre y nota, y una nota vacía queda en `null`.
  - **`orden` y `posicion` cuentan todos los elementos, ocultos incluidos**, y se renumeran
    de 0 a n-1 tras cada movimiento. Dentro del mismo grupo la posición válida es [0, n-1]; en
    otro grupo, [0, m], con m las categorías del destino.
- Frontend: `LayoutPresupuestoPage` arma el menú con `data: seccion(...)` de las rutas hijas.
  Los diálogos (`DialogoPresupuestoComponent`, `DialogoCuentaComponent`) hacen ellos mismos la
  petición y solo se cierran con éxito. `CuentasPage` carga con `toObservable` + `switchMap` y
  recarga con un contador. `@angular/cdk/drag-drop` está disponible en `@angular/cdk` 21.

## Goals / Non-Goals

**Goals:**
- Que el cálculo del reordenamiento sea puro y probado, incluidos los casos con ocultos, un
  grupo vacío y el final de un grupo.
- Que arrastrar se sienta inmediato sin que la vista quede distinta del backend.

**Non-Goals:**
- Arrastrar categorías ocultas a lugares concretos entre ocultas con el interruptor apagado (no
  se ven).
- Reordenar con el teclado por medio de arrastrar: para eso está "Mover a...".
- Animaciones a medida del arrastre más allá de las clases del CDK.

## Decisions

**Funciones puras de reordenamiento en `features/categorias/services/reordenamiento.ts`.**
Sin Angular ni DOM, sobre el árbol (`ArbolCategorias = GrupoCategoriaConCategoriasResponse[]`) y
con índices *visibles* con la semántica del CDK: dentro de la misma lista, `indiceDestino` es el
índice final (como `moveItemInArray`); en otra lista, es el índice de inserción (0..L).
- `posicionGrupo(grupos, desde, hasta): number | null`: `null` si `desde === hasta`; si no,
  `grupos[hasta].orden`.
- `posicionCategoria(arbol, categoriaId, grupoDestinoId, indiceDestino): number | null`:
  - En el mismo grupo: `null` si no cambia; si no, el `orden` de la categoría visible que hoy
    ocupa `indiceDestino`.
  - En otro grupo: el `orden` de la categoría visible en `indiceDestino` (se inserta delante de
    ella); al final, el `orden` de la última visible más uno, o `0` si el destino no tiene
    ninguna visible.
- `moverGrupo(arbol, desde, hasta)` y `moverCategoria(arbol, categoriaId, grupoDestinoId,
  indiceDestino)`: devuelven un árbol **nuevo**, sin mutar el recibido, con el elemento
  reubicado (y el `grupoId` actualizado). Son la actualización optimista.

Por qué funciona con ocultos: si el destino visible está debajo del origen, al quitar el
elemento el destino baja un lugar y la inserción en su `orden` original lo deja justo detrás; si
está arriba, lo deja justo delante. Es el mismo resultado que muestra el CDK. Se prueba con
`A(0) H(1, oculta) B(2) C(3)`.
Alternativa descartada: mandar el índice visible tal cual. Es correcto solo sin ocultos y falla
en silencio con el interruptor apagado, que es el caso por defecto.

**Optimismo, reversión y bloqueo.** La página guarda el árbol en una señal. Al soltar:
1. Si la posición es `null`, no hace nada.
2. Si no, guarda el árbol anterior, aplica `moverGrupo` o `moverCategoria` y pone
   `moviendo = true`.
3. Llama a `/mover`. Si sale bien, recarga el árbol sin spinner, porque los `orden` locales
   quedaron viejos y el siguiente cálculo de posición los necesita al día. Si falla, restaura el
   árbol anterior, avisa (`CATEGORIA_YA_EXISTE` con su texto, 401 nada, el resto con el aviso
   genérico) y recarga.
4. `moviendo` vuelve a `false` cuando llega la recarga.

Mientras `moviendo` es `true`, todos los `cdkDrag` están deshabilitados (`cdkDragDisabled`) y el
manejador de `dropped` ignora eventos.

**Estructura del arrastre.** La lista de grupos es un `cdkDropList` vertical con
`cdkDropListGroup` en el mismo elemento. El grupo de listas no incluye a su propio host (el CDK
inyecta el grupo con `skipSelf`), así que conecta solo las listas de categorías de los grupos,
que son sus descendientes. Cada grupo es un `cdkDrag` con `cdkDragHandle` en el ícono
`drag_indicator`. Las listas de categorías usan `cdkDropListEnterPredicate` para aceptar solo
categorías, y la de grupos solo grupos (`cdkDragData` con un discriminante `tipo`). Una lista sin
categorías visibles muestra "Arrastra una categoría aquí" y conserva una altura mínima para
recibir el soltado.

**Componente de presentación `GrupoCategoriasComponent`.** Pinta un grupo: cabecera con asa,
nombre, `Agregar categoría` y menú, y la lista de categorías como `cdkDropList` con sus menús.
Recibe el grupo, si se muestran ocultas y si hay un movimiento en curso, y emite eventos
(`renombrar`, `alternarVisibilidad`, `agregarCategoria`, `editarCategoria`,
`alternarVisibilidadCategoria`, `moverCategoriaA`, `soltarCategoria`). La página decide todo; el
componente no llama a servicios.

**Diálogos.** Siguen el patrón de cuentas (petición propia, cierre solo con éxito,
`disableClose` mientras envía, errores por `codigo`):
- `DialogoGrupoComponent` (`{ modo: 'crear' } | { modo: 'renombrar'; grupo }`): solo el nombre.
- `DialogoCategoriaComponent` (`{ modo: 'crear'; grupoId } | { modo: 'editar'; categoria }`):
  nombre y nota (`textarea`, `sobreTextoRecortado(Validators.maxLength(500))`), con contador
  `mat-hint align="end"` del texto escrito.
- `DialogoMoverCategoriaComponent` (`{ arbol, categoria }`): selector de grupo, selector de
  lugar (`Al principio` o `Después de ...`, sin la propia) y cálculo de la posición con
  `posicionCategoria`. Si la elección no cambia nada, se cierra sin llamar.

**Servicio `CategoriaService`.** Recibe el `presupuestoId` en cada método, como `CuentaService`:
`obtenerArbol`, `crearGrupo`, `renombrarGrupo`, `ocultarGrupo`, `mostrarGrupo`, `moverGrupo`,
`crearCategoria`, `editarCategoria`, `ocultarCategoria`, `mostrarCategoria` y `moverCategoria`.

**Carga.** Como en `CuentasPage`: `toObservable` sobre presupuesto, `incluirOcultas` y un
contador de recargas, con `switchMap`. El spinner solo aparece en la primera carga (estado
`cargando` sin árbol); las recargas posteriores reemplazan el árbol sin parpadeo.

**Ruta y menú.** Una ruta hija nueva:
`{ path: 'categorias', loadComponent: ..., data: seccion('Categorías', 'category') }`.

### Archivos nuevos o modificados

Rutas desde `frontend/src/app/`.

| Archivo | Estado | Test |
|---|---|---|
| `features/categorias/models/categoria-response.model.ts` | nuevo | (interfaz) |
| `features/categorias/models/grupo-categoria-response.model.ts` | nuevo | (interfaz) |
| `features/categorias/models/grupo-categoria-con-categorias-response.model.ts` | nuevo | (interfaz) |
| `features/categorias/models/arbol-categorias.model.ts` | nuevo | (tipo) |
| `features/categorias/models/crear-grupo-categoria-request.model.ts` | nuevo | (interfaz) |
| `features/categorias/models/actualizar-grupo-categoria-request.model.ts` | nuevo | (interfaz) |
| `features/categorias/models/mover-grupo-categoria-request.model.ts` | nuevo | (interfaz) |
| `features/categorias/models/crear-categoria-request.model.ts` | nuevo | (interfaz) |
| `features/categorias/models/actualizar-categoria-request.model.ts` | nuevo | (interfaz) |
| `features/categorias/models/mover-categoria-request.model.ts` | nuevo | (interfaz) |
| `features/categorias/models/datos-dialogos-categorias.model.ts` | nuevo | (tipos de datos de los tres diálogos) |
| `features/categorias/services/reordenamiento.ts` | nuevo | `features/categorias/services/reordenamiento.spec.ts` |
| `features/categorias/services/categoria.service.ts` | nuevo | `features/categorias/services/categoria.service.spec.ts` |
| `features/categorias/components/dialogo-grupo.component.ts` (+ html, scss) | nuevo | `features/categorias/components/dialogo-grupo.component.spec.ts` |
| `features/categorias/components/dialogo-categoria.component.ts` (+ html, scss) | nuevo | `features/categorias/components/dialogo-categoria.component.spec.ts` |
| `features/categorias/components/dialogo-mover-categoria.component.ts` (+ html, scss) | nuevo | `features/categorias/components/dialogo-mover-categoria.component.spec.ts` |
| `features/categorias/components/grupo-categorias.component.ts` (+ html, scss) | nuevo | (cubierto por `categorias.page.spec.ts`) |
| `features/categorias/pages/categorias.page.ts` (+ html, scss) | nuevo | `features/categorias/pages/categorias.page.spec.ts` |
| `shared/api/problema-api.ts` | modificado | sin cambios en su spec |
| `app.routes.ts` | modificado | `app.routes.spec.ts` |

## Risks / Trade-offs

- [Arrastrar en jsdom no es fiable] → El cálculo se prueba en las funciones puras, y la página se
  prueba disparando `cdkDropListDropped` con un evento armado.
- [Una recarga después de cada movimiento] → Es una lista pequeña; a cambio, los `orden` siempre
  están al día y nunca se calcula una posición con datos viejos.
- [Listas de categorías anidadas dentro de una lista de grupos] → Los predicados de entrada
  impiden soltar un grupo dentro de una lista de categorías y viceversa.

## Migration Plan

Solo frontend. Rollback: revertir el change.
