# Proposal

## Why

Hoy un presupuesto nuevo nace vacío: la persona debe crear a mano todos los grupos y categorías
antes de poder usarlo. Un punto de partida razonable (facturas, necesidades, deseos, ahorro)
elimina esa fricción inicial, que es la primera impresión del producto.

## What Changes

- Todo presupuesto nuevo, tanto el inicial que se crea al registrarse como los creados con
  `POST /api/v1/presupuestos`, nace con 4 grupos de categorías y sus categorías, en español y en
  un orden fijo, sin nada oculto.
- `presupuesto` avisa de la creación con un evento (`PresupuestoCreadoEvento`) y `categoria` lo
  escucha de forma síncrona, dentro de la misma transacción: si algo falla, no queda ni el
  presupuesto ni el árbol (ni el usuario, en el registro).
- `presupuesto` nunca importa `categoria`: se documenta en `AGENTS.md` que una feature se
  comunica con otras mediante eventos y que `evento` es un subpaquete permitido para ello.
- Los tests existentes de categorías y presupuestos que asumían árboles vacíos y nombres libres
  se ajustan.
- Solo backend. No hay cambios rotos en la API: las rutas y los contratos no cambian, solo el
  contenido inicial de un presupuesto.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `categorias`: nuevo requisito "Árbol inicial de un presupuesto" (registro, `POST` de
  presupuesto, árbol propio por presupuesto y atomicidad).

`registro-usuarios` no se modifica: la respuesta y el comportamiento visible del registro no
cambian; el árbol inicial es un comportamiento de `categorias`.

## Impact

- Backend, `com.presupuesto.presupuesto`: nuevo subpaquete `evento` y publicación del evento en
  `PresupuestoService.crear(...)` y `crearInicial(...)`.
- Backend, `com.presupuesto.categoria.service`: listener del evento y constante con los nombres.
- Tests: nuevos (listener, constante, integración, atomicidad en `RegistroAtomicidadTest`) y
  ajustes en los de integración de categorías y presupuestos.
- `AGENTS.md`: regla de comunicación por eventos y árbol de paquetes.
- Fuera de alcance: migrar presupuestos existentes, nombres configurables o por idioma y el
  frontend.
