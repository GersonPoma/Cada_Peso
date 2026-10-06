# Design

## Context

Ver proposal.md - Why. Estado actual observado en `/frontend` (tras `presupuestos-frontend`):

- `LayoutPresupuestoPage` pinta las secciones hijas de `presupuestos/:presupuestoId` solo cuando
  `PresupuestoActivoService.presupuesto()` coincide con la URL, y arma el menú lateral con las
  rutas hijas que tienen `data.seccion` (helper `seccion(etiqueta, icono)` en `app.routes.ts`).
  Cambiar de presupuesto en el selector navega a `/presupuestos/{id}` (Inicio).
- `shared/formato/` tiene `regionUsuario()` (`navigator.language` validado, `en-US` de respaldo)
  y el pipe `monto` (milésimas + moneda, `Intl.NumberFormat`, divide por 1000 **solo para
  mostrar**). No existe ninguna conversión de texto escrito a milésimas.
- `shared/` ofrece `leerProblemaApi`, `CODIGOS_API`, `MENSAJE_ERROR_GENERICO`, `mensajeDeError`
  (prioriza el error `servidor`), `aplicarErroresDeCampos` y `sobreTextoRecortado`.
  `DialogoPresupuestoComponent` y `LayoutPresupuestoPage` son el patrón a seguir: diálogo que
  hace él mismo la petición y solo se cierra con éxito; aviso con "Reintentar" en errores de
  carga; 401 sin aviso.
- Separadores observados con `Intl.NumberFormat(...).formatToParts(1234567.8)`: `es-BO`,
  `es-ES` y `de-DE` usan `.` de miles y `,` decimal; `en-US` usa `,` y `.`; `fr-FR` usa un
  espacio estrecho (U+202F) de miles y `,` decimal.
- Backend (`cuentas` y `transacciones`, archivados): ver proposal. `GET /transacciones/saldos`
  devuelve un `SaldoCuentaResponse` por cada cuenta, abierta o cerrada, con `saldo` y
  `saldoConciliado` que ya incluyen el saldo inicial.

## Goals / Non-Goals

**Goals:**
- Una conversión de montos exacta y reutilizable por transacciones y asignación.
- Replicar en el diálogo todas las reglas del backend, para que un 422 solo llegue por carreras
  o datos viejos.

**Non-Goals:**
- Ordenar las cuentas en el frontend: se respeta el orden de la API (nombre sin mayúsculas).
- Editar `enPresupuesto` o `saldoInicial`, ni mostrar el saldo inicial en la lista.
- Sumar saldos de cuentas de seguimiento o cerradas en el total.
- Aceptar notación científica, signos al final, paréntesis contables o el signo `+`.

## Decisions

**Conversión de montos: `shared/formato/milliunits.ts` con lectura por texto.**
API pública:
- `leerMonto(texto: string, region = regionUsuario()): LecturaMonto`, donde `LecturaMonto` es
  `{ estado: 'vacio' } | { estado: 'valido'; milliunits: number } |
  { estado: 'invalido'; motivo: 'formato' | 'decimales' | 'rango' }`.
- `aMilliunits(texto, region = regionUsuario()): number | null`: atajo que devuelve las
  milésimas o `null` si está vacío o es inválido.
- `deMilliunits(milliunits: number, region = regionUsuario()): string`: texto editable.

Algoritmo de `leerMonto`:
1. Obtener los separadores de la región con `formatToParts(1234567.8)` (`group` y `decimal`).
   Si el de miles es un espacio especial (U+00A0 o U+202F), aceptar también el espacio común.
2. Recortar; si queda vacío → `vacio`. Un `-` inicial opcional (también `−`, U+2212) da el
   signo.
3. Partir en el separador decimal; más de una aparición → `formato`. Parte entera: solo dígitos,
   o grupos de miles bien formados (`^\d{1,3}(G\d{3})+$`). Parte decimal: solo dígitos. Ambas
   vacías → `formato`. Una sola vacía se completa con `0` (`,5` vale `0,5`).
4. Más de 3 dígitos decimales → `decimales` (aunque los sobrantes sean ceros: `1,5000` también
   se rechaza, para no aceptar en silencio una precisión que no se guarda).
5. Componer los dígitos `entera + decimal.padEnd(3, '0')`, quitar ceros a la izquierda y
   convertir con `Number(...)` sobre esa cadena de dígitos, que es una lectura de entero exacta;
   si no es `Number.isSafeInteger` → `rango`. Aplicar el signo, normalizando `-0` a `0`.

