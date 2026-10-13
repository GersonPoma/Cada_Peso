# Design

## Context

Ver proposal.md - Why. Estado observado leyendo el código (todo bajo `frontend/src/app`) y la spec
archivada del backend `tarjetas-credito-backend`:

**Lo que el backend impone** (`422 REGLA_NEGOCIO_VIOLADA` salvo indicación): renombrar, ocultar,
mostrar y mover el grupo `PAGOS_TARJETA`; renombrar, editar la nota, ocultar, mostrar y mover una
categoría de pago; crear una categoría en el grupo o mover una a él; usar una categoría de pago
en una transacción, subtransacción, transferencia, edición o `CATEGORIZAR` en lote; cambiar el
tipo de una cuenta desde o hacia `TARJETA_CREDITO`; categoría predeterminada de un beneficiario.
Sí se permiten asignar, mover dinero y metas en una categoría de pago, y filtrar la lista por
ella (devuelve vacío). El árbol y las filas del mes ya traen `esPagoTarjeta` y `cuentaId`; los
grupos, `tipo`.

**Verificación de los hallazgos**

| # | Hallazgo | Resultado |
|---|---|---|
| 1 | Los modelos no declaran los campos | Confirmado en `categorias/models/*` y `transacciones/models/categoria-resumen.model.ts`. Además `transacciones/models/cuenta-resumen.model.ts` **no tiene `tipo`**, que hace falta para el aviso y para elegir el origen del pago |
| 2 | Categorías ofrece acciones que dan `422` | Confirmado en `grupo-categorias.component.html` (menús de grupo y categoría, `Agregar categoría`, asas) y `dialogo-mover-categoria.component.html` (lista todos los grupos). **Matiz**: `moverOptimista` ya vuelve al árbol anterior y lo recarga si falla; solo falta el mensaje. `dialogo-categoria`, `dialogo-grupo` y `dialogo-mover-categoria` solo tratan `409`/`400` |
| 3 | La fila de pago no se distingue | Confirmado: `grupo-mes.component.html` no usa `esPagoTarjeta`; solo lo usa Auto-asignar |
| 4 | Los selectores no excluyen las de pago | Confirmado en `dialogo-transaccion.component.ts` (`grupos`), `dialogo-transferencia.component.ts` (`grupos`) y `transacciones.page.ts` (`gruposVisibles`, que alimenta la barra de lote y el filtro). El editor de división recibe los `grupos` del diálogo de transacción |
| 5 | Cuentas no bloquea el tipo y confunde el `422` | Confirmado: `tipos = TIPOS_CUENTA` sin restricción; `mostrarError` mapea todo `REGLA_NEGOCIO_VIOLADA` a `MENSAJE_SALDO_NEGATIVO` |
| 6 | No hay flujo de pago | Confirmado: el menú de cuenta solo tiene Editar, Conciliar y Cerrar; `Agregar transferencia` no propone la tarjeta |

Ya excluyen las categorías de pago, y **no se tocan**: `conciliacion/pages/conciliacion.page.ts`,
`transacciones-programadas/services/formulario-programada.ts`,
`beneficiarios/components/dialogo-beneficiario.component.ts` y Auto-asignar.

**Restricciones del proyecto**: una feature no importa de otra (AGENTS.md); `shared/` nunca
importa `features/`; errores por `codigo`, nunca por `detail`; Angular Material y `mat-icon`
(Material Symbols) únicamente; estado con texto e ícono, no solo color.

**Línea base de tests** (antes de cambiar nada, `npx ng test --watch=false`): 120 archivos y
**1328 tests; 1327 pasan** y 1 expira a los 5 s (`dialogo-transferencia.component.spec.ts >
crear > entre dos cuentas del presupuesto envía sin categoriaId...`), que pasa en aislado (26 de
26): es intermitente por la carga de la suite completa y no depende de este change.

## Goals / Non-Goals

**Goals:**
- Que ninguna pantalla ofrezca una acción que el backend rechaza por las reglas de tarjetas, y
  que un `422` que igual ocurra (árbol desactualizado) se explique con un mensaje fijo.
- Un solo filtro y una sola etiqueta compartidos, no copias por pantalla.
- Tocar el mínimo de archivos existentes y ninguno de los que ya funcionan bien.

