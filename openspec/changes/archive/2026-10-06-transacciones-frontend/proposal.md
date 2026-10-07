## Why

Sin transacciones, el presupuesto no tiene actividad: las categorías nunca gastan, las cuentas no
cambian y el "Listo para asignar" no recibe ingresos. El backend ya registra transacciones (con
división, estados, lote y saldos) y transferencias, pero el frontend no tiene ninguna pantalla
para verlas ni cargarlas. Además, escribir montos es la tarea más repetida de la app: conviene un
campo de monto con calculadora, exacto (sin coma flotante) y reutilizable en otros formularios.

## What Changes

- **Calculadora de montos** en `shared/calculadora/`, reutilizable:
  - `evaluarMonto()`: función pura que evalúa `+ - * /` con signo unario y devuelve milésimas, sin
    `eval` ni coma flotante (fracciones de `BigInt` y redondeo explícito).
  - `app-campo-monto`: componente con un `FormControl<number | null>` en milésimas, que muestra
    una pista con el resultado y marca "Monto no válido".
- **Nueva feature `features/transacciones/`**:
  - Modelos que copian los records de `com.presupuesto.transaccion.dto`.
  - Servicio de transacciones con todas las operaciones.
  - Servicios de **lectura propios** de cuentas y del árbol de categorías (solo GET, con sus
    propios modelos), porque una feature no importa de otra.
- **Pantalla `/presupuestos/:id/transacciones`** (enlace "Transacciones" en el menú):
  - Filtros (cuenta, categoría, fechas, estado, sin aprobar y búsqueda con espera de 300 ms) y
    página, guardados en la URL.
  - Tabla de servidor con columnas Salida y Entrada, estado, "sin aprobar" e insignia
    "Transferencia".
  - Saldos arriba de la tabla.
  - Menú por fila con las acciones que admite cada transacción (reconciliada, pata de
    transferencia, cuenta cerrada).
  - Diálogos de crear y editar (con editor de división), de mover de cuenta y de confirmación.
  - Barra de lote que excluye las filas a las que no se les puede aplicar la operación.
- `CODIGOS_API` no cambia: ya tiene todos los códigos que se usan.
- `AGENTS.md`: árbol con `features/transacciones/` y `shared/calculadora/`, y dos reglas: los
  montos editables usan `app-campo-monto`, y una feature que necesita datos de otra usa servicios
  de lectura propios.

Fuera de alcance: autocompletado de beneficiario, crear y editar transferencias (aquí solo se
muestran), transacciones programadas, conciliación guiada, importación CSV, deshacer, atajos de
teclado globales, editar varias a la vez fuera del lote y tema oscuro. No se agregan librerías.

## Capabilities

### New Capabilities
- `transacciones-frontend`: lista paginada con filtros en la URL, columnas y estados, crear,
  editar, dividir, aprobar, cambiar estado, mover, duplicar, borrar con confirmación, lote con
  exclusión de filas no aplicables, reconciliadas y patas de transferencia, saldos y errores.
- `calculadora-monto`: evaluación de expresiones de monto, casos inválidos, precisión sin coma
  flotante, separador decimal de la región y campo reutilizable.

### Modified Capabilities
- `presupuestos-frontend`: el menú lateral incluye `Transacciones`.

## Impact

- **Frontend** (`frontend/src/app`): archivos nuevos en `shared/calculadora/` y
  `features/transacciones/`; una ruta hija nueva en `app.routes.ts` y su spec.
- **API consumida**: `/transacciones` (lista, CRUD, aprobar, estado, mover-cuenta, duplicar, lote
  y saldos), `GET /cuentas` y `GET /categorias`, ya implementadas. El backend no cambia.
- **Dependencias**: ninguna nueva (`MatTable`, `MatPaginator`, `MatDatepicker`,
  `MatButtonToggle`, `MatTooltip` y `MatCheckbox` vienen con Material).
- **Documentación**: `AGENTS.md`.
