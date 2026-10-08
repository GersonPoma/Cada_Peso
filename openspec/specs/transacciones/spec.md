# transacciones Specification

## Purpose

Permite registrar y gestionar los movimientos de dinero de las cuentas de un presupuesto: crearlos,
dividirlos en subtransacciones, listarlos con paginación y filtros, editarlos, aprobarlos, cambiar
su estado, moverlos de cuenta, duplicarlos, operarlos en lote y consultar el saldo de cada cuenta,
sin que nadie más pueda verlos ni tocarlos.

## Requirements

### Requirement: Autenticación obligatoria
El sistema SHALL exigir autenticación en todas las rutas de
`/api/v1/presupuestos/{presupuestoId}/transacciones`.

#### Scenario: Petición sin token
- **DADO** una petición sin token de acceso
- **CUANDO** se llama a cualquier ruta de transacciones de un presupuesto
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

### Requirement: Aislamiento por presupuesto y por persona
El sistema SHALL validar primero, en cada operación, que el presupuesto de la URL pertenece a la
persona autenticada, y SHALL buscar toda transacción dentro de ese presupuesto. Un presupuesto o
una transacción inexistente o ajena SHALL responder `404` con código `RECURSO_NO_ENCONTRADO`,
nunca `403`.

#### Scenario: Presupuesto de otra persona
- **DADO** una persona B autenticada y un presupuesto de la persona A con una transacción
- **CUANDO** B crea, lista, consulta, edita, borra, aprueba, cambia el estado, mueve de cuenta,
  duplica, opera en lote o pide los saldos en el presupuesto de A
- **ENTONCES** el sistema responde `404` en cada caso y no modifica nada

#### Scenario: Transacción de otra persona
- **DADO** una transacción del presupuesto de A y una persona B con su propio presupuesto
- **CUANDO** B la consulta, edita, borra, aprueba, cambia su estado, la mueve o la duplica por
  la URL de su propio presupuesto
- **ENTONCES** el sistema responde `404` en cada caso y la transacción no cambia

#### Scenario: Transacción de otro presupuesto de la misma persona
- **DADO** una persona con dos presupuestos y una transacción en el primero
- **CUANDO** la consulta, edita, borra, aprueba, cambia su estado, la mueve o la duplica por la
  URL del segundo presupuesto
- **ENTONCES** el sistema responde `404` en cada caso

#### Scenario: Presupuesto o transacción inexistente
- **DADO** un id que no existe
- **CUANDO** se usa como presupuesto o como transacción en cualquier ruta
- **ENTONCES** el sistema responde `404` con código `RECURSO_NO_ENCONTRADO`

### Requirement: Crear una transacción
El sistema SHALL permitir crear una transacción en una cuenta del presupuesto con `cuentaId`,
`fecha`, `monto` y, opcionalmente, `categoriaId`, `beneficiario`, `memo`, `aprobada` y
`subtransacciones`, y SHALL responder `201`. El `monto` está en milésimas con signo (entrada
positiva, salida negativa). El estado inicial SHALL ser `NO_CONCILIADA` y `aprobada` SHALL ser
`true` si no se indica. Se permiten fechas futuras.

#### Scenario: Creación mínima
- **DADO** una cuenta abierta del presupuesto
- **CUANDO** se crea una transacción con `cuentaId`, `fecha` y `monto` `-25000`
- **ENTONCES** el sistema responde `201` con la transacción, estado `NO_CONCILIADA`, `aprobada`
  `true`, `categoriaId`, `beneficiario` y `memo` nulos, sin subtransacciones y con
  `fechaCreacion` y `fechaActualizacion`

#### Scenario: Entrada de dinero
- **DADO** una cuenta abierta
- **CUANDO** se crea una transacción con `monto` positivo
- **ENTONCES** el sistema responde `201` y conserva el signo del monto

#### Scenario: Fecha futura
- **DADO** una cuenta abierta
- **CUANDO** se crea una transacción con una fecha posterior a hoy
- **ENTONCES** el sistema responde `201`

#### Scenario: Aprobada explícitamente en falso
- **DADO** una cuenta abierta
- **CUANDO** se crea una transacción con `aprobada` `false`
- **ENTONCES** el sistema responde `201` con `aprobada` `false`

#### Scenario: Beneficiario y memo normalizados
- **DADO** un beneficiario `"  Tienda  "` y un memo `"   "`
- **CUANDO** se crea la transacción
- **ENTONCES** el beneficiario se guarda como `"Tienda"` y el memo como nulo

#### Scenario: Monto cero
- **DADO** una petición con `monto` `0`
- **CUANDO** se crea la transacción
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y el campo `monto` en
  `errores`

#### Scenario: Campos obligatorios y longitudes
- **DADO** una petición sin `fecha`, sin `cuentaId`, sin `monto`, con beneficiario de más de
  100 caracteres o con memo de más de 500