**Non-Goals:**
- Migrar Programadas, Conciliación y Beneficiarios al filtro compartido (dejan su filtro propio;
  queda como deuda anotada).
- Rellenar el monto del pago con la deuda de la tarjeta, o mostrar la deuda como etiqueta en
  Cuentas.
- Cambiar el cálculo de la reserva, el backend o las pantallas de Reportes.

## Decisions

### D1. Campos nuevos en los modelos: aditivos y opcionales
`esPagoTarjeta?: boolean` y `cuentaId?: number | null` en `CategoriaResponse` y
`CategoriaResumen`; `tipo?: 'NORMAL' | 'PAGOS_TARJETA'` en los grupos; `tipo?: string` en
`CuentaResumen`. El backend los envía siempre, pero declararlos obligatorios obligaría a editar
los fixtures tipados de unos 9 specs existentes (`categorias/*`, `transacciones/*`), justo los
archivos más disputados. Un campo ausente se trata como "no es de pago / normal". *Alternativa*:
obligatorios y actualizar fixtures; descartada por el riesgo de conflictos con quien trabaja en
paralelo. Los modelos son interfaces: no tienen test en tiempo de ejecución; su uso lo cubren los
tests de los helpers y de los componentes.

### D2. Lo protegido se muestra como protegido y sin acciones
El grupo de pagos y sus categorías llevan la etiqueta compartida (ícono `credit_card` + `Pago de
tarjeta`), pierden el menú y las asas y el grupo pierde `Agregar categoría`; la categoría añade
la nota `Se crea y se renombra sola con la tarjeta` como motivo. *Alternativa*: dejar el menú con
los ítems deshabilitados y un tooltip; descartada porque un menú entero deshabilitado es ruido y
un tooltip no se lee en pantallas táctiles. El destino de soltar y de `Mover a...` excluye el
grupo de pagos: en el componente del grupo, el predicado de entrada rechaza lo soltado si el
grupo es de pagos; en el diálogo, la lista de grupos lo omite. Reordenar los demás grupos no
cambia (el backend lo permite).

### D3. Un filtro compartido: `sinCategoriasDePago`
Función pura genérica en `shared/pago-tarjeta/categorias-sin-pago.ts`:
`sinCategoriasDePago(grupos, conservarId = null)` quita las categorías con `esPagoTarjeta` y los
grupos que queden vacíos, salvo la categoría `conservarId`. La usan solo `features/transacciones`
(tres puntos: `grupos` del diálogo de transacción, `grupos` del diálogo de transferencia y
`gruposVisibles` de la página, que alimenta la barra de lote). Es genérica por estructura
(`{ id, esPagoTarjeta? }`), así `shared/` no importa ningún modelo de feature. *Alternativa*:
filtrar dentro de cada componente; descartada (cuatro copias). *Alternativa*: reemplazar ya el
filtro de Programadas/Conciliación/Beneficiarios; descartada por la restricción de no tocarlos.

### D4. Filtro por categoría de la lista: se ocultan, salvo la elegida
Filtrar por una categoría de pago es válido pero siempre da una lista vacía, así que ofrecerla
confunde. La página calcula `gruposParaFiltro = sinCategoriasDePago(gruposVisibles(),
filtros().categoriaId)` para que un enlace antiguo `?categoriaId=<pago>` siga mostrando su
elección. *Alternativa*: dejarlas (devuelven vacío); descartada por confusa.

### D5. Mensaje fijo para el `422` de lo protegido
Constante en `shared/pago-tarjeta/mensajes-pago-tarjeta.ts` junto al resto de textos
compartidos. En Categorías: `moverOptimista` y `ejecutar` (página) y los tres diálogos eligen el
mensaje por `codigo === REGLA_NEGOCIO_VIOLADA`; los diálogos de categoría y grupo lo muestran en
el aviso (como hacen con el genérico) y el de `Mover a...` en su `errorGeneral`. Los demás
códigos siguen igual. No se lee `detail`.

### D6. Cuentas: bloqueo por validación y `422` por contexto de la operación
El control `tipo` gana un validador (como el de saldo negativo, que ya existe) que marca el error
cuando, al editar, el tipo elegido difiere del guardado y alguno de los dos es `TARJETA_CREDITO`;
`Guardar` ya se deshabilita con el formulario inválido. No se deshabilitan las opciones: la
persona ve el motivo en el campo y el patrón es el mismo del diálogo.
*Alternativa*: deshabilitar el selector o las opciones; descartada porque no explica el motivo y
rompería el test existente `con saldo inicial negativo, un tipo que no lo admite marca el tipo`
desde su primera aserción.

