# Tasks

Rutas desde `frontend/src/app/`; cada test junto a su archivo; comandos en `frontend/`. Sin
`npm install`, sin librerías nuevas y sin commit. Todo dentro de `features/presupuesto-mensual/`;
no se tocan `app.routes.ts`, el layout del menú lateral, `core/` ni `shared/`.

## 1. Línea base

- [ ] 1.1 `npx ng build` y `npx ng test --watch=false`; anotar el total de tests como línea base.

## 2. Modelos y servicio

- [ ] 2.1 Modelos `features/presupuesto-mensual/models/meta-response.model.ts`,
      `guardar-meta-request.model.ts`, `metas-mes-response.model.ts`, `auto-asignar.model.ts` y
      `datos-dialogos-metas.model.ts` con los nombres y valores exactos de los records y enums del
      backend; `esPagoTarjeta` y `cuentaId` en `categoria-mes-response.model.ts` y en los objetos
      de prueba que lo necesiten. Verificar con `npx ng build`.
- [ ] 2.2 `features/presupuesto-mensual/services/meta.service.ts` (test
      `features/presupuesto-mensual/services/meta.service.spec.ts`): URL, método, parámetros y
      cuerpo de `guardar` (PUT), `obtener` (GET), `quitar` (DELETE), `delMes` (GET con
      `incluirOcultas`), `posponer`, `reanudar` y `autoAsignar` (POST); el test pasa.
- [ ] 2.3 `features/presupuesto-mensual/services/guardar-meta.ts` (test
      `features/presupuesto-mensual/services/guardar-meta.spec.ts`): `aGuardarMetaRequest` con
      cada tipo y frecuencia, solo los campos del tipo y las fechas con `aFechaNegocio`; el test
      pasa.
- [ ] 2.4 `features/presupuesto-mensual/services/presentacion-meta.ts` (test
      `features/presupuesto-mensual/services/presentacion-meta.spec.ts`): texto, ícono, tono y
      progreso para `FINANCIADA`, `FALTA`, `FALTA` en mes pasado, `POSPUESTA`, `SOBREGASTADA`,
      necesidad 0, asignado mayor que la necesidad y asignado negativo; el test pasa.

## 3. Componentes

- [ ] 3.1 `features/presupuesto-mensual/components/indicador-meta.component.ts` (+ html, scss;
      test `features/presupuesto-mensual/components/indicador-meta.component.spec.ts`): estado con
      texto e ícono, `Meta del mes`, barra con `aria-label` y tono neutro en mes pasado; el test
      pasa.
- [ ] 3.2 `features/presupuesto-mensual/components/dialogo-confirmacion-meta.component.ts` (test
      `features/presupuesto-mensual/components/dialogo-confirmacion-meta.component.spec.ts`):
      título, mensaje y cierre con `true` o `false`; el test pasa.
- [ ] 3.3 `features/presupuesto-mensual/components/dialogo-meta.component.ts` (+ html, scss; test
      `features/presupuesto-mensual/components/dialogo-meta.component.spec.ts`, con
      `MatSelectHarness` y `vi.useFakeTimers({ toFake: ['Date'] })`): crear cada tipo y
      frecuencia (escenarios de la spec), campos visibles según el tipo, cambio de tipo que
      descarta campos, monto 0 e intervalo fuera de rango con el botón deshabilitado, editar con
      `GET` e indicador de carga, `Quitar meta` con confirmación y sin enviar al cancelar, errores
      `400` en su campo, `409` y `422` como mensaje, `404` al guardar, al cargar y al quitar con
      aviso y cierre `recargar`, otro error con aviso genérico, botón deshabilitado mientras
      envía; el test pasa.
- [ ] 3.4 `features/presupuesto-mensual/components/dialogo-auto-asignar.component.ts` (+ html,
      scss; test `features/presupuesto-mensual/components/dialogo-auto-asignar.component.spec.ts`):
      estrategias, alcance todas (sin `categoriaIds`) y elegidas (con ids y las de pago marcadas),
      `Vista previa` deshabilitado sin categorías, vista previa con cambios, sin cambios y
      `listoParaAsignar` negativo, aviso de categorías sin meta con `FALTANTE_META`, descarte de la
      vista previa al cambiar estrategia o alcance, `Aplicar` con `simular: false` y cierre con la
      cantidad real, `400` en el diálogo, `404` con cierre y otro error genérico; el test pasa.
- [ ] 3.5 `features/presupuesto-mensual/components/grupo-mes.component.ts` (+ html, scss; test
      `features/presupuesto-mensual/components/grupo-mes.component.spec.ts`): entrada `metas`,
      indicador debajo del nombre solo con meta, menú con `Agregar meta`/`Editar meta`,
      `Posponer este mes`/`Reanudar este mes` (las dos con `SOBREGASTADA`), acciones
      deshabilitadas en curso y emisión con la categoría; ajustar los tests del menú existentes;
      el test pasa.

## 4. Página del mes

- [ ] 4.1 `features/presupuesto-mensual/pages/presupuesto-mensual.page.ts` (+ html, scss; test
      `features/presupuesto-mensual/pages/presupuesto-mensual.page.spec.ts`): pedir mes y metas
      juntos con el mismo `incluirOcultas`; respuesta atrasada ignorada al cambiar de mes; error
      solo en metas con aviso y `Reintentar`; `Falta para tus metas`; abrir el diálogo de meta y
      recargar con cada resultado; posponer y reanudar con recarga y `404` con aviso; botón
      `Auto-asignar` y aviso con la cantidad real tras aplicar; foco devuelto al cerrar los
      diálogos (`restoreFocus` no desactivado); ajustar los tests existentes que responden solo
      el mes; el test pasa.

## 5. Documentación y verificación

- [ ] 5.1 `AGENTS.md`: las metas del frontend viven en `features/presupuesto-mensual` (sin ruta
      ni menú propios), se crean solo desde el mes y `GET /metas` no se usa; revisar el diff.
- [ ] 5.2 Comprobar con `grep` que `core/` y `shared/` no importan `features/`, que
      `features/presupuesto-mensual` no importa otra feature y que `git diff --stat` no incluye
      `app.routes.ts` ni el layout del menú.
- [ ] 5.3 `npx prettier --check` sobre los archivos tocados, `npx ng build` y
      `npx ng test --watch=false`; anotar el total de tests y compararlo con la línea base.
