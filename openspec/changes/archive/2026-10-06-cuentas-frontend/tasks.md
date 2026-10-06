# Tasks

Todas las rutas son desde `frontend/src/app/` y cada test va junto al archivo que prueba. Los
comandos se ejecutan en `frontend/`. No se ejecuta `npm install` ni se agrega ninguna librería, y
no se hace commit.

## 1. Línea base

- [x] 1.1 Ejecutar `npx ng build` y `npx ng test --watch=false` antes de cambiar nada; verificar
      que el build termina sin errores y anotar cuántos tests pasan (255 esperados).

## 2. Dinero y validación compartidos (`shared/`)

- [x] 2.1 Crear `shared/formato/milliunits.ts` (test `shared/formato/milliunits.spec.ts`) con
      `leerMonto`, `aMilliunits` y `deMilliunits` según `design.md`, sin multiplicar ni dividir
      números con decimales. Verificar en el test, con la región como parámetro: `0` → `0`;
      `-5` → `-5000`; `-0.5` → `-500`; `-0` → `0`; `0,001` en `es-BO` → `1`; `1.234,567` en
      `es-BO` y `1,234.567` en `en-US` → `1234567`; `1234,5` en `es-BO` → `1234500`; `,5` →
      `500`; `1.005` → exactamente `1005`; `12.3456` y `1,5000` (`es-BO`) → `decimales`; `abc`,
      `1,2,3`, `12.5` en `es-BO`, `1.23.456` en `es-BO`, `--1`, `1e3` y `-` → `formato`; un
      entero de 20 dígitos → `rango`; `""` y `"   "` → `vacio`; `1 234,5` en `fr-FR` (con espacio
      común y con U+202F) → `1234500`; y `deMilliunits` de `1234567`, `-500`, `5000`, `0` y `1` en
      `es-BO` → `1234,567`, `-0,5`, `5`, `0`, `0,001`, y de `1234567` en `en-US` → `1234.567`;
      además, que `aMilliunits(deMilliunits(x))` devuelve `x` para varios valores.
- [x] 2.2 Crear `shared/validacion/monto.validator.ts` (test
      `shared/validacion/monto.validator.spec.ts`) con `montoValido(region?)`; verificar que
      vacío y `1,5` (`es-BO`) son válidos, y que `abc`, `1,2345` y un entero de 20 dígitos dan
      `montoFormato`, `montoDecimales` y `montoRango`.
- [x] 2.3 Agregar `CUENTA_YA_EXISTE` y `REGLA_NEGOCIO_VIOLADA` a `CODIGOS_API` en
      `shared/api/problema-api.ts` (test `shared/api/problema-api.spec.ts`, sin cambios);
      verificar con `npx ng build`.

## 3. Modelos y servicio de cuentas (`features/cuentas/`)

- [x] 3.1 Crear, uno por archivo: `features/cuentas/models/tipo-cuenta.model.ts` (`TipoCuenta`,
      `TIPOS_CUENTA`, `ETIQUETAS_TIPO_CUENTA`, `TIPOS_CON_SALDO_NEGATIVO`),
      `features/cuentas/models/cuenta-response.model.ts`,
      `features/cuentas/models/crear-cuenta-request.model.ts`,
      `features/cuentas/models/actualizar-cuenta-request.model.ts`,
      `features/cuentas/models/saldo-cuenta-response.model.ts` y
      `features/cuentas/models/datos-dialogo-cuenta.model.ts` (sin test propio: los cubren los
      tests de la página y del diálogo); verificar con `npx ng build`.
- [x] 3.2 Crear `features/cuentas/services/cuenta.service.ts` (test
      `features/cuentas/services/cuenta.service.spec.ts`); verificar con `HttpTestingController`
      que `listar(3, false)` y `listar(3, true)` hacen `GET /api/v1/presupuestos/3/cuentas` con
      `incluirCerradas=false`/`true`, que `crear` hace `POST` con el cuerpo, `actualizar(3, 5, …)`
      `PUT .../cuentas/5`, `cerrar`/`reabrir` `POST .../cuentas/5/cerrar` y `/reabrir`, y
      `saldos(3)` `GET /api/v1/presupuestos/3/transacciones/saldos`.

## 4. Diálogo de crear y editar

- [x] 4.1 Crear `features/cuentas/components/dialogo-cuenta.component.ts` (+ `.html`, `.scss`;
      test `features/cuentas/components/dialogo-cuenta.component.spec.ts`) según `design.md`:
      campos y valores por defecto, validadores (`montoValido`, saldo negativo por tipo con
      reevaluación al cambiar el tipo, tipo incompatible con el saldo existente al editar),
      mensajes de la spec, envío con `aMilliunits`, cierre solo con éxito y errores por `codigo`.
