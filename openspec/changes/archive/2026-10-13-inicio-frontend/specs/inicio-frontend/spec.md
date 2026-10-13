# Spec Delta

## Purpose

Resumir en `Inicio` el estado del presupuesto activo en el mes actual con las cifras que ya calcula
el backend, y llevar a la pantalla donde se actúa sobre cada una.

## ADDED Requirements

### Requirement: Saludo y mes actual
`Inicio` SHALL conservar el saludo `Hola, <nombre>` del usuario autenticado, que se consulta cada
vez que se abre la pantalla, y mostrar el mes actual con `textoMes()`. El mes actual SHALL salir de
la fecha **local** del navegador con `mesActual()`, nunca de `toISOString()` ni de `getUTC*()`.

#### Scenario: Saludo con el mes
- **DADO** el usuario `María José` y la fecha local `2026-10-13`
- **CUANDO** abre `Inicio`
- **ENTONCES** ve `Hola, María José` y `octubre de 2026` con el formato de su región

#### Scenario: Mes con la fecha local, no la UTC
- **DADO** una fecha local `2026-10-31 21:00` (ya es `2026-11-01` en UTC)
- **CUANDO** abre `Inicio`
- **ENTONCES** el mes es octubre de 2026 y las peticiones piden `2026-10`

### Requirement: Tarjetas independientes y en paralelo
`Inicio` SHALL mostrar seis tarjetas: `Listo para asignar`, `Saldo total`, `Categorías
sobregastadas`, `Transacciones sin aprobar`, `Metas que faltan` e `Ingresos y gastos`. Las
peticiones SHALL salir en paralelo (7 `GET` al abrir: el perfil, el mes, las cuentas, los saldos,
las metas del mes, las transacciones sin aprobar y los ingresos y gastos), nunca encadenadas. Cada
tarjeta SHALL tener su propio estado de carga, de vacío y de error con un botón `Reintentar` que
repite solo sus peticiones, y el fallo de una SHALL NOT impedir que se muestren las demás.

#### Scenario: Siete peticiones en paralelo
- **DADO** el presupuesto `3` y el mes `2026-10`
- **CUANDO** se abre `Inicio`
- **ENTONCES** se envían a la vez `GET /usuarios/yo`, `GET /presupuestos/3/meses/2026-10?incluirOcultas=true`, `GET /presupuestos/3/cuentas`, `GET /presupuestos/3/transacciones/saldos`, `GET /presupuestos/3/meses/2026-10/metas`, `GET /presupuestos/3/transacciones?soloSinAprobar=true&page=0&size=1` y `GET /presupuestos/3/reportes/ingresos-gastos?desde=2026-10&hasta=2026-10`
- **Y** ninguna espera a la respuesta de otra

#### Scenario: Una tarjeta falla
- **DADO** que `GET .../metas` responde 500
- **CUANDO** se abre `Inicio`
- **ENTONCES** la tarjeta `Metas que faltan` muestra un error con `Reintentar`
- **Y** las otras cinco tarjetas muestran sus cifras

#### Scenario: Reintentar una tarjeta
- **DADO** la tarjeta `Metas que faltan` con error
- **CUANDO** la persona pulsa `Reintentar`
- **ENTONCES** solo se repite `GET .../metas` y las otras tarjetas no se vuelven a pedir

#### Scenario: Estado de carga por tarjeta
- **DADO** que la respuesta de los saldos aún no llega
- **CUANDO** el resto ya respondió
- **ENTONCES** `Saldo total` muestra su indicador de carga y las demás sus cifras

### Requirement: Respuestas atrasadas se descartan
Cada tarjeta SHALL descartar la respuesta de una petición reemplazada, de modo que nunca muestre
cifras de otro presupuesto, y SHALL cancelar sus peticiones al salir de la pantalla.

#### Scenario: Cambio de presupuesto con una respuesta en camino
- **DADO** `Inicio` del presupuesto `3` con en camino todas las peticiones que dependen del
  presupuesto (el mes, las cuentas, los saldos, las metas, las sin aprobar y los ingresos y
  gastos; el perfil no depende de él)
- **CUANDO** cambia el presupuesto activo al `4` y luego llegan las respuestas del `3`
- **ENTONCES** cada una de las seis tarjetas muestra solo cifras del presupuesto `4`
- **Y** ninguna tarjeta muestra cifras del presupuesto `3` en ningún momento

#### Scenario: Reintentar con una respuesta anterior en camino
- **DADO** una tarjeta que se reintenta mientras la petición anterior sigue en camino
- **CUANDO** llegan las dos respuestas
- **ENTONCES** la tarjeta muestra la de la última petición

#### Scenario: Salir de la pantalla cancela las peticiones
- **DADO** `Inicio` con peticiones aún pendientes
- **CUANDO** la persona sale de `Inicio`
- **ENTONCES** las peticiones pendientes se cancelan
- **Y** una respuesta que llegue después no actualiza nada

