# Tasks

Rutas desde `frontend/src/app/`; los comandos se corren desde `frontend/`. "Suite" =
`npx ng test --watch=false`. **Regla de detención**: la única aserción de un test existente que este change puede cambiar es la
de la tarea 5.3. Si en cualquier momento un test existente falla, o hay que cambiar o borrar una
aserción o un fixture ya escrito (aparte de la tarea 5.3), DETENERSE: no modificarlo, anotar el
archivo, el test y el motivo, y consultar antes de seguir.
Línea base: 120 archivos, 1328 tests, 1 intermitente por carga
(`dialogo-transferencia.component.spec.ts > crear > entre dos cuentas del presupuesto...`, que
pasa aislado). Etapa A = grupos 1-4; etapa B = grupos 5-8; se pueden integrar por separado.

## 1. Compartido: filtro, mensajes y etiqueta (etapa A)

- [x] 1.1 Antes de tocar nada, actualizar la rama con la principal y correr la suite; anotar
  archivos y tests (debe coincidir con la línea base o explicar la diferencia, y repetir aislado
  cualquier timeout antes de investigarlo).
- [x] 1.2 Crear `shared/pago-tarjeta/categorias-sin-pago.ts` con `sinCategoriasDePago(grupos,
  conservarId = null)`, genérica por estructura (`{ id, esPagoTarjeta? }`), sin importar ninguna
  feature; test `shared/pago-tarjeta/categorias-sin-pago.spec.ts`: quita las de pago, quita
  los grupos vacíos, conserva `conservarId`, trata el campo ausente como normal, no muta la
  entrada y devuelve lo mismo si no hay ninguna de pago. Verificar con
  `npx ng test --watch=false --include=src/app/shared/pago-tarjeta/categorias-sin-pago.spec.ts`.
- [x] 1.3 Crear `shared/pago-tarjeta/mensajes-pago-tarjeta.ts` con los textos fijos (`Pago de
  tarjeta`, `Se crea y se renombra sola con la tarjeta`, `Reserva para pagar la tarjeta`, `Ver
  movimientos` y el mensaje del `422` de lo protegido) tal como en las specs; verificar con
  `grep` que cada texto coincide con su spec.
- [x] 1.4 Crear el componente `shared/pago-tarjeta/etiqueta-pago-tarjeta.component.{ts,html,scss}`
  (`app-etiqueta-pago-tarjeta`, `OnPush`, `mat-icon` `credit_card` + texto, colores `--mat-sys-*`)
  y su test `etiqueta-pago-tarjeta.component.spec.ts` (ícono, texto, legible por lector de
  pantalla); verificar que el spec pasa.

## 2. Modelos y ayudantes de lectura (etapa A)

- [x] 2.1 En `features/categorias/models/`: `categoria-response.model.ts` (`esPagoTarjeta?`,
  `cuentaId?`), `grupo-categoria-response.model.ts` (`tipo?` y el tipo exportado
  `TipoGrupoCategoria`) y `grupo-categoria-con-categorias-response.model.ts` (`tipo?`); verificar
  con `npx ng build` sin errores y que los specs existentes de `features/categorias` compilan
  sin cambios.
- [x] 2.2 Crear `features/categorias/services/pago-tarjeta.ts` con `esGrupoDePagos` y
  `esCategoriaDePago` (solo `=== 'PAGOS_TARJETA'` / `=== true`); test
  `features/categorias/services/pago-tarjeta.spec.ts` (con campo, sin campo, `NORMAL`);
  verificar que pasa.
- [x] 2.3 En `features/transacciones/models/`: `categoria-resumen.model.ts` (`esPagoTarjeta?`,
  `cuentaId?`) y `grupo-categorias-resumen.model.ts` (`tipo?`); verificar con `npx ng build` y
  que los specs de `features/transacciones` compilan sin cambios.

## 3. Categorías: lo protegido (etapa A)

- [x] 3.1 `features/categorias/components/grupo-categorias.component.{ts,html,scss}`: el grupo de
  pagos y la categoría de pago muestran `app-etiqueta-pago-tarjeta`; sin menú de grupo, sin
  `Agregar categoría`, sin asa de arrastre ni menú de categoría, y la categoría con su nota; el
  predicado de entrada de la lista rechaza lo soltado en el grupo de pagos. Test nuevo
  `features/categorias/components/grupo-categorias.component.spec.ts`: grupo normal sin cambios
  (asa, menú, `Agregar categoría`), grupo de pagos, categoría de pago (etiqueta, nota, sin menú),
  categoría de pago oculta, y el predicado. Verificar que pasa.