- [x] 4.2 En el test del diálogo (diálogo real con `MatDialog`, animaciones de Material
      deshabilitadas, `navigator.language` fijado con `vi.stubGlobal`, arneses de input, select,
      checkbox y botón), verificar: al crear se ven nombre, tipo, `Cuenta del presupuesto`
      marcada y saldo `0`; crear `"  Efectivo "` + `Efectivo` envía
      `{ nombre: 'Efectivo', tipo: 'EFECTIVO', enPresupuesto: true, saldoInicial: 0 }`; en
      `es-BO`, `Inversión` + casilla desmarcada + `5.000,5` envía `enPresupuesto: false` y
      `saldoInicial: 5000500`; `-100` con `Ahorro` muestra el mensaje y deshabilita el botón, y
      cambiar a `Tarjeta de crédito` lo vuelve válido y envía `-100000`; `12.3456` (`en-US`)
      muestra `Usa como máximo 3 decimales` y `abc` `Escribe un monto válido`; al editar se ven
      el nombre y el tipo actuales sin casilla ni saldo, y guardar hace `PUT` con
      `{ nombre, tipo }`; editar una tarjeta con saldo inicial negativo y elegir `Ahorro` marca
      el tipo y deshabilita el botón; nombre vacío y botón deshabilitado mientras se envía; 409
      `CUENTA_YA_EXISTE` en el nombre; 400 con `errores.saldoInicial` en el saldo; 422 como
      mensaje del diálogo; 500 con aviso genérico; en todos los errores el diálogo sigue abierto.

## 5. Página de cuentas

- [x] 5.1 Crear `features/cuentas/pages/cuentas.page.ts` (+ `.html`, `.scss`; test
      `features/cuentas/pages/cuentas.page.spec.ts`) según `design.md`: carga con `forkJoin` y
      `switchMap` sobre presupuesto, interruptor y recargas; secciones, saldos unidos por
      `cuentaId`, total, tipo en español, saldo negativo en color de error, menú por cuenta,
      estados de carga, vacío y error, y apertura del diálogo para crear y editar.
- [x] 5.2 En su test (presupuesto activo fijado a `{ id: 3, nombre: 'Casa', moneda: 'USD' }`,
      `navigator.language` en `en-US`, `HttpTestingController`, `MatDialog` y `MatSnackBar`
      sustituidos), verificar: spinner mientras carga; lista sin cerradas pide
      `incluirCerradas=false` y muestra las secciones `En el presupuesto` y `Seguimiento`; saldo
      `1500000` se ve como `$1,500.00` y el conciliado como `Conciliado: $1,000.00`; una cuenta
      sin saldo muestra `$0.00`; el saldo negativo tiene la clase de error; el tipo se ve en
      español; el total suma solo las abiertas del presupuesto; activar `Ver cuentas cerradas`
      pide `incluirCerradas=true` y muestra `Cerradas`; sin cuentas muestra
      `Aún no tienes cuentas`; un 500 abre el aviso con `Reintentar` y reintentar vuelve a pedir
      lista y saldos; un 401 no avisa; `Agregar cuenta` abre el diálogo en modo crear y, si
      devuelve una cuenta, recarga; `Editar` abre el diálogo con la cuenta; `Cerrar` y `Reabrir`
      llaman al endpoint y recargan; un 500 al cerrar muestra el aviso genérico.

## 6. Ruta y menú

- [x] 6.1 Agregar en `app.routes.ts` la ruta hija `cuentas` de `presupuestos/:presupuestoId` con
      `loadComponent` de `CuentasPage` y `data: seccion('Cuentas', 'account_balance')` (test
      `app.routes.spec.ts`): agregar un caso que entra con sesión a `/presupuestos/3/cuentas`,
      responde la lista de presupuestos, cuentas y saldos, y ve la pantalla con el enlace
      `Cuentas` en el menú lateral; y otro que, desde `/presupuestos/3`, pulsa `Cuentas` y llega
      a `/presupuestos/3/cuentas`.

## 7. Documentación

- [x] 7.1 Actualizar `AGENTS.md`: agregar `features/cuentas/` al árbol del frontend, describir en
      `shared/` las funciones de dinero (`leerMonto`, `aMilliunits`, `deMilliunits` en
      `formato/` y `montoValido` en `validacion/`) y, en la sección "Dinero", la regla de que
      todo monto escrito por la persona se convierte a milésimas solo con `aMilliunits()`/
      `leerMonto()`, nunca con `parseFloat`, `Number()` sobre el texto ni multiplicando por 1000;
      verificar que ninguna línea nueva supera 100 columnas.

## 8. Verificación final

- [x] 8.1 Ejecutar `npx ng build` y `npx ng test --watch=false`; verificar que el build no da
      errores y que pasan todos los tests (los de la línea base más los nuevos).
- [x] 8.2 Verificar desde `frontend/src/app` que `grep -rn "features/" core shared` devuelve cero
      líneas, que `grep -rnE "from '\.\./\.\./(auth|inicio|presupuestos)/" features/cuentas` también,
      que `grep -rnE "parseFloat|\* ?1000" features/cuentas shared/formato/milliunits.ts`
      no encuentra conversiones de montos, y que
      `npx prettier --check` sobre los archivos nuevos y modificados no da diferencias.
- [x] 8.3 Ejecutar `openspec validate cuentas-frontend --strict` sin errores.
