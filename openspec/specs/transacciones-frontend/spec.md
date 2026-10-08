# transacciones-frontend Specification

## Purpose

Registro de transacciones del presupuesto activo: verlas paginadas y filtradas, cargarlas,
editarlas, dividirlas entre categorías, aprobarlas, conciliarlas, moverlas, duplicarlas,
borrarlas y operar sobre varias a la vez, con los saldos de las cuentas a la vista.

## Requirements

### Requirement: Sección Transacciones
El sistema SHALL ofrecer la pantalla en `/presupuestos/:presupuestoId/transacciones`, con el
enlace `Transacciones` en el menú lateral, y SHALL aceptar en la URL los filtros como parámetros
de consulta (`cuentaId`, `categoriaId`, `desde`, `hasta`, `estado`, `sinAprobar`, `q`, `pagina`
y `tamano`), de modo que recargar o compartir la URL conserva la vista.

#### Scenario: Entrar filtrado por cuenta
- **DADO** una persona con la cuenta `Banco` (id `5`)
- **CUANDO** entra a `/presupuestos/3/transacciones?cuentaId=5`
- **ENTONCES** la lista se pide con `cuentaId=5` y el filtro de cuenta muestra `Banco`

### Requirement: Lista paginada en el servidor
La pantalla SHALL pedir `GET .../transacciones` con `page` y `size` (20 por defecto, opciones
10, 20, 50 y 100) y SHALL mostrar un paginador con el total de la API. Cambiar de página o de
tamaño SHALL actualizar la URL y pedir esa página. Si llega la respuesta de una petición vieja
después de una nueva, SHALL ignorarse.

#### Scenario: Cambiar de página
- **DADO** una lista con `totalElementos` `45` y tamaño 20
- **CUANDO** la persona pasa a la página siguiente
- **ENTONCES** la URL lleva `pagina=1` y se pide `page=1&size=20`

#### Scenario: Respuesta atrasada
- **DADO** una petición pendiente de la página 0
- **CUANDO** la persona cambia un filtro y la respuesta vieja llega después de la nueva
- **ENTONCES** se muestra solo la respuesta de la petición nueva

### Requirement: Filtros
La pantalla SHALL ofrecer filtros de cuenta, categoría, desde, hasta (con calendario, enviados
como `yyyy-MM-dd` con la fecha local), estado, `Solo sin aprobar` y búsqueda de texto. La
búsqueda SHALL enviarse 300 ms después de dejar de escribir. Cambiar cualquier filtro SHALL volver
a la página 0, limpiar la selección y guardarse en la URL; `Limpiar filtros` SHALL quitarlos
todos. Cada filtro SHALL enviarse con su nombre de la API: `cuentaId`, `categoriaId`, `desde`,
`hasta`, `estado`, `soloSinAprobar=true` y `q`.

#### Scenario: Rango de fechas
- **DADO** la zona horaria `Asia/Tokyo` o `America/New_York`
- **CUANDO** la persona elige desde el 1 de octubre de 2026 y hasta el 31 de octubre de 2026
- **ENTONCES** se envían `desde=2026-10-01` y `hasta=2026-10-31`

#### Scenario: Búsqueda con espera
- **DADO** la pantalla en la página 2
- **CUANDO** la persona escribe `super` en la búsqueda
- **ENTONCES** se pide, 300 ms después, la página 0 con `q=super`, y no una petición por letra

### Requirement: Columnas de la tabla
Cada fila SHALL mostrar fecha, nombre de la cuenta, beneficiario, categoría, memo, el monto en
`Salida` si es negativo o en `Entrada` si es positivo (en la moneda del presupuesto), el estado y
un indicador `Sin aprobar`. El beneficiario SHALL ser el nombre actual del beneficiario vinculado
(`beneficiarioId`) o, si no hay vínculo o no se encuentra, el texto `beneficiario` de la
transacción. La categoría SHALL ser su nombre, `Dividida` con las partes si tiene
subtransacciones, o vacía si no tiene. El estado SHALL verse con ícono y texto accesible
(`No conciliada`, `Conciliada`, `Reconciliada` con candado). Las filas sin aprobar SHALL
destacarse, y las patas de transferencia SHALL mostrar la insignia `Transferencia`. En pantallas
estrechas cada fila SHALL apilarse.

