## MODIFIED Requirements

### Requirement: Redirección al primer presupuesto
El sistema SHALL, al entrar a `/` con sesión, pedir la lista de presupuestos de la persona
(`GET /api/v1/presupuestos`, ordenada por nombre por el backend) y SHALL llevarla a
`/presupuestos/{id}` del primero de la lista, reemplazando `/` en el historial del navegador; desde
ahí SHALL abrirse la sección por defecto del presupuesto. Mientras la lista se carga SHALL mostrar
un indicador de carga.

#### Scenario: Un solo presupuesto
- **DADO** una sesión vigente, la API respondiendo un único presupuesto con id `7` y hoy en
  octubre de 2026
- **CUANDO** la persona entra a `/`
- **ENTONCES** llega a `/presupuestos/7/presupuesto/2026-10`

#### Scenario: Varios presupuestos
- **DADO** una sesión vigente y la API respondiendo `casa` (id `3`) y `Viajes` (id `1`), en ese
  orden
- **CUANDO** la persona entra a `/`
- **ENTONCES** llega al presupuesto `3`

#### Scenario: Sin volver a la redirección con el botón Atrás
- **DADO** que la persona entró a `/` y fue llevada al presupuesto `3`
- **CUANDO** se observa el historial del navegador
- **ENTONCES** `/` no quedó como una entrada propia

#### Scenario: Mientras carga
- **DADO** una sesión vigente y la lista de presupuestos todavía pendiente
- **CUANDO** se observa `/`
- **ENTONCES** se ve un indicador de carga

### Requirement: Estructura de la pantalla del presupuesto
El sistema SHALL mostrar en `/presupuestos/:presupuestoId` la cabecera `Cada Peso` con el
selector, las acciones de crear y renombrar y el botón `Cerrar sesión`; debajo, un menú lateral
con los enlaces de las secciones del presupuesto (`Presupuesto`, `Inicio`, `Cuentas` y
`Categorías`, en ese orden) y, junto a él, el contenido de la sección elegida. La sección por
defecto SHALL ser `Presupuesto`: `/presupuestos/:presupuestoId` SHALL llevar a
`/presupuestos/:presupuestoId/presupuesto`, que a su vez lleva al mes actual. En pantallas anchas
el menú SHALL estar siempre visible; en pantallas estrechas SHALL ocultarse y abrirse sobre el
contenido con un botón de menú en la cabecera, cerrándose al elegir un enlace. `Cerrar sesión`
SHALL borrar la sesión y llevar a `/login`.

#### Scenario: Sección por defecto
- **DADO** una persona con el presupuesto `3` y hoy en octubre de 2026
- **CUANDO** entra a `/presupuestos/3`
- **ENTONCES** llega a `/presupuestos/3/presupuesto/2026-10`

#### Scenario: Pantalla ancha
- **DADO** una pantalla de escritorio en el presupuesto `3`
- **CUANDO** se observa la pantalla
- **ENTONCES** el menú lateral con los enlaces `Presupuesto`, `Inicio`, `Cuentas` y `Categorías`
  está visible
- **Y** no hay botón de menú en la cabecera

#### Scenario: Pantalla estrecha
- **DADO** una pantalla de teléfono en el presupuesto `3`
- **CUANDO** la persona pulsa el botón de menú de la cabecera
- **ENTONCES** el menú lateral se abre sobre el contenido
- **Y** al pulsar `Inicio` el menú se cierra

#### Scenario: Cerrar sesión desde la cabecera
- **DADO** una persona en el presupuesto `3`
- **CUANDO** pulsa `Cerrar sesión`
- **ENTONCES** la sesión se borra del almacenamiento local
- **Y** no hay presupuesto activo
- **Y** la persona llega a `/login`
