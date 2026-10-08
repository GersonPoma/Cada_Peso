## MODIFIED Requirements

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