#### Scenario: Salida y entrada
- **DADO** una transacción de `-25000` y otra de `100000`
- **CUANDO** se muestra la tabla
- **ENTONCES** la primera muestra 25 en `Salida` y la segunda 100 en `Entrada`

#### Scenario: Dividida
- **DADO** una transacción con partes en `Comida` y `Ropa`
- **CUANDO** se muestra la tabla
- **ENTONCES** su categoría dice `Dividida` y nombra `Comida` y `Ropa`

#### Scenario: Beneficiario vinculado renombrado
- **DADO** una transacción con texto `Netflix` vinculada a un beneficiario ahora llamado
  `Netflix Premium`
- **CUANDO** se muestra la tabla
- **ENTONCES** la columna `Beneficiario` muestra `Netflix Premium`

### Requirement: Acciones según el estado de cada transacción
El menú de cada fila SHALL ofrecer `Editar`, `Duplicar`, `Mover a otra cuenta`, `Aprobar` (solo
si no está aprobada), `Marcar conciliada` o `Marcar no conciliada`, y `Borrar`, con estas
excepciones, y cada acción deshabilitada SHALL mostrar su motivo:
- Una transacción `RECONCILIADA` SHALL tener deshabilitados `Editar`, `Mover a otra cuenta`,
  `Borrar` y el cambio de estado, con el motivo `Está reconciliada`.
- Una pata de transferencia SHALL ofrecer `Editar transferencia`, `Borrar transferencia`,
  `Aprobar` (solo si no está aprobada) y el cambio de estado, en lugar de `Editar` y `Borrar`;
  `Duplicar` y `Mover a otra cuenta` SHALL estar deshabilitados con el motivo
  `Es parte de una transferencia`. Las reglas de `Editar transferencia` y `Borrar
  transferencia` son las de la capacidad `transferencias-frontend`.
- Una transacción de una cuenta cerrada SHALL NOT ofrecer `Editar`.

#### Scenario: Reconciliada
- **DADO** una transacción `RECONCILIADA`
- **CUANDO** la persona abre su menú
- **ENTONCES** `Editar`, `Mover a otra cuenta`, `Borrar` y el cambio de estado están
  deshabilitados con `Está reconciliada`

#### Scenario: Pata de transferencia
- **DADO** una transacción con `transaccionParId`, no reconciliada y de una cuenta abierta
- **CUANDO** la persona abre su menú
- **ENTONCES** `Editar transferencia`, `Borrar transferencia`, `Aprobar` y `Marcar conciliada`
  están habilitados
- **Y** `Duplicar` y `Mover a otra cuenta` están deshabilitados con
  `Es parte de una transferencia`
- **Y** no aparecen `Editar` ni `Borrar`

### Requirement: Crear y editar una transacción
`Agregar transacción` SHALL abrir un diálogo con:
- La cuenta, agrupada en `En el presupuesto` y `Seguimiento`, sin las cerradas.
- La fecha, hoy en hora local por defecto.
- El beneficiario, con autocompletado por prefijo y texto libre (máximo 100 caracteres, con
  contador), según la capacidad `beneficiarios-frontend`.
- La categoría, agrupada por grupo; las ocultas solo si ya estaban elegidas o las sugirió el
  beneficiario, más `Sin categoría`.
- El tipo `Salida` o `Entrada`.
- El monto en positivo con la calculadora.
- El memo (máximo 500, con contador).
- `Aprobada`, marcada por defecto.

