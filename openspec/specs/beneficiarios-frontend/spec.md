# beneficiarios-frontend Specification

## Purpose

Permite elegir el beneficiario de una transacción con autocompletado por prefijo, rellenar la
categoría que el beneficiario usó la última vez y gestionar los beneficiarios del presupuesto
(nombre y categoría predeterminada) desde su propia sección.

## Requirements

### Requirement: Sugerencias de beneficiario por prefijo
El campo `Beneficiario` del diálogo de transacción SHALL pedir sugerencias con
`GET .../beneficiarios?q={texto recortado}&limite=10` 250 ms después de la última tecla, solo si
el texto recortado tiene al menos 1 carácter. Con el campo vacío, o al enfocarlo vacío, SHALL NOT
pedir nada ni mostrar sugerencias. Si llega la respuesta de una búsqueda vieja después de una
nueva, SHALL ignorarse. Un error de la petición SHALL mostrar la lista vacía sin deshabilitar el
campo ni mostrar un aviso. Cada opción SHALL mostrar el nombre, con la parte que coincide con el
prefijo resaltada, y como texto secundario el nombre de su categoría predeterminada si la tiene y
existe en el árbol de categorías del diálogo.

#### Scenario: Espera antes de pedir
- **DADO** el diálogo de crear una transacción
- **CUANDO** la persona escribe `n`, `ne` y `net` en menos de 250 ms
- **ENTONCES** se hace una sola petición, con `q=net&limite=10`, 250 ms después de la última tecla

#### Scenario: Texto recortado
- **DADO** el diálogo de crear
- **CUANDO** la persona escribe `  net `
- **ENTONCES** la petición lleva `q=net`

#### Scenario: Solo espacios
- **DADO** el diálogo de crear
- **CUANDO** la persona escribe `   `
- **ENTONCES** no se hace ninguna petición y no hay sugerencias

#### Scenario: Respuesta atrasada
- **DADO** una búsqueda de `ne` todavía sin respuesta
- **CUANDO** la persona sigue a `net` y la respuesta de `ne` llega después de la de `net`
- **ENTONCES** las sugerencias son las de `net`

#### Scenario: Error de red
- **DADO** el diálogo de crear
- **CUANDO** la búsqueda falla por un error de red
- **ENTONCES** no hay sugerencias, el campo sigue editable y se puede guardar con el texto

#### Scenario: Categoría predeterminada como texto secundario
- **DADO** la sugerencia `Netflix` con la categoría predeterminada `Ocio` y la sugerencia `Nestlé`
  con una categoría que ya no está en el árbol
- **CUANDO** se muestran las sugerencias de `ne`
- **ENTONCES** `Netflix` muestra `Ocio` como texto secundario, `Nestlé` no muestra ninguno, y en
  ambas está resaltado `Ne`

### Requirement: Texto libre y pista de beneficiario nuevo
El campo SHALL aceptar cualquier texto, coincida o no con una sugerencia; al guardar SHALL
enviarse recortado y, si queda vacío, como `null`. Mientras el texto recortado no esté vacío y no
coincida, sin distinguir mayúsculas, con el nombre de ninguna de las sugerencias recibidas, el
campo SHALL mostrar la pista `Se creará un beneficiario nuevo`. SHALL validar como máximo 100
caracteres tras recortar, con contador. SHALL tener `aria-label` y permitir elegir con las
flechas y `Enter`, y cerrar las sugerencias con `Escape`.

#### Scenario: Beneficiario nuevo
- **DADO** que la búsqueda de `Panadería Sol` no devuelve resultados
- **CUANDO** la persona guarda la transacción
- **ENTONCES** se muestra la pista `Se creará un beneficiario nuevo` antes de guardar
- **Y** se envía `beneficiario: 'Panadería Sol'`, sin crear el beneficiario antes

#### Scenario: Coincidencia exacta sin distinguir mayúsculas
- **DADO** la sugerencia `Netflix`
- **CUANDO** la persona escribe `  NETFLIX `
- **ENTONCES** no se muestra la pista de beneficiario nuevo