En ningún paso se multiplica ni divide un número con decimales. `deMilliunits` también trabaja con
enteros: `resto = abs % 1000`, `entera = (abs - resto) / 1000` (ambas exactas) y la parte
decimal es `String(resto).padStart(3, '0')` sin ceros finales.
Alternativas descartadas: `parseFloat(texto) * 1000` con `Math.round` (pierde exactitud cerca de
2^53 y depende del redondeo binario; el pedido lo prohíbe expresamente) y `BigInt` (exacto, pero
el contrato con la API es `number` y no hace falta pasar de 2^53 milésimas).

**Validador reutilizable `montoValido()` en `shared/validacion/monto.validator.ts`.**
Devuelve `null` para vacío o válido, y `{ montoFormato: true }`, `{ montoDecimales: true }` o
`{ montoRango: true }` según el motivo de `leerMonto`. Acepta la región como parámetro opcional
para poder fijarla en los tests. Es el que usarán transacciones y asignación.

**Regla del saldo negativo por tipo, con validadores que leen al hermano.**
`saldoInicial` lleva, además de `montoValido()`, un validador de la feature
(`saldoNegativoPermitido`) que lee `control.parent?.get('tipo')` y devuelve
`{ saldoNegativoNoPermitido: true }` si el monto es negativo y el tipo no está en
`TIPOS_CON_SALDO_NEGATIVO` (`TARJETA_CREDITO`, `PRESTAMO`). Al editar, como el saldo inicial no
está en el formulario, el control `tipo` lleva otro validador que compara con el
`saldoInicial` de la cuenta recibida (`{ tipoNoAdmiteSaldoNegativo: true }`). El diálogo se
suscribe a `tipo.valueChanges` y llama a `saldoInicial.updateValueAndValidity()`, así la regla
se reevalúa al cambiar el tipo. Ambos validadores viven en el componente del diálogo porque son
reglas de negocio de cuentas, no genéricas.
Alternativa descartada: un validador de grupo, que deja el error en el `FormGroup` y obliga a
pintar el `mat-error` del campo a mano.

**Modelos.** `TipoCuenta` es una unión de literales en `models/tipo-cuenta.model.ts`, junto con
`TIPOS_CUENTA` (orden del selector), `ETIQUETAS_TIPO_CUENTA` (`Record<TipoCuenta, string>`) y
`TIPOS_CON_SALDO_NEGATIVO`. Interfaces `CuentaResponse`, `CrearCuentaRequest`
(`{ nombre, tipo, enPresupuesto, saldoInicial }`, siempre explícitos),
`ActualizarCuentaRequest` (`{ nombre, tipo }`), `SaldoCuentaResponse` y
`DatosDialogoCuenta` (`{ modo: 'crear' } | { modo: 'editar'; cuenta: CuentaResponse }`), una por
archivo.

**Servicio `CuentaService`.** Recibe el `presupuestoId` en cada método (lo da la página desde
`PresupuestoActivoService`) en lugar de leerlo él mismo, para que sea explícito y fácil de
probar: `listar(presupuestoId, incluirCerradas)`, `crear`, `actualizar`, `cerrar`, `reabrir` y
`saldos(presupuestoId)`. `incluirCerradas` viaja siempre como parámetro (`true` o `false`).

**Página `CuentasPage`: carga reactiva con señales y `switchMap`.**
- Lee `presupuestoId` y `moneda` de `PresupuestoActivoService` (el layout garantiza que ya está
  fijado). Mantiene `incluirCerradas` (señal) y un contador `recargas` (señal) que incrementan
  "Reintentar" y cada operación terminada.
- `toObservable(computed(() => ({ id, incluirCerradas, recargas })))` + `switchMap` a
  `forkJoin([listar, saldos])`. `switchMap` descarta una respuesta vieja si el interruptor cambia
  a mitad de camino. El resultado se guarda en señales `cuentas` y `saldos` y un `estado`
  (`cargando | listo | error`).
- `computed` derivados: un `Map<cuentaId, SaldoCuentaResponse>`, las filas
  `{ cuenta, saldo, saldoConciliado }` con `0` si falta el saldo, las tres secciones (abiertas en
  el presupuesto, abiertas de seguimiento, cerradas) y el total (suma entera de los `saldo` de la
  primera sección).
- Presentación con `mat-list`: cada fila muestra nombre, tipo, saldo con el pipe `monto` (clase
  `negativo` con `--mat-sys-error` si es menor que 0) y "Conciliado: ..." como línea secundaria;
  a la derecha un `mat-icon-button` (`aria-label="Acciones de {nombre}"`, ícono `more_vert`)
  con `mat-menu`: `Editar` y `Cerrar` en las abiertas, `Reabrir` en las cerradas.
