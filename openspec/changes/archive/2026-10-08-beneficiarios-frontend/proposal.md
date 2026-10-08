## Why

El backend ya gestiona beneficiarios (búsqueda por prefijo, categoría predeterminada recordada y
vínculo `beneficiarioId` en cada transacción), pero el frontend sigue con un campo de texto libre:
la persona reescribe los mismos nombres, no recibe la categoría que usó la última vez y, si
renombra un beneficiario, la tabla sigue mostrando el texto viejo. Tampoco hay ninguna pantalla
para corregir el nombre o la categoría predeterminada de un beneficiario.

## What Changes

- **Autocompletado de beneficiario dentro de `features/transacciones/`** (sin importar nada de
  `features/beneficiarios`):
  - Modelo propio `BeneficiarioSugerido` y servicio de solo lectura `BeneficiarioLecturaService`
    (`GET /beneficiarios`, con `q` y `limite` o la lista completa).
  - Componente `app-campo-beneficiario` (`MatAutocomplete`) que reemplaza el campo de texto del
    diálogo de transacción: sugerencias por prefijo con espera de 250 ms, respuestas atrasadas y
    errores de red ignorados, prefijo resaltado, categoría predeterminada como texto secundario,
    texto libre con la pista "Se creará un beneficiario nuevo", contador de 100 caracteres y
    teclado.
  - **Categoría recordada**: al elegir una sugerencia con categoría predeterminada, el diálogo
    rellena la categoría solo si se está creando, no está en "Dividir", la categoría está vacía y
    sin tocar y la categoría existe en la lista; muestra "Sugerida por el beneficiario".
  - **Reintento único ante `409 BENEFICIARIO_YA_EXISTE`** al guardar una transacción; si vuelve
    a fallar, el mensaje aparece en el campo beneficiario.
  - **Tabla**: la columna Beneficiario muestra el nombre actual del beneficiario vinculado
    (`beneficiarioId`, resuelto con la lista completa cargada una vez) y el texto de la
    transacción si no hay vínculo; la lista se refresca tras guardar una transacción.
  - `TransaccionResponse` del frontend gana `beneficiarioId` (y `programadaId`, que el backend ya
    devuelve).
- **Nueva feature `features/beneficiarios/`**: pantalla `/presupuestos/:id/beneficiarios` (enlace
  "Beneficiarios" en el menú) con tabla, buscador local por prefijo, estados de carga, vacío y
  error, y un diálogo para crear y editar (nombre y categoría predeterminada con "Ninguna"), con
  sus propios modelos, servicio y servicio de lectura de categorías. Sin borrado.
- `CODIGOS_API` gana `BENEFICIARIO_YA_EXISTE`.
- `AGENTS.md`: árbol del frontend con `features/beneficiarios/` y la regla de que el
  autocompletado vive en `features/transacciones` con su servicio de lectura.

Fuera de alcance: fusionar o borrar beneficiarios, reglas de renombrado automático, sugerir por
ubicación, importar beneficiarios, transferencias y tema oscuro. El filtro de búsqueda de texto de
transacciones no cambia (sigue buscando en el texto guardado). No se agregan librerías ni cambia
el backend.

## Capabilities

### New Capabilities
- `beneficiarios-frontend`: autocompletado (sugerencias por prefijo, texto libre, pista de
  beneficiario nuevo, respuestas atrasadas, error de red, teclado), categoría recordada con sus
  cuatro condiciones, reintento ante 409, nombre vinculado en la tabla y pantalla de gestión
  (lista, filtro, crear, editar, categoría predeterminada, 409, 400, 404, vacío, carga y error).

### Modified Capabilities
- `transacciones-frontend`: el campo de beneficiario del diálogo pasa a ser un autocompletado y
  la columna Beneficiario usa el nombre del beneficiario vinculado.
- `presupuestos-frontend`: el menú lateral incluye `Beneficiarios`.

## Impact

- **Frontend** (`frontend/src/app`): archivos nuevos en `features/transacciones/` y
  `features/beneficiarios/`; cambios en `dialogo-transaccion`, `tabla-transacciones`,
  `transacciones.page`, `transaccion-response.model`, `shared/api/problema-api.ts` y
  `app.routes.ts`.
- **API consumida**: `/beneficiarios` (lista, búsqueda, crear, consultar y editar) y
  `GET /categorias?incluirOcultas=true`, ya implementadas. Requiere el backend con
  `beneficiarios-backend`.
- **Dependencias**: ninguna nueva (`MatAutocomplete` viene con Material).
- **Documentación**: `AGENTS.md`.
