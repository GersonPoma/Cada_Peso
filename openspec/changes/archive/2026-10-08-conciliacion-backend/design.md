# Design

## Context

- `Transaccion.estado` ya tiene `NO_CONCILIADA`, `CONCILIADA` y `RECONCILIADA`. `RECONCILIADA` no
  se edita, mueve, borra ni cambia de estado, y `TransaccionService.cambiarEstado` rechaza fijarla
  a mano ("la fijará la conciliación"). Nada la fija todavía.
- `TransaccionRepository.sumarPorCuenta` y `SaldoCuentaService` calculan
  `saldoConciliado = saldoInicial + suma(estado <> NO_CONCILIADA)`. Esa es la única definición de
  "conciliado" y se reutiliza.
- `CalculadoraMes` define `listoParaAsignar = ingresos - asignado` con
  `ingresos = ingresosSinCategoria + saldosInicialesPositivos`. `ingresosSinCategoria` solo cuenta
  entradas sin categoría, sin subtransacciones, en cuentas del presupuesto que no son tarjeta y
  que no sean la entrada de una transferencia con par en el presupuesto.
- Hay bloqueo pesimista en `PresupuestoRepository.findByIdParaActualizar` y en
  `TransaccionProgramadaRepository`: un solo bloqueo por transacción de base.
- Dependencias vigentes (AGENTS.md): `conciliacion` puede importar `transaccion`, `cuenta`,
  `presupuesto` y `comun`; nadie la importa.

## Goals / Non-Goals

**Goals:**
- Conciliar una cuenta contra el extracto: diferencia, ajuste opcional, reconciliación por lote e
  historial, atómico y sin duplicar ajustes bajo concurrencia.
- Cambio mínimo en `transaccion` y `cuenta`: solo métodos nuevos, sin alterar los existentes.

**Non-Goals:**
- Deshacer una conciliación, frontend, editar o borrar el registro, conciliar varias cuentas a la
  vez, importar el extracto.

## Decisions

### D1. Diferencia hasta la fecha del extracto (no con el saldo conciliado total)
`saldoConciliadoAlCorte = saldoInicial + suma(CONCILIADA y RECONCILIADA con fecha <= fecha)` y
`diferencia = saldoExtracto - saldoConciliadoAlCorte`. El extracto describe la cuenta a una fecha;
lo conciliado después no está en él.

Ejemplo con una transacción posterior: saldo inicial 500.000; conciliadas -120.000 (03-oct),
-80.000 (05-oct) y -50.000 (12-oct); extracto al 10-oct = 300.000.
- Total: 500.000 - 120.000 - 80.000 - 50.000 = 250.000 → diferencia +50.000 (falsa: crearía un
  ajuste de +50.000 que no existe en el banco).
- Al corte: 500.000 - 120.000 - 80.000 = 300.000 → diferencia 0 (correcta). La del 12-oct sigue
  `CONCILIADA` y no se reconcilia.

Alternativa descartada: usar el total. Obliga a reconciliar o a ajustar de más transacciones que
el banco aún no muestra. `saldoConciliado` (total) se devuelve igual, por informar, y coincide con
`GET /transacciones/saldos`.

**Reutilización:** se agrega a `TransaccionRepository` la consulta
`sumaConciliadaDeCuenta(cuentaId, hasta)` con el mismo criterio `estado <> NO_CONCILIADA` que
`sumarPorCuenta`, y `SaldoCuentaService.saldoConciliadoAl(cuenta, hasta)`. El total usa la fecha
`9999-12-31` y un test exige que coincida con `SaldoCuentaService.listar`. `sumarPorCuenta` no se
toca.

### D2. Lógica pura de diferencia
`CalculoConciliacion` (funciones estáticas, sin Spring): `diferencia(saldoExtracto,
saldoConciliadoAlCorte)`, y la regla de categoría del ajuste
(`exigeCategoria(enPresupuesto, tipo, monto)`). Se prueba con números sin base de datos.

### D3. El ajuste y el invariante dinero-en-cuentas = listoParaAsignar + suma(disponibles)
El ajuste es una transacción normal de la cuenta por `diferencia`, fecha del extracto,
beneficiario "Ajuste de conciliación", `CONCILIADA`, `aprobada=true`. Con la categoría pedida
originalmente ("sin categoría") el invariante se rompe en negativos. Números (cuenta corriente
del presupuesto; saldo inicial 500.000; asignado a Comida 200.000; gasto de Comida -120.000):

| Situación | Dinero en cuentas | listoParaAsignar | Disponibles | Invariante |
|---|---|---|---|---|
| Base | 380.000 | 500.000 - 200.000 = 300.000 | 200.000 - 120.000 = 80.000 | 380 = 300 + 80 ✓ |
| Ajuste +10.000 sin categoría (ingreso) | 390.000 | 310.000 | 80.000 | 390 = 310 + 80 ✓ |
| Ajuste -10.000 sin categoría | 370.000 | 300.000 | 80.000 | 370 ≠ 380 ✗ |
| Ajuste -10.000 en Comida | 370.000 | 300.000 | 70.000 | 370 = 300 + 70 ✓ |

