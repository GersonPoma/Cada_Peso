# Spec Delta

## Purpose
Permite conciliar una cuenta contra el saldo de su extracto bancario: ver la diferencia, cerrar
la conciliación con un ajuste opcional, reconciliar las transacciones conciliadas hasta la fecha
del extracto y consultar el historial, sin que nadie más pueda verlo ni tocarlo.

## ADDED Requirements

### Requirement: Autenticación, aislamiento y orden de errores
El sistema SHALL exigir autenticación en todas las rutas de
`/api/v1/presupuestos/{presupuestoId}/cuentas/{cuentaId}/conciliacion`. En cada operación SHALL
validar primero el presupuesto, luego la cuenta dentro de él, luego la petición (`400`) y por
último las reglas de negocio (`422`). Un presupuesto o una cuenta inexistente o ajena SHALL
responder `404` con código `RECURSO_NO_ENCONTRADO`, nunca `403`.

#### Scenario: Petición sin token
- **DADO** una petición sin token de acceso
- **CUANDO** se llama a cualquier ruta de conciliación
- **ENTONCES** el sistema responde `401` con código `NO_AUTENTICADO`

#### Scenario: Presupuesto o cuenta ajenos
- **DADO** una cuenta de un presupuesto de otra persona, o de otro presupuesto propio
- **CUANDO** se llama a cualquier ruta de conciliación con ella
- **ENTONCES** el sistema responde `404` y no revela ni cambia nada

#### Scenario: Cuenta inexistente con cuerpo inválido
- **DADO** una cuenta inexistente
- **CUANDO** se llama a `POST` con un cuerpo inválido
- **ENTONCES** el sistema responde `404`, no `400`

### Requirement: Consultar el estado de la conciliación
El sistema SHALL ofrecer `GET .../conciliacion/estado?saldoExtracto=&fecha=` que no guarda nada.
`saldoExtracto` (milésimas, puede ser negativo) y `fecha` (`yyyy-MM-dd`, la del extracto) son
obligatorios; si falta alguno o `fecha` es posterior a hoy responde `400` `DATOS_INVALIDOS`. La
respuesta SHALL incluir `saldoConciliado` (el actual de la cuenta, igual al de
`GET /transacciones/saldos`), `saldoConciliadoAlCorte`, `saldoExtracto`, `fecha`,
`diferencia = saldoExtracto - saldoConciliadoAlCorte` y las transacciones `NO_CONCILIADA` de la
cuenta (como máximo 100, de la más reciente a la más antigua, con `totalNoConciliadas`).
`saldoConciliadoAlCorte` es `saldoInicial` más la suma de las transacciones `CONCILIADA` y
`RECONCILIADA` con fecha menor o igual a `fecha`.

#### Scenario: Diferencia con una transacción conciliada posterior
- **DADO** una cuenta con saldo inicial 500.000, conciliadas de -120.000 (03-oct) y -80.000
  (05-oct) y una conciliada de -50.000 (12-oct)
- **CUANDO** se consulta el estado con `saldoExtracto=300000` y `fecha=2026-10-10`
- **ENTONCES** `saldoConciliado` es 250.000, `saldoConciliadoAlCorte` es 300.000 y `diferencia`
  es 0

#### Scenario: Diferencia cero tras conciliar con ajuste
- **DADO** una conciliación con ajuste ya creada para un `saldoExtracto` y una `fecha`
- **CUANDO** se consulta el estado con ese mismo `saldoExtracto` y `fecha`
- **ENTONCES** `diferencia` es 0

#### Scenario: Solo lectura
- **CUANDO** se consulta el estado con cualquier valor
- **ENTONCES** no se crea ninguna transacción ni registro y ningún estado cambia

#### Scenario: Parámetro faltante o fecha futura
- **CUANDO** falta `saldoExtracto` o `fecha`, o `fecha` es posterior a hoy
- **ENTONCES** el sistema responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Lista acotada
- **DADO** una cuenta con 120 transacciones `NO_CONCILIADA`
- **CUANDO** se consulta el estado
- **ENTONCES** la lista trae 100 y `totalNoConciliadas` es 120

