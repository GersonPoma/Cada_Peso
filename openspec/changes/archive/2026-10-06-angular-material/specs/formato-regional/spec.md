# Spec Delta

## ADDED Requirements

### Requirement: Selector de fechas según la región detectada
El selector de fechas de la interfaz SHALL usar la región detectada del usuario para los nombres
de los meses y días, el primer día de la semana y el formato con que muestra la fecha elegida, sin
configuración manual.

#### Scenario: Selector con región chilena
- **DADO** que la región detectada del usuario es `es-CL`
- **CUANDO** el usuario elige el 1 de octubre de 2026 en un selector de fechas
- **ENTONCES** el selector muestra los nombres de los meses en español
- **Y** muestra la fecha elegida con el día antes que el mes, con día y mes en dos dígitos y el
  separador de esa región (`01-10-2026`)

#### Scenario: Selector con la región por defecto
- **DADO** que la región detectada del usuario es `en-US`
- **CUANDO** el usuario elige el 1 de octubre de 2026 en un selector de fechas
- **ENTONCES** el selector muestra la fecha elegida con el mes antes que el día (`10/01/2026`)

### Requirement: Interpretación de fechas escritas a mano según la región
Cuando el usuario escribe una fecha en el campo de un selector de fechas, el sistema SHALL
interpretarla según el orden de día, mes y año de la región detectada, aceptando `/`, `-` y `.`
como separadores y un año de cuatro dígitos. Si el texto no corresponde a una fecha que exista en
el calendario, el sistema SHALL tratarlo como una fecha inválida, sin ajustarlo a otro día. La
fecha mostrada tras elegirla en el calendario SHALL poder volver a escribirse tal cual y producir
la misma fecha.

#### Scenario: Día antes que el mes en Bolivia
- **DADO** que la región detectada del usuario es `es-BO`
- **CUANDO** el usuario escribe `05/03/2003` en el campo de fecha
- **ENTONCES** el sistema interpreta el 5 de marzo de 2003

#### Scenario: Mes antes que el día en Estados Unidos
- **DADO** que la región detectada del usuario es `en-US`
- **CUANDO** el usuario escribe `05/03/2003` en el campo de fecha
- **ENTONCES** el sistema interpreta el 3 de mayo de 2003

#### Scenario: Separadores alternativos
- **DADO** que la región detectada del usuario es `es-BO`
- **CUANDO** el usuario escribe `05-03-2003` o `05.03.2003` en el campo de fecha
- **ENTONCES** el sistema interpreta el 5 de marzo de 2003 en ambos casos

#### Scenario: Fecha inexistente
- **DADO** que la región detectada del usuario es `es-BO`
- **CUANDO** el usuario escribe `31/02/2003` en el campo de fecha
- **ENTONCES** el sistema la trata como una fecha inválida
- **Y** no la convierte en el 3 de marzo de 2003 ni en ninguna otra fecha

#### Scenario: Texto que no es una fecha completa
- **DADO** que la región detectada del usuario es `es-BO`
- **CUANDO** el usuario escribe `05/03/03`, `05/2003` o `hoy` en el campo de fecha
- **ENTONCES** el sistema lo trata como una fecha inválida

#### Scenario: La fecha mostrada se puede volver a escribir
- **DADO** que la región detectada del usuario es `es-BO`
- **CUANDO** el usuario elige el 5 de marzo de 2003 en el calendario
- **ENTONCES** el campo muestra `05/03/2003`
- **Y** escribir ese mismo texto produce el 5 de marzo de 2003

### Requirement: Conversión de la fecha elegida a fecha de negocio sin desplazamiento
Cuando el usuario elige una fecha de negocio en un selector de fechas, el sistema SHALL
convertirla al formato `yyyy-MM-dd` usando el año, el mes y el día del calendario local en que
el usuario la eligió, de modo que el valor enviado sea exactamente el día elegido en cualquier
zona horaria, sin aplicar conversión a UTC. Si no hay fecha elegida, el sistema SHALL no producir
ningún valor de fecha.

#### Scenario: Zona horaria con desfase negativo, a medianoche
- **DADO** un navegador en la zona horaria `America/New_York` (desfase negativo respecto a UTC)
- **CUANDO** el usuario elige el 1 de octubre de 2026 (medianoche local)
- **ENTONCES** el valor convertido es `2026-10-01`

#### Scenario: Zona horaria con desfase negativo, al final del día
- **DADO** un navegador en la zona horaria `America/New_York`
- **CUANDO** se convierte el 1 de octubre de 2026 a las 23:59 hora local
- **ENTONCES** el valor convertido es `2026-10-01`, nunca `2026-10-02`

#### Scenario: Zona horaria con desfase positivo, a medianoche
- **DADO** un navegador en la zona horaria `Asia/Tokyo` (desfase positivo respecto a UTC)
- **CUANDO** el usuario elige el 1 de octubre de 2026 (medianoche local)
- **ENTONCES** el valor convertido es `2026-10-01`, nunca `2026-09-30`

#### Scenario: Meses y días de un dígito
- **DADO** cualquier zona horaria
- **CUANDO** el usuario elige el 5 de marzo de 2026
- **ENTONCES** el valor convertido es `2026-03-05`, con el mes y el día en dos dígitos

#### Scenario: Sin fecha elegida
- **DADO** un selector de fechas sin fecha elegida
- **CUANDO** se convierte su valor
- **ENTONCES** el resultado es la ausencia de valor (`null`) y no un string de fecha