- **CUANDO** se crea la transacción
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS` y el campo en `errores`

#### Scenario: Fecha imposible o cuerpo ilegible
- **DADO** una fecha inexistente (`2026-02-31`) o un cuerpo vacío
- **CUANDO** se crea la transacción
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Cuenta y categorías del presupuesto
La cuenta (`cuentaId`) y toda categoría usada (`categoriaId` o la de cada subtransacción) SHALL
pertenecer al presupuesto de la URL; si no, el sistema SHALL responder `404`. Una categoría
oculta SHALL poder usarse.

#### Scenario: Cuenta de otro presupuesto
- **DADO** una cuenta que pertenece a otro presupuesto
- **CUANDO** se crea una transacción con ese `cuentaId`
- **ENTONCES** el sistema responde `404` con código `RECURSO_NO_ENCONTRADO` y no crea nada

#### Scenario: Cuenta inexistente
- **DADO** un `cuentaId` que no existe
- **CUANDO** se crea una transacción
- **ENTONCES** el sistema responde `404`

#### Scenario: Categoría de otro presupuesto
- **DADO** una categoría de otro presupuesto
- **CUANDO** se crea o edita una transacción con ese `categoriaId`
- **ENTONCES** el sistema responde `404` y no modifica nada

#### Scenario: Categoría de otro presupuesto en una subtransacción
- **DADO** una división donde una subtransacción usa una categoría de otro presupuesto
- **CUANDO** se crea o edita la transacción
- **ENTONCES** el sistema responde `404` y no modifica nada

#### Scenario: Categoría oculta permitida
- **DADO** una categoría oculta del presupuesto
- **CUANDO** se crea una transacción, o una subtransacción, con esa categoría
- **ENTONCES** el sistema responde `201` y la transacción queda con esa categoría

### Requirement: Cuenta cerrada
El sistema SHALL rechazar con `422` y código `REGLA_NEGOCIO_VIOLADA` crear o editar una
transacción en una cuenta cerrada.

#### Scenario: Crear en cuenta cerrada
- **DADO** una cuenta cerrada
- **CUANDO** se crea una transacción en ella
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no crea nada

#### Scenario: Editar en cuenta cerrada
- **DADO** una transacción existente cuya cuenta se cerró después
- **CUANDO** se edita
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no cambia nada

### Requirement: División en subtransacciones
Una transacción SHALL poder dividirse en subtransacciones (`categoriaId` opcional, `monto` con
signo distinto de 0 y `memo` de hasta 500 caracteres cada una). Si vienen subtransacciones
SHALL ser entre 2 y 20, la suma de sus montos SHALL ser exactamente el monto de la transacción
y la transacción no SHALL llevar `categoriaId` propio.

#### Scenario: División correcta
- **DADO** una transacción de `-30000` con subtransacciones de `-10000` y `-20000`
- **CUANDO** se crea
- **ENTONCES** el sistema responde `201` con `categoriaId` nulo y las dos subtransacciones, cada
  una con `id`, `categoriaId`, `monto` y `memo`

#### Scenario: Suma distinta del monto
- **DADO** una transacción de `-30000` con subtransacciones que suman `-25000`
- **CUANDO** se crea o edita
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no cambia nada

#### Scenario: Menos de dos subtransacciones
- **DADO** una lista con una sola subtransacción
- **CUANDO** se crea o edita
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Más de veinte subtransacciones
- **DADO** una lista con 21 subtransacciones
- **CUANDO** se crea o edita
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Veinte subtransacciones
- **DADO** una lista de 20 subtransacciones cuya suma es el monto
- **CUANDO** se crea
- **ENTONCES** el sistema responde `201`

#### Scenario: Categoría propia junto a subtransacciones
- **DADO** una petición con `categoriaId` y con subtransacciones
- **CUANDO** se crea o edita
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Subtransacción con monto cero
- **DADO** una subtransacción con `monto` `0`
- **CUANDO** se crea o edita la transacción
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Lista vacía equivale a sin división
- **DADO** una petición con `subtransacciones` vacía
- **CUANDO** se crea
- **ENTONCES** el sistema responde `201` como una transacción sin división

### Requirement: Consultar el detalle
El sistema SHALL devolver una transacción por su id con `id`, `cuentaId`, `fecha`, `monto`,
`categoriaId`, `beneficiario`, `beneficiarioId`, `memo`, `estado`, `aprobada`,
`subtransacciones`, `transaccionParId`, `programadaId`, `fechaCreacion` y `fechaActualizacion`.
`transaccionParId` SHALL ser el id de la otra pata si la transacción es parte de una
transferencia y `null` si no lo es. `beneficiarioId` SHALL ser el id del beneficiario vinculado
y `null` si no tiene. `programadaId` SHALL ser el id de la transacción programada que la generó
y `null` si no fue generada por una plantilla (o si la plantilla se borró). El listado SHALL
devolver los mismos campos en cada elemento.

#### Scenario: Detalle existente
- **DADO** una transacción del presupuesto
- **CUANDO** se consulta por su id
- **ENTONCES** el sistema responde `200` con todos esos campos, `transaccionParId` `null` y
  `programadaId` `null`

#### Scenario: Detalle con beneficiario vinculado
- **DADO** una transacción vinculada a un beneficiario
- **CUANDO** se consulta por su id y se lista
- **ENTONCES** ambas respuestas traen el mismo `beneficiarioId` y el `beneficiario` con el
  nombre del beneficiario

#### Scenario: Detalle de una pata de transferencia
- **DADO** una transferencia con su salida y su entrada
- **CUANDO** se consulta la salida por su id
- **ENTONCES** el sistema responde `200` con `transaccionParId` igual al id de la entrada

#### Scenario: Listado con patas de transferencia
- **DADO** una transferencia y una transacción normal
- **CUANDO** se lista
- **ENTONCES** las dos patas aparecen como transacciones normales, cada una con su
  `transaccionParId`, y la normal con `null`

#### Scenario: Detalle de una transacción generada por una plantilla
- **DADO** una transacción creada por el generador de una transacción programada
- **CUANDO** se consulta por su id y se lista
- **ENTONCES** ambas respuestas traen `programadaId` igual al id de la plantilla

### Requirement: Listado paginado y filtrado
El sistema SHALL listar las transacciones del presupuesto paginadas, con `page` (desde 0, por
defecto 0) y `size` (por defecto 20, entre 1 y 100), ordenadas por fecha descendente y luego
por id descendente, y SHALL responder con `contenido`, `pagina`, `tamano`, `totalElementos` y
`totalPaginas`. Un `page` o `size` fuera de rango SHALL responder `400` con código
`DATOS_INVALIDOS`. SHALL admitir los filtros opcionales y combinables `cuentaId`, `categoriaId`
(que también coincide con las subtransacciones), `desde` y `hasta` (inclusivos), `estado`,
`soloSinAprobar` y `q` (sin distinguir mayúsculas en beneficiario y memo, tratando `%` y `_`
como texto literal).

#### Scenario: Valores por defecto
- **DADO** 25 transacciones del presupuesto
- **CUANDO** se lista sin parámetros
- **ENTONCES** el sistema responde `200` con 20 elementos, `pagina` 0, `tamano` 20,
  `totalElementos` 25 y `totalPaginas` 2

#### Scenario: Última página
- **DADO** 25 transacciones
- **CUANDO** se lista con `page=1&size=20`
- **ENTONCES** el sistema responde con 5 elementos y `pagina` 1

#### Scenario: Página más allá del final
- **DADO** 25 transacciones
- **CUANDO** se lista con `page=5`
- **ENTONCES** el sistema responde `200` con `contenido` vacío y `totalElementos` 25

#### Scenario: Sin transacciones
- **DADO** un presupuesto sin transacciones
- **CUANDO** se lista
- **ENTONCES** el sistema responde `200` con `contenido` vacío, `totalElementos` 0 y
  `totalPaginas` 0

#### Scenario: Tamaño fuera de rango
- **DADO** `size=0`, `size=101` o `page=-1`
- **CUANDO** se lista
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Tamaño máximo
- **DADO** `size=100`
- **CUANDO** se lista
- **ENTONCES** el sistema responde `200` con `tamano` 100

#### Scenario: Orden
- **DADO** transacciones con distintas fechas y dos con la misma fecha
- **CUANDO** se lista
- **ENTONCES** vienen por fecha descendente y, con la misma fecha, por id descendente

#### Scenario: Filtro por cuenta
- **DADO** transacciones en dos cuentas
- **CUANDO** se lista con el `cuentaId` de una de ellas
- **ENTONCES** solo vienen las de esa cuenta

#### Scenario: Filtro por categoría
- **DADO** una transacción con la categoría X, otra dividida con una subtransacción en X y otra
  con la categoría Y
- **CUANDO** se lista con `categoriaId` X
- **ENTONCES** vienen la primera y la dividida, y no la de Y

#### Scenario: Filtro por rango de fechas
- **DADO** transacciones en distintas fechas
- **CUANDO** se lista con `desde` y `hasta`
- **ENTONCES** vienen las de fecha entre ambas, incluyendo los extremos

#### Scenario: Solo desde o solo hasta
- **DADO** transacciones en distintas fechas
- **CUANDO** se lista con solo `desde`, o con solo `hasta`
- **ENTONCES** el filtro se aplica solo por ese extremo

#### Scenario: Rango invertido
- **DADO** `desde` posterior a `hasta`
- **CUANDO** se lista
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Filtro por estado
- **DADO** transacciones en distintos estados
- **CUANDO** se lista con `estado=CONCILIADA`
- **ENTONCES** solo vienen las conciliadas

#### Scenario: Solo sin aprobar
- **DADO** transacciones aprobadas y sin aprobar
- **CUANDO** se lista con `soloSinAprobar=true`
- **ENTONCES** solo vienen las sin aprobar; con `false` o sin el parámetro vienen todas

#### Scenario: Búsqueda de texto
- **DADO** una transacción con beneficiario `Supermercado Norte` y otra con memo `compra norte`
- **CUANDO** se lista con `q=NORTE`
- **ENTONCES** vienen ambas, sin distinguir mayúsculas

#### Scenario: Comodines como texto literal
- **DADO** una transacción con memo `100% listo`, otra con memo `a_b` y otras sin `%` ni `_`
- **CUANDO** se lista con `q=%` o con `q=_`
- **ENTONCES** solo vienen las que contienen literalmente ese carácter

#### Scenario: Filtros combinados
- **DADO** transacciones variadas
- **CUANDO** se lista con cuenta, categoría, rango, estado, sin aprobar y texto a la vez
- **ENTONCES** vienen solo las que cumplen todos los filtros, y los totales reflejan el
  resultado filtrado

#### Scenario: Filtro con cuenta o categoría ajena
- **DADO** un `cuentaId` o `categoriaId` de otro presupuesto
- **CUANDO** se lista con ese filtro
- **ENTONCES** el sistema responde `404`

#### Scenario: Aislamiento del listado
- **DADO** transacciones en dos presupuestos
- **CUANDO** se lista uno
- **ENTONCES** solo vienen las de ese presupuesto

### Requirement: Parámetros de consulta inválidos
Un parámetro de consulta con un tipo inválido (por ejemplo `estado=XYZ`, `desde=ayer` o
`page=abc`) SHALL responder `400` con código `DATOS_INVALIDOS`, nunca `500`.

#### Scenario: Estado inválido
- **DADO** `estado=XYZ`
- **CUANDO** se lista
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Fecha de filtro inválida
- **DADO** `desde=ayer`
- **CUANDO** se lista
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Editar una transacción
El sistema SHALL permitir editar `fecha`, `monto`, `categoriaId`, `beneficiario`, `memo` y
`subtransacciones` (reemplazando las existentes), con las mismas reglas que al crear, y SHALL
responder `200`. La cuenta, el estado y `aprobada` no SHALL cambiar por esta ruta.

#### Scenario: Edición correcta
- **DADO** una transacción `NO_CONCILIADA`
- **CUANDO** se edita con nuevos valores válidos
- **ENTONCES** el sistema responde `200` con los valores nuevos, la misma cuenta, el mismo
  estado y `fechaActualizacion` actualizada

#### Scenario: Pasar de simple a dividida y viceversa
- **DADO** una transacción simple y otra dividida
- **CUANDO** se edita la primera con subtransacciones y la segunda sin ellas y con categoría
- **ENTONCES** las subtransacciones se reemplazan o eliminan y la categoría queda según lo
  enviado

#### Scenario: Reglas de validación al editar
- **DADO** una edición con monto `0`, con suma de subtransacciones distinta, o con categoría
  junto a subtransacciones
- **CUANDO** se edita
- **ENTONCES** el sistema responde `400` o `422` según corresponda y no cambia nada

### Requirement: Borrar una transacción
El sistema SHALL borrar una transacción con sus subtransacciones y responder `204`.

#### Scenario: Borrado correcto
- **DADO** una transacción dividida
- **CUANDO** se borra
- **ENTONCES** el sistema responde `204` y deja de existir, con sus subtransacciones

#### Scenario: Borrar dos veces
- **DADO** una transacción ya borrada
- **CUANDO** se vuelve a borrar
- **ENTONCES** el sistema responde `404`

### Requirement: Transacción reconciliada inmutable
Una transacción `RECONCILIADA` SHALL rechazar con `422` y código `REGLA_NEGOCIO_VIOLADA`
editarla, moverla de cuenta, borrarla y cambiar su estado.

#### Scenario: Editar una reconciliada
- **DADO** una transacción `RECONCILIADA`
- **CUANDO** se edita
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no cambia nada

#### Scenario: Mover una reconciliada
- **DADO** una transacción `RECONCILIADA`
- **CUANDO** se mueve a otra cuenta
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA`