Orden de los mensajes: `mensajeDeError` devuelve solo el primer mensaje por el orden de claves de
`MENSAJES_TIPO`, así que la clave nueva va **después** de `tipoNoAdmiteSaldoNegativo`; el test
existente sigue viendo su mensaje del saldo. Cuando fallan las dos reglas (una tarjeta con saldo
negativo que se cambia a `Ahorro`) el campo muestra ambos mensajes, el segundo en un `mat-error`
propio.

El `422` se interpreta por contexto, sin `detail`: en `mostrarError`, si la operación era editar
y el tipo enviado difiere del que tenía la cuenta con alguno de los dos `TARJETA_CREDITO`, el
mensaje es el del cambio de tipo; en cualquier otro caso se conserva el del saldo negativo (así
los tests de `422` al crear y al editar sin cambio de tipo siguen igual).

**Excepción declarada a "las specs existentes no cambian"**: en ese mismo test
(`dialogo-cuenta.component.spec.ts`, líneas ~379-395) la última parte afirma que, tras elegir
`Préstamo` en una tarjeta con saldo negativo, `Guardar` queda habilitado. El backend ya responde
`422` a ese cambio (`tarjetas-credito-backend` lo declaró `BREAKING (acotado)` y modificó sus
tests equivalentes). La aserción pasa a esperar `Guardar` deshabilitado y el mensaje de tarjeta;
el resto de ese test, y todos los demás, no cambian. Es la única aserción existente que cambia.

### D7. Pagar tarjeta: la URL conecta las dos features
`features/cuentas` no puede importar el diálogo de `features/transacciones`. El ítem `Pagar
tarjeta` del menú de Cuentas es un `routerLink` a `/presupuestos/:id/transacciones` con
`cuentaId=<tarjeta>&pagarTarjeta=<tarjeta>` (mismo patrón que `Conciliar`, que ya viaja por
`routerLink`). `TransaccionesPage` lee `pagarTarjeta` de la URL; cuando las cuentas y el árbol
terminan de cargar, abre el `DialogoTransferenciaComponent` existente con un dato nuevo opcional
`pagoTarjetaId` y limpia el parámetro con `replaceUrl` y `queryParamsHandling: 'merge'`, de modo
que recargar no lo reabra y queda el filtro por cuenta. Un id que no sea una tarjeta abierta del
presupuesto se ignora y se limpia. Al quitar el parámetro, la lista se vuelve a pedir una vez
(los filtros son una señal nueva): se acepta. En modo de pago el diálogo fija el destino
(control deshabilitado, ya se lee con `getRawValue()`), oculta `Invertir`, filtra los orígenes a
`enPresupuesto && tipo !== 'TARJETA_CREDITO'` y muestra la nota; sin orígenes, un mensaje y el
botón deshabilitado. Solo las tarjetas del presupuesto lo ofrecen: una de seguimiento no tiene
categoría de pago ni reserva. *Alternativas*: mover el diálogo a `shared/` (cambio grande sobre
código muy disputado); abrir el diálogo desde Cuentas importándolo (prohibido por AGENTS.md).

### D8. Aviso de entrada sin categoría: informativo
En `dialogo-transaccion`, un texto con ícono `info` (`role="status"`) bajo la categoría cuando
cuenta=tarjeta del presupuesto, tipo=Entrada, sin dividir y sin categoría. No bloquea el guardado
(el backend la admite) ni se vuelve un error.

### D9. Presupuesto mensual: solo la fila, con enlace a movimientos
`GrupoMesComponent` muestra, por fila de pago, la etiqueta, `Reserva para pagar la tarjeta` y
`Ver movimientos` (`/presupuestos/:id/transacciones?cuentaId=`), porque no existe una pantalla
de detalle de cuenta. El `id` del presupuesto sale de `PresupuestoActivoService` (`core/`). No se
tocan la celda de asignado, el menú ni las metas. Los tests nuevos van en un spec hermano
(`grupo-mes.tarjeta.component.spec.ts`) porque necesitan `provideRouter`, que el spec existente
no registra.

