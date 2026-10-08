## Why

Hoy la app solo muestra el presente: el presupuesto de un mes, el saldo actual de cada cuenta y
las metas de un mes. Quien lleva meses usándola no puede responder "¿en qué gasto más?", "¿le
gano a mis gastos?", "¿mi patrimonio sube?" ni "¿cumplo mis metas?" sin sumar a mano. Todos los
datos ya existen; falta leerlos agregados por rango de meses, sin inventar una segunda versión
de las reglas del presupuesto.

## What Changes

- Nueva feature backend `reporte` con cinco endpoints **de solo lectura (GET)** bajo
  `/api/v1/presupuestos/{presupuestoId}/reportes/...`:
  1. `gasto-por-categoria?desde&hasta`: gasto neto por categoría y grupo en el rango, con total y
     porcentaje, más un cubo aparte "Sin categoría".
  2. `ingresos-gastos?desde&hasta`: ingresos, gastos y neto, un elemento por mes del rango.
  3. `patrimonio?desde&hasta`: activos, pasivos y patrimonio neto al cierre de cada mes, con
     todas las cuentas (dentro y fuera del presupuesto, abiertas y cerradas).
  4. `cuentas/{cuentaId}/evolucion-saldo?desde&hasta`: entradas, salidas y saldo al cierre de
     cada mes de una cuenta, partiendo de su `saldoInicial`.
  5. `metas?desde&hasta`: por meta, necesidad, asignado, gastado y porcentaje de cumplimiento,
     mes a mes y en total (`hasta` opcional: por defecto el mismo mes de `desde`).
- Reglas de qué cuenta como gasto, ingreso y patrimonio decididas y documentadas con ejemplos
  numéricos (transferencias, categorías de pago de tarjeta, tarjetas, divisiones, sin
  categoría, cuentas cerradas y fuera del presupuesto).
- Rango máximo configurable (`reportes.max-meses`, 60 por defecto). Un rango inválido, un mes
  mal formado o un rango que excede el máximo responde `400`; los meses sin datos devuelven
  ceros, nunca se omiten.
- Los parámetros llegan como texto y los valida el service, para que el orden de errores sea
  presupuesto `404`, cuenta `404`, parámetros `400`.
- **Sin duplicar reglas**: los reportes 1 y 2 usan las mismas consultas de actividad que el mes
  (`ActividadMensualRepository`); el 5 usa `CalculoMensual` (a través de `asignacion`) y
  `CalculoMeta`. Tests de coincidencia con el mes, con la cuenta y con la suma de saldos.
- Cambios mínimos en código existente, sin cambio de comportamiento: `CalculadoraMes` expone un
  cálculo por rango de meses con una sola carga de datos (`MesPresupuestoService`),
  `CalculoMeta` pasa a ser público, y se agregan consultas de lectura a `MetaPospuestaRepository`
  y `ActividadMensualRepository`. Los tests existentes no cambian ninguna aserción.
- Fuera de alcance: frontend y gráficos, exportar (CSV/PDF), reportes por beneficiario o por
  etiqueta, comparar contra otro periodo, caché, y cualquier tabla nueva o cambio de esquema.

No hay cambios incompatibles (**BREAKING**): no cambia ningún endpoint ni respuesta existente.

## Capabilities

### New Capabilities
- `reportes`: lectura agregada por rango de meses del gasto por categoría, ingresos contra
  gastos, patrimonio neto, evolución del saldo de una cuenta y cumplimiento de metas, con sus
  reglas de contabilización, límites de rango y orden de errores.

### Modified Capabilities
<!-- Ninguna: los requisitos de asignacion, metas, cuentas y transacciones no cambian; solo se
     exponen cálculos internos ya existentes y se agregan consultas de lectura. -->

## Impact

- Backend: paquete nuevo `com.presupuesto.reporte` (`controller`, `service`, `repository`,
  `dto/response`). Toca de forma aditiva `asignacion` (`CalculadoraMes`, `MesPresupuestoService`,
  `ActividadMensualRepository`) y `meta` (`CalculoMeta`, `MetaPospuestaRepository`).
- `application.properties`: `reportes.max-meses=60`. AGENTS.md: `reporte` se agrega al `grep`
  de dependencias de `comun/`, al árbol de paquetes y a la regla de dependencias (reporte
  importa las demás, nadie importa reporte), y una sección "Reportes".
- Sin dependencias nuevas en `pom.xml`, sin cambios de esquema, sin cambios de frontend. La
  línea base de tests debe seguir pasando sin tocar ninguna aserción.