#### Scenario: Borrar una reconciliada
- **DADO** una transacción `RECONCILIADA`
- **CUANDO** se borra
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y sigue existiendo

#### Scenario: Cambiar el estado de una reconciliada
- **DADO** una transacción `RECONCILIADA`
- **CUANDO** se intenta pasar a `NO_CONCILIADA` o a `CONCILIADA`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA`

### Requirement: Aprobar una transacción
El sistema SHALL marcar una transacción como aprobada, de forma idempotente, y responder `200`.

#### Scenario: Aprobar una sin aprobar
- **DADO** una transacción con `aprobada` `false`
- **CUANDO** se aprueba
- **ENTONCES** el sistema responde `200` con `aprobada` `true`

#### Scenario: Aprobar es idempotente
- **DADO** una transacción ya aprobada
- **CUANDO** se aprueba otra vez
- **ENTONCES** el sistema responde `200` con `aprobada` `true`

### Requirement: Cambiar el estado manualmente
El sistema SHALL permitir `PUT /{id}/estado` con `{ estado }` solo entre `NO_CONCILIADA` y
`CONCILIADA`, y responder `200`. Pasar a `RECONCILIADA` por la API SHALL responder `422`.

#### Scenario: Conciliar y desconciliar
- **DADO** una transacción `NO_CONCILIADA`
- **CUANDO** se pone `CONCILIADA` y luego `NO_CONCILIADA`
- **ENTONCES** el sistema responde `200` con cada estado

#### Scenario: Mismo estado
- **DADO** una transacción `CONCILIADA`
- **CUANDO** se pone `CONCILIADA`
- **ENTONCES** el sistema responde `200` sin cambios

#### Scenario: Pasar a reconciliada
- **DADO** una transacción no reconciliada
- **CUANDO** se pone `RECONCILIADA`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no cambia nada

#### Scenario: Estado ausente o inválido
- **DADO** un cuerpo sin `estado` o con un valor desconocido
- **CUANDO** se cambia el estado
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Mover una transacción de cuenta
El sistema SHALL mover una transacción a otra cuenta del mismo presupuesto con
`POST /{id}/mover-cuenta` y `{ cuentaId }`, siempre que la cuenta destino esté abierta, y
responder `200`.

#### Scenario: Movimiento correcto
- **DADO** una transacción y otra cuenta abierta del mismo presupuesto
- **CUANDO** se mueve
- **ENTONCES** el sistema responde `200` con el nuevo `cuentaId` y los saldos reflejan el cambio

#### Scenario: Cuenta destino de otro presupuesto
- **DADO** una cuenta de otro presupuesto
- **CUANDO** se mueve la transacción a ella
- **ENTONCES** el sistema responde `404` y la transacción no cambia

#### Scenario: Cuenta destino cerrada
- **DADO** una cuenta destino cerrada
- **CUANDO** se mueve la transacción a ella
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA`