### Requirement: Crear una conciliación
El sistema SHALL ofrecer `POST .../conciliacion` con `saldoExtracto`, `fecha`, `crearAjuste`
(opcional, falso por defecto) y `categoriaId` (opcional). En una sola transacción de base de
datos SHALL calcular la `diferencia` como en el estado; si no es cero y `crearAjuste` es
verdadero, crear una transacción de ajuste; pasar a `RECONCILIADA` toda transacción
`CONCILIADA` de la cuenta con fecha menor o igual a `fecha`; y guardar el registro. Responde
`201` con el registro (`id`, `cuentaId`, `fecha`, `saldoExtracto`, `ajuste`,
`transaccionAjusteId`, `cantidadReconciliadas`, `fechaCreacion`). Las transacciones `CONCILIADA`
con fecha posterior NO SHALL reconciliarse. El ajuste es una transacción por la `diferencia`, con
fecha igual a la del extracto, beneficiario "Ajuste de conciliación", estado `CONCILIADA` y
aprobada.

#### Scenario: Diferencia cero
- **DADO** la cuenta del escenario anterior
- **CUANDO** se crea la conciliación con `saldoExtracto=300000` y `fecha=2026-10-10`
- **ENTONCES** responde `201` con `ajuste` 0, `transaccionAjusteId` nulo y
  `cantidadReconciliadas` 2; las dos primeras quedan `RECONCILIADA` y la del 12-oct sigue
  `CONCILIADA`

#### Scenario: Diferencia con ajuste
- **DADO** la misma cuenta y un extracto de 310.000 al 10-oct
- **CUANDO** se crea con `crearAjuste=true`
- **ENTONCES** se crea una transacción de +10.000 del 2026-10-10, `ajuste` es 10.000,
  `cantidadReconciliadas` es 3 (las dos y el ajuste) y el saldo conciliado al corte es 310.000

#### Scenario: Diferencia sin pedir ajuste
- **DADO** una diferencia distinta de cero
- **CUANDO** se crea sin `crearAjuste` o con `crearAjuste=false`
- **ENTONCES** responde `422` `REGLA_NEGOCIO_VIOLADA` y no cambia nada (ni transacciones ni
  registro)

#### Scenario: Pedir ajuste sin diferencia
- **DADO** una diferencia cero
- **CUANDO** se crea con `crearAjuste=true`
- **ENTONCES** no se crea ninguna transacción de ajuste y `ajuste` es 0

#### Scenario: Cuerpo inválido
- **CUANDO** falta `saldoExtracto` o `fecha`, o `fecha` es posterior a hoy
- **ENTONCES** responde `400` con código `DATOS_INVALIDOS`

#### Scenario: Cuenta cerrada
- **DADO** una cuenta cerrada
- **CUANDO** se intenta crear una conciliación
- **ENTONCES** responde `422`; el estado y el historial siguen siendo consultables

#### Scenario: Tarjeta de crédito y cuenta fuera del presupuesto
- **DADO** una tarjeta de crédito (saldo negativo) o una cuenta con `enPresupuesto=false`
- **CUANDO** se concilia
- **ENTONCES** se permite con las mismas reglas de saldo y reconciliación

#### Scenario: Transacción reconciliada de fecha posterior
- **DADO** una cuenta con una transacción `RECONCILIADA` de fecha posterior a la del extracto
- **CUANDO** se consulta el estado o se crea una conciliación con esa fecha de extracto
- **ENTONCES** esa transacción no entra en `saldoConciliadoAlCorte` ni en la `diferencia`, no se
  cuenta en `cantidadReconciliadas` y sigue `RECONCILIADA` sin cambios

#### Scenario: Otras transacciones no cambian
- **CUANDO** se concilia una cuenta
- **ENTONCES** las transacciones de otras cuentas y las `NO_CONCILIADA` de esta no cambian

### Requirement: Categoría del ajuste para conservar el dinero del presupuesto
El ajuste SHALL ir sin categoría salvo que se envíe `categoriaId`. En una cuenta del presupuesto,
un ajuste negativo, o uno positivo en una tarjeta de crédito, SHALL exigir `categoriaId` (`422`
si falta), porque sin categoría no afectaría `listoParaAsignar` ni ningún disponible y el dinero
en cuentas dejaría de ser igual a `listoParaAsignar` más la suma de los disponibles. Un ajuste
positivo en una cuenta del presupuesto que no es tarjeta puede ir sin categoría y cuenta como
ingreso. En una cuenta fuera del presupuesto no se acepta `categoriaId` (`422`). Una categoría de
pago de tarjeta se rechaza (`422`) y una inexistente o ajena responde `404`.

