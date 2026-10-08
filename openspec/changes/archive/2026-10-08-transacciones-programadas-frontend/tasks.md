# Tasks

Rutas desde `frontend/src/app/`; cada test junto a su archivo; comandos en `frontend/`. Sin
`npm install`, sin librerías nuevas y sin commit. Archivos compartidos: solo `app.routes.ts` (+
spec); el menú lateral, `core/`, `shared/` y las demás features no se tocan.

## 1. Línea base

- [ ] 1.1 `npx ng build` y `npx ng test --watch=false`; anotar el total de tests como línea base.

## 2. Modelos y servicios

- [ ] 2.1 Modelos en `features/transacciones-programadas/models/`:
      `transaccion-programada-response.model.ts`, `crear-programada-request.model.ts`,
      `actualizar-programada-request.model.ts`, `generacion-response.model.ts`,
      `cuenta-lectura.model.ts`, `grupo-categorias-lectura.model.ts`,
      `categoria-lectura.model.ts` y `datos-dialogo-programada.model.ts`, con los nombres exactos
      de los records del backend. Verificar con `npx ng build`.
- [ ] 2.2 `features/transacciones-programadas/services/transaccion-programada.service.ts` (test
      `features/transacciones-programadas/services/transaccion-programada.service.spec.ts`):
      `listar`, `crear`, `actualizar`, `borrar`, `pausar`, `reanudar` y `generar` con método,
      URL y cuerpo exactos (el `PUT` sin `cuentaId` ni `fechaInicio`); el test pasa.
- [ ] 2.3 `features/transacciones-programadas/services/cuenta-lectura.service.ts` y
      `categoria-lectura.service.ts` (test
      `features/transacciones-programadas/services/lectura.service.spec.ts`):
      `GET .../cuentas?incluirCerradas=true` y `GET .../categorias?incluirOcultas=true`; el test
      pasa.
- [ ] 2.4 `features/transacciones-programadas/services/presentacion-programada.ts` (test
      `features/transacciones-programadas/services/presentacion-programada.spec.ts`): las seis
      frecuencias; estado en orden (error activa, error pausada → `Pausada`, pausada,
      finalizada, activa); `tipoMonto` y `montoAbsoluto` con negativos y positivos;
      `textoResultadoGeneracion` con 0, 1, 2 y con errores; `necesitaAvisoFinDeMes` con días 28,
      29, 31 y frecuencias en días y en meses; `filaProgramada` con cuenta y categoría
      inexistentes en los mapas; el test pasa.
- [ ] 2.5 `features/transacciones-programadas/services/formulario-programada.ts` (test
      `features/transacciones-programadas/services/formulario-programada.spec.ts`):
      `aCrearRequest`/`aActualizarRequest` con signo, milésimas, fechas locales en dos zonas
      horarias y textos recortados o `null`; `finNoAnteriorAInicio` con fin anterior, igual,
      posterior y vacío; `opcionesCategoria` sin ocultas ni de pago, con la actual oculta; el test
      pasa.
- [ ] 2.6 `features/transacciones-programadas/services/errores-programada.ts` y
      `mensajes-programada.ts` (test
      `features/transacciones-programadas/services/errores-programada.spec.ts`): `400` con y sin
      `errores`, `422`, `404`, otro código y error de red; nunca usa `detail`; el test pasa.

## 3. Diálogos

- [ ] 3.1 `features/transacciones-programadas/components/dialogo-programada.component.ts` (+ html,
      scss; test `.../components/dialogo-programada.component.spec.ts`, con
      `MatSelectHarness`, `MatButtonToggleHarness` y `MatDatepickerInputHarness`): valores por
      defecto; solo cuentas abiertas; categorías sin pago ni ocultas; monto con expresión y
      validación; aviso de fin de mes; fin anterior al inicio sin enviar; `POST` con el cuerpo
      exacto; edición con cuenta y fecha de inicio en solo lectura y `PUT` sin ellas; `Guardar`
      deshabilitado mientras envía (un solo envío con doble clic); `400` por campo y sin
      `errores`, `422` en el diálogo conservando lo escrito, `404` que cierra con `'recargar'` y
      otro error con aviso genérico; el test pasa.
- [ ] 3.2 `features/transacciones-programadas/components/dialogo-confirmar-borrado.component.ts`
      (test `.../components/dialogo-confirmar-borrado.component.spec.ts`): texto con las
      transacciones conservadas, foco inicial en `Cancelar`, cierre con `true` o `false`; el test
      pasa.

## 4. Pantalla y ruta

- [ ] 4.1 `features/transacciones-programadas/pages/programadas.page.ts` (+ html, scss; test
      `features/transacciones-programadas/pages/programadas.page.spec.ts`): carga de plantillas,
      cuentas y categorías; filas con cuenta, beneficiario, categoría, monto con `Salida` o
      `Entrada`, frecuencia, próxima fecha y estado con texto; `ultimoError` con la sugerencia;
      vacío y error con `Reintentar`; recarga atrasada descartada; crear y editar abren el
      diálogo y recargan; pausar y reanudar (con aviso) reemplazan la fila y deshabilitan sus
      acciones mientras esperan; borrar con confirmación, cancelar sin enviar; `404` en una
      acción con aviso y recarga; `Generar ahora` deshabilitado mientras espera, con los textos
      del resultado y recarga; vista de tarjetas en ancho estrecho; el test pasa.
- [ ] 4.2 `app.routes.ts`: ruta hija `programadas` con `loadComponent` y
      `data: seccion('Programadas', 'event_repeat')` después de `transacciones` (test
      `app.routes.spec.ts`: la URL muestra la pantalla y el menú lateral incluye
      `Programadas`); el test pasa.

## 5. Documentación y verificación

- [ ] 5.1 `AGENTS.md`: `features/transacciones-programadas/` en el árbol del frontend y una nota
      (ruta `programadas`, sin vista previa de fechas, beneficiario sin autocompletado y sin
      indicador en Transacciones, con el porqué); revisar el diff.
- [ ] 5.2 Comprobar con `grep` que `core/` y `shared/` no importan `features/`, que
      `features/transacciones-programadas` no importa otra feature y no usa `parseFloat`,
      `toISOString` ni pipes nativos de fecha o moneda, y que `git diff --stat` solo toca
      `app.routes.ts` (+ spec) fuera de la feature.
- [ ] 5.3 Revisión manual con `npx ng serve` y el backend local: crear, editar, pausar, reanudar,
      borrar y `Generar ahora`; una plantilla en una cuenta cerrada después muestra su error;
      360 px sin desplazamiento horizontal; navegación con teclado.
- [ ] 5.4 `npx prettier --check` sobre los archivos tocados, `npx ng build` y
      `npx ng test --watch=false`; anotar el total de tests y compararlo con la línea base.