#### Scenario: Mover desde una cuenta cerrada
- **DADO** una transacción en una cuenta cerrada y una cuenta destino abierta
- **CUANDO** se mueve
- **ENTONCES** el sistema responde `200` (mover la saca de la cuenta cerrada)

#### Scenario: Mover a la misma cuenta
- **DADO** una transacción en una cuenta abierta
- **CUANDO** se mueve a esa misma cuenta
- **ENTONCES** el sistema responde `200` sin cambios

#### Scenario: Cuenta ausente
- **DADO** un cuerpo sin `cuentaId`
- **CUANDO** se mueve
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Duplicar una transacción
El sistema SHALL duplicar una transacción con `POST /{id}/duplicar` y responder `201` con una
copia con la fecha de hoy, estado `NO_CONCILIADA`, `aprobada` `true`, y con copia de sus
subtransacciones. Cuenta, monto, categoría, beneficiario (texto y vínculo) y memo SHALL
mantenerse.

#### Scenario: Duplicado simple
- **DADO** una transacción `CONCILIADA` y no aprobada de hace un mes
- **CUANDO** se duplica
- **ENTONCES** el sistema responde `201` con un id nuevo, la fecha de hoy, estado
  `NO_CONCILIADA`, `aprobada` `true` y los demás campos iguales; la original no cambia

