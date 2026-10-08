# transacciones-programadas Specification

## Purpose

Permite definir plantillas de transacciones recurrentes por presupuesto y generar de forma
automática, idempotente y sin duplicados las transacciones reales de cada ocurrencia vencida.

## Requirements

### Requirement: Autenticación y aislamiento por presupuesto
Todas las rutas bajo `/api/v1/presupuestos/{presupuestoId}/transacciones-programadas` SHALL exigir
autenticación (401 sin token válido) y validar primero que el presupuesto sea de la persona. Un
presupuesto inexistente o ajeno, o una plantilla de otro presupuesto, SHALL responder `404`,
nunca `403`.

#### Scenario: Sin token
- **CUANDO** se llama a cualquier ruta sin token
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

#### Scenario: Presupuesto de otra persona
- **DADO** un presupuesto de otra persona
- **CUANDO** se lista, crea, consulta, pausa o genera
- **ENTONCES** el sistema responde `404`

#### Scenario: Plantilla de otro presupuesto
- **DADO** una plantilla de otro presupuesto de la misma persona
- **CUANDO** se consulta, edita, borra, pausa o reanuda con la URL del primero
- **ENTONCES** el sistema responde `404`

### Requirement: Crear una transacción programada
El sistema SHALL crear con `POST` una plantilla con `cuentaId`, `fechaInicio`, `frecuencia`,
`monto` (milésimas con signo, no cero) y opcionalmente `fechaFin`, `categoriaId`, `beneficiario`
(máx. 100) y `memo` (máx. 500). Nace activa, con `proximaFecha` igual a `fechaInicio`, sin error,
y responde `201`. Crearla NO genera transacciones, aunque `fechaInicio` sea pasada.

#### Scenario: Creación mínima
- **CUANDO** se crea con cuenta, `fechaInicio`, `frecuencia` y `monto` válidos
- **ENTONCES** el sistema responde `201` con `activa` en `true`, `proximaFecha` igual a
  `fechaInicio`, `ultimoError` nulo, y no crea ninguna transacción

#### Scenario: Fecha de inicio pasada
- **DADO** que hoy es 2026-10-10
- **CUANDO** se crea una plantilla mensual con `fechaInicio` 2026-07-05
- **ENTONCES** el sistema responde `201` con `proximaFecha` 2026-07-05 y el presupuesto no
  tiene transacciones nuevas hasta que corra el generador

#### Scenario: Texto normalizado
- **CUANDO** se envían `beneficiario` y `memo` con espacios alrededor o vacíos
- **ENTONCES** se guardan recortados y los vacíos quedan en `null`

#### Scenario: Monto cero o datos inválidos
- **CUANDO** el `monto` es 0, falta un campo obligatorio, la `frecuencia` no existe, el
  `beneficiario` supera 100 caracteres, el `memo` supera 500 o el cuerpo es ilegible
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Fecha de fin anterior al inicio
- **CUANDO** `fechaFin` es anterior a `fechaInicio`
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Fecha de fin igual al inicio
- **CUANDO** `fechaFin` es igual a `fechaInicio`
- **ENTONCES** el sistema responde `201` y la plantilla tiene una sola ocurrencia

### Requirement: Cuenta y categoría de la plantilla
La cuenta y la categoría SHALL pertenecer al presupuesto de la URL. El orden de validación
SHALL ser: presupuesto (404), cuenta (404), categoría (404) y después las reglas de negocio
(cuenta cerrada 422, categoría de pago de tarjeta 422). Una categoría oculta SHALL permitirse.
Las plantillas no admiten subtransacciones ni transferencias.

#### Scenario: Cuenta o categoría ajenas
- **CUANDO** la cuenta o la categoría son de otro presupuesto o no existen
- **ENTONCES** el sistema responde `404`

