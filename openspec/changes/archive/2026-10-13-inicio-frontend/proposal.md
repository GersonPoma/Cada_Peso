# Proposal

## Why

`Inicio` hoy solo dice `Hola, <nombre>`: es la primera pantalla de un presupuesto y no responde la
pregunta de cada visita: "¿cómo voy este mes y qué necesita mi atención?". Todas las cifras ya
las calcula el backend (presupuesto mensual, saldos, metas, transacciones, reportes) y el frontend
solo las muestra en pantallas separadas; falta un resumen que las junte con enlaces.

## What Changes

- `Inicio` conserva el saludo `Hola, <nombre>` y agrega el mes actual y **seis tarjetas** de
  resumen del presupuesto activo:
  - **Listo para asignar**, con aviso en texto e ícono si es negativo.
  - **Saldo total** de las cuentas del presupuesto.
  - **Categorías sobregastadas** (cantidad, con enlace al mes).
  - **Transacciones sin aprobar** (cantidad, con enlace a la lista filtrada).
  - **Metas que faltan** este mes (solo la cantidad).
  - **Ingresos y gastos** del mes, con el neto.
- Cada tarjeta carga **por separado**, con su propio estado de carga, vacío y error con
  `Reintentar`; las peticiones salen **en paralelo** (7 al abrir) y una respuesta atrasada (otro
  presupuesto u otro mes) se descarta.
- Estados vacíos con la acción siguiente: presupuesto sin cuentas (`Agregar cuenta`) y mes sin
  movimientos (`Agregar transacción`).
- Ningún cálculo nuevo del dominio: se muestran las cifras del backend; la única suma del cliente
  es el saldo total, con la misma regla que `Total en el presupuesto` de Cuentas.
- **Huecos** (el backend no lo expone, no se inventa): el **monto que falta** de las metas en
  estado `FALTA` (`totalFaltante` suma también las `SOBREGASTADA`) y el **total de transacciones**
  del presupuesto.
- Fuera de alcance: gráficos (viven en `Reportes`), patrimonio, personalización de tarjetas,
  notificaciones y cambios en las pantallas enlazadas.

## Capabilities

### New Capabilities
- `inicio-frontend`: el resumen del presupuesto activo en `Inicio`: saludo, tarjetas
  independientes, enlaces, estados vacíos y comportamiento ante cambios de presupuesto o mes.

### Modified Capabilities

(ninguna: `perfil-frontend` ya dice que Inicio muestra `Hola, <nombre>` y eso se conserva)

## Impact

- `frontend/src/app/features/inicio/`: la página `InicioPage` pasa a componer tarjetas; servicios
  de lectura propios (`resumen-lectura.service.ts`) y modelos mínimos, más componentes de tarjeta.
- Archivos compartidos tocados: **ninguno** (se reutiliza `core/` y `shared/` tal cual); `AGENTS.md`
  gana una sección "Inicio (frontend)".
- Backend: sin cambios; 7 `GET` en paralelo al abrir.
