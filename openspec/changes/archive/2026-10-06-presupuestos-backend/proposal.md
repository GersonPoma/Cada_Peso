# Proposal

## Why

Cada Peso es un presupuesto base cero, pero hoy no existe el concepto de **presupuesto**: no hay
dónde colgar cuentas, categorías ni transacciones. Todo recurso de negocio futuro debe pertenecer
a un presupuesto de una persona, y hay que fijar ahora la forma de validar esa pertenencia para
que las features siguientes la reutilicen sin reinventarla.

## What Changes

- Nueva feature backend `presupuesto` con la entidad `Presupuesto` (tabla `presupuestos`):
  usuario dueño, nombre (1 a 100 tras recortar), moneda ISO 4217 inmutable y unicidad de
  (usuario, nombre sin distinguir mayúsculas).
- Endpoints autenticados bajo `/api/v1/presupuestos`: crear (`POST`, 201), listar todos sin
  paginación ordenados por nombre (`GET`), detalle (`GET /{id}`) y renombrar (`PUT /{id}`, solo el
  nombre). Sin `DELETE`.
- Un presupuesto inexistente o de otra persona responde siempre `404`, nunca `403`.
- Nombre repetido del mismo usuario: `409` con el nuevo `CodigoError.PRESUPUESTO_YA_EXISTE`.
- Si el `POST` no trae moneda, se usa la moneda predeterminada del perfil del usuario.
- `PresupuestoService.obtenerDelUsuario(presupuestoId, usuarioId)`: pieza reutilizable que
  devuelve el presupuesto o lanza `RecursoNoEncontradoException`.
- Al registrarse, en la misma transacción, se crea el presupuesto `Mi presupuesto` con la moneda
  del perfil. Los usuarios ya existentes sin presupuesto quedan fuera de alcance.
- `AGENTS.md` documenta la convención: todo recurso de negocio cuelga de un presupuesto, con URLs
  `/api/v1/presupuestos/{presupuestoId}/<recurso>`, y cada operación valida la pertenencia con
  `obtenerDelUsuario`.

## Capabilities

### New Capabilities
- `presupuestos`: creación, consulta, listado y renombrado de los presupuestos de una persona,
  con aislamiento entre usuarios, unicidad de nombre y moneda inmutable.

### Modified Capabilities
- `registro-usuarios`: el registro exitoso también deja a la persona con un presupuesto inicial
  `Mi presupuesto`, en la moneda de su perfil y de forma atómica con el resto del alta.

## Impact

- Backend: nuevo paquete `com.presupuesto.presupuesto`; cambios en `AuthService`
  (`auth`), en `CodigoError` (`comun/excepcion`) y en `AGENTS.md`.
- Base de datos: nueva tabla `presupuestos` (la crea Hibernate con `ddl-auto=update`).
- API: nuevas rutas `/api/v1/presupuestos/**`; sin cambios incompatibles en las existentes.
- Frontend: sin cambios (fuera de alcance), igual que cuentas, borrado, archivar y Fresh Start.
