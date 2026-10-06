# presupuesto-mensual-frontend Specification

## Purpose

Pantalla principal del presupuesto base cero: por mes, permite repartir el dinero entre las
categorías, ver su actividad y su disponible, saber cuánto queda "Listo para asignar" y mover
dinero entre categorías.

## Requirements

### Requirement: Mes en la URL
La pantalla SHALL vivir en `/presupuestos/:presupuestoId/presupuesto/:mes`, con `mes` en formato
`yyyy-MM` entre `2000-01` y `2100-12`, y el menú lateral SHALL ofrecerla con el enlace
`Presupuesto`. Sin mes, o con un mes inválido o fuera de rango, SHALL llevar al mes actual
reemplazando la URL. El mes actual SHALL calcularse con la fecha local del navegador (año y mes
locales), nunca a partir de la fecha en UTC.

#### Scenario: Sin mes
- **DADO** hoy el 6 de octubre de 2026
- **CUANDO** la persona entra a `/presupuestos/3/presupuesto`
- **ENTONCES** llega a `/presupuestos/3/presupuesto/2026-10`

#### Scenario: Mes inválido
- **DADO** hoy el 6 de octubre de 2026
- **CUANDO** la persona entra a `/presupuestos/3/presupuesto/2026-13` o `.../1999-12`
- **ENTONCES** llega a `/presupuestos/3/presupuesto/2026-10`

#### Scenario: Noche del último día del mes
- **DADO** la zona horaria `America/New_York` y la hora local 31 de octubre de 2026 a las 21:00
  (ya 1 de noviembre en UTC)
- **CUANDO** se calcula el mes actual
- **ENTONCES** es `2026-10`

#### Scenario: Mañana del primer día del mes
- **DADO** la zona horaria `Asia/Tokyo` y la hora local 1 de noviembre de 2026 a las 08:00 (aún
  31 de octubre en UTC)
- **CUANDO** se calcula el mes actual
- **ENTONCES** es `2026-11`

### Requirement: Barra de mes
La pantalla SHALL mostrar el mes en texto largo según la región del usuario (por ejemplo
`octubre de 2026` en `es-BO`), un botón `Mes anterior`, un botón `Mes siguiente` y un botón `Hoy`.
Cada botón SHALL navegar a la URL del mes correspondiente, que vuelve a pedir los datos. `Mes
anterior` SHALL estar deshabilitado en `2000-01` y `Mes siguiente` en `2100-12`. Si se cambia de
mes antes de que llegue la respuesta anterior, la respuesta atrasada SHALL ignorarse.

#### Scenario: Texto del mes
- **DADO** la región `es-BO` y el mes `2026-10`
- **CUANDO** se observa la barra
- **ENTONCES** se lee `octubre de 2026`

#### Scenario: Siguiente y anterior
- **DADO** la pantalla en `2026-12`
- **CUANDO** la persona pulsa `Mes siguiente`
- **ENTONCES** llega a `.../presupuesto/2027-01` y se pide `GET .../meses/2027-01`

#### Scenario: Hoy
- **DADO** hoy en octubre de 2026 y la pantalla en `2025-03`
- **CUANDO** la persona pulsa `Hoy`
- **ENTONCES** llega a `.../presupuesto/2026-10`

#### Scenario: Límites
- **DADO** la pantalla en `2000-01`
- **CUANDO** se observa la barra
- **ENTONCES** `Mes anterior` está deshabilitado

#### Scenario: Respuesta atrasada
- **DADO** la pantalla pidiendo `2026-10`
- **CUANDO** la persona pasa a `2026-11` y la respuesta de `2026-10` llega después
- **ENTONCES** la pantalla muestra solo los datos de `2026-11`

### Requirement: Listo para asignar
La pantalla SHALL mostrar el `listoParaAsignar` del mes en una tarjeta destacada con un ícono y un
texto que no dependen solo del color: positivo, con el monto y `Listo para asignar`; cero, con
`Todo asignado`; negativo, en color de error, con el monto y `Asignaste de más`.

#### Scenario: Positivo
- **DADO** un `listoParaAsignar` de `500000` en `BOB`
- **CUANDO** se observa la tarjeta
- **ENTONCES** muestra el monto de 500 y `Listo para asignar`

#### Scenario: Cero
- **DADO** un `listoParaAsignar` de `0`
- **CUANDO** se observa la tarjeta
- **ENTONCES** muestra `Todo asignado`

#### Scenario: Negativo
- **DADO** un `listoParaAsignar` de `-20000`
- **CUANDO** se observa la tarjeta
- **ENTONCES** muestra el monto de -20 y `Asignaste de más`, en color de error

