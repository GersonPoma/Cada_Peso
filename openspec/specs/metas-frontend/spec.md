# metas-frontend Specification

## Purpose
Permite definir desde el presupuesto del mes la meta de cada categoría, ver cuánto le falta en
ese mes, posponerla y asignar dinero de forma automática con una vista previa antes de aplicar.

## Requirements

### Requirement: Metas del mes junto con el mes
La pantalla del presupuesto del mes SHALL pedir `GET .../meses/{mes}/metas` con el mismo
`incluirOcultas` que el mes, cada vez que pide el mes, y SHALL mostrar solo la respuesta que
corresponde al último mes, filtro y recarga pedidos; una respuesta atrasada SHALL ignorarse. Si
las metas fallan y el mes no, SHALL mostrarse el mes sin indicadores de meta y el aviso
`No pudimos cargar las metas` con `Reintentar`, que vuelve a pedir el mes y sus metas. Tras
guardar, quitar, posponer o reanudar una meta, o aplicar una auto-asignación, SHALL volver a
pedirse el mes y sus metas.

#### Scenario: Cambio de mes rápido
- **DADO** la pantalla en `2026-10` con la petición de metas de octubre todavía sin respuesta
- **CUANDO** la persona pasa a `2026-11` y la respuesta de octubre llega después de la de
  noviembre
- **ENTONCES** los indicadores son los de noviembre

#### Scenario: Error solo en las metas
- **DADO** que el mes responde bien y `GET .../metas` del mes falla
- **CUANDO** se muestra la pantalla
- **ENTONCES** se ven las categorías con su asignado, actividad y disponible, sin indicadores, y
  el aviso `No pudimos cargar las metas` con `Reintentar`
- **Y** al pulsar `Reintentar` se vuelven a pedir el mes y sus metas

#### Scenario: Ocultas
- **DADO** una meta en una categoría oculta
- **CUANDO** la persona activa `Mostrar ocultas`
- **ENTONCES** se piden las metas con `incluirOcultas=true` y la categoría oculta muestra su
  indicador; sin `Mostrar ocultas` no aparece ni suma en el faltante total

### Requirement: Indicador de la meta en la fila
Cada categoría con meta en el mes SHALL mostrar, debajo de su nombre y sin agregar columnas, su
estado con texto e ícono (`Financiada`, `Falta {faltante}`, `Pospuesta este mes`, `Sobregastada`),
la necesidad del mes (`Meta del mes: {necesidad}`) y una barra de progreso con
`asignado / necesidad` (llena con necesidad 0, entre 0 % y 100 %) con `aria-label` que la
describe. Una meta pospuesta SHALL verse con necesidad 0 y la barra llena, sin color de error.
En los meses anteriores al actual, `Falta` SHALL verse como `Faltaron {faltante}` en un tono
neutro, sin color de error, porque la meta vigente se aplica a todos los meses (no hay historial).
El resumen del mes SHALL mostrar `Falta para tus metas: {totalFaltante}` cuando sea mayor que 0.
Las categorías sin meta SHALL verse como hasta ahora. En pantallas estrechas el indicador
SHALL ocupar todo el ancho de la fila apilada.

#### Scenario: Falta dinero
- **DADO** una meta mensual de `100000` con `asignado` `60000` en el mes actual
- **CUANDO** se muestra la fila
- **ENTONCES** se ve `Falta 40` (en la moneda del presupuesto), `Meta del mes: 100` y la barra al
  60 %

#### Scenario: Financiada y sobregastada
- **DADO** una meta `FINANCIADA` y otra `SOBREGASTADA`
- **CUANDO** se muestran las filas
- **ENTONCES** la primera dice `Financiada` con la barra llena y la segunda `Sobregastada` con
  ícono de advertencia, ambas con texto además del color

#### Scenario: Mes anterior al actual
- **DADO** hoy en octubre de 2026 y una meta creada este mes
- **CUANDO** la persona mira `2026-08`, donde la meta tiene `faltante` `100000`
- **ENTONCES** la fila dice `Faltaron 100` en tono neutro, sin color ni ícono de error

#### Scenario: Total faltante
- **DADO** dos metas visibles con `faltante` `40000` y `10000`
- **CUANDO** se muestra el mes
- **ENTONCES** el resumen dice `Falta para tus metas: 50`

### Requirement: Crear y editar la meta de una categoría
El menú de cada categoría del mes SHALL ofrecer `Agregar meta` si no tiene meta o `Editar meta`
si la tiene, que abre un diálogo con el `Tipo` (`Monto cada cierto tiempo`, `Monto para una
fecha`, `Saldo objetivo`), el `Monto` con la calculadora (obligatorio y mayor que 0) y, según el
tipo y la frecuencia, solo estos campos: para `Monto cada cierto tiempo`, la `Frecuencia`
(`Mensual`, `Semanal`, `Personalizada`), con `Semanal` el `Día de la semana` (lunes a domingo,
1 a 7) y con `Personalizada` `Cada cuántos días` (de 2 a 365) y la `Fecha de inicio`; para
`Monto para una fecha`, la `Fecha objetivo` (puede ser pasada). Editar SHALL pedir
`GET .../categorias/{categoriaId}/meta` y mostrar sus valores (con un indicador mientras carga).
Guardar SHALL enviar `PUT .../categorias/{categoriaId}/meta` con `tipo`, `monto` y solo los
campos del tipo, las fechas como `yyyy-MM-dd` locales. El diálogo SHALL mostrar el nombre de la
categoría y la ayuda `La meta se aplica a todos los meses, también a los anteriores`. El botón
SHALL estar deshabilitado mientras el formulario sea inválido o se esté enviando.

