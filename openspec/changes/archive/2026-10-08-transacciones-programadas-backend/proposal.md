# Proposal

## Why

Los gastos e ingresos que se repiten (renta, sueldo, suscripciones) hoy se registran a mano cada
vez. Una plantilla recurrente que genere sola las transacciones reales evita olvidos y mantiene
los saldos al día. Este change entrega solo el backend; la pantalla vendrá en otro change.

## What Changes

- Nueva feature `transaccionprogramada`: plantillas de transacciones recurrentes por presupuesto
  (cuenta, monto con signo, categoría y beneficiario opcionales, memo, frecuencia, fecha de
  inicio y de fin opcional, activa/pausada, `proximaFecha`, `ultimoError`).
- Frecuencias: `DIARIA`, `SEMANAL`, `CADA_2_SEMANAS`, `MENSUAL`, `CADA_3_MESES`, `ANUAL`. Las
  mensuales y anuales se calculan siempre desde `fechaInicio` (nunca encadenando) y se ajustan al
  último día del mes cuando el día no existe.
- Generador `GeneradorProgramadas.generarVencidas(hasta)`: por cada plantilla activa vencida crea
  una transacción real por ocurrencia (con su fecha original, máximo 366 por plantilla y
  ejecución), avanza `proximaFecha` y es idempotente gracias a una restricción única
  `(programada_id, fecha_ocurrencia)`. Una transacción de base por plantilla; el fallo de una no
  detiene a las demás.
- Se ejecuta al arrancar la aplicación, una vez al día (hora configurable por propiedad) y bajo
  demanda con `POST .../transacciones-programadas/generar` (solo el presupuesto de la URL).
- Endpoints CRUD bajo `/api/v1/presupuestos/{presupuestoId}/transacciones-programadas`, más
  `pausar`, `reanudar` y `generar`.
- Las transacciones generadas nacen `NO_CONCILIADA` y `aprobada=false`, y reutilizan la creación
  de `transaccion` (beneficiario auto-creado, categoría recordada, validaciones).
- `transacciones`: dos columnas aditivas (`programada_id`, `fecha_ocurrencia`) y
  `TransaccionResponse.programadaId` (nulo en las manuales).
- Fuera de alcance: plantillas de **transferencias**, frecuencias personalizadas, subtransacciones
  (split) en plantillas, interfaz de usuario, y cualquier migración de datos.

## Capabilities

### New Capabilities
- `transacciones-programadas`: plantillas recurrentes, cálculo de ocurrencias, generación
  idempotente de transacciones reales, pausa/reanudación y endpoints de gestión.

### Modified Capabilities
- `transacciones`: `TransaccionResponse` gana `programadaId` (aditivo) y se declara que las
  transacciones generadas por una plantilla nacen no conciliadas y sin aprobar.

## Impact

- Backend: nuevo paquete `com.presupuesto.transaccionprogramada` (importa `transaccion`, `cuenta`,
  `categoria`, `presupuesto` y `comun`; nadie lo importa). En `transaccion`: entidad, repositorio,
  `TransaccionService` (método público para crear desde plantilla, sin cambiar el comportamiento
  de `crear`) y `TransaccionResponse`.
- Esquema: tabla nueva `transacciones_programadas` y columnas nuevas en `transacciones` vía
  `ddl-auto=update`, sin datos que migrar.
- Configuración: propiedades `programadas.generacion.*`; `@EnableScheduling`; el `pom.xml` desactiva
  el generador automático durante los tests.
- La línea base de 1198 tests debe seguir pasando sin modificar ninguna aserción existente.

## Supuestos (decisiones ya tomadas)

- Crear una plantilla con `fechaInicio` pasada **no** genera nada dentro del `POST`: la primera
  generación ocurre en la siguiente ejecución del generador o con el endpoint manual.
- Reanudar tras una pausa **no** genera las ocurrencias del periodo pausado, ni siquiera la de
  hoy: esa la crea la siguiente ejecución del generador.
- `PUT` ignora `fechaInicio` (y `cuentaId`) si llegan en la petición: no se editan. Para cambiar
  el día de pago o la cuenta se crea otra plantilla.
