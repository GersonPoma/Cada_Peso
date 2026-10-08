# conciliacion-frontend Specification

## Purpose
Permite conciliar una cuenta contra el saldo de su extracto desde el frontend: ver la diferencia,
cerrarla con un ajuste si hace falta, reconciliar lo ya revisado y consultar el historial.

## Requirements

### Requirement: Pantalla y puntos de entrada
El sistema SHALL ofrecer la pantalla `/presupuestos/:presupuestoId/cuentas/:cuentaId/conciliacion`,
sin enlace propio en el menú lateral, que pide la cuenta (`GET .../cuentas/{cuentaId}`), las
categorías con ocultas y el historial, y muestra el nombre y el tipo de la cuenta con un enlace
para volver a `Cuentas`. La pantalla de Cuentas SHALL ofrecer `Conciliar` en el menú de cada
cuenta abierta y `Ver conciliaciones` en el de cada cuenta cerrada; la pantalla de Transacciones
filtrada por una cuenta abierta SHALL ofrecer el botón `Conciliar`. Todos llevan a esa pantalla.
Una cuenta inexistente o de otro presupuesto SHALL mostrar un aviso y llevar a `Cuentas`.

#### Scenario: Desde Cuentas
- **DADO** la cuenta abierta `Banco` (id `5`) en el presupuesto `3`
- **CUANDO** la persona pulsa `Conciliar` en su menú
- **ENTONCES** llega a `/presupuestos/3/cuentas/5/conciliacion` y ve `Banco` y su tipo

#### Scenario: Desde Transacciones
- **DADO** la pantalla de transacciones filtrada por la cuenta abierta `Banco`
- **CUANDO** la persona pulsa `Conciliar`
- **ENTONCES** llega a la conciliación de `Banco`; sin filtro de cuenta, o con una cuenta
  cerrada, no hay botón `Conciliar`

#### Scenario: Cuenta inexistente
- **DADO** la URL de la conciliación de una cuenta que no existe
- **CUANDO** la API responde `404`
- **ENTONCES** se muestra un aviso y la persona llega a `Cuentas`

### Requirement: Estado de la conciliación mientras se escribe
El asistente SHALL tener `Saldo del extracto` (calculadora, admite negativos, obligatorio) y
`Fecha del extracto` (hoy en hora local por defecto, obligatoria, no posterior a hoy, con el
mensaje `La fecha no puede ser futura`). Con ambos válidos SHALL pedir
`GET .../conciliacion/estado?saldoExtracto=&fecha=` 300 ms después del último cambio, con la
fecha como `yyyy-MM-dd` local; una respuesta de una consulta anterior SHALL ignorarse, y con
datos inválidos SHALL NOT pedirse nada. SHALL mostrar el `Saldo conciliado al {fecha}`
(`saldoConciliadoAlCorte`) y la diferencia con texto además de color: `Cuadra con el extracto`
(0), `A lo conciliado le faltan {diferencia}` (positiva) o `Lo conciliado supera al extracto en
{diferencia absoluta}` (negativa). Mientras consulta SHALL mostrar un indicador; si la consulta
falla, `No pudimos calcular la diferencia` con `Reintentar`.

#### Scenario: Espera antes de consultar
- **DADO** el asistente con la fecha de hoy
- **CUANDO** la persona escribe `1`, `15` y `150` en menos de 300 ms
- **ENTONCES** se hace una sola consulta con `saldoExtracto=150000`, 300 ms después

#### Scenario: Respuesta atrasada
- **DADO** una consulta con `100` todavía sin respuesta
- **CUANDO** la persona cambia a `150` y la respuesta de `100` llega después de la de `150`
- **ENTONCES** se muestra la diferencia de `150`

#### Scenario: Diferencia positiva
- **DADO** `saldoConciliadoAlCorte` `120000` y el extracto `150`
- **CUANDO** llega el estado
- **ENTONCES** se lee `A lo conciliado le faltan 30` en la moneda del presupuesto

#### Scenario: Fecha futura
- **DADO** el asistente
- **CUANDO** la persona elige mañana
- **ENTONCES** el campo dice `La fecha no puede ser futura`, no se consulta nada y no se puede
  reconciliar