La petición SHALL llevar el monto con signo negativo si es `Salida`, la fecha como `yyyy-MM-dd`,
y beneficiario y memo recortados. `Editar` SHALL abrir el mismo diálogo con los valores de la
transacción, la cuenta visible pero no editable y sin `Aprobada`, y SHALL enviar `PUT` sin la
cuenta. Cuenta, fecha y monto SHALL ser obligatorios y el monto mayor que 0; el botón SHALL estar
deshabilitado mientras el formulario sea inválido o se esté enviando. Al guardar, SHALL cerrarse
y SHALL volver a pedirse la página actual, los saldos y la lista de beneficiarios.

#### Scenario: Crear una salida
- **DADO** hoy el 6 de octubre de 2026 y el diálogo de crear
- **CUANDO** la persona elige `Banco`, `Comida`, `Salida`, escribe `25,5` y confirma
- **ENTONCES** se envía `{ cuentaId: 5, fecha: '2026-10-06', monto: -25500, categoriaId: 7, ... }`

#### Scenario: Monto cero
- **DADO** el diálogo de crear
- **CUANDO** la persona escribe `0` como monto
- **ENTONCES** se muestra `El monto debe ser mayor que 0` y el botón está deshabilitado

#### Scenario: Editar una entrada
- **DADO** una transacción de `100000` en `Banco`
- **CUANDO** la persona la edita
- **ENTONCES** el diálogo muestra `Entrada`, `100` y la cuenta `Banco` sin poder cambiarla
- **Y** al guardar se envía `PUT .../transacciones/{id}` sin `cuentaId`

#### Scenario: Beneficiario escrito a mano
- **DADO** el diálogo de crear
- **CUANDO** la persona escribe `  Panadería Sol ` sin elegir sugerencia y guarda
- **ENTONCES** se envía `beneficiario: 'Panadería Sol'`

### Requirement: Dividir una transacción
El diálogo SHALL ofrecer el interruptor `Dividir`, que reemplaza la categoría por una lista de 2 a
20 partes, cada una con categoría, monto (con la calculadora; positivo en el sentido de la
transacción, negativo en el contrario) y memo, con botones para agregar y quitar partes. SHALL
mostrar el total de las partes y cuánto falta o sobra para llegar al monto, sumado en milésimas
enteras, y el botón de guardar SHALL estar deshabilitado mientras la suma no coincida
exactamente. La petición de una dividida SHALL llevar `subtransacciones` con el signo de la
transacción y `categoriaId` nulo. Al editar una dividida SHALL cargar sus partes; al desactivar
`Dividir` SHALL volver la categoría simple y enviarse `subtransacciones` vacía.

#### Scenario: Falta asignar
- **DADO** una salida de `30` dividida en partes de `10` y `15`
- **CUANDO** se observa el editor
- **ENTONCES** se lee `Falta asignar 5` y el botón de guardar está deshabilitado

#### Scenario: División exacta
- **DADO** una salida de `30` dividida en partes de `10` y `20`
- **CUANDO** la persona guarda
- **ENTONCES** se envía `categoriaId: null` y `subtransacciones` con montos `-10000` y `-20000`

#### Scenario: Límites de partes
- **DADO** el editor con 2 partes
- **CUANDO** la persona intenta quitar una
- **ENTONCES** no puede bajar de 2, y tampoco puede agregar más de 20

### Requirement: Errores del diálogo de transacción
El diálogo SHALL decidir por `codigo`: `DATOS_INVALIDOS` con `errores` en sus campos;
`REGLA_NEGOCIO_VIOLADA` como mensaje en el diálogo, sin cerrarlo; `RECURSO_NO_ENCONTRADO` con un
aviso, cerrando el diálogo y volviendo a pedir las cuentas, las categorías y la página; cualquier
otro error con el aviso genérico.

#### Scenario: Regla de negocio
- **DADO** el diálogo de crear en una cuenta que se cerró mientras tanto
- **CUANDO** la API responde `422`
- **ENTONCES** el diálogo muestra el mensaje y sigue abierto

