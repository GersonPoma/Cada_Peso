# Tasks

Rutas desde `frontend/src/app/`; cada test junto a su archivo; comandos en `frontend/`. Sin
`npm install`, sin librerías nuevas y sin commit. Archivos compartidos: solo `app.routes.ts` (+
spec) y el movimiento de `mes.ts` a `shared/fecha/`; el layout del menú lateral y `core/` no se
tocan. Parte A = grupos 1 a 6; parte B = grupos 7 a 9 (se pueden aplicar por separado).

## 1. Línea base y meses compartidos

- [x] 1.1 `npx ng build` y `npx ng test --watch=false`; anotar el total de tests como línea base.
- [x] 1.2 Mover el contenido de `features/presupuesto-mensual/services/mes.ts` a
      `shared/fecha/mes.ts` y su test a `shared/fecha/mes.spec.ts` (borrando el viejo);
      `features/presupuesto-mensual/services/mes.ts` queda como
      `export * from '../../../shared/fecha/mes';`. Verificar con `npx ng build` y que
      `shared/fecha/mes.spec.ts` y los tests de `presupuesto-mensual` y `app.routes.spec.ts`
      pasan sin cambiar sus imports.

## 2. Modelos y servicios

- [x] 2.1 Modelos en `features/reportes/models/`: `gasto-por-categoria-response.model.ts`,
      `ingresos-gastos-response.model.ts`, `patrimonio-response.model.ts`,
      `evolucion-saldo-response.model.ts`, `cumplimiento-metas-response.model.ts`,
      `cuenta-reporte.model.ts`, `rango-meses.model.ts` y `estado-carga.model.ts`, con los nombres
      exactos de los records del backend (`porcentaje: number | null` en metas). Verificar con
      `npx ng build`.
- [x] 2.2 `features/reportes/services/reporte.service.ts` (test
      `features/reportes/services/reporte.service.spec.ts`): un método por endpoint con `desde` y
      `hasta` en la query y la URL exacta de la evolución con `cuentaId`; el test pasa.
- [x] 2.3 `features/reportes/services/cuenta-lectura.service.ts` (test
      `features/reportes/services/cuenta-lectura.service.spec.ts`):
      `GET .../cuentas?incluirCerradas=true`; el test pasa.
- [x] 2.4 `features/reportes/services/rango-reporte.ts` (test
      `features/reportes/services/rango-reporte.spec.ts`): `contarMeses` (1, 12, 60, cruce de
      año), `validarRango` (válido, invertido, 60 y 61 meses, mal formado, 1999-12 y 2101-01),
      los cinco atajos con mes local `2026-10` y `2026-01`, `rangoDesdeUrl` (válido, ausente,
      mal formado, invertido, excedido → por defecto con `normalizado`), `primerDia`/`ultimoDia`
      (febrero bisiesto y no bisiesto, diciembre) y `textoRango` en `es-BO`; el test pasa.
- [x] 2.5 `features/reportes/services/formato-reporte.ts` (test
      `features/reportes/services/formato-reporte.spec.ts`): `textoPorcentaje` (`5283`, `0`,
      `-150`, `12500`, `null` → `Sin necesidad`) en `es-BO` y `en-US`; `proporcion` con enteros
      (máximo 0, negativos, límite seguro); `escalaVertical` con valores positivos, negativos y
      todos cero; `presentacionEstadoMeta` con los cuatro estados y `FALTA` pasado →
      `Faltaron` neutro; el test pasa.
- [x] 2.6 `features/reportes/services/errores-reporte.ts` y `mensajes-reporte.ts` (test
      `features/reportes/services/errores-reporte.spec.ts`): `DATOS_INVALIDOS`,
      `RECURSO_NO_ENCONTRADO` en saldo y en otra pestaña, otro código, error de red y un valor
      que no es `HttpErrorResponse`; nunca usa `detail`; el test pasa.
- [x] 2.7 `features/reportes/services/carga-reporte.ts` (test
      `features/reportes/services/carga-reporte.spec.ts`): emite `cargando` y luego `listo` o
      `error`; un parámetro nuevo cancela la petición anterior (desuscripción comprobada) y
      descarta su respuesta; parámetros iguales no repiten; `Reintentar` repite; el test pasa.