#### Scenario: Elegir con el teclado
- **DADO** las sugerencias `Nestlé` y `Netflix`
- **CUANDO** la persona baja con la flecha hasta `Netflix` y pulsa `Enter`
- **ENTONCES** el campo queda con `Netflix`

#### Scenario: Cerrar con Escape
- **DADO** las sugerencias abiertas
- **CUANDO** la persona pulsa `Escape`
- **ENTONCES** las sugerencias se cierran y el texto escrito se conserva

#### Scenario: Demasiado largo
- **DADO** el diálogo de crear
- **CUANDO** la persona escribe 101 caracteres
- **ENTONCES** el campo muestra `El beneficiario no puede superar los 100 caracteres` y el botón
  está deshabilitado

### Requirement: Categoría recordada del beneficiario
Al elegir una sugerencia con `categoriaPredeterminadaId`, el diálogo de transacción SHALL
rellenar la categoría con ella solo si se cumplen las cuatro condiciones: se está creando la
transacción, no está activo `Dividir`, el control de categoría está vacío y sin tocar
(`pristine`), y la categoría existe en el árbol de categorías del diálogo (aunque esté oculta). Al
rellenarla SHALL mostrar en el campo la pista `Sugerida por el beneficiario`, que SHALL
desaparecer cuando la persona cambie la categoría. En cualquier otro caso SHALL NOT tocar la
categoría.

#### Scenario: Se rellena al crear
- **DADO** el diálogo de crear con la categoría vacía y sin tocar
- **CUANDO** la persona elige `Netflix`, cuya categoría predeterminada es `Ocio`
- **ENTONCES** la categoría queda en `Ocio` con la pista `Sugerida por el beneficiario`
- **Y** al cambiarla a `Comida` la pista desaparece

#### Scenario: Categoría oculta
- **DADO** la categoría predeterminada `Ocio` oculta y el diálogo de crear sin categoría
- **CUANDO** la persona elige `Netflix`
- **ENTONCES** la categoría queda en `Ocio`, que aparece como opción del select

#### Scenario: La persona ya eligió categoría
- **DADO** el diálogo de crear con la categoría `Comida` elegida a mano
- **CUANDO** la persona elige `Netflix`
- **ENTONCES** la categoría sigue en `Comida`

#### Scenario: Tocada y vaciada
- **DADO** el diálogo de crear donde la persona eligió `Comida` y luego `Sin categoría`
- **CUANDO** elige `Netflix`
- **ENTONCES** la categoría sigue vacía

#### Scenario: En modo Dividir
- **DADO** el diálogo de crear con `Dividir` activo
- **CUANDO** la persona elige `Netflix`
- **ENTONCES** ninguna parte cambia de categoría

#### Scenario: Al editar
- **DADO** el diálogo de editar una transacción sin categoría
- **CUANDO** la persona elige `Netflix`
- **ENTONCES** la categoría sigue vacía

#### Scenario: La categoría ya no existe
- **DADO** `Netflix` con una categoría predeterminada que no está en el árbol del diálogo
- **CUANDO** la persona lo elige al crear
- **ENTONCES** la categoría sigue vacía y no hay pista

### Requirement: Reintento ante beneficiario duplicado
Si guardar una transacción responde `409` con código `BENEFICIARIO_YA_EXISTE` (dos peticiones
crearon el mismo beneficiario a la vez), el diálogo SHALL repetir la misma petición una sola vez.
Si el reintento tiene éxito SHALL cerrarse como cualquier guardado; si vuelve a fallar con el
mismo código, SHALL mostrar `Ya existe un beneficiario con ese nombre` en el campo beneficiario y
seguir abierto.

#### Scenario: El reintento funciona
- **DADO** el diálogo de crear con el beneficiario `Netflix`
- **CUANDO** el `POST` responde `409 BENEFICIARIO_YA_EXISTE` y el segundo `POST` responde `201`
- **ENTONCES** se hicieron exactamente dos `POST` iguales y el diálogo se cierra como guardado

