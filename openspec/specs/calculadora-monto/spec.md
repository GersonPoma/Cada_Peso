# calculadora-monto Specification

## Purpose

Permite escribir montos como expresiones simples (por ejemplo `30+20+50`) en cualquier formulario
y obtener un resultado exacto en milésimas, sin aritmética de coma flotante.

## Requirements

### Requirement: Evaluar una expresión de monto
El sistema SHALL evaluar una expresión con números, los operadores `+`, `-`, `*` y `/` y un signo
`-` unario (al principio o después de otro operador), y SHALL devolver el resultado en milésimas.
Cada número SHALL escribirse con el separador decimal (y, opcionalmente, el de miles) de la región
del usuario y tener como máximo 3 decimales. `*` y `/` SHALL tener precedencia sobre `+` y `-`.
El cálculo SHALL ser exacto, sin coma flotante, y el resultado final SHALL redondearse a 3
decimales con la mitad hacia el lado alejado de cero.

#### Scenario: Suma
- **DADO** la región `en-US`
- **CUANDO** se evalúa `30+20+50`
- **ENTONCES** el resultado es `100000`

#### Scenario: Decimales y precedencia
- **DADO** la región `es-BO`
- **CUANDO** se evalúan `12,5*2`, `-5+2` y `2+3*4`
- **ENTONCES** los resultados son `25000`, `-3000` y `14000`

#### Scenario: División con redondeo
- **DADO** la región `en-US`
- **CUANDO** se evalúan `10/3`, `2/3` y `-2/3`
- **ENTONCES** los resultados son `3333`, `667` y `-667`

#### Scenario: Separadores por región
- **DADO** las regiones `es-BO` y `en-US`
- **CUANDO** se evalúan `1.234,567` en `es-BO` y `1,234.567` en `en-US`, y `0,001` en `es-BO`
- **ENTONCES** los resultados son `1234567`, `1234567` y `1`

#### Scenario: Sin pérdida de precisión
- **DADO** la región `en-US`
- **CUANDO** se evalúa `0.1+0.2`
- **ENTONCES** el resultado es exactamente `300`

### Requirement: Expresiones inválidas
El sistema SHALL devolver "sin resultado" (y no un error de ejecución) para una expresión vacía,
con un operador al final, con dos operadores seguidos (salvo un `-` unario), con letras u otros
caracteres (incluidos los paréntesis), con una división por cero, con un número de más de 3
decimales o con un resultado fuera del rango de enteros exactos.

#### Scenario: Casos inválidos
- **DADO** la región `en-US`
- **CUANDO** se evalúan `1/0`, `5++2`, `5+`, `""`, `abc`, `12.3456` y `(1+2)`
- **ENTONCES** ninguno tiene resultado

### Requirement: Campo de monto reutilizable
El sistema SHALL ofrecer un campo de monto reutilizable en cualquier formulario, con una etiqueta,
un indicador de obligatorio y un control cuyo valor está en milésimas. Mientras se escribe una
expresión con operadores, SHALL mostrar como pista su resultado. Al salir del campo o con `Enter`,
SHALL evaluar el texto: si es válido, SHALL reemplazarlo por el resultado escrito con el separador
de la región y poner el valor en el control; si es inválido, SHALL mostrar `Monto no válido` sin
borrar lo escrito. Un campo vacío SHALL dejar el control sin valor.

#### Scenario: Evaluar al salir
- **DADO** el campo con la región `es-BO`
- **CUANDO** la persona escribe `30+20,5` y sale del campo
- **ENTONCES** el texto pasa a `50,5` y el control vale `50500`

#### Scenario: Pista mientras se escribe
- **DADO** el campo
- **CUANDO** la persona escribe `10*3`
- **ENTONCES** se ve la pista `= 30`

#### Scenario: Expresión inválida
- **DADO** el campo
- **CUANDO** la persona escribe `5+` y pulsa `Enter`
- **ENTONCES** se muestra `Monto no válido` y el texto sigue siendo `5+`