Decisión: en una cuenta del presupuesto, el ajuste **negativo exige `categoriaId`** (nuevo campo
opcional del request, 422 si falta). El positivo sin categoría cuenta como ingreso (sube
`listoParaAsignar`). Una **tarjeta de crédito** también exige categoría en el positivo, porque
`ingresosSinCategoria` excluye las entradas de tarjetas y el invariante se rompería igual. Una
cuenta fuera del presupuesto no entra en ningún cálculo: no admite `categoriaId` (422, como las
transferencias). La categoría de pago de tarjeta se rechaza con las reglas de `transaccion`.
Es la única desviación del texto de la petición ("sin categoría"); se documenta aquí porque lo
contrario deja el presupuesto descuadrado en silencio.

### D4. No se guarda `conciliacion_id` en las transacciones
`transacciones` no cambia. Justificación: sin deshacer en alcance, la columna solo serviría a un
cambio futuro, tocaría la tabla más grande y obligaría a que el `UPDATE` por lote la rellene. El
registro guarda `cantidadReconciliadas` y `transaccionAjusteId`, suficiente para auditar. Costo:
el futuro "deshacer" deberá agregar la columna y deducir las ya reconciliadas (por fecha y orden
de las conciliaciones), ambiguo con transacciones de fecha anterior reconciliadas tarde; por eso
el riesgo R1 lo deja manual.

### D5. Reconciliación por lote con un `UPDATE`
`TransaccionRepository.reconciliarConciliadasHasta(cuentaId, hasta, ahora)`:
`update Transaccion t set t.estado = RECONCILIADA, t.fechaActualizacion = :ahora where
t.cuenta.id = :cuentaId and t.estado = CONCILIADA and t.fecha <= :hasta`, que devuelve la
cantidad (`flushAutomatically` y `clearAutomatically`, como `desvincularProgramada`). El ajuste se
guarda con `saveAndFlush` antes, así entra en el lote. Se fija `fechaActualizacion` a mano porque
el `UPDATE` JPQL no dispara `@UpdateTimestamp`; `ahora` sale del `Clock`.

### D6. Concurrencia
`CuentaRepository.findByIdAndPresupuestoIdParaActualizar` (`PESSIMISTIC_WRITE`, un solo bloqueo,
sobre la cuenta; nunca se bloquea también el presupuesto). Se toma después de validar el
presupuesto y antes de calcular la diferencia: la segunda conciliación espera, recalcula con lo
ya reconciliado y obtiene diferencia 0, así no duplica el ajuste. La carrera real no tiene test
automático (necesita dos transacciones de base en paralelo); solo se prueba la secuencia.
`cambiarEstado` y `crear` de transacciones no toman este bloqueo: ver R2.

### D7. Reglas y orden de errores
1. Presupuesto (`obtenerDelUsuario`) 404 → cuenta 404. Solo `POST` busca la cuenta con
   `findByIdAndPresupuestoIdParaActualizar` (bloqueo); `GET /estado` y el historial usan
   `findByIdAndPresupuestoId`, sin bloqueo (son de solo lectura y no deben esperar a un `POST`).
2. 400 (`DatosInvalidosException`): `saldoExtracto` o `fecha` ausentes, o `fecha` > hoy. El "hoy" sale del
   bean `Clock` (`LocalDate.now(clock)`, reloj UTC, mismo criterio que la generación de
   transacciones programadas): en Bolivia, desde las 20:00 el hoy UTC ya es el día siguiente, así
   que una fecha de extracto "de mañana" local se acepta esa noche.
   En el estado los parámetros van como opcionales y se validan en el service para no tocar el
   manejador global.
3. 422: cuenta cerrada (solo `POST`); diferencia ≠ 0 sin `crearAjuste`; categoría exigida
   faltante; `categoriaId` en cuenta fuera del presupuesto.
4. Dentro del ajuste: categoría inexistente 404, de pago de tarjeta 422 (reglas de `transaccion`).
Todo es una sola transacción: cualquier error revierte ajuste, lote y registro.
Se permiten tarjetas y cuentas con `enPresupuesto=false`. Una `fecha` anterior a una conciliación
previa se permite (no hay regla de monotonía). `crearAjuste=true` con diferencia 0 no crea nada.

