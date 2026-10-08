## Why

El backend ya calcula cinco reportes de solo lectura por rango de meses (gasto por categoría,
ingresos contra gastos, patrimonio, evolución del saldo de una cuenta y cumplimiento de metas;
spec `reportes`), con las mismas reglas que el presupuesto mensual. Pero el frontend no los usa:
la persona solo ve un mes a la vez y no puede saber en qué se le va el dinero, si gana más de lo
que gasta ni cómo evoluciona su patrimonio.

## What Changes

- **Nueva feature `features/reportes/`** con la pantalla `/presupuestos/:presupuestoId/reportes`
  y su entrada `Reportes` en el menú lateral (una ruta hija con `data: seccion(...)`):
  - **Una sola pantalla con pestañas** (`mat-tab-group`), una por reporte: `Gasto`,
    `Ingresos y gastos`, `Patrimonio`, `Saldo de una cuenta` y `Metas`. La pestaña activa, el
    rango y la cuenta viven en la URL (`reporte`, `desde`, `hasta`, `cuentaId`) para recargar o
    compartir. Cada pestaña pide su reporte solo cuando se ve.
  - **Selector de rango compartido** (mes y año de `Desde` y de `Hasta`, sin calendario de días)
    con atajos `Este mes`, `Últimos 3 meses`, `Últimos 6 meses`, `Últimos 12 meses` y
    `Este año`; valida en la interfaz `desde <= hasta`, el máximo de 60 meses y los años 2000 a
    2100. Sin rango en la URL se usa `Últimos 6 meses` y se escribe en la URL.
  - **Gasto por categoría**: barras horizontales por grupo y categoría con total, porcentaje y
    "Sin categoría" aparte, total general y enlace `Ver transacciones` a la lista filtrada por
    categoría y fechas.
  - **Ingresos contra gastos**: barras mensuales agrupadas (ingresos y gastos) con el neto como
    texto y línea, y tabla con totales.
  - **Patrimonio**: línea mensual de patrimonio con activos y pasivos, y tabla.
  - **Saldo de una cuenta**: selector con todas las cuentas (también cerradas y fuera del
    presupuesto), línea de saldo y tabla con entradas, salidas y saldo por mes.
  - **Metas**: por meta, porcentaje y estado de cada mes y del rango, con texto además de color;
    porcentaje `null` como `Sin necesidad`.
  - Notas breves: el saldo inicial cuenta desde el primer mes (las cuentas no tienen fecha de
    apertura) y la meta vigente se evalúa también en meses anteriores a su creación.
  - **Gráficos en SVG propio**, sin librería nueva: componentes de barras horizontales, barras
    mensuales y líneas con su tabla equivalente, patrones además de color y ancho adaptable.
  - Errores por `codigo` (`DATOS_INVALIDOS`, `RECURSO_NO_ENCONTRADO`, otro con `Reintentar`),
    respuestas atrasadas descartadas con `switchMap`, estados de carga, vacío y error.
- **`shared/fecha/mes.ts`**: las funciones de meses `yyyy-MM` (`mesActual`, `sumarMeses`,
  `esMesValido`, `textoMes`) pasan de `features/presupuesto-mensual/services/mes.ts` a `shared/`
  para que reportes las use sin importar otra feature; el archivo viejo queda como reexportación
  de una línea y nadie más cambia sus imports.
- `app.routes.ts`: una ruta hija `reportes` con `data: seccion('Reportes', 'bar_chart')`.
- `AGENTS.md`: la feature `reportes` del frontend y la nueva ubicación de `mes.ts`.

Fuera de alcance: exportar a PDF o CSV, reportes personalizados, comparar rangos, guardar
filtros (más allá de la URL), reportes por beneficiario y un drill-down propio (solo el enlace a
la lista de transacciones existente, que ya entiende `categoriaId`, `cuentaId`, `desde` y
`hasta`). No cambia el backend ni se agregan dependencias.

**Tamaño.** Cabe en un solo change porque los cinco reportes comparten selector, servicio,
errores y componentes de gráfico. Si se prefiere partir, el corte natural está en las tareas: la
**parte A** (grupos 1 a 6: base, selector, gráficos, gasto e ingresos contra gastos) se puede
aplicar y publicar sola; la **parte B** (grupos 7 a 9: patrimonio, saldo de una cuenta y metas)
agrega pestañas sin tocar lo anterior.

## Capabilities

### New Capabilities
- `reportes-frontend`: sección Reportes con pestañas, selector de rango en la URL, los cinco
  reportes con gráfico y tabla, notas de interpretación, carga al verse, errores por código,
  accesibilidad y pantallas estrechas.

### Modified Capabilities
<!-- Ninguna: mover mes.ts a shared/ no cambia ningún comportamiento del presupuesto mensual. -->

## Impact

- **Frontend** (`frontend/src/app`): archivos nuevos en `features/reportes/` y
  `shared/fecha/mes.ts` (+ spec). Archivos compartidos modificados: **solo** `app.routes.ts` (una
  ruta) y `app.routes.spec.ts` (un caso), más `features/presupuesto-mensual/services/mes.ts`
  (pasa a reexportar) y el movimiento de su spec. El layout del menú lateral, `core/` y el resto
  de `shared/` no cambian.
- **API consumida**: `GET .../reportes/gasto-por-categoria`, `ingresos-gastos`, `patrimonio`,
  `cuentas/{cuentaId}/evolucion-saldo` y `metas`, y `GET .../cuentas?incluirCerradas=true`; ya
  implementadas.
- **Dependencias**: ninguna nueva (se descartó ng2-charts + Chart.js; ver design).
- **Documentación**: `AGENTS.md`.