#### Scenario: Duplicado conserva el beneficiario
- **DADO** una transacción vinculada a un beneficiario
- **CUANDO** se duplica
- **ENTONCES** la copia tiene el mismo `beneficiario` y `beneficiarioId` y no se crea otro
  beneficiario

#### Scenario: Duplicado de una dividida
- **DADO** una transacción con subtransacciones
- **CUANDO** se duplica
- **ENTONCES** la copia tiene sus propias subtransacciones con ids nuevos y los mismos datos

#### Scenario: Duplicar una reconciliada
- **DADO** una transacción `RECONCILIADA`
- **CUANDO** se duplica
- **ENTONCES** el sistema responde `201` con la copia `NO_CONCILIADA`

#### Scenario: Duplicar en cuenta cerrada
- **DADO** una transacción en una cuenta cerrada
- **CUANDO** se duplica
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA`

### Requirement: Operaciones en lote
El sistema SHALL ofrecer `POST /lote` con `{ ids, operacion }`, donde `operacion` es
`CATEGORIZAR` (con `categoriaId`), `APROBAR` o `BORRAR`, y responder `200` con la cantidad
afectada. SHALL aceptar entre 1 y 100 ids y SHALL ser atómica: si falla alguna validación no
se aplica nada. `CATEGORIZAR` SHALL fijar además esa categoría como predeterminada de cada
beneficiario vinculado a las transacciones categorizadas.

#### Scenario: Categorizar
- **DADO** varias transacciones simples del presupuesto y una categoría del presupuesto
- **CUANDO** se envía `CATEGORIZAR` con esa categoría
- **ENTONCES** el sistema responde `200` con la cantidad y todas quedan con esa categoría

#### Scenario: Categorizar actualiza la categoría recordada
- **DADO** transacciones vinculadas a `"Netflix"` y `"Spotify"` y otra sin beneficiario
- **CUANDO** se envía `CATEGORIZAR` con la categoría `Ocio`
- **ENTONCES** `"Netflix"` y `"Spotify"` quedan con `Ocio` como categoría predeterminada y la
  transacción sin beneficiario solo se categoriza

#### Scenario: Categorizar sin categoría
- **DADO** una operación `CATEGORIZAR` sin `categoriaId`
- **CUANDO** se envía
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Categorizar con categoría ajena
- **DADO** una categoría de otro presupuesto
- **CUANDO** se envía `CATEGORIZAR` con ella
- **ENTONCES** el sistema responde `404` y no cambia nada

#### Scenario: Categorizar una dividida
- **DADO** entre los ids una transacción con subtransacciones
- **CUANDO** se envía `CATEGORIZAR`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no cambia ninguna,
  tampoco las categorías predeterminadas

#### Scenario: Aprobar en lote
- **DADO** varias transacciones sin aprobar, algunas ya aprobadas
- **CUANDO** se envía `APROBAR`
- **ENTONCES** el sistema responde `200` con la cantidad de ids y todas quedan aprobadas

#### Scenario: Borrar en lote
- **DADO** varias transacciones del presupuesto
- **CUANDO** se envía `BORRAR`
- **ENTONCES** el sistema responde `200` con la cantidad y dejan de existir

#### Scenario: Un id ajeno o inexistente
- **DADO** entre los ids uno de otro presupuesto, de otra persona o inexistente
- **CUANDO** se envía cualquier operación
- **ENTONCES** el sistema responde `404` y no se aplica nada a los demás

#### Scenario: Una reconciliada en el lote
- **DADO** entre los ids una transacción `RECONCILIADA`
- **CUANDO** se envía `CATEGORIZAR` o `BORRAR`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no se aplica nada

#### Scenario: Aprobar en lote con reconciliadas
- **DADO** entre los ids una transacción `RECONCILIADA` sin aprobar, igual que el aprobar
  individual
- **CUANDO** se envía `APROBAR`
- **ENTONCES** el sistema responde `200`, cuenta también la reconciliada y todas quedan aprobadas

#### Scenario: Ids repetidos
- **DADO** un mismo id repetido en `ids`
- **CUANDO** se envía una operación
- **ENTONCES** el sistema lo cuenta una sola vez

#### Scenario: Cantidad de ids u operación fuera de rango
- **DADO** una lista vacía, una con más de 100 ids, o una `operacion` ausente o desconocida
- **CUANDO** se envía
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

### Requirement: Saldos por cuenta
El sistema SHALL responder `GET /saldos` con, por cada cuenta del presupuesto (abiertas y
cerradas), `cuentaId`, `saldo` (saldo inicial más la suma de todas sus transacciones) y
`saldoConciliado` (saldo inicial más la suma de las `CONCILIADA` y `RECONCILIADA`), en
milésimas.

#### Scenario: Cuenta sin transacciones
- **DADO** una cuenta con saldo inicial `100000` y sin transacciones
- **CUANDO** se piden los saldos
- **ENTONCES** su `saldo` y su `saldoConciliado` son `100000`

#### Scenario: Cuenta con transacciones
- **DADO** una cuenta con saldo inicial `100000`, una `NO_CONCILIADA` de `-20000`, una
  `CONCILIADA` de `-10000` y una `RECONCILIADA` de `5000`
- **CUANDO** se piden los saldos
- **ENTONCES** `saldo` es `75000` y `saldoConciliado` es `95000`

#### Scenario: Cuenta cerrada
- **DADO** una cuenta cerrada con transacciones
- **CUANDO** se piden los saldos
- **ENTONCES** aparece con sus saldos calculados

#### Scenario: Sin cuentas
- **DADO** un presupuesto sin cuentas
- **CUANDO** se piden los saldos
- **ENTONCES** el sistema responde `200` con una lista vacía

#### Scenario: Aislamiento de saldos
- **DADO** transacciones en cuentas de otro presupuesto
- **CUANDO** se piden los saldos de este presupuesto
- **ENTONCES** solo cuentan las transacciones de las cuentas de este presupuesto

#### Scenario: Saldos con una transferencia
- **DADO** dos cuentas con saldos `100000` y `40000` y una transferencia de `30000` de la primera
  a la segunda
- **CUANDO** se piden los saldos
- **ENTONCES** la primera tiene `saldo` `70000`, la segunda `70000`, y la suma de los dos saldos
  sigue siendo `140000`

### Requirement: Patas de transferencia protegidas
Una transacción que es pata de una transferencia (`transaccionParId` no nulo) SHALL rechazar con
`422` y código `REGLA_NEGOCIO_VIOLADA`, con el mensaje "es parte de una transferencia; usa
/transferencias", editarla (`PUT`), borrarla (`DELETE`), moverla de cuenta y duplicarla; y en
`/lote` las operaciones `BORRAR` y `CATEGORIZAR`, de forma atómica como el resto del lote. Aprobar
(individual y en lote) y cambiar el estado entre `NO_CONCILIADA` y `CONCILIADA` SHALL seguir
permitidos por pata. Esta regla SHALL prevalecer sobre los requisitos de editar, borrar, mover,
duplicar y operar en lote.

#### Scenario: Editar o borrar una pata
- **DADO** una pata de transferencia
- **CUANDO** se llama a `PUT` o `DELETE` de `/transacciones/{id}` con su id
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y la transferencia no
  cambia

#### Scenario: Mover o duplicar una pata
- **DADO** una pata de transferencia
- **CUANDO** se mueve de cuenta o se duplica
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no se crea ni cambia
  nada

#### Scenario: Borrar o categorizar una pata en lote
- **DADO** entre los ids del lote una pata de transferencia y otras transacciones normales
- **CUANDO** se envía `BORRAR` o `CATEGORIZAR`
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA` y no se aplica nada a
  ninguna

