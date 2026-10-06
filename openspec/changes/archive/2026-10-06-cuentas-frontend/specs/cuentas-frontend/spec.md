## Purpose

Permite ver, crear, editar, cerrar y reabrir las cuentas del presupuesto activo con sus saldos, y
define cómo el frontend convierte un monto escrito por la persona a milésimas de forma exacta.

## ADDED Requirements

### Requirement: Sección Cuentas en el menú del presupuesto
El sistema SHALL ofrecer la pantalla de cuentas en `/presupuestos/:presupuestoId/cuentas`, con
sesión, y SHALL mostrar en el menú lateral del presupuesto un enlace `Cuentas` que lleva a ella,
además de `Inicio`.

#### Scenario: Enlace en el menú
- **DADO** una persona en `/presupuestos/3`
- **CUANDO** pulsa `Cuentas` en el menú lateral
- **ENTONCES** llega a `/presupuestos/3/cuentas`

#### Scenario: Entrar directo a la URL
- **DADO** una sesión vigente y un presupuesto propio con id `3`
- **CUANDO** la persona entra a `/presupuestos/3/cuentas`
- **ENTONCES** ve la pantalla de cuentas del presupuesto `3`

### Requirement: Lista de cuentas con saldos
La pantalla SHALL pedir las cuentas del presupuesto activo y sus saldos
(`GET .../transacciones/saldos`) y SHALL mostrar las cuentas abiertas en dos secciones:
`En el presupuesto` (las de `enPresupuesto` verdadero) y `Seguimiento` (las demás), cada una en
el orden que da la API. Cada cuenta SHALL mostrar su nombre, su tipo en español, su saldo
formateado en la moneda del presupuesto y, como texto secundario, su saldo conciliado. Un saldo
negativo SHALL verse en color de error. Una cuenta que no aparece en la lista de saldos SHALL
mostrar saldo `0`. Una sección sin cuentas SHALL NOT mostrarse.

Los tipos SHALL mostrarse como: `CORRIENTE` → `Corriente`, `AHORRO` → `Ahorro`, `EFECTIVO` →
`Efectivo`, `TARJETA_CREDITO` → `Tarjeta de crédito`, `INVERSION` → `Inversión`, `PRESTAMO` →
`Préstamo`.

#### Scenario: Secciones
- **DADO** un presupuesto en `BOB` con las cuentas abiertas `Banco` (en el presupuesto) e
  `Inversiones` (de seguimiento)
- **CUANDO** la persona entra a la pantalla de cuentas
- **ENTONCES** ve `Banco` en `En el presupuesto` e `Inversiones` en `Seguimiento`

#### Scenario: Saldos unidos por cuenta
- **DADO** la cuenta `Banco` (id `5`) y la API de saldos con `cuentaId` `5`, `saldo` `1500000` y
  `saldoConciliado` `1000000`
- **CUANDO** se muestra la lista
- **ENTONCES** `Banco` muestra su saldo como 1.500 en la moneda del presupuesto y su saldo
  conciliado como 1.000

#### Scenario: Saldo negativo
- **DADO** una tarjeta de crédito con saldo `-250000`
- **CUANDO** se muestra la lista
- **ENTONCES** su saldo se ve en color de error

#### Scenario: Cuenta sin saldo informado
- **DADO** una cuenta cuyo id no está en la respuesta de saldos
- **CUANDO** se muestra la lista
- **ENTONCES** su saldo y su saldo conciliado se muestran como `0`

#### Scenario: Tipo en español
- **DADO** una cuenta de tipo `TARJETA_CREDITO`
- **CUANDO** se muestra la lista
- **ENTONCES** se lee `Tarjeta de crédito`

### Requirement: Total de las cuentas del presupuesto
La pantalla SHALL mostrar arriba el total de los saldos de las cuentas abiertas que están en el
presupuesto, sin contar las de seguimiento ni las cerradas, sumado como entero en milésimas y
formateado en la moneda del presupuesto.

#### Scenario: Total
- **DADO** las cuentas abiertas del presupuesto `Banco` (saldo `1500000`) y `Tarjeta` (saldo
  `-250000`) y la cuenta de seguimiento `Inversiones` (saldo `9000000`)
- **CUANDO** se muestra la pantalla
- **ENTONCES** el total es 1.250 en la moneda del presupuesto