- [x] 3.2 `features/categorias/pages/categorias.page.{ts,html}`: el arrastre del grupo de pagos
  queda deshabilitado (`cdkDragDisabled`); `moverOptimista` y `ejecutar` muestran el mensaje
  fijo ante `REGLA_NEGOCIO_VIOLADA` y conservan el comportamiento actual para los demás códigos.
  Tests nuevos en `features/categorias/pages/categorias.page.spec.ts`: `422` al ocultar (mensaje
  fijo, no el genérico, árbol vuelto a pedir), `422` al arrastrar (vuelve al orden anterior,
  mensaje fijo), y reordenar otro grupo con el de pagos al final. Verificar que pasa.
- [x] 3.3 `features/categorias/components/dialogo-mover-categoria.component.ts`: la lista de
  grupos omite el de pagos y `REGLA_NEGOCIO_VIOLADA` muestra el mensaje fijo en `errorGeneral`;
  `dialogo-categoria.component.ts` y `dialogo-grupo.component.ts` muestran el mensaje fijo en el
  aviso sin cerrar el diálogo. Tests nuevos en los tres specs existentes de esos componentes
  (`it` nuevos): destino sin el grupo de pagos y `422` en cada diálogo. Verificar que pasan.
- [x] 3.4 Correr los specs de `features/categorias` completos: todos los existentes pasan sin
  cambiar ninguna aserción.

## 4. Selectores de Transacciones sin categorías de pago (etapa A)

- [x] 4.1 `features/transacciones/components/dialogo-transaccion.component.ts`: `grupos` pasa por
  `sinCategoriasDePago` (también para el editor de división, que lo recibe); `arbol`, usado para
  el autocompletado del beneficiario, no cambia. Tests nuevos en
  `dialogo-transaccion.component.spec.ts`: el selector simple y una parte de la división no
  ofrecen `Pago: Visa`, y el grupo de pagos no aparece. Verificar que pasa.
- [x] 4.2 `features/transacciones/components/dialogo-transferencia.component.ts`: `grupos` pasa
  por `sinCategoriasDePago`. Test nuevo en `dialogo-transferencia.component.spec.ts` (del
  presupuesto a seguimiento: no ofrece `Pago: Visa`). Verificar que pasa.
- [x] 4.3 `features/transacciones/pages/transacciones.page.{ts,html}`: `gruposVisibles` sin las de
  pago (barra de lote) y nuevo `gruposParaFiltro = sinCategoriasDePago(gruposVisibles(),
  filtros().categoriaId)` para el filtro. Tests nuevos en `transacciones.page.spec.ts`: la barra
  de lote no ofrece las de pago, el filtro tampoco, y `?categoriaId=<pago>` la sigue mostrando
  como elegida sin error. Verificar que pasa.
- [x] 4.4 Checkpoint de la etapa A: correr la suite completa y comparar con la línea base (mismos
  1328 tests pasando más los nuevos, ningún fallo nuevo, ninguna aserción existente cambiada; si un test existente falla, detenerse y consultar);
  `npx prettier --check` sobre los archivos tocados y `npx ng build`. Anotar los conteos.

## 5. Cuentas: el tipo de una tarjeta no cambia (etapa B)

- [x] 5.1 `features/cuentas/components/dialogo-cuenta.component.{ts,html}`: validador del control
  `tipo` al editar (desde o hacia `TARJETA_CREDITO`) con los mensajes `El tipo de una tarjeta de
  crédito no se puede cambiar` y `Una cuenta existente no puede pasar a tarjeta de crédito`;
  `Guardar` deshabilitado; crear no se afecta. La clave nueva de `MENSAJES_TIPO` va después de la del
  saldo negativo y, si fallan las dos, se muestran ambos mensajes (un segundo `mat-error`). Tests nuevos en `dialogo-cuenta.component.spec.ts`
  (los 5 escenarios de la spec). Verificar que pasa.
- [x] 5.2 Mismo componente: `mostrarError` elige el mensaje de `REGLA_NEGOCIO_VIOLADA` por el
  contexto (editar + tipo distinto + alguno `TARJETA_CREDITO` → mensaje de cambio de tipo; si no,
  el del saldo negativo) sin leer `detail`. Tests nuevos: ese escenario; los `422` existentes de
  crear y de editar sin cambio de tipo no cambian.