### Requirement: No conciliadas hasta la fecha
El asistente SHALL explicar que solo se reconcilian las transacciones `Conciliada` con fecha hasta
la del extracto y SHALL mostrar cuántas transacciones `No conciliada` de la cuenta tienen fecha
hasta la del extracto, contadas sobre la lista que devuelve el estado; si la API devolvió menos
transacciones que `totalNoConciliadas`, SHALL decir `al menos {n}`. Con al menos una SHALL ofrecer
`Revisar en Transacciones`, que lleva a la lista filtrada por la cuenta, el estado `No conciliada`
y `hasta` la fecha del extracto.

#### Scenario: Pendientes de marcar
- **DADO** tres transacciones `NO_CONCILIADA` de la cuenta, dos hasta el 30 de septiembre y una
  del 2 de octubre, y la fecha del extracto 30 de septiembre
- **CUANDO** llega el estado
- **ENTONCES** se lee que hay 2 transacciones no conciliadas hasta esa fecha que no se
  reconciliarán
- **Y** `Revisar en Transacciones` lleva a
  `/presupuestos/3/transacciones?cuentaId=5&estado=NO_CONCILIADA&hasta=2026-09-30`

#### Scenario: Lista recortada
- **DADO** `totalNoConciliadas` `130` y 100 transacciones en la lista, todas hasta la fecha
- **CUANDO** llega el estado
- **ENTONCES** se lee `al menos 100`

### Requirement: Ajuste y su categoría
Con diferencia 0, el asistente SHALL ofrecer `Reconciliar` sin ajuste. Con diferencia distinta de
0 SHALL mostrar `Crear transacción de ajuste por {diferencia}` (entrada si es positiva, salida si
es negativa); sin marcarla, `Reconciliar` SHALL estar deshabilitado con el texto `Para cerrar la
conciliación con diferencia, crea el ajuste o revisa tus transacciones`. La categoría del ajuste
SHALL seguir las reglas del backend: en una cuenta fuera del presupuesto no se muestra ni se envía;
en una cuenta del presupuesto es obligatoria si el ajuste es negativo o la cuenta es una tarjeta de
crédito (ayuda `El ajuste sale del dinero de una categoría`), y opcional si es positivo en otra
cuenta (ayuda `Sin categoría cuenta como ingreso`). El select SHALL agruparse por grupo, sin
categorías ocultas ni de pago de tarjeta. Si la diferencia cambia, la categoría SHALL vaciarse
cuando deje de mostrarse.

#### Scenario: Ajuste negativo
- **DADO** la cuenta `Banco` del presupuesto y una diferencia de `-5000`
- **CUANDO** la persona marca el ajuste sin categoría
- **ENTONCES** la categoría es obligatoria y `Reconciliar` está deshabilitado
- **Y** al elegir `Comisiones` se puede reconciliar con `categoriaId`

#### Scenario: Ajuste positivo
- **DADO** la cuenta `Banco` (corriente, del presupuesto) y una diferencia de `2000`
- **CUANDO** la persona marca el ajuste sin categoría
- **ENTONCES** puede reconciliar y se envía `categoriaId: null`

#### Scenario: Tarjeta de crédito
- **DADO** la tarjeta `Visa` del presupuesto y una diferencia de `2000`
- **CUANDO** la persona marca el ajuste
- **ENTONCES** la categoría es obligatoria

#### Scenario: Cuenta de seguimiento
- **DADO** la cuenta `Inversiones` fuera del presupuesto y una diferencia de `-5000`
- **CUANDO** la persona marca el ajuste
- **ENTONCES** no hay campo de categoría y la petición no lleva `categoriaId`

### Requirement: Confirmar y reconciliar
`Reconciliar` SHALL abrir una confirmación con la fecha, el saldo del extracto, el ajuste (si lo
hay) y el aviso `Las transacciones conciliadas hasta esta fecha quedarán reconciliadas: ya no se
podrán editar, mover, borrar ni cambiar de estado. Esto no se puede deshacer.` Al confirmar SHALL
enviar `POST .../conciliacion` con `{ saldoExtracto, fecha, crearAjuste, categoriaId }`
(`crearAjuste` verdadero solo si se marcó el ajuste; `categoriaId` solo si la categoría aplica)
con el botón deshabilitado mientras se envía. Al responder SHALL mostrar el resultado real de la
respuesta (`Se reconciliaron {cantidadReconciliadas} transacciones` y, si `ajuste` no es 0,
`Se creó un ajuste de {ajuste}`), aunque difiera de la diferencia vista antes, y volver a pedir el
estado y el historial. Cancelar la confirmación SHALL NOT enviar nada.

