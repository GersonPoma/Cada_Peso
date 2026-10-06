## ADDED Requirements

### Requirement: Árbol inicial de un presupuesto
El sistema SHALL crear, junto con todo presupuesto nuevo, los grupos de categorías y las
categorías iniciales, sin nada oculto y con `orden` consecutivo desde 0 en los grupos y, dentro
de cada grupo, en las categorías. El orden y los nombres son:

- **Facturas**: Alquiler, Luz, Agua, Internet, Teléfono
- **Necesidades**: Comida, Transporte, Salud
- **Deseos**: Restaurantes, Ocio, Ropa
- **Ahorro**: Fondo de emergencia, Vacaciones

#### Scenario: Registro de una persona
- **DADO** una persona que se registra con datos válidos
- **CUANDO** consulta el árbol de categorías de su presupuesto "Mi presupuesto"
- **ENTONCES** el sistema responde `200` con los 4 grupos y sus categorías, en el orden y con los
  nombres indicados, todos visibles

#### Scenario: Presupuesto creado con POST
- **DADO** una persona autenticada
- **CUANDO** crea un presupuesto con `POST /api/v1/presupuestos`
- **ENTONCES** el árbol de categorías de ese presupuesto contiene los 4 grupos y sus categorías,
  en el orden y con los nombres indicados

#### Scenario: Cada presupuesto tiene su propio árbol
- **DADO** una persona con dos presupuestos
- **CUANDO** oculta o renombra un grupo o una categoría del primero
- **ENTONCES** el árbol del segundo conserva los 4 grupos y sus categorías sin cambios, y los ids
  de ambos árboles son distintos

#### Scenario: Los nombres iniciales ocupan sus nombres
- **DADO** un presupuesto recién creado
- **CUANDO** se crea un grupo "Facturas", o una categoría "Luz" en el grupo "Facturas"
- **ENTONCES** el sistema responde `409` con el código de nombre ya existente, como con cualquier
  otro grupo o categoría

#### Scenario: Presupuesto rechazado por nombre repetido
- **DADO** una persona que ya tiene un presupuesto llamado "Viajes"
- **CUANDO** crea otro presupuesto "Viajes" y el sistema responde `409`
- **ENTONCES** no se crea ningún grupo ni categoría nuevos

#### Scenario: Atomicidad ante un fallo
- **DADO** un fallo al crear los grupos o las categorías iniciales
- **CUANDO** se registra una persona o se crea un presupuesto
- **ENTONCES** no queda el presupuesto ni sus grupos o categorías y, en el registro, tampoco el
  usuario ni su perfil