## 3. Selector de rango y estados

- [x] 3.1 `features/reportes/components/selector-rango.component.ts` (+ html, scss; test
      `features/reportes/components/selector-rango.component.spec.ts`, con `MatSelectHarness`):
      etiquetas `Mes desde`, `Año desde`, `Mes hasta`, `Año hasta`; años 2000-2100; atajos que
      emiten el rango correcto con el reloj fijado en dos zonas horarias; `Desde no puede ser
      posterior a Hasta` y `El rango no puede superar 60 meses` en `role="alert"` sin emitir; el
      test pasa.
- [x] 3.2 `features/reportes/components/estado-reporte.component.ts` (+ html, scss; test
      `features/reportes/components/estado-reporte.component.spec.ts`): indicador de carga,
      `Sin movimientos en este rango`, aviso de error anunciado con `Reintentar` (emite) o
      `Recargar` según el aviso; el test pasa.

## 4. Gráficos SVG

- [x] 4.1 `features/reportes/components/grafico-barras-horizontales.component.ts` (+ html, scss;
      test `.../grafico-barras-horizontales.component.spec.ts`): ancho por mil con enteros, sin
      barra para negativos, texto del monto y porcentaje en cada fila; el test pasa.
- [x] 4.2 `features/reportes/components/grafico-barras-mensuales.component.ts` (+ html, scss;
      test `.../grafico-barras-mensuales.component.spec.ts`): una barra por serie y mes con su
      patrón, eje con el cero, línea opcional con marcadores, `role="img"` y `aria-label`,
      `viewBox` con ancho 100 %, `<title>` por barra; el test pasa.
- [x] 4.3 `features/reportes/components/grafico-lineas.component.ts` (+ html, scss; test
      `.../grafico-lineas.component.spec.ts`): una línea por serie con trazo y marcador
      distintos, leyenda con texto, línea del cero con negativos, un solo mes dibuja un punto,
      `role="img"` y `aria-label`; el test pasa.

## 5. Pantalla y ruta

- [x] 5.1 `features/reportes/pages/reportes.page.ts` (+ html, scss; test
      `features/reportes/pages/reportes.page.spec.ts`): pestañas en orden con `matTabContent` y
      `preserveContent`; `reporte` de la URL (desconocido → `Gasto`); rango de la URL o por
      defecto escrito con `replaceUrl`; cambiar rango o pestaña actualiza la URL sin perder
      `cuentaId`; solo la pestaña visible pide (`HttpTestingController` con una sola petición);
      el test pasa.
- [x] 5.2 `app.routes.ts`: ruta hija `reportes` con `loadComponent` y
      `data: seccion('Reportes', 'bar_chart')` después de `beneficiarios` (test
      `app.routes.spec.ts`: la URL muestra la pantalla y el menú lateral incluye `Reportes`); el
      test pasa.

## 6. Gasto e ingresos contra gastos (fin de la parte A)

- [x] 6.1 `features/reportes/components/gasto-reporte.component.ts` (+ html, scss; test
      `features/reportes/components/gasto-reporte.component.spec.ts`): ejemplo de octubre con
      totales y porcentajes de la API en orden, `Sin categoría` aparte, `Oculta`, total negativo
      con nota y sin barra, porcentajes que suman `10001` sin ajuste, nota de pagos de tarjeta,
      `Ver transacciones` con `categoriaId`, `desde` y `hasta` correctos, vacío, error `400` con
      el aviso del rango, respuesta atrasada descartada; el test pasa.
- [x] 6.2 `features/reportes/components/ingresos-gastos-reporte.component.ts` (+ html, scss; test
      `.../ingresos-gastos-reporte.component.spec.ts`): gráfico con dos series y la línea del
      neto, tabla con los meses en ceros y la fila `Total` de la API, `Déficit` en un neto
      negativo, nota del saldo inicial, vacío y error; el test pasa.