### Requirement: Grupos, categorías y totales
La pantalla SHALL mostrar los grupos en el orden de la API con un encabezado de columnas
`Categoría`, `Asignado`, `Actividad` y `Disponible`. Cada grupo SHALL tener un encabezado
plegable (botón con `aria-expanded`) con su nombre y la suma de asignado, actividad y disponible
de sus categorías, y debajo una fila por categoría. Al final SHALL mostrarse la fila del total del
mes. Todos los montos SHALL formatearse con la moneda del presupuesto activo. En pantallas
estrechas cada fila SHALL apilar el nombre arriba y los tres montos abajo, cada uno con su
etiqueta.

#### Scenario: Totales del grupo
- **DADO** el grupo `Facturas` con `Luz` (asignado `100000`) y `Agua` (asignado `50000`)
- **CUANDO** se muestra el mes
- **ENTONCES** el encabezado de `Facturas` muestra 150 como asignado

#### Scenario: Plegar un grupo
- **DADO** el grupo `Facturas` desplegado
- **CUANDO** la persona pulsa su encabezado
- **ENTONCES** sus categorías dejan de verse y el encabezado tiene `aria-expanded="false"`

### Requirement: Disponible y sobregasto
El disponible de cada categoría SHALL verse en color positivo si es mayor que 0, neutro si es 0 y,
si la categoría está sobregastada, en color de error con un ícono de advertencia y el texto
accesible `Sobregastado`.

#### Scenario: Sobregastada
- **DADO** la categoría `Comida` con `disponible` `-30000` y `sobregastada` verdadero
- **CUANDO** se muestra el mes
- **ENTONCES** su disponible se ve en color de error con un ícono de advertencia y el texto
  `Sobregastado`

#### Scenario: Positivo y cero
- **DADO** una categoría con `disponible` `10000` y otra con `0`
- **CUANDO** se muestra el mes
- **ENTONCES** la primera se ve en color positivo y la segunda en color neutro

### Requirement: Mostrar ocultas
La pantalla SHALL pedir el mes sin ocultas por defecto y SHALL ofrecer un interruptor
`Mostrar ocultas` que lo vuelve a pedir con `incluirOcultas=true`; los grupos y las categorías
ocultos SHALL verse atenuados.

#### Scenario: Activar
- **DADO** la pantalla del mes
- **CUANDO** la persona activa `Mostrar ocultas`
- **ENTONCES** el mes se pide con `incluirOcultas=true` y las ocultas se ven atenuadas

### Requirement: Editar el asignado
La celda de asignado de cada categoría SHALL mostrarse como un botón; al pulsarlo SHALL
convertirse en un campo con el valor actual seleccionado, escrito con el separador decimal de la
región. `Enter` o salir del campo SHALL guardar, `Escape` SHALL cancelar sin llamar al backend y
`Tab` SHALL guardar y llevar el foco a la celda de la categoría siguiente. El texto SHALL
convertirse a milésimas sin coma flotante; un campo vacío vale `0`. Un texto inválido SHALL
marcar la celda con error sin llamar al backend. Un valor igual al actual SHALL NOT llamar al
backend. Al guardar, el valor nuevo SHALL verse de inmediato, SHALL enviarse
`PUT .../meses/{mes}/categorias/{categoriaId}` con `{ asignado }` y la respuesta SHALL
reemplazar la fila de la categoría y el `listoParaAsignar`; los totales SHALL recalcularse. Si
falla, la celda SHALL volver al valor anterior y SHALL avisarse (en la celda si es `400` con
`errores.asignado`; si no, con el aviso genérico). Mientras una celda guarda, otro guardado de
esa misma celda SHALL ignorarse. Al terminar, el foco SHALL volver a la celda.

#### Scenario: Guardar con Enter
- **DADO** la categoría `Luz` (id `7`) con asignado `0` y la región `es-BO`
- **CUANDO** la persona pulsa la celda, escribe `150,5` y pulsa `Enter`
- **ENTONCES** la celda muestra 150,50 antes de la respuesta
- **Y** se envía `{ asignado: 150500 }`
- **Y** la fila y el `listoParaAsignar` toman los valores de la respuesta

#### Scenario: Cancelar con Escape
- **DADO** la celda de `Luz` en edición con un valor nuevo
- **CUANDO** la persona pulsa `Escape`
- **ENTONCES** la celda vuelve a su valor y no se llama al backend