### D11. El foco del pago vuelve al botón `Agregar transferencia`
Hallado en la revisión manual: el pago se abre por URL, así que cuando se cierra no hay botón de
origen y el foco quedaba en el documento. `TransaccionesPage` pasa `restoreFocus` (el elemento del
botón `Agregar transferencia`) **solo** al abrir el diálogo de pago; los demás diálogos no llevan
esa opción, porque un test existente comprueba que no la tienen y su manejo del foco funciona. El
botón se obtiene con `viewChild('botonTransferencia', { read: ElementRef })`: sin `read`, una
referencia de plantilla sobre un elemento con `mat-stroked-button` devuelve el componente
`MatButton`, no el elemento. *Alternativa*: dar `restoreFocus` a todos los diálogos; descartada por
cambiar comportamiento que ya funciona. *Alternativa*: enfocar el botón a mano al cerrarse;
descartada porque `restoreFocus` ya lo resuelve para Escape, `Cancelar` y guardar.

### D10. Dos etapas
**A** y **B** son independientes salvo por la etiqueta compartida (A la crea, B la usa) y se
pueden integrar y probar por separado. Si se prefiere un change por etapa, el corte es entre
`tasks.md` 1-4 (A) y 5-8 (B): las specs también se separan limpiamente (A: `tarjetas-credito-
frontend`, `categorias-frontend`; B: `cuentas-frontend`, `presupuesto-mensual-frontend`,
`transferencias-frontend`, `transacciones-frontend` salvo el filtro de categoría).

## Archivos y tests (rutas desde `src/app/`)

Riesgo: **B**ajo (archivo nuevo o cambio aislado), **M**edio (archivo compartido pero poco
tocado), **A**lto (archivo que suele cambiar en paralelo).

### Etapa A

| Archivo | Cambio | Riesgo | Test |
|---|---|---|---|
| `shared/pago-tarjeta/categorias-sin-pago.ts` (nuevo) | `sinCategoriasDePago` | B | `categorias-sin-pago.spec.ts` (nuevo) |
| `shared/pago-tarjeta/mensajes-pago-tarjeta.ts` (nuevo) | Textos fijos compartidos | B | los cubren los componentes que los usan |
| `shared/pago-tarjeta/etiqueta-pago-tarjeta.component.{ts,html,scss}` (nuevo) | `app-etiqueta-pago-tarjeta` | B | `etiqueta-pago-tarjeta.component.spec.ts` (nuevo) |
| `features/categorias/models/categoria-response.model.ts` | `+ esPagoTarjeta?`, `cuentaId?` | B | tipos usados en los specs de abajo |
| `features/categorias/models/grupo-categoria-response.model.ts` | `+ tipo?`; exporta `TipoGrupoCategoria` | B | ídem |
| `features/categorias/models/grupo-categoria-con-categorias-response.model.ts` | `+ tipo?` | B | ídem |
| `features/categorias/services/pago-tarjeta.ts` (nuevo) | `esGrupoDePagos`, `esCategoriaDePago` | B | `pago-tarjeta.spec.ts` (nuevo) |
| `features/categorias/components/grupo-categorias.component.{ts,html,scss}` | Etiqueta, sin menú/asa/`Agregar`; predicado de entrada | M | `grupo-categorias.component.spec.ts` (nuevo: hoy no existe) |
| `features/categorias/pages/categorias.page.{ts,html}` | `cdkDragDisabled` del grupo de pagos; mensaje `422` en `moverOptimista`/`ejecutar` | M | `categorias.page.spec.ts` (nuevos `it`) |
| `features/categorias/components/dialogo-mover-categoria.component.ts` | Omite el grupo de pagos; mensaje `422` | M | `dialogo-mover-categoria.component.spec.ts` (nuevos `it`) |
| `features/categorias/components/dialogo-categoria.component.ts` | Mensaje `422` | B | `dialogo-categoria.component.spec.ts` (nuevo `it`) |
| `features/categorias/components/dialogo-grupo.component.ts` | Mensaje `422` | B | `dialogo-grupo.component.spec.ts` (nuevo `it`) |
| `features/transacciones/models/categoria-resumen.model.ts` | `+ esPagoTarjeta?`, `cuentaId?` | B | tipos usados abajo |
| `features/transacciones/models/grupo-categorias-resumen.model.ts` | `+ tipo?` | B | ídem |
| `features/transacciones/components/dialogo-transaccion.component.ts` | `grupos` pasa por `sinCategoriasDePago` (cubre el editor de división) | A | `dialogo-transaccion.component.spec.ts` (nuevos `it`) |
| `features/transacciones/components/dialogo-transferencia.component.ts` | `grupos` pasa por `sinCategoriasDePago` | A | `dialogo-transferencia.component.spec.ts` (nuevo `it`) |
| `features/transacciones/pages/transacciones.page.{ts,html}` | `gruposVisibles` sin las de pago; `gruposParaFiltro` | A | `transacciones.page.spec.ts` (nuevos `it`: lote y filtro) |

