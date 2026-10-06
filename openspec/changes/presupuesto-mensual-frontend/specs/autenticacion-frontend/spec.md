## MODIFIED Requirements

### Requirement: Página de inicio protegida
El sistema SHALL mostrar la página de inicio en `/presupuestos/:presupuestoId/inicio`, con el
saludo `Hola, {nombre}` y el nombre que devuelve `GET /api/v1/usuarios/yo`, y SHALL ofrecerla en
el menú lateral con el enlace `Inicio`. La página de inicio SHALL NOT tener botón propio de cerrar
sesión: el botón `Cerrar sesión` vive en la cabecera de la pantalla del presupuesto, borra la
sesión y lleva a `/login`.

#### Scenario: Saludo
- **DADO** una sesión vigente, un presupuesto con id `3` y la API respondiendo el nombre `Ana`
- **CUANDO** la persona entra a `/presupuestos/3/inicio`
- **ENTONCES** ve `Hola, Ana`

#### Scenario: Mientras carga
- **DADO** una sesión vigente y la respuesta de `GET /api/v1/usuarios/yo` todavía pendiente
- **CUANDO** se observa la página de inicio
- **ENTONCES** se ve un indicador de carga y todavía no hay saludo

#### Scenario: Cerrar sesión
- **DADO** la pantalla de un presupuesto con sesión
- **CUANDO** la persona pulsa `Cerrar sesión` en la cabecera
- **ENTONCES** la sesión se borra del almacenamiento local
- **Y** la persona llega a `/login`

#### Scenario: Sin botón de cerrar sesión en la página
- **DADO** la página de inicio
- **CUANDO** se observa su contenido, sin la cabecera
- **ENTONCES** no hay ningún botón `Cerrar sesión`

#### Scenario: Error al cargar los datos
- **DADO** una sesión vigente
- **CUANDO** `GET /api/v1/usuarios/yo` responde 500
- **ENTONCES** se muestra el aviso genérico y el texto `No pudimos cargar tus datos.`
- **Y** la sesión se conserva