#### Scenario: Ajuste positivo sin categoría
- **DADO** una cuenta del presupuesto, no tarjeta, y una diferencia de +10.000
- **CUANDO** se crea con `crearAjuste=true` sin categoría
- **ENTONCES** `listoParaAsignar` sube 10.000 y el invariante se mantiene

#### Scenario: Ajuste negativo sin categoría
- **DADO** una cuenta del presupuesto y una diferencia de -10.000
- **CUANDO** se crea con `crearAjuste=true` sin `categoriaId`
- **ENTONCES** responde `422` y no cambia nada

#### Scenario: Ajuste negativo con categoría
- **DADO** la misma diferencia y una categoría con disponible 80.000
- **CUANDO** se crea con `crearAjuste=true` y esa `categoriaId`
- **ENTONCES** el disponible baja a 70.000 y el invariante se mantiene

#### Scenario: Ajuste positivo con categoría en cuenta del presupuesto
- **DADO** una cuenta del presupuesto, una diferencia de +10.000 y una categoría con disponible
  80.000
- **CUANDO** se crea con `crearAjuste=true` y esa `categoriaId`
- **ENTONCES** se permite, el disponible de la categoría sube a 90.000 y `listoParaAsignar` no
  cambia

#### Scenario: Ajuste positivo en tarjeta de crédito sin categoría
- **DADO** una tarjeta de crédito del presupuesto y una diferencia de +10.000
- **CUANDO** se crea con `crearAjuste=true` sin `categoriaId`
- **ENTONCES** responde `422` `REGLA_NEGOCIO_VIOLADA` y no cambia nada

#### Scenario: Ajuste con categoría de pago de tarjeta
- **DADO** una diferencia distinta de cero y la categoría de pago de una tarjeta
- **CUANDO** se crea con `crearAjuste=true` y esa `categoriaId`
- **ENTONCES** responde `422` `REGLA_NEGOCIO_VIOLADA` y no cambia nada (ni ajuste, ni estados, ni
  registro)

#### Scenario: Ajuste con categoría ajena o inexistente
- **DADO** una diferencia distinta de cero y una categoría de otro presupuesto, o que no existe
- **CUANDO** se crea con `crearAjuste=true` y esa `categoriaId`
- **ENTONCES** responde `404` `RECURSO_NO_ENCONTRADO` y no cambia nada (ni ajuste, ni estados, ni
  registro)

#### Scenario: Categoría en cuenta fuera del presupuesto
- **CUANDO** se envía `categoriaId` para una cuenta con `enPresupuesto=false`
- **ENTONCES** responde `422`

### Requirement: Concurrencia
Dos conciliaciones simultáneas de la misma cuenta SHALL serializarse y no SHALL duplicar el
ajuste: la segunda recalcula la diferencia con lo ya reconciliado.

#### Scenario: Segunda conciliación idéntica
- **DADO** una conciliación con ajuste ya creada para el mismo extracto
- **CUANDO** se crea otra igual
- **ENTONCES** la diferencia es 0, no se crea otro ajuste y `cantidadReconciliadas` es 0

### Requirement: Historial inmutable
El sistema SHALL ofrecer `GET .../conciliacion` con las conciliaciones de la cuenta (como máximo
50), de la más reciente a la más antigua (fecha y luego id descendente). NO SHALL existir `PUT`
ni `DELETE` sobre una conciliación.

#### Scenario: Historial ordenado y por cuenta
- **DADO** dos conciliaciones de una cuenta y una de otra
- **CUANDO** se pide el historial de la primera
- **ENTONCES** trae solo sus dos, la más reciente primero

#### Scenario: Sin modificar ni borrar
- **CUANDO** se llama a `PUT` o `DELETE` sobre `.../conciliacion` (la ruta existe solo con `GET` y
  `POST`)
- **ENTONCES** el sistema responde `405` y no cambia nada

#### Scenario: Ruta de una conciliación individual inexistente
- **CUANDO** se llama a `PUT` o `DELETE` sobre `.../conciliacion/{id}` (no hay ninguna ruta con
  ese patrón)
- **ENTONCES** el sistema responde `404` y no cambia nada