### Requirement: Cuentas cerradas a pedido
La pantalla SHALL ocultar por defecto las cuentas cerradas y SHALL ofrecer un interruptor
`Ver cuentas cerradas`. Al activarlo SHALL volver a pedir la lista con `incluirCerradas=true` y
mostrar las cerradas en una sección `Cerradas`; al desactivarlo SHALL volver a pedirla sin ellas.

#### Scenario: Por defecto sin cerradas
- **DADO** un presupuesto con una cuenta cerrada
- **CUANDO** la persona entra a la pantalla de cuentas
- **ENTONCES** la lista se pide con `incluirCerradas=false`
- **Y** no se ve la sección `Cerradas`

#### Scenario: Ver cerradas
- **DADO** la pantalla de cuentas
- **CUANDO** la persona activa `Ver cuentas cerradas`
- **ENTONCES** la lista se pide con `incluirCerradas=true`
- **Y** la cuenta cerrada aparece en la sección `Cerradas`

### Requirement: Estados de carga, vacío y error
La pantalla SHALL mostrar un indicador de carga mientras espera la lista o los saldos. Si el
presupuesto no tiene cuentas que mostrar, SHALL mostrar `Aún no tienes cuentas` con un botón para
agregar una. Si la carga falla por un error distinto de `401`, SHALL mostrar el aviso genérico
con la opción `Reintentar`, que vuelve a pedir la lista y los saldos.

#### Scenario: Cargando
- **DADO** la lista de cuentas todavía pendiente
- **CUANDO** se observa la pantalla
- **ENTONCES** se ve un indicador de carga

#### Scenario: Sin cuentas
- **DADO** un presupuesto sin cuentas abiertas
- **CUANDO** la persona entra a la pantalla de cuentas
- **ENTONCES** ve `Aún no tienes cuentas` y un botón para agregar una cuenta

#### Scenario: Error y reintento
- **DADO** que `GET .../cuentas` responde `500`
- **CUANDO** la persona pulsa `Reintentar`
- **ENTONCES** la lista y los saldos se vuelven a pedir

### Requirement: Crear una cuenta
La pantalla SHALL ofrecer `Agregar cuenta`, que abre un diálogo con nombre, tipo (selector con
los tipos en español), `Cuenta del presupuesto` (marcada por defecto) y saldo inicial (`0` por
defecto). La petición SHALL llevar el nombre recortado, el tipo, `enPresupuesto` y el saldo
inicial convertido a milésimas. Al crearse, el diálogo SHALL cerrarse y la lista y los saldos
SHALL volver a pedirse.

#### Scenario: Valores por defecto
- **DADO** el diálogo de crear recién abierto
- **CUANDO** se observa
- **ENTONCES** `Cuenta del presupuesto` está marcada y el saldo inicial es `0`

#### Scenario: Crear con valores por defecto
- **DADO** el diálogo de crear
- **CUANDO** la persona escribe `"  Efectivo "`, elige `Efectivo` y confirma
- **ENTONCES** la petición lleva nombre `Efectivo`, tipo `EFECTIVO`, `enPresupuesto` verdadero y
  `saldoInicial` `0`

#### Scenario: Cuenta de seguimiento con saldo
- **DADO** el diálogo de crear, con la región `es-BO`
- **CUANDO** la persona elige `Inversión`, desmarca `Cuenta del presupuesto`, escribe
  `5.000,5` como saldo inicial y confirma
- **ENTONCES** la petición lleva `enPresupuesto` falso y `saldoInicial` `5000500`

#### Scenario: Lista actualizada tras crear
- **DADO** el diálogo de crear con datos válidos
- **CUANDO** la API responde `201`
- **ENTONCES** el diálogo se cierra y se vuelven a pedir la lista y los saldos

### Requirement: Editar una cuenta sin sus campos inmutables
El menú de cada cuenta SHALL ofrecer `Editar`, que abre el mismo diálogo con el nombre y el tipo
actuales y sin los campos `Cuenta del presupuesto` ni saldo inicial. La petición SHALL llevar
solo el nombre recortado y el tipo. Al guardarse, la lista y los saldos SHALL volver a pedirse.

#### Scenario: Campos del diálogo de editar
- **DADO** la cuenta `Banco` de tipo `CORRIENTE`
- **CUANDO** la persona elige `Editar`
- **ENTONCES** el diálogo muestra `Banco` y `Corriente`
- **Y** no muestra `Cuenta del presupuesto` ni el saldo inicial

