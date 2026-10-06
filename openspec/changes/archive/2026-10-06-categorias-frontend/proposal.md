## Why

El backend ya organiza el dinero en grupos de categorías y categorías (cada presupuesto nace con
Facturas, Necesidades, Deseos y Ahorro), pero el frontend no tiene ninguna pantalla para verlas
ni ordenarlas. Son la base de la asignación mensual (ya terminada en el backend): antes de asignar
dinero, la persona tiene que poder ajustar su lista de categorías a su vida.

## What Changes

- Nueva feature `features/categorias/` con modelos que copian los records del backend
  (`com.presupuesto.categoria.dto`), un servicio HTTP con todas las operaciones de grupos y
  categorías, la página de categorías y sus diálogos.
- Página `/presupuestos/:presupuestoId/categorias`, con su enlace "Categorías" en el menú
  lateral:
  - El árbol de grupos con sus categorías, cada una con su nota si la tiene.
  - Un interruptor "Mostrar ocultas" que muestra los ocultos atenuados y con ícono.
  - Crear, renombrar y editar grupos y categorías; ocultarlos y mostrarlos.
  - Estados de carga, error y vacío.
- Reordenar con arrastrar y soltar (`@angular/cdk/drag-drop`, ya instalado):
  - Mueve grupos, categorías dentro de su grupo y categorías entre grupos, incluido un grupo
    vacío.
  - La vista se actualiza al instante y se revierte con un aviso si el backend rechaza el
    movimiento.
  - Mientras un movimiento está en curso, se ignoran los arrastres nuevos.
- Acción "Mover a..." de cada categoría (grupo y lugar dentro del grupo), para teclado y
  pantallas táctiles.
- El cálculo del reordenamiento vive en funciones puras, probadas sin DOM. Traducen el índice
  visible de la lista a la `posicion` del backend, que cuenta también los elementos ocultos.
- `CODIGOS_API` incorpora `GRUPO_CATEGORIA_YA_EXISTE` y `CATEGORIA_YA_EXISTE`.
- `AGENTS.md`: árbol con `features/categorias/`.

Fuera de alcance: asignar dinero a las categorías (asignación mensual), metas, borrar grupos o
categorías (el backend no lo permite) y mover grupos entre presupuestos. No se agregan librerías
ni se ejecuta `npm install`.

## Capabilities

### New Capabilities
- `categorias-frontend`: pantalla del árbol de grupos y categorías del presupuesto activo
  (ocultas a pedido, estados), diálogos de grupo y de categoría con sus reglas y errores, ocultar
  y mostrar, reordenar con arrastrar y soltar y con "Mover a...", y enlace en el menú lateral.

### Modified Capabilities
Ninguna: el menú lateral se arma a partir de las rutas hijas, así que el enlace nuevo no cambia
los requisitos de `presupuestos-frontend`.

## Impact

- **Frontend** (`frontend/src/app`): archivos nuevos en `features/categorias/`; cambios en
  `app.routes.ts` (una ruta hija) y su spec, y en `shared/api/problema-api.ts`.
- **API consumida**: `GET /categorias`, `POST/PUT /categorias[/{id}]`,
  `POST /categorias/{id}/ocultar|mostrar|mover`, `POST/PUT /grupos-categorias[/{id}]` y
  `POST /grupos-categorias/{id}/ocultar|mostrar|mover`, todas bajo
  `/api/v1/presupuestos/{presupuestoId}` y ya implementadas. El backend no cambia.
- **Dependencias**: ninguna nueva (`@angular/cdk/drag-drop` viene con `@angular/cdk`).
- **Documentación**: `AGENTS.md`.