#### Scenario: Monto mensual
- **DADO** la categoría `Comida` sin meta
- **CUANDO** la persona elige `Monto cada cierto tiempo`, `Mensual`, escribe `100` y guarda
- **ENTONCES** se envía `{ tipo: 'MONTO_MENSUAL', monto: 100000, frecuencia: 'MENSUAL' }` y se
  vuelven a pedir el mes y sus metas

#### Scenario: Semanal
- **DADO** el diálogo con `Monto cada cierto tiempo` y `Semanal`
- **CUANDO** la persona elige `Lunes` y escribe `20`
- **ENTONCES** se envía `{ tipo: 'MONTO_MENSUAL', monto: 20000, frecuencia: 'SEMANAL',
  diaSemana: 1 }`

#### Scenario: Personalizada
- **DADO** el diálogo con `Personalizada`
- **CUANDO** la persona escribe `1` en `Cada cuántos días`
- **ENTONCES** el campo dice `Entre 2 y 365 días` y el botón está deshabilitado
- **Y** con `14` y la fecha de inicio 2 de octubre de 2026 se envía `intervaloDias: 14,
  fechaInicio: '2026-10-02'`

#### Scenario: Para una fecha
- **DADO** el diálogo con `Monto para una fecha`
- **CUANDO** la persona escribe `600` y elige el 15 de diciembre de 2026
- **ENTONCES** se envía `{ tipo: 'MONTO_PARA_FECHA', monto: 600000, fechaObjetivo: '2026-12-15' }`
  sin `frecuencia`

#### Scenario: Cambiar de tipo descarta los campos
- **DADO** el diálogo con `Semanal` y `Lunes` elegidos
- **CUANDO** la persona cambia a `Saldo objetivo` y guarda
- **ENTONCES** se envía solo `{ tipo: 'SALDO_OBJETIVO', monto }`

#### Scenario: Editar
- **DADO** una categoría con meta `MONTO_PARA_FECHA` de `600000` para `2026-12-15`
- **CUANDO** la persona pulsa `Editar meta`
- **ENTONCES** se pide `GET .../categorias/{categoriaId}/meta` y el diálogo muestra el tipo, `600`
  y la fecha

#### Scenario: Monto inválido
- **DADO** el diálogo
- **CUANDO** la persona escribe `0`
- **ENTONCES** se muestra `El monto debe ser mayor que 0` y el botón está deshabilitado

### Requirement: Quitar la meta
`Editar meta` SHALL ofrecer `Quitar meta`, que pide confirmación (`Se quitará la meta de
{categoría} y su estado en todos los meses. El dinero asignado no cambia.`) y, si se confirma,
envía `DELETE .../categorias/{categoriaId}/meta` y vuelve a pedir el mes y sus metas. Cancelar
SHALL NOT enviar nada.

#### Scenario: Confirmar
- **DADO** la categoría `Comida` con meta
- **CUANDO** la persona pulsa `Quitar meta` y confirma
- **ENTONCES** se envía `DELETE` y la fila deja de mostrar el indicador tras recargar

#### Scenario: Cancelar
- **DADO** la confirmación abierta
- **CUANDO** la persona cancela
- **ENTONCES** no se envía ninguna petición

### Requirement: Errores del diálogo de meta
El diálogo SHALL decidir por `codigo`: `DATOS_INVALIDOS` con `errores` en sus campos;
`REGLA_NEGOCIO_VIOLADA` y `CONFLICTO` como mensaje en el diálogo, sin cerrarlo;
`RECURSO_NO_ENCONTRADO` con un aviso, cerrando el diálogo y volviendo a pedir el mes y sus metas
(también si ocurre al cargar la meta para editarla o al quitarla); cualquier otro error con el
aviso genérico, sin cerrarse.

#### Scenario: Datos inválidos
- **DADO** el diálogo
- **CUANDO** la API responde `400 DATOS_INVALIDOS` con `errores: { fechaInicio: '...' }`
- **ENTONCES** ese mensaje se muestra en `Fecha de inicio`

#### Scenario: Conflicto
- **DADO** el diálogo
- **CUANDO** la API responde `409 CONFLICTO`
- **ENTONCES** el diálogo muestra `La meta cambió al mismo tiempo; vuelve a guardar` y sigue
  abierto

#### Scenario: La categoría ya no existe
- **DADO** el diálogo de una categoría que otra sesión borró
- **CUANDO** la API responde `404`
- **ENTONCES** se muestra un aviso, el diálogo se cierra y se vuelven a pedir el mes y sus metas

