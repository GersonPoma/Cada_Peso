# Tasks

Rutas desde `frontend/src/app/`; cada test junto a su archivo; comandos en `frontend/`. No se
ejecuta `npm install`, no se agregan librerías y no se hace commit.

## 1. Línea base

- [x] 1.1 Ejecutar `npx ng build` y `npx ng test --watch=false`; anotar los tests que pasan (454).

## 2. Modelos, códigos, mes y servicio

- [x] 2.1 Crear los ocho modelos de `features/presupuesto-mensual/models/` con los nombres de
      campo exactos de `com.presupuesto.asignacion.dto` (interfaces sin test) y agregar
      `CONFLICTO` y `RECURSO_NO_ENCONTRADO` a `CODIGOS_API` (`shared/api/problema-api.ts`);
      verificar con `npx ng build`.
- [x] 2.2 Crear `features/presupuesto-mensual/services/mes.ts` (test
      `features/presupuesto-mensual/services/mes.spec.ts`); verificar `mesActual` con
      `vi.useFakeTimers({ toFake: ['Date'] })` en `America/New_York` (31/10/2026 21:00 → `2026-10`)
      y `Asia/Tokyo` (01/11/2026 08:00 → `2026-11`), comprobando que `toISOString()` daría otro mes;
      `esMesValido` (válidos, `2026-13`, `2026-1`, `1999-12`, `2101-01`, texto); `sumarMeses`
      (cruce de año en ambos sentidos); y `textoMes` en `es-BO` (`octubre de 2026`) y `en-US`.
- [x] 2.3 Crear `features/presupuesto-mensual/services/mes-presupuesto.service.ts` (test
      `features/presupuesto-mensual/services/mes-presupuesto.service.spec.ts`) con `obtener`,
      `asignar` y `moverDinero`; verificar URL, método, `incluirOcultas` y cuerpo.

## 3. Componentes de presentación

- [x] 3.1 `features/presupuesto-mensual/components/barra-mes.component.ts` (test `barra-mes.component.spec.ts`
      en la misma carpeta): texto largo, flechas que emiten el mes anterior y el siguiente
      (cruzando el año), `Hoy` y los límites deshabilitados.
- [x] 3.2 `features/presupuesto-mensual/components/resumen-listo-para-asignar.component.ts`
      (test `resumen-listo-para-asignar.component.spec.ts`): los tres estados con su texto, su
      ícono y su clase.
- [x] 3.3 `features/presupuesto-mensual/components/celda-asignado.component.ts` (test
      `celda-asignado.component.spec.ts`): abrir con el valor seleccionado, `Enter`, `blur`,
      `Escape`, `Tab` hacia la siguiente celda, vacío como 0, texto inválido sin emitir, mismo
      valor sin emitir, separador de la región, error del servidor y foco de vuelta al botón.
- [x] 3.4 `features/presupuesto-mensual/components/grupo-mes.component.ts` (test
      `grupo-mes.component.spec.ts`): totales del grupo sumados, plegar con `aria-expanded`,
      clases e ícono de disponible, menú con `Mover dinero...` y `Cubrir sobregasto` solo en las
      sobregastadas, ocultas atenuadas y reenvío de `guardar` como `asignar`.

## 4. Diálogo de mover dinero

- [x] 4.1 `features/presupuesto-mensual/components/dialogo-mover-dinero.component.ts` (test
      `dialogo-mover-dinero.component.spec.ts`): origen preseleccionado, cubrir sobregasto con el
      destino y el monto propuestos, solo categorías visibles, ayuda con el disponible,
      validaciones (mismo origen y destino, 0, más que el disponible), envío en milésimas,
      cierre con el mes devuelto, 422 en el diálogo, 400 en los campos y 404 con aviso y
      `recargar`.

## 5. Página

- [x] 5.1 Crear `features/presupuesto-mensual/pages/presupuesto-mensual.page.ts` (+ html, scss;
      test `presupuesto-mensual.page.spec.ts`): carga por mes, ocultas y recargas con
      `switchMap`, mes inválido a mes actual, navegación desde la barra, totales del mes, estados
      de carga, error y vacío, asignación optimista con reversión y un guardado por celda, y
      diálogo de mover dinero.
- [x] 5.2 En el test de la página, verificar: mes inválido redirige al actual; pide
      `incluirOcultas=false` y con el interruptor `true`; muestra el listo para asignar, los
      grupos y el total del mes; cambiar de mes navega; una respuesta atrasada se ignora; spinner,
      error con `Reintentar` y vacío con enlace a categorías; editar una celda manda el `PUT`,
      muestra el valor de inmediato y aplica la respuesta (fila, listo para asignar y totales);
      un 500 revierte y avisa; un 400 muestra el error en la celda; un segundo guardado de la
      misma celda se ignora; `Mover dinero...` y `Cubrir sobregasto` abren el diálogo con su
      preselección, y su resultado reemplaza el mes o lo recarga.

## 6. Rutas

- [x] 6.1 Cambiar las rutas hijas de `app.routes.ts` según `design.md` (`''` → `presupuesto`,
      `presupuesto` → mes actual con su `seccion`, `presupuesto/:mes`, `inicio`) y actualizar
      `app.routes.spec.ts`: `/` termina en `/presupuestos/3/presupuesto/{mes actual}` con el
      mes fijado por reloj falso; `/presupuestos/3/inicio` saluda; el menú muestra
      `Presupuesto`, `Inicio`, `Cuentas` y `Categorías` en ese orden.

## 7. Documentación

- [x] 7.1 `AGENTS.md`: `features/presupuesto-mensual/` en el árbol, la regla de que los montos se
      editan siempre con `aMilliunits()` y la de que el mes se construye con la fecha local; líneas
      de hasta 100 columnas.

## 8. Verificación

- [x] 8.1 `npx ng build` y `npx ng test --watch=false` en verde; informar el total de tests.
- [x] 8.2 Desde `frontend/src/app`, `grep -rn "features/" core shared` y
      `grep -rnE "from '\.\./\.\./(auth|inicio|presupuestos|cuentas|categorias)/" features/presupuesto-mensual`
      en cero líneas, `grep -rn "toISOString" features/presupuesto-mensual --include=*.ts` sin
      usos fuera de los tests, y `npx prettier --check --end-of-line auto` sin diferencias.
- [x] 8.3 `openspec validate presupuesto-mensual-frontend --strict` sin errores.
