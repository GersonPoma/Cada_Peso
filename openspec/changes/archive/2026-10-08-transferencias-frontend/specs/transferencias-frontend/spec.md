## ADDED Requirements

### Requirement: Agregar una transferencia
La pantalla de transacciones SHALL ofrecer el botón `Agregar transferencia` junto a
`Agregar transacción`, que abre el diálogo de transferencia. Si hay un filtro de cuenta activo y
esa cuenta está abierta, SHALL preseleccionarla como cuenta origen.

#### Scenario: Con filtro de cuenta
- **DADO** la pantalla filtrada por la cuenta abierta `Banco`
- **CUANDO** la persona pulsa `Agregar transferencia`
- **ENTONCES** el diálogo se abre con `Banco` como cuenta origen

#### Scenario: Sin filtro
- **DADO** la pantalla sin filtro de cuenta
- **CUANDO** la persona pulsa `Agregar transferencia`
- **ENTONCES** el diálogo se abre con origen y destino vacíos

### Requirement: Diálogo de crear una transferencia
El diálogo SHALL tener `Cuenta origen` y `Cuenta destino` (selects agrupados en
`En el presupuesto` y `Seguimiento`, solo con cuentas abiertas; cada uno excluye la cuenta
elegida en el otro), un botón `Invertir` (con `aria-label`) que intercambia origen y destino, la
fecha (hoy en hora local por defecto, enviada como `yyyy-MM-dd` con `aFechaNegocio()`), el monto
con la calculadora (`app-campo-monto`, siempre positivo), la categoría según la regla de
categoría y el memo (máximo 500 tras recortar, con contador). Origen, destino, fecha y monto
SHALL ser obligatorios; el monto mayor que 0; origen distinto de destino. El botón SHALL estar
deshabilitado mientras el formulario sea inválido o se esté enviando. Crear SHALL enviar
`POST .../transferencias` con `{ cuentaOrigenId, cuentaDestinoId, fecha, monto, categoriaId,
memo }`, el memo recortado (vacío como `null`).

#### Scenario: Crear entre dos cuentas del presupuesto
- **DADO** hoy el 8 de octubre de 2026 y las cuentas `Banco` y `Ahorro`, ambas del presupuesto
- **CUANDO** la persona elige `Banco` → `Ahorro`, escribe `150` y confirma
- **ENTONCES** se envía `{ cuentaOrigenId: 1, cuentaDestinoId: 2, fecha: '2026-10-08',
  monto: 150000, memo: null }` sin `categoriaId`

#### Scenario: Exclusión mutua
- **DADO** `Banco` elegida como origen
- **CUANDO** la persona abre el select de destino
- **ENTONCES** `Banco` no aparece, ni ninguna cuenta cerrada

#### Scenario: Invertir
- **DADO** `Banco` como origen y `Visa` como destino
- **CUANDO** la persona pulsa `Invertir`
- **ENTONCES** el origen es `Visa` y el destino `Banco`

#### Scenario: Monto cero o negativo
- **DADO** el diálogo de crear
- **CUANDO** la persona escribe `0` o `-5` como monto
- **ENTONCES** el campo muestra `El monto debe ser mayor que 0` y el botón está deshabilitado

#### Scenario: Fecha local
- **DADO** el reloj del navegador en `America/New_York` el 8 de octubre de 2026 a las 23:00 (y,
  aparte, en `Asia/Tokyo` el 9 de octubre a las 01:00)
- **CUANDO** se crea la transferencia con la fecha por defecto
- **ENTONCES** se envía `2026-10-08` (y `2026-10-09` en Tokio), nunca la fecha UTC

### Requirement: Categoría según las cuentas
El campo de categoría SHALL depender de `enPresupuesto` de origen y destino:
- Ambas del presupuesto: SHALL ocultarse, SHALL NOT enviarse `categoriaId` y SHALL verse el texto
  `Mover dinero entre cuentas del presupuesto no afecta a ninguna categoría`.
- Ambas de seguimiento: SHALL ocultarse, SHALL NOT enviarse `categoriaId` y SHALL verse
  `Mover dinero entre cuentas de seguimiento no afecta al presupuesto`.