### Requirement: Posponer y reanudar en el mes
El menú de una categoría con meta SHALL ofrecer `Posponer este mes` si su estado no es
`POSPUESTA` y `Reanudar este mes` si lo es; con estado `SOBREGASTADA` (que oculta si está
pospuesta) SHALL ofrecer las dos. Cada una SHALL enviar
`POST .../meses/{mes}/metas/{categoriaId}/posponer` o `/reanudar` y, al terminar, volver a pedir
el mes y sus metas. Un `404` SHALL avisar y recargar; otro error, el aviso genérico.

#### Scenario: Posponer
- **DADO** la meta de `Comida` en `FALTA` en `2026-10`
- **CUANDO** la persona pulsa `Posponer este mes`
- **ENTONCES** se envía `POST .../meses/2026-10/metas/{id de Comida}/posponer` y, tras recargar,
  la fila dice `Pospuesta este mes` con necesidad 0

#### Scenario: Reanudar
- **DADO** la meta `POSPUESTA` en `2026-10`
- **CUANDO** la persona pulsa `Reanudar este mes`
- **ENTONCES** se envía `/reanudar` y la fila vuelve a mostrar su faltante tras recargar

### Requirement: Auto-asignar con vista previa
La pantalla del mes SHALL ofrecer `Auto-asignar`, que abre un diálogo con la `Estrategia`
(`Lo que falta para las metas`, `Lo asignado el mes pasado`, `Lo gastado el mes pasado`,
`Promedio asignado (3 meses)`, `Promedio gastado (3 meses)`) y el alcance: `Todas las categorías
visibles` (sin `categoriaIds`; la ayuda dice que no incluye las de pago de tarjeta) o `Elegir
categorías` (lista de las categorías del mes cargado con casillas, incluidas las de pago de
tarjeta marcadas como tales). Con `Elegir categorías` y ninguna marcada, `Vista previa` SHALL
estar deshabilitado con el texto `Elige al menos una categoría`. `Vista previa` SHALL enviar
`POST .../meses/{mes}/auto-asignar` con `simular: true` y mostrar cada cambio (categoría,
asignado antes y después), el `Listo para asignar` antes y después (negativo en rojo con texto)
y, sin cambios, `Ninguna categoría cambia con esta estrategia`. Con `Lo que falta para las metas`
y categorías elegidas sin meta, SHALL avisar `Las categorías sin meta no cambian`. `Aplicar`
SHALL enviar el mismo cuerpo con `simular: false`, cerrar el diálogo, avisar cuántas categorías
cambiaron según la respuesta real y volver a pedir el mes y sus metas. Cambiar la estrategia o el
alcance SHALL descartar la vista previa. Un `400` SHALL mostrarse en el diálogo, un `404` SHALL
avisar, cerrar y recargar, y otro error, el aviso genérico.

#### Scenario: Simular y aplicar
- **DADO** `Comida` con meta de `100000` y asignado `30000`
- **CUANDO** la persona elige `Lo que falta para las metas` y pulsa `Vista previa`
- **ENTONCES** se envía `{ estrategia: 'FALTANTE_META', simular: true }` y se ve `Comida` de 30 a
  100
- **Y** al pulsar `Aplicar` se envía `{ estrategia: 'FALTANTE_META', simular: false }` y el mes
  se vuelve a pedir

#### Scenario: Sin categorías elegidas
- **DADO** el alcance `Elegir categorías` sin ninguna marcada
- **CUANDO** se observa el diálogo
- **ENTONCES** `Vista previa` está deshabilitado y se lee `Elige al menos una categoría`, sin
  enviar nada

#### Scenario: Lista vacía rechazada
- **DADO** una petición que llega con `categoriaIds` vacío
- **CUANDO** la API responde `400 DATOS_INVALIDOS`
- **ENTONCES** el diálogo muestra `Elige al menos una categoría` y sigue abierto

#### Scenario: Sin cambios
- **DADO** que todas las categorías ya tienen el asignado de la estrategia
- **CUANDO** la persona pide la vista previa
- **ENTONCES** se lee `Ninguna categoría cambia con esta estrategia` y `Aplicar` está
  deshabilitado

#### Scenario: Cambiar la estrategia descarta la vista previa
- **DADO** una vista previa mostrada
- **CUANDO** la persona cambia la estrategia
- **ENTONCES** la vista previa desaparece y `Aplicar` está deshabilitado hasta pedir otra

### Requirement: Accesibilidad de las metas
Los diálogos de meta y de auto-asignar SHALL llevar el foco a su primer campo al abrirse y
devolverlo al botón que los abrió al cerrarse; cada campo SHALL tener etiqueta visible; el estado
de la meta y la diferencia de `Listo para asignar` SHALL leerse como texto, no solo por color.

#### Scenario: Foco al cerrar
- **DADO** el diálogo de meta abierto desde el menú de una fila
- **CUANDO** la persona lo cancela
- **ENTONCES** el foco vuelve al botón del menú de esa fila
