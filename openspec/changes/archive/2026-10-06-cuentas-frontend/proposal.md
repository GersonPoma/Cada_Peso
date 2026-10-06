## Why

El backend ya permite crear, listar, editar, cerrar y reabrir cuentas, y calcula el saldo de cada
una (`GET /transacciones/saldos`), pero el frontend todavía no tiene ninguna pantalla para
usarlas. Las cuentas son el primer paso del flujo de YNAB: sin ellas no hay dinero que asignar
ni dónde registrar transacciones. Además, es la primera pantalla donde la persona escribe un
monto, y esa conversión de texto a milésimas la van a reutilizar transacciones y asignación:
tiene que ser exacta desde el principio, sin aritmética de coma flotante.

## What Changes

- Nueva feature `features/cuentas/` con modelos, servicio HTTP (`listar`, `crear`,
  `actualizar`, `cerrar`, `reabrir`, `saldos`), la página de cuentas y un diálogo de crear y
  editar.
- Página `/presupuestos/:presupuestoId/cuentas`, con su enlace "Cuentas" en el menú lateral:
  - Secciones "En el presupuesto" y "Seguimiento" y, si se piden las cerradas, "Cerradas".
  - Cada cuenta con nombre, tipo en español, saldo (en rojo si es negativo) y saldo conciliado.
  - Total de las cuentas del presupuesto.
  - Interruptor "Ver cuentas cerradas" y menú por cuenta: editar, cerrar o reabrir.
  - Estados de carga, vacío y error con "Reintentar".
- Diálogo de cuenta. Crear: nombre, tipo, "Cuenta del presupuesto" y saldo inicial. Editar:
  solo nombre y tipo. Replica las reglas del backend, incluida la del saldo negativo, que solo
  se admite en tarjeta de crédito y préstamo.
- Nuevas funciones de dinero en `shared/formato/`: `aMilliunits()` lee un texto escrito según la
  región del usuario y devuelve el entero en milésimas sin pasar por coma flotante;
  `deMilliunits()` hace la conversión inversa. Además, un validador reutilizable de montos en
  `shared/validacion/`.
- `CODIGOS_API` incorpora `CUENTA_YA_EXISTE` y `REGLA_NEGOCIO_VIOLADA`.
- `AGENTS.md`: árbol con `features/cuentas/` y la regla de que todo monto escrito por el usuario
  se convierte con `aMilliunits()`.

Fuera de alcance: transacciones, borrar cuentas, saldo del día, gráficos y transferencias. No
se agregan librerías ni se ejecuta `npm install`.

## Capabilities

### New Capabilities
- `cuentas-frontend`: pantalla de cuentas del presupuesto activo (secciones, saldos, total,
  cuentas cerradas, estados vacío y error), diálogo de crear y editar con sus reglas y errores,
  cerrar y reabrir, enlace en el menú lateral, y conversión de montos escritos a milésimas sin
  coma flotante.

### Modified Capabilities
Ninguna. El menú lateral de `presupuestos-frontend` ya se arma a partir de las rutas hijas, así
que agregar "Cuentas" no cambia sus requisitos; el enlace se especifica en `cuentas-frontend`.

## Impact

- **Frontend** (`frontend/src/app`): archivos nuevos en `features/cuentas/`,
  `shared/formato/milliunits.ts` y `shared/validacion/monto.validator.ts`; cambios en
  `app.routes.ts` (una ruta hija) y su spec, y en `shared/api/problema-api.ts`.
- **API consumida**: `GET/POST /cuentas`, `PUT /cuentas/{id}`, `POST /cuentas/{id}/cerrar` y
  `/reabrir`, y `GET /transacciones/saldos`, todas bajo `/api/v1/presupuestos/{presupuestoId}` y
  ya implementadas. El backend no cambia.
- **Dependencias**: ninguna nueva. Se suman los módulos de Material ya instalados para la
  lista, el interruptor y la casilla (`list`, `slide-toggle`, `checkbox`).
- **Documentación**: `AGENTS.md`.
