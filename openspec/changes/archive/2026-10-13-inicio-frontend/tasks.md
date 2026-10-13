## 1. Línea base

- [x] 1.1 Correr la suite completa del frontend (`npx ng test --watch=false`) y anotar archivos y
  tests de la línea base; confirmar que `features/inicio/pages/inicio.page.spec.ts` (4 `it`) pasa.
  Regla del change: no cambiar ninguna aserción ni fixture de un test existente; si falla uno o
  hay que tocarlo, detenerse y consultar.

## 2. Modelos y servicio de lectura

- [x] 2.1 Crear en `features/inicio/models/` (desde `src/app/`) `mes-resumen.model.ts`,
  `cuenta-resumen.model.ts`, `metas-resumen.model.ts`, `ingresos-gastos-resumen.model.ts` y
  `pagina-resumen.model.ts` con los campos mínimos de la tabla de rutas del design (sin lógica).
- [x] 2.2 Crear `features/inicio/services/resumen-lectura.service.ts` (6 `GET`: mes con
  `incluirOcultas=true`, cuentas, saldos, metas, transacciones `soloSinAprobar=true&page=0&size=1`
  e ingresos y gastos) y `resumen-lectura.service.spec.ts` (ruta, parámetros y cuerpo de cada una).
  Verificar que pasa.

## 3. Lógica pura y cargador

- [x] 3.1 Crear `features/inicio/services/resumen-inicio.ts` (`saldoTotalDelPresupuesto`,
  `contarSobregastadas`, `contarMetasQueFaltan`) y `resumen-inicio.spec.ts` con el conjunto del
  design D4 (`1200000`, `-150000`, `600000`, cerrada `300000` da `1050000`), ocultas contadas, un
  solo `FALTA` entre estados mezclados y que `totalFaltante` no se usa. Verificar que pasa.
- [x] 3.2 Crear `features/inicio/services/mensajes-inicio.ts` (textos fijos y singular/plural) y
  `mensajes-inicio.spec.ts`. Verificar que pasa.
- [x] 3.3 Crear `features/inicio/services/cargador-tarjeta.ts` y `cargador-tarjeta.spec.ts`: estado
  `cargando`, `listo` y `error`, `recargar()` repite solo su petición, una respuesta vieja se
  descarta al cambiar de presupuesto o al reintentar, y se cancela al destruirse. Verificar que
  pasa.
- [x] 3.4 Crear `features/inicio/services/resumen-inicio.store.ts` y `resumen-inicio.store.spec.ts`:
  las 7 peticiones salen a la vez (sin esperar una respuesta), el fallo de una fuente no cambia
  las otras y `mes` alimenta dos tarjetas con una sola petición. Verificar que pasa.

## 4. Tarjeta y página

- [x] 4.1 Crear `features/inicio/components/tarjeta-resumen.component.{ts,html,scss}` y su
  `tarjeta-resumen.component.spec.ts`: región con `aria-labelledby`, carga, error con
  `role="alert"` y `Reintentar`, vacío y contenido proyectado. Verificar que pasa.
- [x] 4.2 Modificar `features/inicio/pages/inicio.page.{ts,html,scss}` para mostrar el saludo con
  el mes (`textoMes(mesActual())`) y las seis tarjetas (contenido, enlaces y textos de la spec)
  solo con presupuesto activo; rejilla que se apila a 390 px. No tocar los `it` actuales de
  `inicio.page.spec.ts`; agregar los `it` nuevos: saludo con mes (con reloj en dos zonas
  horarias), cada tarjeta, estados vacíos, enlaces, error por tarjeta con `Reintentar`, cambio de
  presupuesto con respuesta atrasada (con las peticiones del presupuesto `3` en camino se cambia el
  presupuesto activo a `4` en `PresupuestoActivoService`, sin router real, y las respuestas del
  `3` llegan después: las seis tarjetas muestran solo cifras del `4`), salir de la pantalla
  (destruir el componente con peticiones pendientes: se cancelan y una respuesta posterior no
  actualiza nada) y las 7 peticiones en paralelo. Verificar que pasa.
- [x] 4.3 Agregar a `features/cuentas/pages/cuentas.page.spec.ts` un `it` nuevo (sin editar los
  existentes) que compruebe `Total en el presupuesto` con el mismo conjunto de datos de la tarea
  3.1 (`1050000`). Verificar que pasa.

## 5. Documentación y verificación

- [x] 5.1 En `AGENTS.md` (raíz), agregar `inicio/` al árbol del frontend si hace falta y una
  sección "Inicio (frontend)" con D1 a D9 en resumen, sin pasar de 100 columnas; comprobar cada
  afirmación contra el código.
- [x] 5.2 Suite completa: comparar con 1.1 (solo crece; `git diff -U0 -- '*.spec.ts'` sin líneas
  eliminadas); `npx prettier --check --end-of-line auto` sobre los archivos tocados, `npx ng
  build` y `openspec validate inicio-frontend --strict`.
- [x] 5.3 Revisión manual en el navegador: presupuesto con datos y presupuesto sin cuentas,
  pantalla ancha y de 390 px, teclado y lector (regiones, `Reintentar`), comparar `Saldo total`
  con `Total en el presupuesto` de Cuentas, y simular un fallo de una petición. Anotar el resultado
  aquí.

**Resultado de 5.3 (2026-10-13):**
- Hecho en el navegador (presupuesto con datos, solo lectura): las seis tarjetas con cifras
  reales; `Saldo total` y `Total en el presupuesto` de Cuentas coinciden (BOB 18,963.90); los
  enlaces apuntan a `presupuesto/2026-10`, `cuentas`, `transacciones?sinAprobar=1` y `reportes`;
  a 390 px (iframe) las tarjetas se apilan en una columna sin desbordar y hay 6 regiones con nombre.
- Corregido al revisar: el mes salía `Octubre De 2026` (`capitalize` de CSS); ahora solo la primera
  letra va en mayúscula.
- Según informó el usuario, el resto de la revisión manual está hecha; no lo verificó el agente: presupuesto sin cuentas, fallo real de una petición y
  `Reintentar` en el navegador, teclado y lector de pantalla.