#### Scenario: Aprobar una pata en lote
- **DADO** entre los ids del lote una pata de transferencia sin aprobar
- **CUANDO** se envía `APROBAR`
- **ENTONCES** el sistema responde `200` y la pata queda aprobada

#### Scenario: Aprobar y cambiar el estado de una pata
- **DADO** una pata de transferencia
- **CUANDO** se aprueba o se cambia su estado a `CONCILIADA`
- **ENTONCES** el sistema responde `200` y solo cambia esa pata

### Requirement: Beneficiario vinculado a la transacción
Al crear o editar una transacción con `beneficiario` (texto) no vacío, el sistema SHALL buscar
el beneficiario del presupuesto por su nombre sin distinguir mayúsculas y, si no existe,
crearlo sin categoría predeterminada; SHALL vincular la transacción a él y SHALL guardar como
texto `beneficiario` el nombre del beneficiario. Sin `beneficiario`, o vacío, la transacción SHALL
quedar sin vínculo y con `beneficiario` y `beneficiarioId` nulos. Las transferencias SHALL NOT
llevar beneficiario.

#### Scenario: Beneficiario nuevo
- **DADO** un presupuesto sin el beneficiario `"Netflix"`
- **CUANDO** se crea una transacción con `beneficiario` `"  Netflix "`
- **ENTONCES** el sistema responde `201` con `beneficiario` `"Netflix"` y un `beneficiarioId`, y
  el beneficiario `"Netflix"` existe en el presupuesto sin categoría predeterminada