### Requirement: Listo para asignar
La tarjeta SHALL mostrar `listoParaAsignar` de `GET .../meses/{mes}` con el pipe `monto` y la moneda
del presupuesto, y un enlace al presupuesto del mes (`/presupuestos/:id/presupuesto/:mes`). Si es
negativo SHALL mostrar el aviso `Asignaste más de lo que tienes` con un ícono además del color; si
es cero, `Todo el dinero está asignado`.

#### Scenario: Positivo
- **DADO** `listoParaAsignar` `13403000` en un presupuesto en `BOB`
- **CUANDO** se muestra la tarjeta
- **ENTONCES** se lee el monto con el formato de la región y sin aviso

#### Scenario: Negativo
- **DADO** `listoParaAsignar` `-50000`
- **CUANDO** se muestra la tarjeta
- **ENTONCES** se lee el monto negativo y el aviso `Asignaste más de lo que tienes` con su ícono

#### Scenario: Enlace al mes
- **DADO** el presupuesto `3` en octubre de 2026
- **CUANDO** la persona pulsa el enlace de la tarjeta
- **ENTONCES** llega a `/presupuestos/3/presupuesto/2026-10`

### Requirement: Saldo total
La tarjeta SHALL mostrar la suma entera, en milésimas, del `saldo` de las cuentas **abiertas** que
están **en el presupuesto** (`cerrada` falso y `enPresupuesto` verdadero), con las tarjetas de
crédito con su signo, tomando los saldos de `GET .../transacciones/saldos`. SHALL ser la misma
regla y el mismo resultado que `Total en el presupuesto` de `Cuentas`, y SHALL enlazar a `Cuentas`.
El backend no expone ese total: es la única suma que hace el cliente.

#### Scenario: Misma regla que Cuentas
- **DADO** una cuenta corriente con saldo `1200000`, una tarjeta con `-150000`, una cuenta de
  seguimiento con `600000` y una cuenta cerrada con `300000`
- **CUANDO** se muestran `Inicio` y `Cuentas` con esos datos
- **ENTONCES** ambos muestran `1050000`

#### Scenario: Enlace a Cuentas
- **DADO** la tarjeta con su saldo
- **CUANDO** la persona pulsa su enlace
- **ENTONCES** llega a `/presupuestos/:id/cuentas`

#### Scenario: Se piden cuentas y saldos juntos
- **DADO** el presupuesto abierto
- **CUANDO** una de las dos peticiones (cuentas o saldos) falla
- **ENTONCES** la tarjeta muestra error, porque sin las dos no hay total

### Requirement: Categorías sobregastadas
La tarjeta SHALL contar las categorías con `sobregastada` verdadero en `GET .../meses/{mes}` pedido
con `incluirOcultas=true` (también las ocultas, igual que `listoParaAsignar`), sin recalcular
ningún disponible, y enlazar al presupuesto del mes. Con cero SHALL decir `Ninguna categoría
sobregastada`; con más de cero, `N categorías sobregastadas` con ícono de advertencia además del
color.

#### Scenario: Con sobregastos
- **DADO** un mes con 2 categorías con `sobregastada` verdadero, una de ellas oculta
- **CUANDO** se muestra la tarjeta
- **ENTONCES** se lee `2 categorías sobregastadas` con el ícono de advertencia

#### Scenario: Sin sobregastos
- **DADO** un mes sin categorías sobregastadas
- **CUANDO** se muestra la tarjeta
- **ENTONCES** se lee `Ninguna categoría sobregastada`

#### Scenario: Una sola
- **DADO** una categoría sobregastada
- **CUANDO** se muestra la tarjeta
- **ENTONCES** se lee `1 categoría sobregastada` (singular)

### Requirement: Transacciones sin aprobar
La tarjeta SHALL mostrar el `totalElementos` de `GET .../transacciones?soloSinAprobar=true&page=0&
size=1` y enlazar a `/presupuestos/:id/transacciones?sinAprobar=1`. Con cero SHALL decir `Todo
aprobado`.

#### Scenario: Con pendientes
- **DADO** `totalElementos` `7`
- **CUANDO** se muestra la tarjeta
- **ENTONCES** se lee `7 transacciones sin aprobar`
- **Y** su enlace lleva a `/presupuestos/3/transacciones?sinAprobar=1`

#### Scenario: Sin pendientes
- **DADO** `totalElementos` `0`
- **CUANDO** se muestra la tarjeta
- **ENTONCES** se lee `Todo aprobado`

#### Scenario: Una sola petición ligera
- **DADO** 5000 transacciones sin aprobar
- **CUANDO** se pide la tarjeta
- **ENTONCES** la petición lleva `size=1` y solo se usa `totalElementos`

### Requirement: Metas que faltan
La tarjeta SHALL contar las metas de `GET .../meses/{mes}/metas` con `estado` `FALTA` y enlazar al
presupuesto del mes, donde se gestionan las metas. NO SHALL mostrar un monto: `totalFaltante` suma
también las metas `SOBREGASTADA`, así que no corresponde a esa cuenta. Con cero SHALL decir `Ninguna
meta pendiente`; sin metas, `Aún no tienes metas`.