- Del presupuesto a una de seguimiento: SHALL ser obligatoria, con la ayuda
  `Este dinero sale del presupuesto`.
- De una de seguimiento al presupuesto: SHALL ser opcional, con la ayuda
  `Sin categoría cuenta como ingreso; con categoría, como reembolso de esa categoría`.

Mientras falte alguna de las dos cuentas, el campo SHALL ocultarse. Al cambiar las cuentas, si el
campo deja de mostrarse SHALL vaciarse. El select SHALL agruparse por grupo e incluir las
categorías ocultas solo si ya estaban elegidas.

#### Scenario: Ambas del presupuesto
- **DADO** `Banco` y `Ahorro`, del presupuesto
- **CUANDO** la persona las elige como origen y destino
- **ENTONCES** no hay campo de categoría, se ve el texto de cuentas del presupuesto y la petición
  no lleva `categoriaId`

#### Scenario: Ambas de seguimiento
- **DADO** `Hipoteca` e `Inversiones`, de seguimiento
- **CUANDO** la persona las elige
- **ENTONCES** no hay campo de categoría, se ve el texto de cuentas de seguimiento y la petición no
  lleva `categoriaId`

#### Scenario: Del presupuesto a seguimiento
- **DADO** `Banco` (presupuesto) como origen e `Inversiones` (seguimiento) como destino
- **CUANDO** la persona no elige categoría
- **ENTONCES** el campo muestra `Este dinero sale del presupuesto`, marca la categoría como
  obligatoria y el botón está deshabilitado
- **Y** al elegir `Ahorro largo plazo` se envía su `categoriaId`

#### Scenario: De seguimiento al presupuesto
- **DADO** `Inversiones` como origen y `Banco` como destino
- **CUANDO** la persona confirma sin categoría
- **ENTONCES** se envía `categoriaId: null` y se ve la ayuda de ingreso o reembolso

#### Scenario: Limpiar al cambiar las cuentas
- **DADO** `Banco` → `Inversiones` con la categoría `Ahorro largo plazo` elegida
- **CUANDO** la persona cambia el destino a `Ahorro` (del presupuesto)
- **ENTONCES** el campo desaparece y la categoría queda vacía
- **Y** al volver a `Inversiones` la categoría sigue vacía

### Requirement: Editar una transferencia
`Editar transferencia` SHALL abrir el diálogo con el id de la pata pulsada, pedir
`GET .../transferencias/{id}` y mostrar las cuentas de la salida (origen) y de la entrada
(destino) sin poder cambiarlas ni invertirlas, la fecha, el monto en valor absoluto, el memo y la
categoría de la pata que la lleve. Mientras carga SHALL mostrar un indicador. Guardar SHALL
enviar `PUT .../transferencias/{id}` con `{ fecha, monto, categoriaId, memo }`, aplicando la
misma regla de categoría.

#### Scenario: Cargar valores
- **DADO** una transferencia `Banco` → `Visa` (seguimiento) de `50000` con `categoriaId` `7` en
  la salida y el memo `Cuota`
- **CUANDO** la persona pulsa `Editar transferencia` en la pata de entrada (id `21`)
- **ENTONCES** se pide `GET .../transferencias/21`
- **Y** el diálogo muestra `Banco` y `Visa` bloqueadas, `50`, la categoría `7` y `Cuota`, sin
  `Invertir`

#### Scenario: Guardar la edición
- **DADO** el diálogo de editar la transferencia de la pata `21`
- **CUANDO** la persona cambia el monto a `60` y guarda
- **ENTONCES** se envía `PUT .../transferencias/21` con `monto: 60000` y sin cuentas

### Requirement: Borrar una transferencia
`Borrar transferencia` SHALL pedir confirmación con el mensaje `Se borrarán las dos transacciones
de esta transferencia (la salida y la entrada). No se puede deshacer.` y, si se confirma, SHALL
enviar `DELETE .../transferencias/{id}` con el id de la pata pulsada. Si se cancela, SHALL NOT
enviarse nada.

#### Scenario: Confirmar
- **DADO** una pata de transferencia con id `20`
- **CUANDO** la persona pulsa `Borrar transferencia` y confirma
- **ENTONCES** se envía `DELETE .../transferencias/20` y se recargan la página y los saldos