### Etapa B

| Archivo | Cambio | Riesgo | Test |
|---|---|---|---|
| `features/transacciones/models/cuenta-resumen.model.ts` | `+ tipo?` | B | tipos usados abajo |
| `features/transacciones/models/datos-dialogos-transacciones.model.ts` | `DatosDialogoTransferencia.pagoTarjetaId?` | B | ídem |
| `features/transacciones/components/dialogo-transferencia.component.{ts,html}` | Modo de pago: destino fijo, orígenes elegibles, nota, sin `Invertir` | A | `dialogo-transferencia.component.spec.ts` (nuevo `describe`) |
| `features/transacciones/pages/transacciones.page.{ts,html}` | Lee `pagarTarjeta`, abre el diálogo, limpia la URL y devuelve el foco a `Agregar transferencia` (D11) | A | `transacciones.page.spec.ts` (nuevo `describe`) |
| `features/transacciones/components/dialogo-transaccion.component.{ts,html}` | Aviso de entrada sin categoría en tarjeta | A | `dialogo-transaccion.component.spec.ts` (nuevo `describe`) |
| `features/cuentas/components/dialogo-cuenta.component.{ts,html}` | Validador de tipo, mensajes, `422` por contexto | A | `dialogo-cuenta.component.spec.ts` (nuevo `describe` + **1 aserción cambiada**, ver D6) |
| `features/cuentas/pages/cuentas.page.html` | Ítem `Pagar tarjeta` | M | `cuentas.page.spec.ts` (nuevos `it`) |
| `features/presupuesto-mensual/components/grupo-mes.component.{ts,html,scss}` | Etiqueta, reserva y enlace en la fila de pago | M | `grupo-mes.tarjeta.component.spec.ts` (nuevo) |
| `AGENTS.md` (raíz del repositorio) | Sección "Tarjetas de crédito (frontend)" | M | — |

Todos los componentes nuevos o tocados usan `ChangeDetectionStrategy.OnPush`, `mat-icon` y los
tokens `--mat-sys-*`; la etiqueta y las aclaraciones se apilan en pantallas estrechas sin
desbordar. Ningún archivo nuevo está fuera de `shared/pago-tarjeta/` y de la feature que lo usa.

## Risks / Trade-offs

- **Archivos disputados** (`transacciones.page.ts`, `dialogo-transferencia.component.*`,
  `dialogo-cuenta.component.*`) → los cambios son aditivos y pequeños; ver el orden en
  `tasks.md` (rebase de la rama antes de cada etapa).
- **Campos opcionales en los modelos** debilitan el tipado → solo `=== true`/`=== 'PAGOS_TARJETA'`
  se tratan como de pago; un helper por feature concentra la lectura.
- **El árbol puede quedar desactualizado** (otra sesión crea una tarjeta) y un `422` igual
  ocurre → mensaje fijo y recarga, como ya hacen los movimientos.
- **Dos filtros equivalentes viven en Programadas/Conciliación/Beneficiarios** → deuda anotada,
  fuera de alcance por la restricción de no tocarlos.
- **Una aserción existente cambia** (D6) → declarada en proposal, design y tasks.
- **Una petición extra de la lista** al quitar `pagarTarjeta` de la URL → se acepta.
- **Test intermitente previo** de `dialogo-transferencia` por carga → no se confunde con una
  regresión: se reejecuta aislado antes de investigar.

## Migration Plan

Solo frontend, sin datos ni API. Cada etapa es desplegable por separado. Revertir una etapa es
revertir sus archivos; la etapa B no cambia nada que A necesite.