- [x] 5.3 Excepción declarada: en `dialogo-cuenta.component.spec.ts`, test `con saldo inicial
  negativo, un tipo que no lo admite marca el tipo`, cambiar **solo** su última parte (tras
  elegir `Préstamo` en una tarjeta, `Guardar` sigue deshabilitado y se ve el mensaje de tarjeta).
  Verificar con un `git diff` del spec que es la única aserción existente modificada de todo el
  change. Si el diff muestra otra aserción cambiada o borrada en cualquier spec, detenerse y
  consultar (regla de detención).

## 6. Diálogo de transferencia en modo de pago (etapa B)

- [x] 6.1 `features/transacciones/models/cuenta-resumen.model.ts` (`tipo?: string`) y
  `datos-dialogos-transacciones.model.ts` (`DatosDialogoTransferencia.pagoTarjetaId?`); verificar
  con `npx ng build` y que los specs de `features/transacciones` compilan sin cambios.
- [x] 6.2 `features/transacciones/components/dialogo-transferencia.component.{ts,html}`: con
  `pagoTarjetaId`, título `Pagar tarjeta`, destino fijo y deshabilitado, sin `Invertir`, orígenes
  `enPresupuesto` y no tarjeta, nota `Este pago rebaja lo reservado para pagar {nombre}`, y el
  mensaje `No tienes otra cuenta del presupuesto desde la cual pagar` con el botón deshabilitado
  si no hay orígenes. Tests nuevos en `dialogo-transferencia.component.spec.ts` (nuevo
  `describe` con los 4 escenarios de la spec, incluida la petición con `cuentaDestinoId` y sin
  `categoriaId`); verificar que pasan y que los 26 existentes siguen pasando.

## 7. Pagar tarjeta desde Cuentas (etapa B)

- [x] 7.1 `features/transacciones/pages/transacciones.page.ts`: leer `pagarTarjeta` de la URL;
  cuando cuentas y árbol cargaron, abrir el diálogo en modo de pago si es una tarjeta abierta del
  presupuesto; en todo caso quitar el parámetro con `replaceUrl` y `queryParamsHandling:
  'merge'`. Tests nuevos en `transacciones.page.spec.ts` (nuevo `describe`): abre y deja
  `?cuentaId=4`, recargar no reabre, id que no es tarjeta, tarjeta cerrada o inexistente.
  Verificar que pasa.
- [x] 7.2 `features/cuentas/pages/cuentas.page.html`: ítem `Pagar tarjeta` (`routerLink` a
  `transacciones` con `cuentaId` y `pagarTarjeta`) solo en tarjetas abiertas del presupuesto.
  Tests nuevos en `cuentas.page.spec.ts`: destino con los dos query params y las tres cuentas sin
  la acción. Verificar que pasa.

## 8. Aviso, presupuesto mensual y documentación (etapa B)

- [x] 8.1 `features/transacciones/components/dialogo-transaccion.component.{ts,html}`: aviso con
  ícono `info` y `role="status"` para entrada sin categoría, sin dividir, en una tarjeta del
  presupuesto; no bloquea el guardado. Tests nuevos en `dialogo-transaccion.component.spec.ts`
  (nuevo `describe`: con aviso; reembolso con categoría, otra cuenta y salida sin aviso).
  Verificar que pasa.
- [x] 8.2 `features/presupuesto-mensual/components/grupo-mes.component.{ts,html,scss}`: en la fila
  de pago, etiqueta + `Reserva para pagar la tarjeta` + `Ver movimientos` con
  `/presupuestos/:id/transacciones?cuentaId=`; el `id` de `PresupuestoActivoService`; apilado en
  pantalla estrecha. Test nuevo `grupo-mes.tarjeta.component.spec.ts` (con `provideRouter`): fila
  de pago, enlace, acciones que siguen y fila normal sin cambios. Verificar que pasa y que
  `grupo-mes.component.spec.ts` y `presupuesto-mensual.page.spec.ts` siguen igual.
- [x] 8.3 Documentar en `AGENTS.md` (raíz) una sección "Tarjetas de crédito (frontend)": el
  filtro y la etiqueta compartidos, que las categorías de pago se muestran protegidas, el tipo de
  tarjeta fijo y el flujo `Pagar tarjeta` por URL; verificar que cada afirmación coincide con el
  código y que el `grep` de dependencias de `comun/` sigue en cero líneas.

## 9. Verificación final