#### Scenario: Guardar cambios
- **DADO** el diálogo de editar de la cuenta id `5`
- **CUANDO** la persona cambia el nombre a `Ahorros`, el tipo a `Ahorro` y confirma
- **ENTONCES** la petición es `PUT .../cuentas/5` con `{ nombre: 'Ahorros', tipo: 'AHORRO' }`

### Requirement: Validación del diálogo de cuenta
El diálogo SHALL exigir un nombre de 1 a 100 caracteres medidos sin los espacios de los extremos
y un tipo. El saldo inicial SHALL ser opcional (vacío equivale a `0`), un número escrito con el
separador decimal de la región y como máximo 3 decimales. Un saldo inicial negativo SHALL ser
válido solo con los tipos `Tarjeta de crédito` y `Préstamo`, y la regla SHALL reevaluarse al
cambiar el tipo. Al editar una cuenta con saldo inicial negativo, elegir un tipo que no lo admite
SHALL marcarse como error en el tipo. El botón de confirmar SHALL estar deshabilitado mientras el
formulario sea inválido o se esté enviando. Los mensajes SHALL ser:
- `El nombre es obligatorio` y `El nombre no puede superar los 100 caracteres`.
- `El tipo es obligatorio`.
- `Escribe un monto válido` y `Usa como máximo 3 decimales`.
- `El saldo inicial negativo solo se permite en tarjetas de crédito y préstamos`.
- `Esta cuenta tiene saldo inicial negativo: solo puede ser tarjeta de crédito o préstamo`.

#### Scenario: Saldo negativo en un tipo que no lo admite
- **DADO** el diálogo de crear con el tipo `Ahorro`
- **CUANDO** la persona escribe `-100` como saldo inicial
- **ENTONCES** se muestra el mensaje del saldo inicial negativo
- **Y** el botón de confirmar está deshabilitado

#### Scenario: El tipo corrige el saldo negativo
- **DADO** el diálogo de crear con saldo inicial `-100` y tipo `Ahorro`, marcado como inválido
- **CUANDO** la persona cambia el tipo a `Tarjeta de crédito`
- **ENTONCES** el saldo inicial deja de ser inválido

#### Scenario: Demasiados decimales
- **DADO** el diálogo de crear, con la región `en-US`
- **CUANDO** la persona escribe `12.3456`
- **ENTONCES** se muestra `Usa como máximo 3 decimales`

#### Scenario: Texto que no es un monto
- **DADO** el diálogo de crear
- **CUANDO** la persona escribe `abc` como saldo inicial
- **ENTONCES** se muestra `Escribe un monto válido`

#### Scenario: Editar a un tipo incompatible con el saldo existente
- **DADO** el diálogo de editar de una tarjeta de crédito con saldo inicial negativo
- **CUANDO** la persona cambia el tipo a `Ahorro`
- **ENTONCES** el tipo muestra el mensaje de saldo inicial negativo existente
- **Y** el botón de confirmar está deshabilitado

#### Scenario: Nombre vacío
- **DADO** el diálogo abierto
- **CUANDO** la persona deja el nombre en `"   "` y sale del campo
- **ENTONCES** se muestra `El nombre es obligatorio`

#### Scenario: Botón deshabilitado mientras se envía
- **DADO** un formulario válido enviado, con la respuesta todavía pendiente
- **CUANDO** se observa el botón de confirmar
- **ENTONCES** está deshabilitado

### Requirement: Errores del diálogo de cuenta según su código
El diálogo SHALL decidir cómo mostrar un error de la API por su `codigo`, nunca por `detail`:
`CUENTA_YA_EXISTE` SHALL mostrarse en el campo nombre como `Ya tienes una cuenta con ese nombre`;
`DATOS_INVALIDOS` con `errores` SHALL mostrar cada mensaje en su campo;
`REGLA_NEGOCIO_VIOLADA` SHALL mostrarse como mensaje dentro del diálogo:
`El saldo inicial negativo solo se permite en tarjetas de crédito y préstamos`; cualquier otro
error SHALL mostrar el aviso genérico. En todos los casos el diálogo SHALL quedar abierto con lo
escrito.

