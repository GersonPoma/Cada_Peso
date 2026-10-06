# Design

## Context

`PresupuestoService.crear(...)` (POST) y `crearInicial(...)` (registro, se une a la transacción
del alta) pasan ambos por `guardarNuevo(...)`. `categoria` ya depende de `presupuesto`
(`GrupoCategoriaService` usa `PresupuestoService`); lo contrario está prohibido por `AGENTS.md`.
Los grupos y categorías llevan `orden` desde 0, `oculto`/`oculta` en `false` por defecto y
unicidad por nombre normalizado (restricciones únicas de Hibernate).

## Goals / Non-Goals

**Goals:**
- Árbol inicial confirmado en la misma transacción que el presupuesto, sin que `presupuesto`
  conozca `categoria`.
- Un mecanismo reutilizable por otras features que deban reaccionar a un presupuesto nuevo.

**Non-Goals:**
- Migrar presupuestos existentes, nombres configurables o por idioma, frontend.
- Eventos asíncronos o `@TransactionalEventListener`.

## Decisions

1. **Evento de Spring publicado desde `PresupuestoService`.** `PresupuestoCreadoEvento` es un
   record con el `Presupuesto` ya guardado (`saveAndFlush`, con id). Se publica con
   `ApplicationEventPublisher` al final de `crear(...)` y de `crearInicial(...)`, después de
   guardar. Ambos pasan por `guardarNuevo(...)`, pero `renombrar` no, así que la publicación
   va en el final de esos dos métodos (o en `guardarNuevo`, que es equivalente y evita
   olvidarla en un camino nuevo; se elige `guardarNuevo`).
   *Descartado:* que `presupuesto` llame a `categoria` (viola la regla de dependencias), o que
   `AuthService` y el controller creen el árbol (duplica la regla y es fácil olvidar un camino).
2. **`@EventListener` síncrono, no `@TransactionalEventListener`.** Corre en el hilo y la
   transacción del publicador: el rollback deshace presupuesto, árbol y, en el registro, usuario
   y perfil; una excepción del listener se propaga al publicador. *Descartado:*
   `@TransactionalEventListener(AFTER_COMMIT)`, que ya no participa en la transacción y dejaría
   un presupuesto sin árbol si falla.
3. **Listener `CategoriasInicialesListener`** (`categoria/service`) con
   `GrupoCategoriaRepository` y `CategoriaRepository`. Guarda los grupos (orden 0..3) y luego
   las categorías (orden 0..n-1 dentro de cada grupo); el nombre normalizado sale de
   `GrupoCategoria.normalizar` y `Categoria.normalizar`; nada oculto (valor por defecto). Usa
   los repositorios y no `GrupoCategoriaService`/`CategoriaService`, que exigen `usuarioId` y
   revalidan la pertenencia de un presupuesto recién creado.
4. **`CategoriasIniciales`**: clase final con una constante inmutable
   `Map<String, List<String>>` en español (orden de inserción = orden de creación), package-private
   porque solo la usa el listener, que está en el mismo paquete.
5. **Subpaquete `presupuesto/evento`**: nuevo; `AGENTS.md` lo declara permitido para los eventos
   de dominio que una feature publica (se agrega a la plantilla, a la tabla de subpaquetes y al
   árbol actual). El record es `public` porque lo consume `categoria`.
6. **Tests existentes.** Los de integración de `categoria/controller` que usan nombres como
   "Facturas" cambian a nombres que no choquen, y los conteos pasan a ser relativos al árbol
   inicial (4 grupos, 13 categorías). `PresupuestoServiceTest` (Mockito) necesita un
   `ApplicationEventPublisher` simulado por el constructor nuevo; los tests de repositorio,
   entidad y DTO crean datos directamente y no se ven afectados.

## Risks / Trade-offs

- [Acoplamiento implícito: nada en `presupuesto` muestra quién escucha] → documentado en
  `AGENTS.md` y cubierto por pruebas de integración.
- [Un fallo del listener revierte la creación del presupuesto] → es lo deseado (atomicidad); el
  costo son ~17 inserciones por presupuesto nuevo.
- [Los nombres iniciales ocupan nombres: crear un grupo "Facturas" da `409`] → especificado; es
  el comportamiento normal de unicidad.
- [Presupuestos anteriores quedan sin árbol] → fuera de alcance; se crea a mano.

## Paquetes de las clases

| Clase | Paquete | Test (mismo paquete) |
|-------|---------|----------------------|
| `PresupuestoCreadoEvento` (nueva, record) | `com.presupuesto.presupuesto.evento` | `PresupuestoCreadoEventoTest` en `com.presupuesto.presupuesto.evento` |
| `PresupuestoService` (modificada) | `com.presupuesto.presupuesto.service` | `PresupuestoServiceTest` en `com.presupuesto.presupuesto.service` |
| `CategoriasIniciales` (nueva) | `com.presupuesto.categoria.service` | `CategoriasInicialesTest` en `com.presupuesto.categoria.service` |
| `CategoriasInicialesListener` (nueva) | `com.presupuesto.categoria.service` | `CategoriasInicialesListenerTest` en `com.presupuesto.categoria.service` (Mockito) |
| — (integración del árbol inicial, nuevo) | — | `ArbolInicialIntegracionTest` en `com.presupuesto.categoria.controller` |
| `RegistroAtomicidadTest` (ampliado) | — | `com.presupuesto.auth.controller` |
| `CategoriaIntegracionTest`, `GrupoCategoriaIntegracionTest` (ajustados) | — | `com.presupuesto.categoria.controller` |
| `PresupuestoIntegracionTest` (ajustado si hace falta) | — | `com.presupuesto.presupuesto.controller` |