#### Scenario: Sin diferencia
- **DADO** una diferencia de 0 al 30 de septiembre de 2026 con el extracto `150`
- **CUANDO** la persona pulsa `Reconciliar` y confirma
- **ENTONCES** se envía `{ saldoExtracto: 150000, fecha: '2026-09-30', crearAjuste: false }`
- **Y** con la respuesta `cantidadReconciliadas: 4` se lee `Se reconciliaron 4 transacciones`

#### Scenario: Resultado distinto al previsto
- **DADO** una diferencia vista de `-5000` con el ajuste marcado
- **CUANDO** el `POST` responde `ajuste: 0` porque otra sesión cuadró la cuenta
- **ENTONCES** se muestra la cantidad reconciliada sin mensaje de ajuste

#### Scenario: Cancelar
- **DADO** la confirmación abierta
- **CUANDO** la persona cancela
- **ENTONCES** no se envía ninguna petición

### Requirement: Historial de conciliaciones
La pantalla SHALL mostrar el historial (`GET .../conciliacion`, de la más reciente a la más
antigua) con la fecha, el saldo del extracto, el ajuste (`—` si es 0) y la cantidad reconciliada.
Sin conciliaciones SHALL decir `Aún no has conciliado esta cuenta`; mientras carga, un indicador; si
falla, un mensaje con `Reintentar`. En pantallas estrechas cada conciliación SHALL apilarse.

#### Scenario: Historial
- **DADO** dos conciliaciones, la del 30 de septiembre con ajuste `-5000` y 4 reconciliadas
- **CUANDO** se muestra la pantalla
- **ENTONCES** la del 30 de septiembre aparece primero con su ajuste y `4`

#### Scenario: Vacío
- **DADO** una cuenta sin conciliaciones
- **CUANDO** se muestra la pantalla
- **ENTONCES** se lee `Aún no has conciliado esta cuenta`

### Requirement: Cuenta cerrada
Para una cuenta cerrada la pantalla SHALL mostrar el historial y el aviso `La cuenta está cerrada;
reábrela en Cuentas para conciliarla`, sin el asistente.

#### Scenario: Cerrada
- **DADO** la cuenta cerrada `Vieja`
- **CUANDO** la persona pulsa `Ver conciliaciones`
- **ENTONCES** ve el aviso y el historial, sin campos para conciliar

### Requirement: Errores de la conciliación
El asistente SHALL decidir por `codigo`: `DATOS_INVALIDOS` con `errores` en sus campos y, sin
`errores`, `Revisa el saldo y la fecha del extracto`; `REGLA_NEGOCIO_VIOLADA` como mensaje en el
asistente, sin perder lo escrito (`No se pudo reconciliar: la cuenta está cerrada, falta el ajuste
o su categoría no corresponde.`); `RECURSO_NO_ENCONTRADO` con un aviso y volviendo a pedir la
cuenta, las categorías, el estado y el historial; cualquier otro error con el aviso genérico.

#### Scenario: Regla de negocio
- **DADO** una cuenta que se cerró en otra sesión
- **CUANDO** el `POST` responde `422`
- **ENTONCES** el asistente muestra el mensaje y conserva el saldo y la fecha

#### Scenario: Categoría borrada
- **DADO** el ajuste con una categoría que otra sesión borró
- **CUANDO** el `POST` responde `404`
- **ENTONCES** se muestra un aviso y se vuelven a pedir la cuenta, las categorías, el estado y el
  historial

### Requirement: Accesibilidad y pantallas estrechas
Cada campo SHALL tener etiqueta visible; la diferencia y el resultado SHALL anunciarse con
`aria-live`; la confirmación SHALL llevar el foco a `Cancelar` y devolverlo a `Reconciliar` al
cerrarse. En pantallas estrechas el asistente y el historial SHALL apilarse en una columna.

#### Scenario: Foco
- **DADO** la confirmación abierta
- **CUANDO** la persona la cancela
- **ENTONCES** el foco vuelve a `Reconciliar`
