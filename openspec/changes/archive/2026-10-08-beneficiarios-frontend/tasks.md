# Tasks

Rutas desde `frontend/src/app/`; cada test junto a su archivo; comandos en `frontend/`. Sin
`npm install`, sin librerías nuevas y sin commit. Requiere el backend con `beneficiarios-backend`.

## 1. Línea base

- [x] 1.1 `npx ng build` y `npx ng test --watch=false`; anotar el total de tests.

## 2. Comunes y modelos

- [x] 2.1 `shared/api/problema-api.ts`: agregar `BENEFICIARIO_YA_EXISTE` a `CODIGOS_API` (test
      `shared/api/problema-api.spec.ts`).
- [x] 2.2 `features/transacciones/models/transaccion-response.model.ts`: agregar
      `beneficiarioId: number | null` y `programadaId: number | null`; actualizar los objetos de
      prueba de los specs de `features/transacciones/` que lo necesiten.
- [x] 2.3 `features/transacciones/models/beneficiario-sugerido.model.ts`
      (`{ id, nombre, categoriaPredeterminadaId }`, sin test).

## 3. Autocompletado en `features/transacciones/`

- [x] 3.1 `features/transacciones/services/beneficiario-lectura.service.ts` (test en
      `features/transacciones/services/lectura.service.spec.ts`): `buscar` con `q` y
      `limite=10`, y `listar` sin parámetros.
- [x] 3.2 `features/transacciones/services/categoria-recordada.ts` (test
      `features/transacciones/services/categoria-recordada.spec.ts`): rellena al crear, vacía y
      pristine; no rellena en Dividir, tocada, al editar, sin predeterminada o si no existe; sí
      con una oculta.
- [x] 3.3 `features/transacciones/components/campo-beneficiario.component.ts` (+ html, scss; test
      `features/transacciones/components/campo-beneficiario.component.spec.ts`, con
      `MatAutocompleteHarness` y `vi.useFakeTimers()`): una petición tras 250 ms, nada con vacío
      o espacios, `q` recortada, `limite=10`, respuesta atrasada ignorada, error de red sin
      sugerencias, texto secundario de categoría (y ausente si no existe), prefijo resaltado,
      pista de nuevo y su ausencia con coincidencia exacta sin mayúsculas, elegir una opción
      (emite `elegido`), flechas + `Enter`, `Escape`, contador y error de 100 caracteres.

## 4. Diálogo y tabla de transacciones

- [x] 4.1 `features/transacciones/components/dialogo-transaccion.component.ts` (+ html; test
      `features/transacciones/components/dialogo-transaccion.component.spec.ts`): reemplazar el
      input por `app-campo-beneficiario`; categoría recordada con la pista
      `Sugerida por el beneficiario` que desaparece al cambiarla (casos de 3.2 en el diálogo);
      `grupos` incluye la sugerida oculta; reintento único ante `409 BENEFICIARIO_YA_EXISTE` (dos
      `POST` y cierre; dos 409 y mensaje en el campo, sin tercer intento); texto libre enviado
      recortado. Ajustar los tests existentes que usaban el input simple.
- [x] 4.2 `features/transacciones/components/tabla-transacciones.component.ts` (+ html; test
      `features/transacciones/components/tabla-transacciones.component.spec.ts`): entrada
      `nombresBeneficiario`; columna con vínculo, sin vínculo, id no encontrado y renombrado.
- [x] 4.3 `features/transacciones/pages/transacciones.page.ts` (+ html; test
      `features/transacciones/pages/transacciones.page.spec.ts`): cargar `listar` de
      beneficiarios una vez, pasar el mapa a la tabla, recargarlo tras guardar desde el diálogo
      y caer al texto si falla.

## 5. Feature `features/beneficiarios/`

- [x] 5.1 Modelos en `features/beneficiarios/models/`: `beneficiario-response.model.ts`,
      `crear-beneficiario-request.model.ts`, `actualizar-beneficiario-request.model.ts`,
      `categoria-lectura.model.ts`, `grupo-categorias-lectura.model.ts` y
      `datos-dialogo-beneficiario.model.ts` (sin test; verificar con `npx ng build`).
- [x] 5.2 `features/beneficiarios/services/beneficiario.service.ts` (test
      `features/beneficiarios/services/beneficiario.service.spec.ts`): URL, método y cuerpo de
      `listar`, `crear` y `actualizar`.
- [x] 5.3 `features/beneficiarios/services/categoria-lectura.service.ts` (test
      `features/beneficiarios/services/categoria-lectura.service.spec.ts`):
      `GET /categorias?incluirOcultas=true`.
- [x] 5.4 `features/beneficiarios/components/dialogo-beneficiario.component.ts` (+ html, scss;
      test `features/beneficiarios/components/dialogo-beneficiario.component.spec.ts`): crear con
      y sin categoría, editar, `Ninguna` envía `null`, nombre recortado, vacío y 101 caracteres,
      ocultas solo si ya elegida, sin categorías de pago, pista al renombrar, botón
      deshabilitado mientras envía, 409 en el campo, 400 con `aplicarErroresDeCampos`, 404 con
      aviso y cierre `recargar`, otro error con aviso genérico.
- [x] 5.5 `features/beneficiarios/pages/beneficiarios.page.ts` (+ html, scss; test
      `features/beneficiarios/pages/beneficiarios.page.spec.ts`): carga, lista con categoría o
      `—`, filtro local por prefijo con 250 ms sin nueva petición, sin coincidencias, vacío con
      `Agregar beneficiario`, error con `Reintentar`, abrir el diálogo para crear y editar y
      recargar tras guardar.
- [x] 5.6 `app.routes.ts`: ruta hija `beneficiarios` tras `categorias` con
      `data: seccion('Beneficiarios', 'storefront')`; ajustar
      `features/presupuestos/pages/layout-presupuesto.page.spec.ts` si enumera los enlaces.

## 6. Documentación y verificación

- [x] 6.1 `AGENTS.md`: `features/beneficiarios/` en el árbol del frontend y una nota de que el
      autocompletado vive en `features/transacciones` con `BeneficiarioLecturaService` y modelos
      propios; la limitación del filtro de texto.
- [x] 6.2 Comprobar que `core/` y `shared/` no importan `features/` y que
      `features/transacciones` no importa `features/beneficiarios` (ni al revés) con `grep`.
- [x] 6.3 `npx prettier --check` sobre los archivos tocados, `npx ng build` y
      `npx ng test --watch=false`; anotar el total de tests.