#### Scenario: Beneficiario existente con otras mayúsculas
- **DADO** un beneficiario `"Netflix"` del presupuesto
- **CUANDO** se crea una transacción con `beneficiario` `"NETFLIX"`
- **ENTONCES** el sistema responde `201` con `beneficiario` `"Netflix"` y el `beneficiarioId` del
  beneficiario existente, sin crear otro

#### Scenario: Mismo nombre en otro presupuesto
- **DADO** un beneficiario `"Netflix"` en otro presupuesto de la persona
- **CUANDO** se crea una transacción con `beneficiario` `"Netflix"` en este presupuesto
- **ENTONCES** el sistema crea un beneficiario propio de este presupuesto y vincula la
  transacción a él

#### Scenario: Sin beneficiario
- **DADO** una transacción creada sin `beneficiario` o con `"   "`
- **CUANDO** se consulta
- **ENTONCES** tiene `beneficiario` y `beneficiarioId` nulos

#### Scenario: Editar cambia el vínculo
- **DADO** una transacción vinculada a `"Netflix"`
- **CUANDO** se edita con `beneficiario` `"Spotify"`
- **ENTONCES** el sistema responde `200` con `beneficiario` `"Spotify"` y el `beneficiarioId` de
  ese beneficiario, que se crea si no existía; `"Netflix"` no se borra

#### Scenario: Editar sin beneficiario quita el vínculo
- **DADO** una transacción vinculada a `"Netflix"`
- **CUANDO** se edita sin `beneficiario`
- **ENTONCES** el sistema responde `200` con `beneficiario` y `beneficiarioId` nulos

#### Scenario: Transacción existente sin vínculo
- **DADO** una transacción anterior con texto de beneficiario y sin vínculo
- **CUANDO** se consulta, se lista o se aprueba
- **ENTONCES** conserva su texto, `beneficiarioId` es nulo y no se crea ningún beneficiario

#### Scenario: Una transacción anterior se vincula al editarla
- **DADO** una transacción anterior con texto `"Tienda"` y sin vínculo
- **CUANDO** se edita con `beneficiario` `"Tienda"`
- **ENTONCES** queda vinculada a un beneficiario `"Tienda"`

#### Scenario: Mover de cuenta y cambiar el estado no tocan el vínculo
- **DADO** una transacción vinculada a un beneficiario
- **CUANDO** se mueve de cuenta, se aprueba o se cambia su estado
- **ENTONCES** su `beneficiario` y su `beneficiarioId` no cambian

#### Scenario: Una pata de transferencia no tiene beneficiario
- **DADO** una transferencia creada, con su salida y su entrada
- **CUANDO** se consulta cada pata por su id y en el listado de transacciones
- **ENTONCES** las dos patas tienen `beneficiario` y `beneficiarioId` nulos, y la transferencia
  no crea ningún beneficiario en el presupuesto

