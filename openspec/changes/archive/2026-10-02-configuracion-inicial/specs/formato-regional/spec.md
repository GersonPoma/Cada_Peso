# Spec Delta

## Purpose

Define cómo el frontend muestra montos y fechas al usuario: detectando automáticamente su
región y evitando que una fecha de negocio sin hora se muestre corrida un día por conversión de
zona horaria.

## ADDED Requirements

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