#### Scenario: Cuenta cerrada
- **CUANDO** se crea una plantilla en una cuenta cerrada
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA`

#### Scenario: Categoría de pago de tarjeta
- **CUANDO** se crea o edita una plantilla con una categoría de pago de tarjeta
- **ENTONCES** el sistema responde `422` con código `REGLA_NEGOCIO_VIOLADA`

#### Scenario: Categoría ajena con cuenta cerrada
- **CUANDO** la categoría es ajena y la cuenta está cerrada
- **ENTONCES** el sistema responde `404` (los 404 van antes que los 422)

### Requirement: Calendario de ocurrencias
El sistema SHALL calcular la ocurrencia `n` (desde 0) de una plantilla siempre desde
`fechaInicio`, sin encadenar: `DIARIA` suma `n` días, `SEMANAL` `7n` días, `CADA_2_SEMANAS` `14n`
días, `MENSUAL` `n` meses, `CADA_3_MESES` `3n` meses y `ANUAL` `n` años. En las mensuales y
anuales, si el día no existe en el mes resultante SHALL usarse el último día de ese mes, y el
día original se recupera en los meses que lo tienen.

#### Scenario: Día 31 mensual
- **DADO** una plantilla `MENSUAL` con `fechaInicio` 2027-01-31
- **ENTONCES** sus ocurrencias son 2027-01-31, 2027-02-28, 2027-03-31, 2027-04-30 y 2027-05-31
  (en 2028, año bisiesto, febrero cae el 29)

#### Scenario: Febrero en año bisiesto
- **DADO** una plantilla `MENSUAL` con `fechaInicio` 2028-01-31
- **ENTONCES** la ocurrencia 1 es 2028-02-29 y la 2 es 2028-03-31

#### Scenario: Día 29 y 30
- **DADO** plantillas `MENSUAL` con `fechaInicio` 2027-01-29 y 2027-01-30
- **ENTONCES** en febrero de 2027 ambas caen el 28 y en marzo recuperan el 29 y el 30

#### Scenario: Cada 3 meses desde el 30 de noviembre
- **DADO** una `CADA_3_MESES` con inicio 2027-11-30
- **ENTONCES** sus ocurrencias son 2027-11-30, 2028-02-29 y 2028-05-30

#### Scenario: Cada 3 meses desde el 31 de enero
- **DADO** una `CADA_3_MESES` con inicio 2027-01-31
- **ENTONCES** sus ocurrencias son 2027-01-31, 2027-04-30, 2027-07-31 y 2027-10-31

#### Scenario: Anual desde el 29 de febrero de 2028
- **DADO** una `ANUAL` con inicio 2028-02-29
- **ENTONCES** sus ocurrencias son 2028-02-29, 2029-02-28, 2030-02-28, 2031-02-28 y 2032-02-29

#### Scenario: Semanales y diarias
- **DADO** inicio 2026-10-01
- **ENTONCES** `DIARIA` da 10-01, 10-02; `SEMANAL` 10-01, 10-08; `CADA_2_SEMANAS` 10-01, 10-15

### Requirement: Consultar transacciones programadas
El sistema SHALL listar con `GET` las plantillas del presupuesto sin paginar, ordenadas por
`proximaFecha` ascendente (las finalizadas al final) y luego por id, con `?soloActivas=true`
para excluir las pausadas. `GET /{id}` devuelve una plantilla. Cada una incluye `id`, `cuentaId`,
`fechaInicio`, `frecuencia`, `fechaFin`, `monto`, `categoriaId`, `beneficiario`, `memo`,
`activa`, `proximaFecha`, `ultimoError`, `fechaCreacion` y `fechaActualizacion`.
`proximaFecha` SHALL ser `null` cuando ya no quedan ocurrencias por generar.

#### Scenario: Listar todas y solo activas
- **DADO** una plantilla activa y una pausada
- **CUANDO** se lista sin filtro y con `?soloActivas=true`
- **ENTONCES** la primera respuesta trae ambas y la segunda solo la activa

#### Scenario: Plantilla con error
- **DADO** una plantilla cuya generación falló
- **CUANDO** se consulta por id y se lista
- **ENTONCES** ambas respuestas traen `ultimoError` con el motivo y la `proximaFecha` sin avanzar

#### Scenario: Plantilla inexistente
- **CUANDO** se consulta un id que no existe
- **ENTONCES** el sistema responde `404`

### Requirement: Editar una transacción programada
El sistema SHALL permitir con `PUT` cambiar `monto`, `categoriaId`, `memo`, `beneficiario`,
`frecuencia` y `fechaFin`. NO SHALL permitir cambiar la cuenta ni `fechaInicio`; el cambio solo
afecta a las ocurrencias futuras y no modifica transacciones ya generadas. Si la plantilla está
activa y cambia la `frecuencia` (o se extiende `fechaFin` sobre una finalizada), `proximaFecha`
SHALL recalcularse desde `fechaInicio` como la primera ocurrencia posterior a la última generada
(o `fechaInicio` si no se generó ninguna), o `null` si supera `fechaFin`.

#### Scenario: Editar monto y memo
- **DADO** una plantilla con transacciones ya generadas
- **CUANDO** se edita el `monto` y el `memo`
- **ENTONCES** la plantilla responde con los nuevos valores y las transacciones generadas no cambian

#### Scenario: Cambiar la frecuencia
- **DADO** una plantilla `MENSUAL` desde 2026-01-15 cuya última ocurrencia generada es 2026-09-15
- **CUANDO** se cambia a `SEMANAL`
- **ENTONCES** `proximaFecha` es 2026-09-17 (primera ocurrencia semanal posterior al 09-15: ambos son jueves, porque 2026-01-15 es jueves)

#### Scenario: Cambiar la frecuencia de una plantilla pausada
- **DADO** una plantilla pausada con `proximaFecha` 2026-06-15
- **CUANDO** se cambia la `frecuencia` con `PUT`
- **ENTONCES** la plantilla sigue pausada, `proximaFecha` no se recalcula (sigue 2026-06-15) y se
  recalcula recién al reanudar

#### Scenario: Extender la fecha de fin de una finalizada
- **DADO** una plantilla finalizada (`proximaFecha` nula) con `fechaFin` 2026-09-15
- **CUANDO** se cambia `fechaFin` a 2026-12-15
- **ENTONCES** `proximaFecha` pasa a la primera ocurrencia posterior a la última generada

#### Scenario: La cuenta no se cambia
- **CUANDO** el cuerpo trae `cuentaId`
- **ENTONCES** el sistema ignora el campo y la plantilla conserva su cuenta

#### Scenario: La fecha de inicio no se cambia
- **CUANDO** el cuerpo trae `fechaInicio` distinta de la guardada
- **ENTONCES** el sistema responde `200`, ignora el campo, conserva `fechaInicio` y no recalcula
  `proximaFecha` por ese motivo

#### Scenario: Validaciones de edición
- **CUANDO** el monto es 0, `fechaFin` es anterior a `fechaInicio`, la categoría es de pago de
  tarjeta o la categoría es ajena
- **ENTONCES** el sistema responde `400`, `400`, `422` y `404` respectivamente

### Requirement: Borrar una transacción programada
El sistema SHALL borrar con `DELETE` la plantilla y responder `204`. Las transacciones ya
generadas SHALL conservarse con `programadaId` en `null`.

#### Scenario: Borrar conserva lo generado
- **DADO** una plantilla con tres transacciones generadas
- **CUANDO** se borra
- **ENTONCES** el sistema responde `204`, las tres transacciones siguen existiendo y su
  `programadaId` es `null`

### Requirement: Pausar y reanudar
`POST /{id}/pausar` SHALL dejar la plantilla inactiva (el generador la ignora) y
`POST /{id}/reanudar` SHALL activarla, limpiar `ultimoError` y fijar `proximaFecha` en la
primera ocurrencia (según su calendario) igual o posterior a hoy y posterior a la última
generada, o `null` si supera `fechaFin`. Reanudar NO genera las ocurrencias del periodo pausado.
Ambas son idempotentes y responden `200` con la plantilla.

#### Scenario: Pausada no genera
- **DADO** una plantilla pausada con ocurrencias vencidas
- **CUANDO** corre el generador
- **ENTONCES** no se crea ninguna transacción

#### Scenario: Reanudar salta lo perdido
- **DADO** una plantilla `MENSUAL` el día 5 pausada desde junio y que hoy es 2026-10-10
- **CUANDO** se reanuda
- **ENTONCES** `proximaFecha` es 2026-11-05 y no se genera ninguna transacción de junio a octubre

#### Scenario: Reanudar con ocurrencia hoy
- **DADO** una plantilla pausada cuya ocurrencia cae hoy
- **CUANDO** se reanuda
- **ENTONCES** `proximaFecha` es hoy, la respuesta del reanudar no crea ninguna transacción, y la
  transacción de hoy se crea en la siguiente ejecución del generador

#### Scenario: Pausar y reanudar repetidos
- **CUANDO** se pausa una pausada o se reanuda una activa
- **ENTONCES** el sistema responde `200` sin error

### Requirement: Generación de transacciones vencidas
El generador SHALL crear, para cada plantilla activa con `proximaFecha` menor o igual a la
fecha límite (hoy según el reloj del sistema), una transacción por cada ocurrencia vencida con la
fecha original de la ocurrencia, y avanzar `proximaFecha` hasta la primera ocurrencia posterior a
la límite (o `null` si supera `fechaFin`). Crea como máximo 366 ocurrencias por plantilla y
ejecución; el resto queda para la siguiente. Cada transacción generada nace `NO_CONCILIADA` y
`aprobada` en `false`, con `cuentaId`, `monto`, `categoriaId`, `beneficiario` y `memo` de la
plantilla, y aplica las mismas reglas que una transacción manual (beneficiario creado si no
existía, categoría recordada). Ejecutarlo de nuevo con la misma fecha SHALL no crear nada.

#### Scenario: Una ocurrencia vencida
- **DADO** una plantilla `MENSUAL` con `proximaFecha` 2026-10-05 y hoy 2026-10-05
- **CUANDO** corre el generador
- **ENTONCES** se crea una transacción del 2026-10-05, `NO_CONCILIADA`, `aprobada` falsa, con
  `programadaId` de la plantilla, y `proximaFecha` pasa a 2026-11-05

#### Scenario: Ocurrencia futura
- **DADO** una plantilla con `proximaFecha` mañana
- **CUANDO** corre el generador
- **ENTONCES** no se crea nada

#### Scenario: Ocurrencias perdidas
- **DADO** una plantilla `SEMANAL` con `proximaFecha` 2026-09-12 y hoy 2026-10-10 (backend apagado)
- **CUANDO** corre el generador
- **ENTONCES** se crean transacciones con fechas 09-12, 09-19, 09-26, 10-03 y 10-10, cada una con
  su fecha original, y `proximaFecha` pasa a 2026-10-17

#### Scenario: Tope de 366 por ejecución
- **DADO** una plantilla `DIARIA` con 400 ocurrencias vencidas
- **CUANDO** corre el generador
- **ENTONCES** se crean exactamente 366 transacciones, las más antiguas, y una segunda ejecución
  crea las 34 restantes

#### Scenario: Fin de mes
- **DADO** una plantilla `MENSUAL` con `fechaInicio` 2027-01-31 y hoy 2027-03-31
- **CUANDO** corre el generador
- **ENTONCES** se crean transacciones del 01-31, 02-28 y 03-31

#### Scenario: Fecha de fin
- **DADO** una plantilla con `fechaFin` 2026-10-05 y hoy 2026-10-20
- **CUANDO** corre el generador
- **ENTONCES** no se crea ninguna ocurrencia posterior al 2026-10-05 y `proximaFecha` queda en `null`

#### Scenario: Idempotencia
- **CUANDO** el generador corre dos veces con la misma fecha
- **ENTONCES** la segunda ejecución no crea ninguna transacción

#### Scenario: Generación manual acotada al presupuesto
- **DADO** una plantilla de otro presupuesto con ocurrencias vencidas
- **CUANDO** se llama a `POST /generar` de este presupuesto
- **ENTONCES** solo se generan las de este presupuesto

### Requirement: Generación sin duplicados y tolerante a fallos
Cada plantilla SHALL procesarse en su propia transacción de base de datos. La combinación
(plantilla, fecha de ocurrencia) SHALL ser única en las transacciones, de modo que dos
ejecuciones simultáneas no dupliquen. El fallo de una plantilla SHALL registrarse y no impedir
procesar las demás.

#### Scenario: Fallo de una plantilla no afecta otra
- **DADO** dos plantillas vencidas, una en una cuenta que luego se cerró
- **CUANDO** corre el generador
- **ENTONCES** la plantilla válida genera sus transacciones y la otra no genera ninguna

#### Scenario: Ocurrencia ya existente
- **DADO** que ya existe una transacción de la plantilla para esa fecha de ocurrencia
- **CUANDO** corre el generador
- **ENTONCES** no se crea un duplicado y `proximaFecha` avanza

#### Scenario: Ejecuciones simultáneas
- **DADO** dos ejecuciones del generador al mismo tiempo sobre la misma plantilla
- **CUANDO** ambas terminan
- **ENTONCES** existe exactamente una transacción por ocurrencia. Esto se garantiza con la
  omisión de ocurrencias ya existentes y la restricción única (plantilla, fecha de ocurrencia);
  la carrera real entre dos instancias es un riesgo aceptado y no tiene test automatizado
  (los tests cubren la omisión por existencia y la restricción única por separado)

### Requirement: Errores de generación visibles
Si una validación falla al generar (cuenta cerrada después, categoría que pasó a ser de pago de
tarjeta, etc.) la plantilla SHALL conservar su `proximaFecha`, no generar ninguna ocurrencia de
esa ejecución y exponer el motivo en `ultimoError`. En una ejecución posterior exitosa,
`ultimoError` SHALL quedar en `null`. La plantilla nunca se desactiva ni se borra por un error.

#### Scenario: Cuenta cerrada después
- **DADO** una plantilla en una cuenta que se cerró
- **CUANDO** corre el generador
- **ENTONCES** no se crea ninguna transacción, `proximaFecha` no avanza y `ultimoError` dice que
  la cuenta está cerrada

#### Scenario: Recuperación
- **DADO** una plantilla con `ultimoError` cuya cuenta se reabrió
- **CUANDO** corre el generador
- **ENTONCES** se generan las ocurrencias pendientes y `ultimoError` queda en `null`

### Requirement: Cuándo corre el generador
El generador SHALL ejecutarse para todos los presupuestos al arrancar la aplicación y una vez al
día a la hora configurada por propiedad, y SHALL poder desactivarse por propiedad (los tests lo
usan). `POST /transacciones-programadas/generar` SHALL ejecutarlo solo para el presupuesto de la
URL, con la fecha de hoy, y responder `200` con la cantidad de transacciones generadas y de
plantillas con error.

#### Scenario: Generación manual
- **DADO** una plantilla con dos ocurrencias vencidas
- **CUANDO** se llama a `POST /generar`
- **ENTONCES** el sistema responde `200` con `generadas` 2 y `plantillasConError` 0

#### Scenario: Generar en un presupuesto ajeno
- **DADO** un presupuesto de otra persona o inexistente
- **CUANDO** se llama a `POST /generar`
- **ENTONCES** el sistema valida primero el presupuesto de la URL, responde `404` y no genera nada

#### Scenario: Dos presupuestos con plantillas vencidas
- **DADO** dos presupuestos de la misma persona, cada uno con una plantilla vencida
- **CUANDO** se llama a `POST /generar` con el id del primero
- **ENTONCES** solo se generan las transacciones del primero y la plantilla del segundo conserva
  su `proximaFecha` y no tiene transacciones nuevas

#### Scenario: Generación al arrancar
- **DADO** el generador habilitado y plantillas vencidas
- **CUANDO** la aplicación arranca
- **ENTONCES** se generan las ocurrencias vencidas de todos los presupuestos

#### Scenario: Generador deshabilitado
- **DADO** la propiedad que lo deshabilita
- **CUANDO** la aplicación arranca
- **ENTONCES** no se genera nada automáticamente (el endpoint manual sigue disponible)