### Requirement: Categoría recordada del beneficiario
Cuando una transacción, al crearse o editarse, queda con `categoriaId` (y sin
subtransacciones) y con beneficiario vinculado, el sistema SHALL fijar esa categoría como la
categoría predeterminada del beneficiario, reemplazando la anterior. Una transacción dividida o
sin categoría SHALL NOT cambiar la categoría predeterminada.

#### Scenario: Al crear
- **DADO** un beneficiario `"Netflix"` sin categoría predeterminada y una categoría `Ocio`
- **CUANDO** se crea una transacción con `beneficiario` `"Netflix"` y `categoriaId` de `Ocio`
- **ENTONCES** `categoriaPredeterminadaId` de `"Netflix"` pasa a ser el id de `Ocio`

#### Scenario: Al editar
- **DADO** un beneficiario con categoría predeterminada `Ocio`
- **CUANDO** se edita una transacción suya con `categoriaId` de `Comida`
- **ENTONCES** `categoriaPredeterminadaId` pasa a ser el id de `Comida`

#### Scenario: Transacción dividida
- **DADO** un beneficiario con categoría predeterminada `Ocio`
- **CUANDO** se crea o edita una transacción suya con subtransacciones
- **ENTONCES** su categoría predeterminada sigue siendo `Ocio`

#### Scenario: Sin categoría
- **DADO** un beneficiario con categoría predeterminada `Ocio`
- **CUANDO** se crea una transacción suya sin `categoriaId`
- **ENTONCES** su categoría predeterminada sigue siendo `Ocio`

#### Scenario: Categoría ajena no deja efectos
- **DADO** una categoría de otro presupuesto
- **CUANDO** se crea una transacción con un beneficiario nuevo y esa categoría
- **ENTONCES** el sistema responde `404` y el beneficiario no se crea

### Requirement: Una categoría de pago de tarjeta no admite transacciones
El sistema SHALL responder `422` con `REGLA_NEGOCIO_VIOLADA` y no guardar nada cuando una
transacción, una subtransacción, una transferencia, una edición o una operación en lote
`CATEGORIZAR` use como categoría una categoría de pago de tarjeta. Filtrar el listado
por una categoría de pago SHALL seguir siendo válido y devolver una lista vacía.

#### Scenario: Crear una transacción en la categoría de pago
- **DADO** la categoría `Pago: Visa`
- **CUANDO** se crea una transacción con ese `categoriaId`
- **ENTONCES** el sistema responde `422` con `REGLA_NEGOCIO_VIOLADA`

#### Scenario: Subtransacción en la categoría de pago
- **DADO** una división donde una parte usa `Pago: Visa`
- **CUANDO** se crea la transacción
- **ENTONCES** el sistema responde `422` y no se guarda nada

#### Scenario: Editar o categorizar en lote
- **DADO** una transacción sin categoría
- **CUANDO** se edita, o se categoriza en lote, con `Pago: Visa`
- **ENTONCES** el sistema responde `422` y la transacción no cambia

#### Scenario: Transferencia con la categoría de pago
- **DADO** una transferencia del presupuesto a una cuenta externa, que exige categoría
- **CUANDO** se crea con el `categoriaId` de `Pago: Visa`
- **ENTONCES** el sistema responde `422`

#### Scenario: Filtrar por la categoría de pago
- **DADO** la categoría `Pago: Visa`
- **CUANDO** se lista con `categoriaId` igual a esa categoría
- **ENTONCES** el sistema responde `200` con la lista vacía

#### Scenario: Categoría de pago de otro presupuesto
- **DADO** una categoría de pago de otro presupuesto
- **CUANDO** se usa como categoría de una transacción
- **ENTONCES** el sistema responde `404`

### Requirement: Transacciones generadas por una plantilla
El sistema SHALL tratar una transacción generada por una plantilla como una transacción normal:
SHALL poder editarse, aprobarse, conciliarse y borrarse con los mismos endpoints y reglas.
Nace con estado `NO_CONCILIADA`, `aprobada` en `false` y la fecha de su ocurrencia.

#### Scenario: Aprobar una generada
- **DADO** una transacción generada con `aprobada` en `false`
- **CUANDO** se aprueba con `POST /transacciones/{id}/aprobar`
- **ENTONCES** el sistema responde `200` con `aprobada` en `true` y conserva su `programadaId`

#### Scenario: Borrar una generada no afecta la plantilla
- **DADO** una transacción generada por una plantilla
- **CUANDO** se borra con `DELETE /transacciones/{id}`
- **ENTONCES** el sistema responde `204` y la plantilla conserva su `proximaFecha`
- **Y** la ocurrencia borrada no se vuelve a generar en ninguna ejecución posterior del generador

#### Scenario: Las transacciones manuales no cambian
- **DADO** una transacción creada con `POST /transacciones`
- **CUANDO** se consulta
- **ENTONCES** `programadaId` es `null` y el resto de sus campos no cambia
