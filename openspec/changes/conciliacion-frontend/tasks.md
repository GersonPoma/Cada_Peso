# Tasks

Rutas desde `frontend/src/app/`; cada test junto a su archivo; comandos en `frontend/`. Sin
`npm install`, sin librerías nuevas y sin commit. Archivos compartidos: solo una ruta en
`app.routes.ts`; el menú lateral, `core/` y `shared/` no se tocan.

## 1. Línea base

- [ ] 1.1 `npx ng build` y `npx ng test --watch=false`; anotar el total de tests como línea base.

## 2. Modelos y servicios

- [ ] 2.1 Modelos en `features/conciliacion/models/`: `estado-conciliacion-response.model.ts`,
      `transaccion-no-conciliada.model.ts`, `crear-conciliacion-request.model.ts`,
      `conciliacion-response.model.ts`, `cuenta-conciliacion.model.ts`,
      `grupo-categorias-lectura.model.ts`, `categoria-lectura.model.ts` y
      `datos-dialogo-confirmar-conciliacion.model.ts`, con los nombres exactos de los records del
      backend. Verificar con `npx ng build`.
- [ ] 2.2 `features/conciliacion/services/conciliacion.service.ts` (test
      `features/conciliacion/services/conciliacion.service.spec.ts`): `estado` (GET con
      `saldoExtracto` y `fecha`), `crear` (POST con el cuerpo) e `historial` (GET); el test pasa.
- [ ] 2.3 `features/conciliacion/services/cuenta-lectura.service.ts` y
      `categoria-lectura.service.ts` (test
      `features/conciliacion/services/lectura.service.spec.ts`):
      `GET .../cuentas/{id}` y `GET .../categorias?incluirOcultas=true`; el test pasa.
- [ ] 2.4 `features/conciliacion/services/regla-categoria-ajuste.ts` (test
      `features/conciliacion/services/regla-categoria-ajuste.spec.ts`): diferencia 0, cuenta fuera
      del presupuesto (positiva y negativa), negativa del presupuesto, positiva en tarjeta y
      positiva en otra cuenta del presupuesto; el test pasa.
- [ ] 2.5 `features/conciliacion/services/presentacion-conciliacion.ts` (test
      `features/conciliacion/services/presentacion-conciliacion.spec.ts`): `textoDiferencia` con
      0, positiva y negativa; `noConciliadasHasta` con fechas antes, igual y después del corte y
      con la lista recortada (`minimo`); `noFutura` con hoy, ayer y mañana en hora local y en dos
      zonas horarias; el test pasa.

## 3. Componentes

- [ ] 3.1 `features/conciliacion/components/dialogo-confirmar-conciliacion.component.ts` (+ html,
      scss; test
      `features/conciliacion/components/dialogo-confirmar-conciliacion.component.spec.ts`):
      resumen con fecha, saldo y ajuste (o sin ajuste), aviso de irreversibilidad, foco inicial en
      `Cancelar` y cierre con `true` o `false`; el test pasa.
- [ ] 3.2 `features/conciliacion/components/historial-conciliaciones.component.ts` (+ html, scss;
      test `features/conciliacion/components/historial-conciliaciones.component.spec.ts`): filas en
      el orden recibido con fecha, saldo, ajuste (`—` si 0) y cantidad; vacío, carga y error con
      `Reintentar` (emite); el test pasa.

## 4. Pantalla

- [ ] 4.1 `features/conciliacion/pages/conciliacion.page.ts` (+ html, scss; test
      `features/conciliacion/pages/conciliacion.page.spec.ts`, con `vi.useFakeTimers()` para la
      espera y `MatSelectHarness`): carga de cuenta, categorías e historial; cuenta inexistente con
      aviso y vuelta a Cuentas; una sola consulta 300 ms después del último cambio, sin consulta
      con datos inválidos o fecha futura, respuesta atrasada ignorada y error con `Reintentar`;
      textos de la diferencia; no conciliadas hasta la fecha con `al menos` y el enlace filtrado;
      ajuste con categoría obligatoria, opcional y no admitida, sin ocultas ni de pago, y limpieza
      al cambiar la diferencia; `Reconciliar` deshabilitado con diferencia sin ajuste; confirmación
      que envía el cuerpo correcto, cancelar sin enviar y botón deshabilitado mientras envía;
      resultado real del `POST` (también distinto al previsto) y recarga de estado e historial;
      errores `400` con y sin `errores`, `422` conservando lo escrito, `404` con recarga y otro con
      aviso genérico; cuenta cerrada sin asistente; el test pasa.
- [ ] 4.2 `app.routes.ts`: ruta hija `cuentas/:cuentaId/conciliacion` sin `data: seccion(...)`
      (test `app.routes.spec.ts`: la URL muestra la pantalla y el menú lateral sigue con las
      mismas entradas); el test pasa.

## 5. Puntos de entrada

- [ ] 5.1 `features/cuentas/pages/cuentas.page.ts` (+ html; test
      `features/cuentas/pages/cuentas.page.spec.ts`): `Conciliar` en el menú de las abiertas y
      `Ver conciliaciones` en el de las cerradas, con el enlace a la ruta; el test pasa.
- [ ] 5.2 `features/transacciones/pages/transacciones.page.ts` (+ html; test
      `features/transacciones/pages/transacciones.page.spec.ts`): botón `Conciliar` solo con un
      filtro de cuenta abierta y enlace a la ruta; ausente sin filtro o con una cuenta cerrada; el
      test pasa.

## 6. Documentación y verificación

- [ ] 6.1 `AGENTS.md`: `features/conciliacion/` en el árbol del frontend y una nota de su ruta
      (sin entrada en el menú), sus puntos de entrada y la regla del ajuste; revisar el diff.
- [ ] 6.2 Comprobar con `grep` que `core/` y `shared/` no importan `features/` y que
      `features/conciliacion`, `features/cuentas` y `features/transacciones` no se importan entre
      sí, y que `git diff --stat` no incluye el layout del menú lateral.
- [ ] 6.3 `npx prettier --check` sobre los archivos tocados, `npx ng build` y
      `npx ng test --watch=false`; anotar el total de tests y compararlo con la línea base.