#### Scenario: Nombre repetido
- **DADO** un presupuesto con la cuenta `Banco`
- **CUANDO** se crea otra con nombre `banco` y la API responde `409` con `CUENTA_YA_EXISTE`
- **ENTONCES** el campo nombre muestra `Ya tienes una cuenta con ese nombre`
- **Y** el diálogo sigue abierto

#### Scenario: Error de campo del backend
- **DADO** el diálogo de crear
- **CUANDO** la API responde `400` con `DATOS_INVALIDOS` y `errores.saldoInicial`
- **ENTONCES** ese mensaje se muestra en el campo saldo inicial

#### Scenario: Regla de negocio
- **DADO** el diálogo de editar
- **CUANDO** la API responde `422` con `REGLA_NEGOCIO_VIOLADA`
- **ENTONCES** el diálogo muestra el mensaje del saldo inicial negativo y sigue abierto

#### Scenario: Error desconocido
- **DADO** el diálogo abierto
- **CUANDO** la API responde `500`
- **ENTONCES** se muestra el aviso genérico y el diálogo sigue abierto

### Requirement: Cerrar y reabrir una cuenta
El menú de cada cuenta abierta SHALL ofrecer `Cerrar`, y el de cada cuenta cerrada `Reabrir`.
Cada acción SHALL llamar a `POST .../cuentas/{id}/cerrar` o `/reabrir` y, al responder, SHALL
volver a pedir la lista y los saldos. Un error distinto de `401` SHALL mostrar el aviso genérico.

#### Scenario: Cerrar
- **DADO** la cuenta abierta `Banco` (id `5`)
- **CUANDO** la persona elige `Cerrar` en su menú
- **ENTONCES** se llama a `POST .../cuentas/5/cerrar`
- **Y** se vuelven a pedir la lista y los saldos

#### Scenario: Reabrir
- **DADO** las cuentas cerradas visibles y la cuenta cerrada `Viejo` (id `8`)
- **CUANDO** la persona elige `Reabrir` en su menú
- **ENTONCES** se llama a `POST .../cuentas/8/reabrir`
- **Y** se vuelven a pedir la lista y los saldos

### Requirement: Conversión de montos escritos a milésimas
El sistema SHALL convertir un monto escrito por la persona a un entero en milésimas sin usar
aritmética de coma flotante: SHALL separar el texto en signo, parte entera y parte decimal y
componer el entero a partir de los dígitos. SHALL usar el separador decimal y el de miles de la
región del usuario, y SHALL aceptar el de miles solo en grupos de tres dígitos. SHALL rechazar
más de 3 decimales, cualquier texto que no sea un número y los valores fuera del rango de
enteros exactos. Un texto vacío SHALL tratarse como "sin valor", distinto de un valor inválido.
La conversión inversa SHALL producir el texto que la persona puede editar: con el separador
decimal de la región, sin separador de miles y sin ceros decimales sobrantes.

#### Scenario: Cero y negativos
- **DADO** la región `en-US`
- **CUANDO** se convierten `0`, `-5` y `-0.5`
- **ENTONCES** se obtienen `0`, `-5000` y `-500`

#### Scenario: La milésima más pequeña
- **DADO** la región `es-BO`
- **CUANDO** se convierte `0,001`
- **ENTONCES** se obtiene `1`

#### Scenario: Separadores de miles por región
- **DADO** las regiones `es-BO` y `en-US`
- **CUANDO** se convierten `1.234,567` en `es-BO` y `1,234.567` en `en-US`
- **ENTONCES** ambos dan `1234567`

#### Scenario: Más de 3 decimales
- **DADO** la región `en-US`
- **CUANDO** se convierte `12.3456`
- **ENTONCES** se rechaza por tener más de 3 decimales

#### Scenario: Sin pérdida de precisión
- **DADO** la región `en-US`
- **CUANDO** se convierte `1.005`
- **ENTONCES** se obtiene exactamente `1005`, no `1004.9999999999999` ni `1004`

#### Scenario: Texto vacío
- **DADO** cualquier región
- **CUANDO** se convierte `""` o `"   "`
- **ENTONCES** el resultado es "sin valor", no un error

#### Scenario: Conversión inversa
- **DADO** la región `es-BO`
- **CUANDO** se convierten a texto `1234567`, `-500` y `5000`
- **ENTONCES** se obtienen `1234,567`, `-0,5` y `5`