- [x] 9.1 Suite completa: comparar con el conteo del checkpoint 4.4 y con la línea base (ningún
  test existente cambia salvo la aserción de 5.3; cualquier otra diferencia detiene el change y se
  consulta); `npx prettier --check` sobre todos los
  archivos tocados, `npx ng build`, y `openspec validate tarjetas-credito-frontend --strict`.
- [x] 9.2 Revisión manual en el navegador (anotar el resultado): Categorías con una tarjeta
  creada (etiquetas, sin acciones, arrastre bloqueado); Presupuesto mensual (fila de pago y
  enlace); Transacciones (selectores sin `Pago: ...`, aviso de entrada); Cuentas (tipo de una
  tarjeta y `Pagar tarjeta` hasta registrar un pago y ver la reserva bajar en el mes); en
  pantalla estrecha, con teclado y con lector de pantalla.
  Resultado parcial (2026-10-13, cuenta demo, pantalla ancha, sin guardar ningún cambio): Cuentas
  ofrece `Pagar tarjeta` en la tarjeta; el pago abre el diálogo con destino fijo, sin `Invertir`, con
  la nota de reserva, orígenes sin tarjetas y la URL limpia; Categorías muestra el grupo y la
  categoría de pago con etiqueta, nota y sin acciones; el mes muestra la fila con etiqueta, reserva y
  `Ver movimientos`; el selector de categoría de una transacción no ofrece el grupo de pagos; editar
  el tipo de la tarjeta a Ahorro muestra el mensaje y deshabilita `Guardar`. Defecto hallado y
  corregido: sin `Invertir`, el campo `Cuenta origen` quedaba angosto (columnas del diálogo en
  modo de pago). Pago de prueba de BOB 10 (Cuenta Corriente BNB a Tarjeta Visa BCP, memo `Pago de
  prueba`, 13/10/2026): la reserva del grupo de pagos bajó de actividad -465,00 a -475,00 y de
  disponible 210,00 a 200,00; el saldo de la tarjeta pasó de -210,00 a -200,00 y el de la cuenta
  de 11.899,90 a 11.889,90; `Listo para asignar` (13.403,00) y el total (19.058,90) no cambiaron.
  Pantalla estrecha (iframe de 390 px, porque la ventana no se pudo redimensionar): la fila de pago
  apila etiqueta, reserva y `Ver movimientos` sin desbordar; Categorías parte la etiqueta del grupo en
  una segunda línea sin botones ni desborde; el diálogo de pago queda en una columna (campos de 236
  px) sin `Invertir`. El desborde horizontal de `app-barra-mes` a 390 px ya existía y no es de este
  change. Teclado: el menú de la tarjeta se abre con Enter, las flechas llegan a `Pagar tarjeta` y
  Enter abre el diálogo (`role=dialog`, nombre `Pagar tarjeta`) con el foco en `Cuenta origen`; el
  orden de Tab es origen, fecha, calendario, monto, memo y cancelar, y salta el destino
  deshabilitado; Escape lo cierra, pero el foco queda en el documento porque se abrió por URL y no
  hay botón de origen al que volver. Lector de pantalla (semántica del DOM, no un lector real): los
  íconos llevan `aria-hidden`, el texto `Pago de tarjeta` se lee, el grupo es un `h2` sin elementos
  interactivos ni asas, y el aviso de entrada sin categoría tiene `role=status`.

## 10. Foco al cerrar el pago (ajuste hallado en la revisión manual)

- [x] 10.1 `features/transacciones/pages/transacciones.page.{ts,html}`: referencia `#botonTransferencia`
  leída con `viewChild('botonTransferencia', { read: ElementRef })`; `abrir` acepta `restaurarFoco` y
  solo el diálogo de pago lo recibe. Tests nuevos en `transacciones.page.spec.ts`: el diálogo de
  pago lleva `restoreFocus` igual al botón `Agregar transferencia` y la transferencia normal no lleva
  la opción. Verificado en el navegador: Escape devuelve el foco al botón.
- [x] 10.2 Documentar en `AGENTS.md` (sección "Tarjetas de crédito (frontend)") que el foco del pago
  vuelve a `Agregar transferencia`, y en la spec `transacciones-frontend` el requisito y sus
  escenarios; verificar con `openspec validate tarjetas-credito-frontend --strict`.
- [x] 10.3 Repetir la verificación final: suite completa sin fallos (y sin tocar ninguna aserción
  existente), `npx prettier --check --end-of-line auto` sobre los archivos tocados y `npx ng build`.