#### Scenario: Con metas pendientes
- **DADO** 3 metas `FALTA`, 1 `SOBREGASTADA`, 1 `FINANCIADA` y 1 `POSPUESTA`
- **CUANDO** se muestra la tarjeta
- **ENTONCES** se lee `3 metas por cumplir este mes`

#### Scenario: Ninguna pendiente
- **DADO** metas solo en estado `FINANCIADA` o `POSPUESTA`
- **CUANDO** se muestra la tarjeta
- **ENTONCES** se lee `Ninguna meta pendiente`

#### Scenario: Sin metas
- **DADO** una lista de metas vacía
- **CUANDO** se muestra la tarjeta
- **ENTONCES** se lee `Aún no tienes metas`

#### Scenario: Nunca se muestra el monto
- **DADO** `totalFaltante` `500000`
- **CUANDO** se muestra la tarjeta
- **ENTONCES** ese monto no aparece en la pantalla

### Requirement: Ingresos y gastos del mes
La tarjeta SHALL mostrar `ingresos`, `gastos` y `neto` del mes tal como los devuelve `GET .../
reportes/ingresos-gastos?desde={mes}&hasta={mes}`, con el formato de monto, sin recalcularlos, y
enlazar a `Reportes` en esa pestaña y ese mes (`/presupuestos/:id/reportes?reporte=ingresos-gastos&
desde={mes}&hasta={mes}`). Un `neto` negativo SHALL llevar el texto `Gastaste más de lo que
ingresó` además del color.

#### Scenario: Cifras del backend
- **DADO** `ingresos` `500000`, `gastos` `265000` y `neto` `235000`
- **CUANDO** se muestra la tarjeta
- **ENTONCES** se leen los tres montos y ninguno se recalcula en el cliente

#### Scenario: Neto negativo
- **DADO** `neto` `-40000`
- **CUANDO** se muestra la tarjeta
- **ENTONCES** se lee el aviso con su ícono

#### Scenario: Enlace a Reportes
- **DADO** el presupuesto `3` en octubre de 2026
- **CUANDO** la persona pulsa el enlace
- **ENTONCES** llega a `/presupuestos/3/reportes?reporte=ingresos-gastos&desde=2026-10&hasta=2026-10`

### Requirement: Estados vacíos con la acción siguiente
Si el presupuesto no tiene cuentas (`GET .../cuentas` vacío), `Inicio` SHALL mostrar un aviso
`Aún no tienes cuentas` con un enlace `Agregar cuenta` a `Cuentas`, y `Saldo total` SHALL mostrar
su estado vacío. Si `ingresos`, `gastos` y `neto` del mes son cero, `Ingresos y gastos` SHALL
decir `Sin movimientos este mes` con un enlace `Agregar transacción` a `Transacciones`. Las demás
tarjetas SHALL seguir mostrando lo que tengan.

#### Scenario: Presupuesto sin cuentas
- **DADO** un presupuesto recién creado sin cuentas
- **CUANDO** se abre `Inicio`
- **ENTONCES** ve `Aún no tienes cuentas` y el enlace `Agregar cuenta` a `/presupuestos/3/cuentas`

#### Scenario: Mes sin movimientos
- **DADO** un mes con `ingresos`, `gastos` y `neto` en cero
- **CUANDO** se muestra `Ingresos y gastos`
- **ENTONCES** ve `Sin movimientos este mes` y el enlace `Agregar transacción`

#### Scenario: Las demás tarjetas siguen
- **DADO** un presupuesto sin cuentas
- **CUANDO** se abre `Inicio`
- **ENTONCES** `Listo para asignar` y `Categorías sobregastadas` muestran sus cifras

### Requirement: Formato, accesibilidad y pantallas estrechas
Todo monto SHALL formatearse con el pipe `monto` y la moneda del presupuesto, y todo estado SHALL
decirse con texto además del color y del ícono (`mat-icon` con `aria-hidden`). Cada tarjeta SHALL
ser una región con nombre (`aria-labelledby`), sus cambios de estado SHALL anunciarse con
`aria-live` y sus enlaces SHALL tener un nombre visible. En pantallas estrechas las tarjetas SHALL
apilarse en una columna sin desbordar la página.

#### Scenario: Región con nombre
- **DADO** cualquier tarjeta
- **CUANDO** se lee con un lector de pantalla
- **ENTONCES** es una región con el título de la tarjeta como nombre

#### Scenario: 390 px
- **DADO** una pantalla de 390 px de ancho
- **CUANDO** se abre `Inicio`
- **ENTONCES** las tarjetas se apilan y la página no tiene desplazamiento horizontal

#### Scenario: Aviso de error accesible
- **DADO** una tarjeta que falla
- **CUANDO** aparece su error
- **ENTONCES** se anuncia con `role="alert"` y su botón `Reintentar` es alcanzable con el teclado
