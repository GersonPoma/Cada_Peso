# reportes-frontend Specification

## Purpose
Permite ver desde el frontend los cinco reportes de solo lectura del presupuesto activo (gasto
por categoría, ingresos contra gastos, patrimonio, evolución del saldo de una cuenta y
cumplimiento de metas) para un rango de meses, con gráfico y tabla equivalente.

## Requirements

### Requirement: Sección Reportes con pestañas
El sistema SHALL ofrecer la pantalla `/presupuestos/:presupuestoId/reportes` con la entrada
`Reportes` en el menú lateral. La pantalla SHALL mostrar una pestaña por reporte, en este orden:
`Gasto`, `Ingresos y gastos`, `Patrimonio`, `Saldo de una cuenta` y `Metas`. La pestaña activa
SHALL vivir en el parámetro de consulta `reporte` (`gasto`, `ingresos-gastos`, `patrimonio`,
`saldo`, `metas`); un valor ausente o desconocido SHALL mostrar `Gasto`. Cambiar de pestaña
SHALL actualizar la URL sin agregar una entrada al historial por cada cambio de rango.

#### Scenario: Entrada desde el menú lateral
- **DADO** el presupuesto `3` activo
- **CUANDO** la persona pulsa `Reportes` en el menú lateral
- **ENTONCES** llega a `/presupuestos/3/reportes` con la pestaña `Gasto` activa

#### Scenario: Pestaña desde la URL
- **DADO** la URL `/presupuestos/3/reportes?reporte=patrimonio&desde=2026-01&hasta=2026-06`
- **CUANDO** la persona la abre o recarga la página
- **ENTONCES** ve la pestaña `Patrimonio` con el rango de enero a junio de 2026

#### Scenario: Pestaña desconocida
- **DADO** la URL con `reporte=otro`
- **CUANDO** se abre
- **ENTONCES** se muestra la pestaña `Gasto`

### Requirement: Selector de rango compartido
La pantalla SHALL tener un único selector de rango, compartido por todas las pestañas, con mes y
año para `Desde` y para `Hasta` (sin calendario de días), cada uno con su etiqueta visible. SHALL
ofrecer los atajos `Este mes`, `Últimos 3 meses`, `Últimos 6 meses`, `Últimos 12 meses` y
`Este año`, calculados con el mes local del navegador e inclusivos del mes actual. El rango SHALL
guardarse en la URL como `desde` y `hasta` (`yyyy-MM`). Sin rango válido en la URL SHALL usarse
`Últimos 6 meses` y escribirse en la URL. SHALL validarse en la interfaz, antes de pedir nada,
que `Desde` no sea posterior a `Hasta` (`Desde no puede ser posterior a Hasta`) y que el rango no
supere 60 meses (`El rango no puede superar 60 meses`); con un rango inválido SHALL NOT pedirse
ningún reporte y SHALL mantenerse en la URL el último rango válido. Los años ofrecidos SHALL ir
de 2000 a 2100. El texto del rango elegido SHALL mostrarse con los meses en texto largo según la
región (ej. `enero de 2026 – junio de 2026`).

#### Scenario: Atajos
- **DADO** el mes local `2026-10`
- **CUANDO** la persona elige cada atajo
- **ENTONCES** `Este mes` da `2026-10` a `2026-10`; `Últimos 3 meses`, `2026-08` a `2026-10`;
  `Últimos 6 meses`, `2026-05` a `2026-10`; `Últimos 12 meses`, `2025-11` a `2026-10`; y
  `Este año`, `2026-01` a `2026-10`

#### Scenario: Mes actual en hora local
- **DADO** el 31 de octubre de 2026 a las 21:00 en Bolivia (ya noviembre en UTC)
- **CUANDO** la persona elige `Este mes`
- **ENTONCES** el rango es `2026-10` a `2026-10`

#### Scenario: Sin rango en la URL
- **DADO** el mes local `2026-10` y la URL sin `desde` ni `hasta`
- **CUANDO** se abre la pantalla
- **ENTONCES** el rango es `2026-05` a `2026-10` y la URL lo refleja

#### Scenario: Rango mal formado en la URL
- **DADO** la URL con `desde=2026-13`
- **CUANDO** se abre la pantalla
- **ENTONCES** se usa `Últimos 6 meses` y no se pide nada con el valor recibido

#### Scenario: Desde posterior a Hasta
- **DADO** el rango `2026-03` a `2026-06`
- **CUANDO** la persona cambia `Desde` a julio de 2026
- **ENTONCES** se lee `Desde no puede ser posterior a Hasta`, no se hace ninguna petición y la
  URL conserva `2026-03` a `2026-06`

