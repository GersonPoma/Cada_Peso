# Spec Delta

## ADDED Requirements

### Requirement: Importar CSV desde el menú de la cuenta
El menú de cada cuenta abierta SHALL ofrecer `Importar CSV`, que lleva a
`/presupuestos/:presupuestoId/cuentas/:cuentaId/importacion`. Las cuentas cerradas SHALL NOT
ofrecerlo.

#### Scenario: Cuenta abierta
- **DADO** la cuenta abierta `Banco` (id `5`) del presupuesto `3`
- **CUANDO** la persona abre su menú y elige `Importar CSV`
- **ENTONCES** llega a `/presupuestos/3/cuentas/5/importacion`

#### Scenario: Cuenta cerrada
- **DADO** la cuenta cerrada `Vieja`
- **CUANDO** la persona abre su menú
- **ENTONCES** no ofrece `Importar CSV`

#### Scenario: Se anuncia como ítem de menú y no como enlace
- **DADO** el menú abierto de una cuenta abierta
- **CUANDO** se lee el ítem `Importar CSV`
- **ENTONCES** es un botón nativo con `role="menuitem"`, sin `href` ni `role="link"`, de modo
  que un lector de pantalla lo anuncia como ítem de menú

#### Scenario: Operable con teclado
- **DADO** el menú de una cuenta abierta con el teclado
- **CUANDO** la persona baja con las flechas hasta `Importar CSV` y pulsa `Enter` o `Espacio`
- **ENTONCES** el foco recorre los ítems en orden, el ítem queda enfocado y al activarlo llega a
  la pantalla de importación de esa cuenta

#### Scenario: También las tarjetas y las de seguimiento
- **DADO** una tarjeta de crédito abierta y una cuenta de seguimiento abierta
- **CUANDO** la persona abre el menú de cada una
- **ENTONCES** ambas ofrecen `Importar CSV`
