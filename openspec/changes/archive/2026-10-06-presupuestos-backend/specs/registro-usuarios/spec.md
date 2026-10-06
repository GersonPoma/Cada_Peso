# Spec Delta

## ADDED Requirements

### Requirement: Presupuesto inicial al registrarse
El sistema SHALL crear, junto con las credenciales y el perfil y en la misma operación atómica,
un presupuesto llamado `Mi presupuesto` cuya moneda sea la moneda predeterminada del perfil
recién creado. Si falla cualquier parte del alta, el sistema SHALL NOT dejar guardado ningún
presupuesto de esa persona.

#### Scenario: Registro con moneda explícita
- **DADO** un registro válido con moneda predeterminada `USD`
- **CUANDO** la persona se registra
- **ENTONCES** al listar sus presupuestos con el token recibido hay uno solo, `Mi presupuesto`,
  con moneda `USD`

#### Scenario: Registro sin moneda
- **DADO** un registro válido sin moneda predeterminada
- **CUANDO** la persona se registra
- **ENTONCES** su único presupuesto es `Mi presupuesto` con moneda `BOB`

#### Scenario: Un fallo al crear el presupuesto no deja un alta parcial
- **DADO** un registro con datos válidos
- **CUANDO** falla la creación del presupuesto inicial
- **ENTONCES** no queda guardada ninguna cuenta ni perfil con ese email