#### Scenario: Más de 60 meses
- **CUANDO** la persona elige `2021-01` a `2026-01` (61 meses)
- **ENTONCES** se lee `El rango no puede superar 60 meses` y no se pide nada
- **Y** `2021-02` a `2026-01` (60 meses) es válido

#### Scenario: El rango se comparte entre pestañas
- **DADO** el rango `2026-01` a `2026-03` en la pestaña `Gasto`
- **CUANDO** la persona cambia a `Patrimonio`
- **ENTONCES** el patrimonio se pide con el mismo rango

### Requirement: Carga al verse, respuestas atrasadas y estados
Cada pestaña SHALL pedir su reporte solo cuando está visible, nunca los cinco a la vez. Al
cambiar el rango o la cuenta SHALL cancelarse la petición anterior y descartarse su respuesta. La
pestaña SHALL mostrar un indicador de carga mientras espera, `Sin movimientos en este rango`
cuando el reporte no tiene datos, y el error con `Reintentar` cuando falla. Volver a una pestaña
ya cargada con el mismo rango SHALL NOT repetir la petición.

#### Scenario: Solo el reporte visible
- **DADO** la pantalla abierta en `Gasto`
- **CUANDO** termina de cargar
- **ENTONCES** se hizo una sola petición, a `gasto-por-categoria`

#### Scenario: Respuesta atrasada
- **DADO** una petición del rango `2026-01` a `2026-03` sin respuesta
- **CUANDO** la persona elige `2026-04` a `2026-06`
- **ENTONCES** la primera petición se cancela y solo se muestran las cifras del segundo rango

#### Scenario: Volver a una pestaña
- **DADO** `Gasto` cargado con un rango
- **CUANDO** la persona va a `Patrimonio` y vuelve a `Gasto` sin cambiar el rango
- **ENTONCES** no se pide de nuevo el gasto

#### Scenario: Sin movimientos
- **DADO** un rango sin transacciones
- **CUANDO** se carga `Gasto` o `Ingresos y gastos`
- **ENTONCES** se lee `Sin movimientos en este rango`, sin gráfico vacío

### Requirement: Errores por código
Los errores SHALL interpretarse por el `codigo` del `ProblemDetail`, nunca por `detail`:
`DATOS_INVALIDOS` SHALL mostrar `El rango de meses no es válido. Elige otro rango.`;
`RECURSO_NO_ENCONTRADO` en `Saldo de una cuenta` SHALL mostrar `La cuenta ya no existe.`, recargar
la lista de cuentas y quitar `cuentaId` de la URL, y en las demás pestañas `No encontramos el
presupuesto.` con la opción de recargar; cualquier otro error SHALL mostrar el aviso genérico con
`Reintentar`. Los avisos SHALL anunciarse a los lectores de pantalla.

#### Scenario: Rango rechazado por el servidor
- **DADO** un servidor configurado con un máximo menor a 60 meses
- **CUANDO** la persona pide 24 meses y la API responde `400 DATOS_INVALIDOS`
- **ENTONCES** se lee `El rango de meses no es válido. Elige otro rango.` junto al selector

#### Scenario: Cuenta borrada
- **DADO** la pestaña `Saldo de una cuenta` con una cuenta que otra sesión borró
- **CUANDO** la API responde `404 RECURSO_NO_ENCONTRADO`
- **ENTONCES** se lee `La cuenta ya no existe.`, la lista de cuentas se recarga y la URL queda
  sin `cuentaId`

#### Scenario: Error de red
- **CUANDO** la API no responde
- **ENTONCES** se muestra el aviso genérico con `Reintentar`, que repite solo la petición de la
  pestaña visible

### Requirement: Montos y porcentajes
Todo monto SHALL mostrarse con el pipe `monto` y la moneda del presupuesto activo, y todo mes con
su texto según la región. Los porcentajes SHALL llegar en centésimas de punto porcentual y
convertirse a texto solo al mostrar, con dos decimales y el formato de la región (`5283` →
`52,83%` en `es-BO`). Ninguna suma, resta ni comparación de dinero o porcentaje SHALL hacerse
con coma flotante; las proporciones de las barras y las alturas de los gráficos SHALL calcularse
con enteros. La interfaz SHALL mostrar los porcentajes que da la API, sin recalcularlos ni
forzar que sumen 100 %.

#### Scenario: Porcentaje en la región
- **DADO** la región `es-BO`
- **CUANDO** se muestra el porcentaje `5283`
- **ENTONCES** se lee `52,83%`

#### Scenario: Porcentajes que no suman 100
- **DADO** un reporte cuyos porcentajes suman `10001`
- **CUANDO** se muestra
- **ENTONCES** cada fila muestra el porcentaje de la API, sin ajuste

