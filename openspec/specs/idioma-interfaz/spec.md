# idioma-interfaz Specification

## Purpose

Garantiza que los textos que muestran los propios componentes de interfaz (etiquetas de
navegación, rangos y botones internos que la aplicación no escribe) aparezcan en español, igual
que el resto de Cada Peso.

## Requirements

### Requirement: Textos internos del paginador en español
El paginador de listas SHALL mostrar en español todos sus textos internos: la etiqueta de
elementos por página, el rango mostrado y las etiquetas de los botones de primera, anterior,
siguiente y última página.

#### Scenario: Rango de una página intermedia
- **DADO** una lista de 25 elementos paginada de a 10
- **CUANDO** se muestra la segunda página
- **ENTONCES** el paginador muestra el rango `11 – 20 de 25`
- **Y** la etiqueta `Elementos por página:`

#### Scenario: Lista vacía
- **DADO** una lista sin elementos
- **CUANDO** se muestra el paginador
- **ENTONCES** el paginador muestra el rango `0 de 0`

#### Scenario: Botones de navegación
- **DADO** un paginador visible
- **CUANDO** se leen las etiquetas accesibles de sus botones
- **ENTONCES** son `Primera página`, `Página anterior`, `Página siguiente` y `Última página`

### Requirement: Textos internos del selector de fechas en español
El selector de fechas SHALL mostrar en español las etiquetas internas del calendario: abrir el
calendario, elegir mes y año, y avanzar o retroceder un mes, un año o un rango de años.

#### Scenario: Botones del calendario
- **DADO** un selector de fechas
- **CUANDO** se leen las etiquetas accesibles de su botón y de los controles del calendario
- **ENTONCES** incluyen `Abrir calendario`, `Mes anterior`, `Mes siguiente` y
  `Elegir mes y año`, y ninguna está en inglés