#### Scenario: El reintento también falla
- **DADO** el diálogo de crear con el beneficiario `Netflix`
- **CUANDO** los dos `POST` responden `409 BENEFICIARIO_YA_EXISTE`
- **ENTONCES** no hay un tercer intento, el campo beneficiario muestra
  `Ya existe un beneficiario con ese nombre` y el diálogo sigue abierto

### Requirement: Nombre del beneficiario vinculado en la tabla
La pantalla de transacciones SHALL pedir una vez la lista completa de beneficiarios del
presupuesto (`GET .../beneficiarios`, sin `q`) y la columna `Beneficiario` SHALL mostrar el nombre
actual del beneficiario cuyo id es `beneficiarioId`; si la transacción no tiene `beneficiarioId`
o el id no está en la lista, SHALL mostrar su texto `beneficiario`. La lista SHALL volver a
pedirse después de guardar una transacción desde el diálogo. Si la lista no se puede cargar, la
columna SHALL mostrar el texto de cada transacción.

#### Scenario: Beneficiario renombrado
- **DADO** una transacción con `beneficiario` `Netflix` y `beneficiarioId` `4`, y el beneficiario
  `4` renombrado a `Netflix Premium`
- **CUANDO** se muestra la tabla
- **ENTONCES** la columna muestra `Netflix Premium`

#### Scenario: Sin vínculo
- **DADO** una transacción antigua con `beneficiario` `Tienda` y `beneficiarioId` nulo
- **CUANDO** se muestra la tabla
- **ENTONCES** la columna muestra `Tienda`

#### Scenario: Vínculo que no está en la lista
- **DADO** una transacción con `beneficiarioId` `9` que no está en la lista cargada
- **CUANDO** se muestra la tabla
- **ENTONCES** la columna muestra su texto `beneficiario`

#### Scenario: Refresco tras guardar
- **DADO** la pantalla con la lista de beneficiarios cargada
- **CUANDO** la persona crea una transacción con un beneficiario nuevo
- **ENTONCES** se vuelve a pedir `GET .../beneficiarios` junto con la página y los saldos

### Requirement: Sección Beneficiarios
El sistema SHALL ofrecer la pantalla en `/presupuestos/:presupuestoId/beneficiarios`, con el
enlace `Beneficiarios` en el menú lateral, que pide `GET .../beneficiarios` (lista completa) y el
árbol de categorías con ocultas, y SHALL mostrar una tabla con las columnas `Nombre` y
`Categoría predeterminada` (su nombre, o `—` si no tiene o ya no existe) y la acción `Editar` en
cada fila, en el orden de la API. Un buscador SHALL filtrar la lista cargada, sin pedir al
servidor, por los nombres que empiezan por el texto recortado sin distinguir mayúsculas, 250 ms
después de la última tecla. No SHALL haber acción de borrar.

#### Scenario: Lista
- **DADO** los beneficiarios `Banco` sin categoría y `Netflix` con `Ocio`
- **CUANDO** la persona entra a la sección
- **ENTONCES** la tabla muestra `Banco` con `—` y `Netflix` con `Ocio`, cada uno con `Editar`

#### Scenario: Filtrar por prefijo
- **DADO** los beneficiarios `Banco Unión`, `Nestlé` y `Netflix`
- **CUANDO** la persona escribe `NE` en el buscador
- **ENTONCES** la tabla muestra `Nestlé` y `Netflix`, sin `Banco Unión`, y no se hace otra
  petición

#### Scenario: Filtro sin resultados
- **DADO** la lista cargada
- **CUANDO** la persona busca un texto con el que no empieza ningún nombre
- **ENTONCES** la pantalla dice que ningún beneficiario coincide con la búsqueda

### Requirement: Estados de la pantalla de beneficiarios
Mientras carga, la pantalla SHALL mostrar un indicador de progreso. Sin beneficiarios SHALL
mostrar `Aún no tienes beneficiarios; se crean solos al registrar transacciones` con el botón
`Agregar beneficiario`. Si la carga falla, SHALL mostrar un mensaje de error con `Reintentar`, que
vuelve a pedir la lista.