- Cabecera de la página: título "Cuentas", total, `mat-slide-toggle` "Ver cuentas cerradas" y
  botón "Agregar cuenta". El estado vacío ("Aún no tienes cuentas" + "Agregar cuenta") se
  muestra cuando no hay ninguna fila en ninguna sección.
- Cerrar y reabrir llaman al servicio y al responder incrementan `recargas`; un error que no sea
  401 abre el aviso genérico (`Cerrar`). Un error de carga abre el aviso con "Reintentar".

**Cerradas en una tercera sección "Cerradas".** El pedido habla de dos secciones y de ver las
cerradas; mezclar las cerradas dentro de "En el presupuesto" o "Seguimiento" haría que su saldo
pareciera parte del total. Una sección propia al final (como YNAB) lo deja claro, y el total no
las cuenta.

**Diálogo `DialogoCuentaComponent`.** Mismo patrón que `DialogoPresupuestoComponent`: hace la
petición, se cierra con la `CuentaResponse` solo si tiene éxito, `disableClose` mientras envía,
"Cancelar" con `[mat-dialog-close]="undefined"`. Formulario: `nombre` (`sobreTextoRecortado`
con `required` y `maxLength(100)`), `tipo` (`Validators.required`, `null` al crear), y solo al
crear `enPresupuesto` (`mat-checkbox`, `true`) y `saldoInicial` (texto, valor inicial
`deMilliunits(0)` = `"0"`, `inputmode="decimal"`). Al editar, esos dos controles no se crean,
así `getRawValue()` no puede enviarlos. El saldo se envía como `aMilliunits(texto) ?? 0`.
Errores: `CUENTA_YA_EXISTE` → error `servidor` en `nombre`; `DATOS_INVALIDOS` →
`aplicarErroresDeCampos` (aviso genérico si queda una clave sin control);
`REGLA_NEGOCIO_VIOLADA` → señal `errorGeneral` mostrada en un `<p role="alert">` del diálogo;
401 → nada; otro → aviso genérico.

**Ruta y menú.** Una ruta hija nueva en `app.routes.ts`:
`{ path: 'cuentas', loadComponent: ..., data: seccion('Cuentas', 'account_balance') }`.

### Archivos nuevos o modificados

Rutas desde `frontend/src/app/`.

| Archivo | Estado | Test |
|---|---|---|
| `shared/formato/milliunits.ts` | nuevo | `shared/formato/milliunits.spec.ts` |
| `shared/validacion/monto.validator.ts` | nuevo | `shared/validacion/monto.validator.spec.ts` |
| `shared/api/problema-api.ts` | modificado | `shared/api/problema-api.spec.ts` (sin cambios) |
| `features/cuentas/models/tipo-cuenta.model.ts` | nuevo | (constantes, cubiertas por los tests de la página y el diálogo) |
| `features/cuentas/models/cuenta-response.model.ts` | nuevo | (interfaz, sin test) |
| `features/cuentas/models/crear-cuenta-request.model.ts` | nuevo | (interfaz, sin test) |
| `features/cuentas/models/actualizar-cuenta-request.model.ts` | nuevo | (interfaz, sin test) |
| `features/cuentas/models/saldo-cuenta-response.model.ts` | nuevo | (interfaz, sin test) |
| `features/cuentas/models/datos-dialogo-cuenta.model.ts` | nuevo | (interfaz, sin test) |
| `features/cuentas/services/cuenta.service.ts` | nuevo | `features/cuentas/services/cuenta.service.spec.ts` |
| `features/cuentas/components/dialogo-cuenta.component.ts` (+ `.html`, `.scss`) | nuevo | `features/cuentas/components/dialogo-cuenta.component.spec.ts` |
| `features/cuentas/pages/cuentas.page.ts` (+ `.html`, `.scss`) | nuevo | `features/cuentas/pages/cuentas.page.spec.ts` |
| `app.routes.ts` | modificado | `app.routes.spec.ts` |

## Risks / Trade-offs

- [Una persona en `es-BO` escribe `12.5` pensando en un decimal] → Con `.` como separador de
  miles mal agrupado se rechaza como `Escribe un monto válido`, nunca se lee como `125`; el campo
  muestra como ayuda un ejemplo en el formato de la región (`deMilliunits(1234500)` → `1234,5`).
- [El saldo de una cuenta llega por una petición distinta de la lista] → `forkJoin` espera
  ambas y una cuenta sin saldo muestra `0`.
- [Dos peticiones extra (lista y saldos) después de cada operación] → Son listas pequeñas y
  mantener la verdad del backend evita recalcular saldos en el frontend.
- [El total suma números en JavaScript] → Son enteros en milésimas, exactos hasta 2^53
  (9 billones de unidades); se documenta.

## Migration Plan

Solo frontend, sin datos nuevos. Rollback: revertir el change.