#### Scenario: Cancelar
- **DADO** la confirmación abierta
- **CUANDO** la persona cancela
- **ENTONCES** no se envía ninguna petición

### Requirement: Acciones bloqueadas de una transferencia
`Editar transferencia` y `Borrar transferencia` SHALL estar deshabilitados, con el motivo visible
(tooltip y texto en el menú), si la pata está `RECONCILIADA` o su pata par está en la página y es
`RECONCILIADA` (`Una de las transacciones está reconciliada`), o si la cuenta de la pata o la de
su pata par en la página está cerrada (`La cuenta está cerrada`). La reconciliada tiene
prioridad. `Aprobar` y el cambio de estado siguen las reglas de cada pata.

#### Scenario: Pata reconciliada
- **DADO** una pata `RECONCILIADA`
- **CUANDO** la persona abre su menú
- **ENTONCES** `Editar transferencia` y `Borrar transferencia` están deshabilitados con
  `Una de las transacciones está reconciliada`

#### Scenario: Pata par reconciliada en la página
- **DADO** una pata `NO_CONCILIADA` cuya pata par, en la misma página, está `RECONCILIADA`
- **CUANDO** la persona abre su menú
- **ENTONCES** ambas acciones están deshabilitadas con `Una de las transacciones está
  reconciliada`

#### Scenario: Cuenta cerrada
- **DADO** una pata de una cuenta cerrada
- **CUANDO** la persona abre su menú
- **ENTONCES** ambas acciones están deshabilitadas con `La cuenta está cerrada`

### Requirement: Descripción de una pata
La columna de beneficiario de una pata de transferencia SHALL decir `Transferencia a {cuenta}`
en la salida (monto negativo) y `Transferencia desde {cuenta}` en la entrada, con el nombre de la
cuenta de la pata par si esa pata está en la página cargada, o `Transferencia` si no está. SHALL
NOT hacerse ninguna petición para resolverlo. La insignia `Transferencia` se mantiene.

#### Scenario: Ambas patas en la página
- **DADO** la salida en `Banco` y la entrada en `Ahorro` en la misma página
- **CUANDO** se muestra la tabla
- **ENTONCES** la salida dice `Transferencia a Ahorro` y la entrada `Transferencia desde Banco`

#### Scenario: Pata par fuera de la página
- **DADO** la página filtrada por `Banco`, con solo la salida
- **CUANDO** se muestra la tabla
- **ENTONCES** la salida dice `Transferencia` y no se hace ninguna petición extra

### Requirement: Recarga y errores del diálogo de transferencia
Tras crear, editar o borrar una transferencia, la pantalla SHALL volver a pedir la página actual
y los saldos. El diálogo SHALL decidir por `codigo`: `DATOS_INVALIDOS` con `errores` en sus
campos (`aplicarErroresDeCampos`); `REGLA_NEGOCIO_VIOLADA` como mensaje en el diálogo, sin
cerrarlo; `RECURSO_NO_ENCONTRADO` con un aviso, cerrando el diálogo y volviendo a pedir las
cuentas, las categorías, la página y los saldos; cualquier otro error con el aviso genérico. Un
`404` al cargar la transferencia para editar SHALL cerrar el diálogo de la misma forma. Al
cerrarse el diálogo, el foco SHALL volver al botón de acciones de la fila o al botón que lo
abrió.

#### Scenario: Regla de negocio
- **DADO** el diálogo de crear con una cuenta que se cerró mientras tanto
- **CUANDO** la API responde `422`
- **ENTONCES** el diálogo muestra el mensaje y sigue abierto

#### Scenario: Datos inválidos
- **DADO** el diálogo de crear
- **CUANDO** la API responde `400` con `errores: { monto: '...' }`
- **ENTONCES** ese mensaje se muestra en el campo monto

#### Scenario: Ya no existe
- **DADO** el diálogo de editar una transferencia que otra sesión borró
- **CUANDO** la API responde `404`
- **ENTONCES** se muestra un aviso, el diálogo se cierra y se recargan listas, página y saldos

#### Scenario: Recarga tras crear
- **DADO** el diálogo de crear
- **CUANDO** la API responde `201`
- **ENTONCES** el diálogo se cierra y se vuelven a pedir la página y los saldos
