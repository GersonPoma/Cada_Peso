## Why

Un presupuesto base cero reparte el dinero entre categorías agrupadas (Vivienda, Comida,
Ahorros...). Hoy cada presupuesto tiene cuentas, pero no categorías. Los grupos de categorías y
las categorías son el siguiente recurso que cuelga de un presupuesto y la base de la asignación
mensual y de las transacciones.

## What Changes

- Nueva feature `categoria` en el backend con dos entidades: `GrupoCategoria` (tabla
  `grupos_categoria`) y `Categoria` (tabla `categorias`). Ambas tienen nombre único sin
  distinguir mayúsculas (por presupuesto el grupo, por grupo la categoría), `orden` consecutivo
  desde 0 y estado oculto; la categoría añade una `nota` opcional de hasta 500 caracteres.
- Endpoints bajo `/api/v1/presupuestos/{presupuestoId}`:
  - `/grupos-categorias`: crear, renombrar, `ocultar`, `mostrar` y `mover`.
  - `/categorias`: crear, editar nombre y nota, `ocultar`, `mostrar`, `mover` (dentro del grupo
    o a otro grupo del presupuesto) y detalle.
  - `GET /categorias`: árbol completo de grupos con sus categorías ordenados por `orden`, con
    el parámetro `incluirOcultas` (por defecto `false`).
- Mover renumera sin huecos ni repetidos (un grupo en el presupuesto; una categoría en el grupo
  de origen y en el de destino).
- Nuevos `CodigoError.GRUPO_CATEGORIA_YA_EXISTE` y `CodigoError.CATEGORIA_YA_EXISTE` (409), y
  una nueva `DatosInvalidosException` (400 `DATOS_INVALIDOS`) en `comun/excepcion`, con su
  manejador, para la `posicion` fuera de rango que solo el service puede comprobar.
- No hay borrado: grupos y categorías se ocultan.
- `AGENTS.md`: `categoria/` en el árbol de paquetes, en la regla de dependencias y en la
  alternancia del `grep` de `comun/`; `DatosInvalidosException` en la jerarquía de excepciones.
- Sin cambios incompatibles.

## Capabilities

### New Capabilities
- `categorias`: gestión de los grupos de categorías y las categorías de un presupuesto (alta,
  edición, ocultar y mostrar, reordenar y mover, consulta y árbol), con aislamiento entre
  personas y presupuestos.

### Modified Capabilities
<!-- Ninguna: los requisitos de `presupuestos` y `cuentas` no cambian. -->

## Impact

- Código: `com.presupuesto.categoria.*` (nuevo), `CodigoError` (dos valores),
  `DatosInvalidosException` y `ManejadorGlobalExcepciones` (un manejador), `AGENTS.md`.
- Base de datos: tablas `grupos_categoria` y `categorias` creadas por Hibernate
  (`ddl-auto=update`), con restricciones únicas `(presupuesto_id, nombre_normalizado)` y
  `(grupo_id, nombre_normalizado)`.
- Dependencias: `categoria` → `presupuesto` y `comun`; `presupuesto` y `cuenta` no importan
  `categoria`; `comun` no importa ninguna feature.
- Fuera de alcance: frontend, categorías por defecto al crear un presupuesto, asignación mensual
  y "Ready to Assign", metas (targets), categorías automáticas de pago de tarjeta,
  transacciones, borrado y paginación.
