# Tasks

Rutas desde `frontend/src/app/`; cada test junto a su archivo; comandos en `frontend/`. Sin
`npm install`, sin librerías nuevas y sin commit. Todo dentro de `features/transacciones/`.

## 1. Línea base

- [ ] 1.1 `npx ng build` y `npx ng test --watch=false`; anotar el total de tests.

## 2. Modelos y servicios

- [ ] 2.1 Modelos `features/transacciones/models/crear-transferencia-request.model.ts`,
      `actualizar-transferencia-request.model.ts` y `transferencia-response.model.ts` con los
      nombres exactos de los records, y `DatosDialogoTransferencia` en
      `datos-dialogos-transacciones.model.ts` (sin test; verificar con `npx ng build`).
- [ ] 2.2 `features/transacciones/services/transferencia.service.ts` (test
      `features/transacciones/services/transferencia.service.spec.ts`): URL, método y cuerpo de
      `crear`, `obtener`, `actualizar` y `borrar`.
- [ ] 2.3 `features/transacciones/services/regla-categoria-transferencia.ts` (test
      `features/transacciones/services/regla-categoria-transferencia.spec.ts`): las cuatro
      combinaciones de `enPresupuesto` y `pendiente` con una cuenta vacía.
- [ ] 2.4 `features/transacciones/services/acciones-transaccion.ts` (test
      `features/transacciones/services/acciones-transaccion.spec.ts`): `editarTransferencia` y
      `borrarTransferencia` en una pata normal, reconciliada, con par reconciliada, cuenta
      cerrada y cuenta par cerrada (prioridad de la reconciliada); ocultas en una transacción
      normal; `editar` y `borrar` ocultos en una pata. Ajustar los casos existentes de la pata.

## 3. Diálogo de transferencia

- [ ] 3.1 `features/transacciones/components/dialogo-transferencia.component.ts` (+ html, scss;
      test `features/transacciones/components/dialogo-transferencia.component.spec.ts`, con
      `MatSelectHarness` y `vi.useFakeTimers({ toFake: ['Date'] })`):
      - Crear: solo cuentas abiertas, agrupadas, exclusión mutua, `Invertir`, origen
        preseleccionado, fecha por defecto hoy y envío con `aFechaNegocio` en
        `America/New_York` y `Asia/Tokyo`, monto 0 y negativo inválidos, memo recortado.
      - Categoría: oculta con su texto y sin `categoriaId` en ambas del presupuesto y ambas de
        seguimiento; obligatoria con su ayuda y botón deshabilitado; opcional con su ayuda y
        `categoriaId: null`; limpieza al cambiar las cuentas; ocultas solo si ya elegidas.
      - Editar: `GET` con el id de la pata, cuentas bloqueadas sin `Invertir`, valores cargados
        (monto absoluto, categoría de la pata que la lleve, memo), `PUT` sin cuentas.
      - Errores: 400 con `aplicarErroresDeCampos`, 422 como mensaje sin cerrar, 404 al guardar y
        al cargar con aviso y cierre `recargar`, otro con aviso genérico.

## 4. Tabla y página

- [ ] 4.1 `features/transacciones/components/tabla-transacciones.component.ts` (+ html; test
      `features/transacciones/components/tabla-transacciones.component.spec.ts`): menú de una
      pata (normal, reconciliada, cuenta cerrada) con `Editar transferencia` y `Borrar
      transferencia` y sus motivos; emisión con la transacción de la fila; `Transferencia a` y
      `Transferencia desde` con la pata par en la página y `Transferencia` sin ella. Ajustar los
      tests del menú de la pata.
- [ ] 4.2 `features/transacciones/pages/transacciones.page.ts` (+ html; test
      `features/transacciones/pages/transacciones.page.spec.ts`): botón `Agregar transferencia`
      con origen del filtro de cuenta; editar abre el diálogo con el id de la pata; borrar
      confirma con el mensaje de las dos transacciones y llama a `DELETE` con el id de la pata;
      cancelar no llama; recarga de página y saldos tras crear, editar y borrar; 404 recarga las
      listas; el foco vuelve al botón de la fila.

## 5. Documentación y verificación

- [ ] 5.1 `AGENTS.md`: las transferencias del frontend viven en `features/transacciones`
      (`TransferenciaService`, `DialogoTransferenciaComponent`) y la regla de categoría según
      `enPresupuesto` (`reglaCategoriaTransferencia`).
- [ ] 5.2 Comprobar con `grep` que `core/` y `shared/` no importan `features/` y que
      `features/transacciones` no importa otra feature.
- [ ] 5.3 `npx prettier --check` sobre los archivos tocados, `npx ng build` y
      `npx ng test --watch=false`; anotar el total de tests.
