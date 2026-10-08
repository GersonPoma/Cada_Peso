## Why

El backend ya guarda una meta por categoría, calcula mes a mes cuánto falta para financiarla, deja
posponerla en un mes y asigna dinero de forma automática según una estrategia (spec `metas`),
pero el frontend no lo usa: la persona no puede definir metas ni ver en el presupuesto del mes
cuánto le falta a cada categoría, que es lo que hace útil un presupuesto base cero.

## What Changes

- **Metas dentro de `features/presupuesto-mensual/`** (sin feature nueva ni ruta nueva):
  - Modelos propios de la meta (`MetaResponse`, `GuardarMetaRequest`, `MetasMesResponse`,
    `MetaMesResponse`, `AutoAsignarRequest`, `AutoAsignarResponse`) copiados de los records del
    backend, y `MetaService` (guardar, obtener, borrar, metas del mes, posponer, reanudar y
    auto-asignar).
  - Diálogo de meta para crear, editar y quitar la meta de una categoría desde el menú de su fila
    en el mes, con los tres tipos (`MONTO_MENSUAL` con frecuencia mensual, semanal o
    personalizada; `MONTO_PARA_FECHA`; `SALDO_OBJETIVO`) y solo los campos de cada tipo, con
    `app-campo-monto` y el datepicker regional.
  - Indicador de la meta debajo del nombre de cada categoría con meta: estado con texto e ícono,
    necesidad, faltante y barra de progreso; el resumen del mes suma el faltante de las metas.
  - Posponer y reanudar la meta en el mes desde el menú de la fila.
  - Diálogo de auto-asignar: estrategia, alcance (todas las visibles o una selección), vista
    previa con `simular` y confirmación que aplica.
  - La pantalla del mes pide las metas del mes junto con el mes y las vuelve a pedir tras cada
    operación; un error al cargar las metas no impide ver el mes.
- `CategoriaMesResponse` del frontend gana `esPagoTarjeta` y `cuentaId`, que el backend ya
  devuelve.
- `AGENTS.md`: las metas del frontend viven en `features/presupuesto-mensual`.

Fuera de alcance: `GET /reportes/metas` (queda para `reportes-frontend`), la lista `GET /metas`
(ver design), crear metas desde la sección Categorías, historial de metas y avisos de metas
vencidas. No cambia el backend ni se agregan librerías, rutas ni entradas del menú lateral.

## Capabilities

### New Capabilities
- `metas-frontend`: crear, editar y quitar la meta de una categoría; ver su estado en el mes;
  posponer y reanudar; auto-asignar con vista previa; estados de carga y error de las metas.

### Modified Capabilities
<!-- Ninguna: el indicador, las acciones del menú y el resumen se agregan sin cambiar los
requisitos de presupuesto-mensual-frontend. -->

## Impact

- **Frontend** (`frontend/src/app/features/presupuesto-mensual/`): archivos nuevos (modelos,
  servicio, función pura de progreso, diálogos de meta y de auto-asignar, indicador) y cambios en
  `presupuesto-mensual.page`, `grupo-mes.component` y `categoria-mes-response.model`. Ningún
  archivo compartido (`app.routes.ts`, menú lateral, `core/`, `shared/`) cambia.
- **API consumida**: `PUT/GET/DELETE .../categorias/{categoriaId}/meta`,
  `GET .../meses/{mes}/metas`, `POST .../meses/{mes}/metas/{categoriaId}/posponer` y `/reanudar`,
  `POST .../meses/{mes}/auto-asignar`; ya implementadas.
- **Dependencias**: ninguna nueva.
- **Documentación**: `AGENTS.md`.