#### Scenario: Texto inválido
- **DADO** la celda de `Luz` en edición
- **CUANDO** la persona escribe `abc` y pulsa `Enter`
- **ENTONCES** la celda queda en edición marcada con error y no se llama al backend

#### Scenario: Mismo valor
- **DADO** la celda de `Luz` con asignado `100000`
- **CUANDO** la persona la abre y pulsa `Enter` sin cambiarla
- **ENTONCES** no se llama al backend

#### Scenario: Tab a la siguiente
- **DADO** las categorías `Luz` y `Agua`, en ese orden
- **CUANDO** la persona edita `Luz` y pulsa `Tab`
- **ENTONCES** se guarda `Luz` y el foco pasa a la celda de `Agua`

#### Scenario: Error del servidor
- **DADO** la celda de `Luz` con asignado `100000`
- **CUANDO** la persona guarda `200` y la API responde `500`
- **ENTONCES** la celda vuelve a 100 y se muestra el aviso genérico

### Requirement: Mover dinero
El menú de cada categoría SHALL ofrecer `Mover dinero...`, que abre un diálogo con el origen
preseleccionado; en una categoría sobregastada SHALL ofrecer además `Cubrir sobregasto`, que
preselecciona esa categoría como destino y propone como monto su sobregasto. El diálogo SHALL
tener origen y destino (solo categorías visibles, agrupadas por grupo) y monto, y SHALL mostrar
el disponible del origen. SHALL exigir un monto mayor que 0 escrito con el separador decimal de
la región, un origen distinto del destino y un monto no mayor al disponible del origen, con el
botón deshabilitado mientras el formulario sea inválido o se esté enviando. Al confirmar SHALL
enviar `POST .../meses/{mes}/mover-dinero` con `{ origenId, destinoId, monto }` y, al responder,
SHALL reemplazar el mes con el que devuelve el backend y cerrarse. Un `422` SHALL mostrarse en el
diálogo; un `400` con `errores`, en sus campos; un `404` SHALL avisar, cerrar el diálogo y
recargar el mes.

#### Scenario: Mover desde una categoría
- **DADO** `Comida` (id `5`) con disponible `70000` y `Ocio` (id `6`)
- **CUANDO** la persona elige `Mover dinero...` en `Comida`, elige `Ocio`, escribe `30` y
  confirma
- **ENTONCES** se envía `{ origenId: 5, destinoId: 6, monto: 30000 }`
- **Y** la pantalla muestra el mes que devuelve la API

#### Scenario: Cubrir sobregasto
- **DADO** `Ropa` (id `8`) sobregastada con disponible `-25000`
- **CUANDO** la persona elige `Cubrir sobregasto` en `Ropa`
- **ENTONCES** el diálogo abre con destino `Ropa` y monto 25

#### Scenario: Validaciones
- **DADO** el diálogo con origen `Comida` (disponible `70000`)
- **CUANDO** la persona elige `Comida` también como destino, o escribe `0` o `70,001`
- **ENTONCES** se muestra el error correspondiente y el botón está deshabilitado

#### Scenario: Disponible insuficiente en el servidor
- **DADO** el diálogo con datos válidos
- **CUANDO** la API responde `422` con `REGLA_NEGOCIO_VIOLADA`
- **ENTONCES** el diálogo muestra `El origen no tiene suficiente disponible` y sigue abierto

#### Scenario: Categoría inexistente
- **DADO** el diálogo con datos válidos
- **CUANDO** la API responde `404`
- **ENTONCES** se muestra un aviso, el diálogo se cierra y el mes se vuelve a pedir

### Requirement: Estados de carga, error y vacío
La pantalla SHALL mostrar un indicador de carga mientras espera un mes que todavía no tiene. Si la
carga falla por un error distinto de `401`, SHALL mostrar el aviso genérico con `Reintentar`. Si
el mes no tiene ninguna categoría, SHALL mostrar `Aún no tienes categorías` con un enlace a la
pantalla de categorías.

#### Scenario: Cargando
- **DADO** el mes todavía pendiente
- **CUANDO** se observa la pantalla
- **ENTONCES** se ve un indicador de carga

#### Scenario: Error y reintento
- **DADO** que `GET .../meses/2026-10` responde `500`
- **CUANDO** la persona pulsa `Reintentar`
- **ENTONCES** el mes se vuelve a pedir

#### Scenario: Sin categorías
- **DADO** un mes sin grupos ni categorías
- **CUANDO** se muestra la pantalla
- **ENTONCES** se ve `Aún no tienes categorías` con un enlace a `Categorías`