### Requirement: Aprobar, conciliar, mover, duplicar y borrar
`Aprobar` SHALL llamar a `POST .../{id}/aprobar`, y el cambio de estado a `PUT .../{id}/estado`
con `CONCILIADA` o `NO_CONCILIADA`. `Mover a otra cuenta` SHALL abrir un diálogo con las cuentas
abiertas sin la actual y llamar a `POST .../{id}/mover-cuenta`. `Duplicar` SHALL llamar a
`POST .../{id}/duplicar`. `Borrar` SHALL pedir confirmación y, si se confirma, llamar a
`DELETE .../{id}`. Después de cada una SHALL volver a pedirse la página y los saldos; un `422`
o un `404` SHALL mostrar un aviso.

#### Scenario: Borrar con confirmación
- **DADO** una transacción no reconciliada
- **CUANDO** la persona elige `Borrar` y confirma
- **ENTONCES** se llama a `DELETE .../transacciones/{id}` y la página se vuelve a pedir

#### Scenario: Cancelar el borrado
- **DADO** el diálogo de confirmación de borrado
- **CUANDO** la persona cancela
- **ENTONCES** no se llama al backend

### Requirement: Operaciones en lote
Cada fila SHALL tener una casilla, y el encabezado una para seleccionar toda la página. Con una o
más filas seleccionadas SHALL aparecer una barra con la cantidad y las acciones `Aprobar`,
`Categorizar` (con un selector de categoría) y `Borrar`. Antes de enviar, SHALL excluir las filas
a las que no se les puede aplicar la operación: de `Categorizar`, las patas de transferencia, las
divididas y las reconciliadas; de `Borrar`, las patas y las reconciliadas. Si excluye alguna,
SHALL avisar cuántas omite y pedir confirmación; si no queda ninguna, SHALL avisar sin llamar al
backend. `Borrar` SHALL pedir confirmación siempre. SHALL enviar como máximo 100 ids. La
selección SHALL limpiarse al cambiar de página o de filtros y después de cada operación.

#### Scenario: Categorizar excluyendo una pata
- **DADO** seleccionadas dos transacciones simples y una pata de transferencia
- **CUANDO** la persona categoriza en `Comida` y confirma el aviso de 1 omitida
- **ENTONCES** se envía `POST .../lote` con los 2 ids, `CATEGORIZAR` y la categoría

#### Scenario: Aprobar en lote
- **DADO** tres filas seleccionadas, una de ellas pata de transferencia
- **CUANDO** la persona pulsa `Aprobar`
- **ENTONCES** se envía `APROBAR` con los 3 ids

### Requirement: Saldos
Arriba de la tabla SHALL mostrarse el saldo y el saldo conciliado de la cuenta filtrada, o la suma
de todas las cuentas si no hay filtro de cuenta, y SHALL refrescarse después de cualquier cambio.

#### Scenario: Saldo de la cuenta filtrada
- **DADO** el filtro de cuenta `Banco` con saldo `150000` y conciliado `100000`
- **CUANDO** se muestra la pantalla
- **ENTONCES** se lee el saldo de 150 y el conciliado de 100

### Requirement: Estados de carga, vacío y error
La pantalla SHALL mostrar un indicador de carga mientras espera la primera página. Sin
transacciones SHALL mostrar `Aún no hay transacciones`; con filtros y sin resultados,
`Ningún resultado con estos filtros` con el botón `Limpiar filtros`. Si la carga falla por un
error distinto de `401`, SHALL mostrar el aviso genérico con `Reintentar`.

#### Scenario: Sin resultados con filtros
- **DADO** un filtro de estado `RECONCILIADA` sin resultados
- **CUANDO** se muestra la pantalla
- **ENTONCES** se lee `Ningún resultado con estos filtros` y `Limpiar filtros` quita los filtros