#### Scenario: Porcentaje mayor que 100
- **DADO** una meta con porcentaje `12500`
- **CUANDO** se muestra
- **ENTONCES** se lee `125,00%` y su barra queda llena con la marca `Más de lo necesario`

### Requirement: Gasto por categoría
La pestaña `Gasto` SHALL pedir `GET .../reportes/gasto-por-categoria?desde&hasta` y mostrar el
gasto total del rango, cada grupo con su total y porcentaje y, dentro, sus categorías con total,
porcentaje y una barra horizontal proporcional a su total (las categorías ocultas con la marca
`Oculta`), en el orden que da la API, y el cubo `Sin categoría` aparte, siempre visible. La
barra SHALL acompañarse del monto y el porcentaje en texto. Una categoría con total negativo
(reembolso mayor que el gasto) SHALL mostrarse con su signo, sin barra, y con la nota
`Reembolsos mayores que el gasto`. Cada categoría SHALL ofrecer `Ver transacciones`, enlace a la
lista de transacciones filtrada por `categoriaId`, `desde` (primer día del primer mes) y `hasta`
(último día del último mes). La pantalla SHALL indicar que los pagos de tarjeta no son gasto
(el gasto con tarjeta está en su categoría). El reporte está vacío si `total` es `0` y no hay
grupos.

#### Scenario: Ejemplo de octubre
- **DADO** la API devuelve total `265000`, Comida `140000` (`5283`), Metas de ahorro `100000`
  (`3774`), Hogar `20000` (`755`) y Sin categoría `5000` (`189`), en moneda `BOB`
- **CUANDO** se muestra la pestaña
- **ENTONCES** se lee el total de 265 con el pipe `monto` y `BOB`, y cada fila con su monto y su porcentaje, en el orden
  recibido, y `Sin categoría` aparte con `1,89 %`

#### Scenario: Reembolso mayor que el gasto
- **DADO** una categoría con total `-20000`
- **CUANDO** se muestra
- **ENTONCES** se lee el monto negativo y la nota `Reembolsos mayores que el gasto`, sin barra

#### Scenario: Enlace a transacciones
- **DADO** el rango `2026-09` a `2026-10` y la categoría `7`
- **CUANDO** la persona pulsa `Ver transacciones`
- **ENTONCES** llega a `/presupuestos/3/transacciones?categoriaId=7&desde=2026-09-01&hasta=2026-10-31`

### Requirement: Ingresos contra gastos
La pestaña `Ingresos y gastos` SHALL pedir `GET .../reportes/ingresos-gastos?desde&hasta` y
mostrar un gráfico de barras agrupadas por mes (ingresos y gastos, con relleno distinto además
de color) con el neto como línea con puntos, y una tabla con una fila por mes (`Mes`,
`Ingresos`, `Gastos`, `Neto`) y la fila `Total` con los totales de la API. Un neto negativo SHALL
mostrarse con su signo y la palabra `Déficit` además del color. SHALL incluir la nota de que el
saldo inicial de las cuentas no es ingreso de ningún mes. El reporte está vacío si los totales
de ingresos y gastos son `0`.

#### Scenario: Mes a mes
- **DADO** octubre con `500000`, `265000` y `235000` y noviembre en ceros
- **CUANDO** se muestra el rango `2026-10` a `2026-11`
- **ENTONCES** la tabla tiene las filas de octubre, noviembre (en ceros) y `Total`

#### Scenario: Déficit
- **DADO** un mes con neto `-30000`
- **CUANDO** se muestra
- **ENTONCES** la celda dice el monto negativo y `Déficit`

### Requirement: Patrimonio
La pestaña `Patrimonio` SHALL pedir `GET .../reportes/patrimonio?desde&hasta` y mostrar un
gráfico de líneas con `Patrimonio`, `Activos` y `Pasivos` (trazos distintos además de color, y
la línea del cero si hay valores negativos) y una tabla con `Mes`, `Activos`, `Pasivos` y
`Patrimonio`. SHALL explicar en una nota que incluye todas las cuentas (dentro y fuera del
presupuesto, abiertas y cerradas), que tarjetas y préstamos son pasivos, y que el saldo inicial
de cada cuenta cuenta desde el primer mes del rango porque las cuentas no tienen fecha de
apertura. El reporte está vacío si todos los meses valen `0`.

#### Scenario: Patrimonio negativo
- **DADO** un mes con activos `1800000`, pasivos `3050000` y patrimonio `-1250000`
- **CUANDO** se muestra
- **ENTONCES** la tabla muestra los tres montos y el gráfico dibuja la línea del cero

