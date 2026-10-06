# formato-regional Specification

## Purpose

Define cómo el frontend muestra montos y fechas al usuario: detectando automáticamente su
región y evitando que una fecha de negocio sin hora se muestre corrida un día por conversión de
zona horaria.

## Requirements

### Requirement: Detección automática de la región del usuario
El sistema SHALL determinar la región de formato a partir de `navigator.language` del
navegador. Si `navigator.language` no está disponible o no es un identificador de locale válido,
el sistema SHALL usar `en-US` como región de formato por defecto. El sistema SHALL NOT requerir
que el usuario seleccione manualmente una región ni un idioma de formato.

#### Scenario: El navegador reporta una región válida
- **DADO** que `navigator.language` del navegador del usuario es `es-CL`
- **CUANDO** el sistema formatea cualquier monto o fecha visible para el usuario
- **ENTONCES** el sistema usa `es-CL` como región para ese formato

#### Scenario: El navegador no reporta una región utilizable
- **DADO** que `navigator.language` no está disponible o no es un identificador de locale válido
- **CUANDO** el sistema formatea cualquier monto o fecha visible para el usuario
- **ENTONCES** el sistema usa `en-US` como región para ese formato

### Requirement: Formato de montos monetarios según la región detectada
El sistema SHALL formatear todo monto monetario visible para el usuario según la región
detectada, recibiendo el valor en milésimas de la unidad monetaria y un código de moneda ISO
4217, y mostrando el valor convertido a su unidad monetaria completa con el símbolo o código de
esa moneda.

#### Scenario: Monto en milésimas se muestra como unidad monetaria completa
- **DADO** un monto de 1500000 milésimas con código de moneda `USD`
- **CUANDO** el sistema lo formatea para mostrarlo al usuario
- **ENTONCES** el sistema muestra el equivalente a 1500 unidades de `USD`, formateado según la
  región detectada del usuario (separadores de miles/decimales y posición del símbolo de moneda
  propios de esa región)

### Requirement: Formato de instantes exactos convertido a la zona horaria del usuario
El sistema SHALL tratar cualquier valor de fecha y hora expresado como instante ISO-8601 con
designador de zona horaria UTC (`Z`) como un momento exacto, y SHALL convertirlo a la zona
horaria local del navegador del usuario antes de mostrarlo.

#### Scenario: Un instante UTC se muestra en la hora local del usuario
- **DADO** un instante `2026-10-01T23:30:00Z`, un navegador cuya zona horaria local tiene un
  desfase de `-04:00` respecto a UTC, y una región detectada `es-CL`
- **CUANDO** el sistema formatea ese instante para mostrarlo al usuario
- **ENTONCES** el sistema muestra la fecha y hora equivalentes en esa zona horaria local,
  formateadas según la región (por ejemplo, "1 de octubre de 2026, 19:30"), no la fecha y hora
  en UTC

### Requirement: Formato de fechas de negocio sin conversión de zona horaria
El sistema SHALL tratar cualquier valor de fecha sin componente de hora (formato `yyyy-MM-dd`)
como una fecha de calendario independiente de zona horaria, y SHALL mostrar exactamente el año,
mes y día recibidos, sin aplicar ninguna conversión de zona horaria que pueda desplazar el día
mostrado.

#### Scenario: Una fecha de negocio no se desplaza un día en una zona horaria con desfase negativo
- **DADO** una fecha de negocio `2026-10-01` (sin componente de hora) y un navegador cuya zona
  horaria local tiene un desfase negativo respecto a UTC
- **CUANDO** el sistema formatea esa fecha para mostrarla al usuario
- **ENTONCES** el sistema muestra el día 1 de octubre de 2026, nunca el 30 de septiembre de 2026

#### Scenario: Una fecha de negocio se formatea según la región detectada
- **DADO** una fecha de negocio `2026-10-01` y una región detectada `es-CL`
- **CUANDO** el sistema formatea esa fecha para mostrarla al usuario
- **ENTONCES** el sistema muestra el día, mes y año en el orden y formato convencional de esa
  región, conservando el año, mes y día exactos recibidos

### Requirement: Formato de un valor ausente
El sistema SHALL mostrar una cadena vacía, sin lanzar ningún error, al formatear un monto o una
fecha cuyo valor es `null` o `undefined`. Esto es independiente de la validación de formato de
los demás requisitos de esta capacidad: un valor presente que no es `null` ni `undefined`, pero
cuyo formato no es reconocido, sigue estando sujeto a esas reglas (por ejemplo, sigue causando un
error si no coincide con ninguno de los formatos de fecha válidos).

#### Scenario: Un monto ausente se muestra como cadena vacía
- **DADO** un monto ausente (`null` o `undefined`)
- **CUANDO** el sistema lo formatea para mostrarlo al usuario
- **ENTONCES** el sistema muestra una cadena vacía, sin lanzar ningún error

#### Scenario: Una fecha ausente se muestra como cadena vacía
- **DADO** una fecha ausente (`null` o `undefined`)
- **CUANDO** el sistema la formatea para mostrarla al usuario
- **ENTONCES** el sistema muestra una cadena vacía, sin lanzar ningún error

#### Scenario: Una fecha presente con formato no reconocido lanza un error
- **DADO** un valor de fecha presente (no `null` ni `undefined`) cuyo formato no es ni
  `yyyy-MM-dd` ni un instante ISO-8601 con `Z` (por ejemplo, `"01/10/2026"`)
- **CUANDO** el sistema intenta formatear ese valor para mostrarlo al usuario
- **ENTONCES** el sistema lanza un error

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