### D8. Superficie añadida en `transaccion` (mínima)
- `TransaccionService.crearAjuste(Presupuesto, Cuenta, LocalDate, long monto, Long categoriaId)`:
  resuelve la categoría con `referencias.categoriaParaRegistrar`, el beneficiario con
  `BeneficiarioService.obtenerOCrear` (sin recordar categoría predeterminada; el
  beneficiario sí aparece en los listados, ver R0) y guarda con `saveAndFlush`. Devuelve la `Transaccion`.
- `TransaccionService.reconciliarHasta(Long cuentaId, LocalDate hasta)`: llama al `UPDATE`.
- `TransaccionService.listarNoConciliadas(Long cuentaId, int limite)` y
  `contarNoConciliadas(Long cuentaId)` (orden fecha desc, id desc).
- `SaldoCuentaService.saldoConciliadoAl(Cuenta, LocalDate)`.
`conciliacion` no importa categorías ni beneficiarios; todo pasa por `transaccion`.

### D9. Modelo `Conciliacion`
Tabla `conciliaciones`: `presupuesto` y `cuenta` (`@ManyToOne` LAZY, no nulos),
`fecha` (`LocalDate`), `saldoExtracto`, `ajuste` (`long`, 0 si no hubo), `transaccionAjusteId`
(`Long` nullable, sin relación JPA ni FK, igual que `Transaccion.programadaId`),
`cantidadReconciliadas` (`int`), y `fechaCreacion` heredada de `EntidadBase`. Índice
`(cuenta_id, fecha desc)`. Sin setters (se construye con el builder); sin `PUT` ni `DELETE`.
Historial: máximo 50, `fecha desc, id desc`.

### Tabla de paquetes

| Clase | Paquete | Test (mismo paquete) |
|---|---|---|
| `Conciliacion` | `com.presupuesto.conciliacion.entity` | (cubierta por integración) |
| `ConciliacionRepository` | `com.presupuesto.conciliacion.repository` | (cubierta por integración) |
| `CrearConciliacionRequest` | `com.presupuesto.conciliacion.dto.request` | `CrearConciliacionRequestTest` |
| `ConciliacionResponse` | `com.presupuesto.conciliacion.dto.response` | `ConciliacionResponseTest` |
| `EstadoConciliacionResponse` | `com.presupuesto.conciliacion.dto.response` | `EstadoConciliacionResponseTest` |
| `CalculoConciliacion` | `com.presupuesto.conciliacion.service` | `CalculoConciliacionTest` |
| `ConciliacionService` | `com.presupuesto.conciliacion.service` | `ConciliacionServiceTest` |
| `ConciliacionController` | `com.presupuesto.conciliacion.controller` | `ConciliacionIntegracionTest` |
| `TransaccionService` (métodos nuevos) | `com.presupuesto.transaccion.service` | `TransaccionServiceConciliacionTest` |
| `SaldoCuentaService` (método nuevo) | `com.presupuesto.transaccion.service` | `SaldoCuentaServiceConciliadoTest` |
| `TransaccionRepository` (consultas nuevas) | `com.presupuesto.transaccion.repository` | (vía los tests de service) |
| `CuentaRepository` (método con bloqueo) | `com.presupuesto.cuenta.repository` | (vía integración) |

`ConciliacionResponse` y `EstadoConciliacionResponse` llevan `desde(...)`; el estado incluye
`TransaccionResponse` de `transaccion.dto.response` (permitido).

## Risks / Trade-offs

- **R0. Beneficiario visible** → "Ajuste de conciliación" se crea con `obtenerOCrear` como
  cualquier beneficiario del presupuesto, por lo que aparece en el listado y el autocompletado de
  beneficiarios (y puede renombrarse o borrarse como otro). Es intencional: reutiliza el
  mecanismo existente sin un tipo especial. No recuerda categoría.
- **R1. Sin deshacer** → un error de conciliación (extracto equivocado) deja transacciones
  `RECONCILIADA` y exige intervención manual en la base. Mitigación: `GET /estado` permite
  revisar antes; deshacer será un change aparte.
- **R2. Carrera con cambios de estado** → una transacción pasada a `CONCILIADA` entre el cálculo
  de la diferencia y el `UPDATE` quedaría reconciliada sin estar en la diferencia. Mitigación: la
  ventana es de milisegundos y `cambiarEstado` no toma el bloqueo de la cuenta; documentado, sin
  test automático.
- **R3. Carrera real sin test** → el bloqueo de D6 se prueba solo por secuencia; la concurrencia
  real no tiene test automático.
- **R4. Ajuste negativo exige categoría** → difiere de la petición literal; sin ello el
  presupuesto se descuadra (D3). Reversible si se prefiere permitirlo y documentar el descuadre.
- **R5. Conciliar con fecha pasada** deja fuera del lote lo posterior, a propósito (D1).

## Migration Plan

Hibernate crea `conciliaciones` al arrancar (`ddl-auto=update`); no toca datos existentes.
Rollback: borrar el paquete y la tabla; `transacciones` queda igual.