- [x] 6.3 Verificación de la parte A: `npx ng build` sin avisos de presupuesto de tamaño nuevos
      y comprobar en el informe del build que el chunk diferido de `reportes.page` no está en el
      bundle inicial; anotar su tamaño.

## 7. Patrimonio

- [x] 7.1 `features/reportes/components/patrimonio-reporte.component.ts` (+ html, scss; test
      `.../patrimonio-reporte.component.spec.ts`): tres líneas, tabla, patrimonio negativo con la
      línea del cero, nota de cuentas incluidas y del saldo inicial, vacío y error; el test pasa.

## 8. Saldo de una cuenta

- [x] 8.1 `features/reportes/components/saldo-cuenta-reporte.component.ts` (+ html, scss; test
      `.../saldo-cuenta-reporte.component.spec.ts`, con `MatSelectHarness`): todas las cuentas
      con `Cerrada` y `Fuera del presupuesto`; sin cuenta no pide y muestra `Elige una cuenta para
      ver su saldo`; `cuentaId` ajeno a la lista se quita de la URL; elegir cuenta escribe
      `cuentaId` y pide la evolución; cambio de cuenta con respuesta atrasada descartada; `404`
      con `La cuenta ya no existe.`, recarga de cuentas y URL sin `cuentaId`; saldo inicial,
      tabla, gráfico y `Ver transacciones` con `cuentaId`; el test pasa.

## 9. Metas (fin de la parte B)

- [x] 9.1 `features/reportes/components/metas-reporte.component.ts` (+ html, scss; test
      `.../metas-reporte.component.spec.ts`): metas en orden con `Oculta`, totales del rango, una
      fila por mes con estado en texto e ícono, `Sin necesidad` con `null`, `125,00 %` con
      `Más de lo necesario`, `Faltaron` neutro en meses pasados, nota del historial de metas,
      `No hay metas en este presupuesto` y error; el test pasa.

## 10. Documentación y verificación

- [x] 10.1 `AGENTS.md`: `features/reportes/` en el árbol del frontend, `shared/fecha/mes.ts` en
      la lista de `shared/` y en la sección de fechas (en lugar de
      `features/presupuesto-mensual/services/mes.ts`), y una nota de la feature (pestañas en una
      ruta, rango en la URL, gráficos SVG propios sin librería); revisar el diff.
- [x] 10.2 Comprobar con `grep` que `core/` y `shared/` no importan `features/`, que
      `features/reportes` no importa otra feature, que no hay `parseFloat`, `toISOString` ni
      pipes nativos `number`/`currency`/`percent`/`date` en `features/reportes`, y que
      `git diff --stat` no incluye el layout del menú lateral ni `core/`.
- [ ] 10.3 Revisión manual con `npx ng serve` y el backend local: cada pestaña con datos, rango
      inválido, 360 px de ancho sin desplazamiento horizontal de la página, navegación con
      teclado (foco visible en pestañas, atajos, selectores y enlaces).
- [x] 10.4 `npx prettier --check` sobre los archivos tocados, `npx ng build` y
      `npx ng test --watch=false`; anotar el total de tests y compararlo con la línea base.

## Resultado de la aplicación

- Línea base: 991 tests. Ahora: 1150 (159 nuevos: 158 de `features/reportes` y `shared/fecha`,
  más 1 de rutas). `npx ng build` sin errores; el aviso de presupuesto inicial ya existía
  (732,39 kB → 733,40 kB, la entrada de la ruta). Chunk diferido `reportes-page`: 106,06 kB
  (22,19 kB transferidos), fuera del bundle inicial.
- 10.3 queda pendiente: requiere el backend con PostgreSQL local; la pantalla se probó con
  `HttpTestingController` y `RouterTestingHarness`.
- Ajustes respecto del design: las funciones de geometría de los gráficos (`posicionY`,
  `posicionesX`, `textoMesCorto`, `textoEje`, `pasoRotulos`) viven en `formato-reporte.ts`;
  `fechasDelRango` en `rango-reporte.ts`; cada meta del reporte es una sección con su tabla (sin
  `mat-expansion-panel`); el porcentaje en `es-BO` se lee `52,83%` (sin espacio, como lo da
  `Intl`).