#### Scenario: Vacío
- **DADO** un presupuesto sin beneficiarios
- **CUANDO** la persona entra a la sección
- **ENTONCES** ve el mensaje de lista vacía y el botón `Agregar beneficiario`

#### Scenario: Error y reintento
- **DADO** que `GET .../beneficiarios` falla
- **CUANDO** la persona pulsa `Reintentar` y la petición responde bien
- **ENTONCES** se muestra la tabla

### Requirement: Crear y editar un beneficiario
`Agregar beneficiario` y `Editar` SHALL abrir un diálogo con el nombre (obligatorio, de 1 a 100
caracteres tras recortar, con contador) y la categoría predeterminada (select agrupado por grupo
con la opción `Ninguna`; las categorías y grupos ocultos solo si ya era la categoría elegida, y
sin las categorías de pago de tarjeta). Crear SHALL enviar `POST` con `{ nombre, categoriaId }` y
editar `PUT .../beneficiarios/{id}` con lo mismo, el nombre recortado y `categoriaId` nulo si se
eligió `Ninguna`. Al editar, si el nombre recortado cambia, SHALL mostrarse la pista
`Las transacciones antiguas conservan el texto original, pero se mostrarán con el nombre nuevo`.
El botón SHALL estar deshabilitado mientras el formulario sea inválido o se esté enviando. Al
guardar SHALL cerrarse y volver a pedir la lista.

#### Scenario: Crear con categoría
- **DADO** el diálogo de crear
- **CUANDO** la persona escribe `  Spotify ` y elige `Ocio`
- **ENTONCES** se envía `POST` con `{ nombre: 'Spotify', categoriaId: 7 }` y la lista se recarga

#### Scenario: Quitar la categoría
- **DADO** el beneficiario `Netflix` con la categoría `Ocio`
- **CUANDO** la persona lo edita y elige `Ninguna`
- **ENTONCES** se envía `PUT` con `{ nombre: 'Netflix', categoriaId: null }`

#### Scenario: Pista al renombrar
- **DADO** el diálogo de editar `Netflix`
- **CUANDO** la persona cambia el nombre a `Netflix Premium`
- **ENTONCES** se muestra la pista sobre las transacciones antiguas
- **Y** no se muestra si deja el nombre como estaba

#### Scenario: Categoría oculta ya elegida
- **DADO** un beneficiario con una categoría predeterminada oculta
- **CUANDO** la persona lo edita
- **ENTONCES** esa categoría aparece y está elegida; las demás ocultas no aparecen

#### Scenario: Nombre vacío
- **DADO** el diálogo de crear
- **CUANDO** la persona escribe solo espacios
- **ENTONCES** se muestra `El nombre es obligatorio` y el botón está deshabilitado

### Requirement: Errores del diálogo de beneficiario
El diálogo SHALL decidir por `codigo`: `BENEFICIARIO_YA_EXISTE` como
`Ya existe un beneficiario con ese nombre` en el campo nombre; `DATOS_INVALIDOS` con `errores`
en sus campos; `RECURSO_NO_ENCONTRADO` con un aviso, cerrando el diálogo y volviendo a pedir la
lista y las categorías; cualquier otro error (incluido `REGLA_NEGOCIO_VIOLADA`) con un aviso
genérico, sin cerrarse.

#### Scenario: Nombre repetido
- **DADO** el beneficiario `Netflix`
- **CUANDO** la persona crea `netflix` y la API responde `409 BENEFICIARIO_YA_EXISTE`
- **ENTONCES** el campo nombre muestra `Ya existe un beneficiario con ese nombre` y el diálogo
  sigue abierto

#### Scenario: Datos inválidos
- **DADO** el diálogo de crear
- **CUANDO** la API responde `400 DATOS_INVALIDOS` con `errores: { nombre: '...' }`
- **ENTONCES** ese mensaje se muestra en el campo nombre

#### Scenario: Ya no existe
- **DADO** el diálogo de editar un beneficiario o con una categoría que se borró
- **CUANDO** la API responde `404`
- **ENTONCES** se muestra un aviso, el diálogo se cierra y se vuelven a pedir la lista y las
  categorías
