# Tasks

Rutas desde `frontend/src/app/`; cada test junto a su archivo; comandos en `frontend/`. Sin
`npm install`, sin librerías nuevas y sin commit.

## 1. Línea base

- [x] 1.1 `npx ng build` y `npx ng test --watch=false`; anotar los tests (545).

## 2. Calculadora (`shared/calculadora/`)

- [x] 2.1 `shared/calculadora/evaluar-monto.ts` (test `shared/calculadora/evaluar-monto.spec.ts`,
      sin DOM): `30+20+50`, `12,5*2` (`es-BO`), `-5+2`, `2+3*4`, `5--2`, `5*-2`, `10/3`, `2/3`,
      `-2/3`, `0.1+0.2`, `0,001`, `1.234,567` (`es-BO`), `1,234.567` (`en-US`), `0,0005*1`
      (redondeo hacia arriba), y como `null`: `1/0`, `5++2`, `5+`, `""`, `abc`, `12.3456`,
      `(1+2)`, `*5` y un resultado fuera del rango seguro.
- [x] 2.2 `shared/calculadora/campo-monto.component.ts` (test
      `shared/calculadora/campo-monto.component.spec.ts`): texto inicial desde el control,
      evaluación con `blur` y `Enter`, inválido con `Monto no válido` sin borrar, pista `= x`,
      vacío como `null`, `requerido` y sincronización cuando el control cambia desde fuera.

## 3. Modelos y servicios de la feature

- [x] 3.1 Modelos de `features/transacciones/models/` con los nombres exactos de los records
      (interfaces sin test); verificar con `npx ng build`.
- [x] 3.2 `features/transacciones/services/transaccion.service.ts` (test
      `transaccion.service.spec.ts`): URL, método, parámetros y cuerpo de cada operación.
- [x] 3.3 `cuenta-lectura.service.ts` y `categoria-lectura.service.ts` (test
      `lectura.service.spec.ts`): `GET /cuentas?incluirCerradas=true` y
      `GET /categorias?incluirOcultas=true`.
- [x] 3.4 `acciones-transaccion.ts` (test `acciones-transaccion.spec.ts`): acciones de una
      normal, una reconciliada, una pata, una aprobada y una de cuenta cerrada; `filtrarLote` para
      cada operación.
- [x] 3.5 `filtros-url.ts` (test `filtros-url.spec.ts`): lectura y escritura de cada parámetro,
      valores inválidos descartados y `hayFiltros`.

## 4. Componentes

- [x] 4.1 `filtros-transacciones.component.ts` (test `filtros-transacciones.component.spec.ts`):
      emite cada filtro, `q` con 300 ms de espera, fechas con `aFechaNegocio` en
      `America/New_York` y `Asia/Tokyo`, `Limpiar filtros`.
- [x] 4.2 `tabla-transacciones.component.ts` (test `tabla-transacciones.component.spec.ts`):
      columnas `Salida` y `Entrada`, `Dividida`, estado con ícono y texto, sin aprobar, insignia
      `Transferencia`, menú según `accionesDe` con motivos, selección por fila y de la página.
- [x] 4.3 `dialogo-transaccion.component.ts` y `editor-division.component.ts` (test
      `dialogo-transaccion.component.spec.ts`):
      - Crear: fecha por defecto hoy, signo por tipo, monto 0 inválido y cuentas sin las cerradas.
      - Editar: tipo y monto desde el signo, cuenta fija y `PUT` sin cuenta.
      - División: suma exacta y mensajes de lo que falta o sobra, 2 a 20 partes, payload con
        `categoriaId: null` y partes con signo, y quitar `Dividir`.
      - Errores 400, 404 y 422.
- [x] 4.4 `dialogo-mover-cuenta.component.ts` (test): cuentas abiertas sin la actual, `POST` y
      cierre.
- [x] 4.5 `barra-lote.component.ts` y `dialogo-confirmacion.component.ts` (test
      `barra-lote.component.spec.ts`): cantidad y emisión de cada acción.

## 5. Página

- [x] 5.1 `features/transacciones/pages/transacciones.page.ts` (+ html, scss; test
      `transacciones.page.spec.ts`).
- [x] 5.2 El test de la página verifica:
      - Lista y URL: filtros y página leídos de la URL y enviados con sus nombres; un cambio de
        filtro vuelve a la página 0; paginador de servidor; respuesta atrasada ignorada.
      - Saldos: de la cuenta filtrada o la suma de todas, y su refresco.
      - Estados: cargando, vacío con y sin filtros, y error con `Reintentar`.
      - Diálogos: crear y editar recargan la página y los saldos.
      - Acciones de fila: aprobar, estado, duplicar, mover, y borrar con confirmación y con
        cancelación.
      - Lote: aprobar; categorizar y borrar con exclusión, aviso y confirmación; sin aplicables
        no llama; selección limpia al paginar.

## 6. Ruta, menú y documentación

- [x] 6.1 Ruta hija `transacciones` en `app.routes.ts` con su `seccion`; actualizar
      `app.routes.spec.ts` (orden del menú y entrada a la pantalla).
- [x] 6.2 `AGENTS.md`: `features/transacciones/` y `shared/calculadora/` en el árbol, montos
      editables con `app-campo-monto` y servicios de lectura propios entre features.

## 7. Verificación

- [x] 7.1 `npx ng build` y `npx ng test --watch=false` en verde; informar el total.
- [x] 7.2 Desde `frontend/src/app`, `grep -rn "features/" core shared` y
      `grep -rnE "from '\.\./\.\./(auth|inicio|presupuestos|cuentas|categorias|presupuesto-mensual)/" features/transacciones`
      en cero; `grep -rnE "eval\(|new Function|parseFloat" shared/calculadora` en cero; Prettier
      sin diferencias.
- [x] 7.3 `openspec validate transacciones-frontend --strict`.