### Requirement: Evolución del saldo de una cuenta
La pestaña `Saldo de una cuenta` SHALL tener el selector `Cuenta` con todas las cuentas del
presupuesto (`GET .../cuentas?incluirCerradas=true`), con las marcas `Cerrada` y
`Fuera del presupuesto`, y guardar la elegida en la URL (`cuentaId`). Sin cuenta elegida SHALL
pedir que se elija una y SHALL NOT pedir el reporte; un `cuentaId` que no está en la lista SHALL
quitarse de la URL. Con cuenta SHALL pedir
`GET .../reportes/cuentas/{cuentaId}/evolucion-saldo?desde&hasta` y mostrar una línea de saldo
con barras de entradas y salidas por mes, la tabla `Mes`, `Entradas`, `Salidas`, `Saldo`, el
saldo inicial de la cuenta, la nota de que el saldo inicial cuenta desde el primer mes y el
enlace `Ver transacciones` filtrado por `cuentaId` y las fechas del rango.

#### Scenario: Cuenta cerrada
- **DADO** la cuenta cerrada `Ahorro viejo`
- **CUANDO** la persona la elige
- **ENTONCES** se pide su evolución y se muestra con la marca `Cerrada`

#### Scenario: Cambio de cuenta con respuesta atrasada
- **DADO** la evolución de `Banco` sin respuesta
- **CUANDO** la persona elige `Visa`
- **ENTONCES** solo se muestran las cifras de `Visa`

#### Scenario: Sin cuenta
- **DADO** la URL sin `cuentaId`
- **CUANDO** se abre la pestaña
- **ENTONCES** se lee `Elige una cuenta para ver su saldo` y no se pide la evolución

### Requirement: Cumplimiento de metas
La pestaña `Metas` SHALL pedir `GET .../reportes/metas?desde&hasta` y mostrar cada meta en el
orden recibido con su nombre (y `Oculta` si corresponde), tipo, monto y los totales del rango
(`necesidad`, `asignado`, `gastado`, porcentaje), y una fila por mes con necesidad, asignado,
gastado, disponible, faltante, estado y porcentaje. El estado SHALL mostrarse con texto e ícono
además de color: `Financiada`, `Falta`, `Sobregastada`, `Pospuesta`. Un porcentaje `null` SHALL
leerse `Sin necesidad`. SHALL incluir la nota de que la meta actual se aplica también a los meses
anteriores a su creación porque no hay historial de metas; por eso un `FALTA` de un mes anterior
al actual SHALL leerse `Faltaron` en tono neutro, no como error. Sin metas SHALL leerse
`No hay metas en este presupuesto`.

#### Scenario: Ejemplo de un mes
- **DADO** una meta con necesidad `100000`, asignado `80000`, gastado `60000`, faltante `20000`,
  estado `FALTA` y porcentaje `8000` en el mes actual
- **CUANDO** se muestra
- **ENTONCES** se lee `Falta`, el faltante y `80,00 %`

#### Scenario: Sin necesidad
- **DADO** un mes con porcentaje `null` y estado `POSPUESTA`
- **CUANDO** se muestra
- **ENTONCES** se lee `Pospuesta` y `Sin necesidad`

#### Scenario: Mes anterior a la meta
- **DADO** un mes anterior al actual con estado `FALTA`
- **CUANDO** se muestra
- **ENTONCES** se lee `Faltaron` en tono neutro, junto a la nota sobre el historial de metas

### Requirement: Accesibilidad y pantallas estrechas
Todo gráfico SHALL tener `role="img"` y una descripción textual (`aria-label` con el reporte y
el rango) y su tabla equivalente visible; ninguna serie SHALL distinguirse solo por color
(patrones, trazos, marcadores o leyenda con texto), con contraste suficiente sobre el fondo
usando los tokens `--mat-sys-*`. Los selectores de mes, año y cuenta SHALL tener etiqueta, el
foco SHALL verse en pestañas, atajos, selectores y enlaces, y los avisos de error y de rango
inválido SHALL anunciarse (`aria-live`). Los gráficos SHALL adaptarse al ancho de su contenedor
y las tablas SHALL desplazarse dentro de su contenedor, sin desplazamiento horizontal de la
página en un ancho de 360 px.

#### Scenario: Gráfico con alternativa
- **DADO** la pestaña `Patrimonio` cargada
- **CUANDO** un lector de pantalla llega al gráfico
- **ENTONCES** lee su descripción y puede recorrer la tabla con las mismas cifras

#### Scenario: Pantalla estrecha
- **DADO** un ancho de 360 px y un rango de 12 meses
- **CUANDO** se muestra `Ingresos y gastos`
- **ENTONCES** el gráfico ocupa el ancho disponible y la tabla se desplaza dentro de su
  contenedor, sin que la página se desplace a lo ancho
