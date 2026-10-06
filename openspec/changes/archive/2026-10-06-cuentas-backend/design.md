# Design

## Context

La feature `presupuesto` ya ofrece `PresupuestoService.obtenerDelUsuario(presupuestoId,
usuarioId)` y el patrón de nombre único (`nombre` + `nombreNormalizado`, consulta previa y
traducción de `DataIntegrityViolationException`). `cuenta` replica ese patrón y cuelga de un
presupuesto. Motivo y alcance en `proposal.md`; comportamiento en la spec `cuentas`.

## Goals / Non-Goals

**Goals:**
- Feature `cuenta` conforme a la estructura obligatoria de AGENTS.md.
- Reutilizar `obtenerDelUsuario` como única vía de validación de pertenencia.

**Non-Goals:**
- Saldo calculado, transacciones, paginación, borrado, frontend.

## Decisions

**1. Entidad `Cuenta`** (`@Table(name = "cuentas")`, `uk_cuentas_presupuesto_nombre` sobre
`presupuesto_id, nombre_normalizado`). `presupuesto` es `@ManyToOne(LAZY, optional = false)`.
`nombre` y `nombreNormalizado` con `@Setter(AccessLevel.NONE)`; solo cambian con
`renombrar(String)`, y `normalizar(String)` (`toLowerCase(Locale.ROOT)`) es estático, igual que
en `Presupuesto`. `tipo` es `@Enumerated(EnumType.STRING)`, también con setter bloqueado: solo cambia con
`cambiarTipo(TipoCuenta)`, que el service llama tras validar la regla del saldo negativo.
`enPresupuesto` y `saldoInicial` se
declaran `updatable = false` y sin setter (`@Setter(AccessLevel.NONE)`), de modo que el tipo
impide editarlos. `cerrada` cambia con `cerrar()` y `reabrir()` (setter bloqueado, también). El
builder usa `@Builder.Default` para `cerrada = false`.

**2. `TipoCuenta`** (enum en `entity`, persiste) con `admiteSaldoNegativo()`: verdadero solo para
`TARJETA_CREDITO` y `PRESTAMO`. La regla vive en el enum para que el alta y la edición la
compartan.

**3. Requests como records.**
- `CrearCuentaRequest(@NotBlank @Size(max = 100) String nombre, @NotNull TipoCuenta tipo,
  Boolean enPresupuesto, Long saldoInicial)`: el constructor compacto recorta el nombre y deja
  `enPresupuesto` en `true` y `saldoInicial` en `0` si vienen `null`. Un tipo desconocido lo
  rechaza Jackson (`HttpMessageNotReadableException` → 400 `DATOS_INVALIDOS`, ya manejado).
- `ActualizarCuentaRequest(nombre, tipo)`: solo esos dos campos. Jackson ignora propiedades
  desconocidas (valor por defecto de Spring Boot), así que `saldoInicial` y `enPresupuesto` se
  descartan sin error, igual que la moneda en presupuestos.

**4. Saldo negativo → 422.** Es una regla de negocio dependiente de dos campos, no de uno solo,
así que va en el service (`ReglaNegocioException`) y no como anotación de Bean Validation. Por
eso `cuenta` no necesita subpaquete `validacion` (AGENTS.md: solo los subpaquetes necesarios).
En el PUT se comprueba contra el `saldoInicial` guardado: `saldoInicial < 0` y
`!nuevoTipo.admiteSaldoNegativo()`.

**5. Doble defensa ante duplicados.** Consulta previa
(`existsByPresupuestoIdAndNombreNormalizado`, y en el PUT `...AndIdNot`) y `saveAndFlush` con
traducción de `DataIntegrityViolationException` a `ConflictoException(CUENTA_YA_EXISTE)`. Se
agrega `CUENTA_YA_EXISTE` a `CodigoError`.

**6. Búsqueda siempre por `(id, presupuestoId)`** con `findByIdAndPresupuestoId`; cada método
del service llama primero a `presupuestoService.obtenerDelUsuario(...)`. Cuenta inexistente o de
otro presupuesto → `RecursoNoEncontradoException("Cuenta no encontrada")`.

**7. Listado.** `findByPresupuestoIdOrderByNombreNormalizado` y la variante
`findByPresupuestoIdAndCerradaFalseOrderByNombreNormalizado`; el controller recibe
`@RequestParam(defaultValue = "false") boolean incluirCerradas`. Devuelve `List`, sin paginar.

**8. Cerrar y reabrir idempotentes**: asignan el estado y guardan; repetir deja el mismo
resultado con `200`. Se permite editar (PUT) una cuenta cerrada; la spec no lo prohíbe.

**9. Respuesta.** `CuentaResponse(id, nombre, tipo, enPresupuesto, saldoInicial, cerrada,
fechaCreacion, fechaActualizacion)` con `desde(Cuenta)`. No incluye saldo calculado.

**10. Sin DELETE.** El controller no declara el método; Spring responde `405` por sí mismo y el
test solo comprueba el estado. No se agrega un manejador de 405 (fuera de alcance).

**11. AGENTS.md.** Se agrega `cuenta/` (controller, dto/request, dto/response, entity,
repository, service) al árbol actual y `cuenta` a la alternancia del `grep` de `comun/`.

### Clases nuevas o modificadas

| Clase | Paquete | Test (paquete) |
|---|---|---|
| `Cuenta` | `com.presupuesto.cuenta.entity` | `CuentaTest` (`...entity`) |
| `TipoCuenta` | `com.presupuesto.cuenta.entity` | `TipoCuentaTest` (`...entity`) |
| `CuentaRepository` | `com.presupuesto.cuenta.repository` | `CuentaRepositoryTest` (`...repository`) |
| `CrearCuentaRequest` | `com.presupuesto.cuenta.dto.request` | `CrearCuentaRequestTest` (`...dto.request`) |
| `ActualizarCuentaRequest` | `com.presupuesto.cuenta.dto.request` | `ActualizarCuentaRequestTest` (`...dto.request`) |
| `CuentaResponse` | `com.presupuesto.cuenta.dto.response` | `CuentaResponseTest` (`...dto.response`) |
| `CuentaService` | `com.presupuesto.cuenta.service` | `CuentaServiceTest` (`...service`, Mockito) |
| `CuentaController` | `com.presupuesto.cuenta.controller` | `CuentaIntegracionTest` (`...controller`) |
| `CodigoError` (modificada) | `com.presupuesto.comun.excepcion` | cubierto por `CuentaIntegracionTest` |

## Risks / Trade-offs

- [Un test no transaccional deja cuentas que impiden borrar un presupuesto por la FK] → los
  tests de integración usan `@Transactional`, que revierte todo.
- [`Locale.ROOT` no cubre todo el plegado Unicode] → aceptable, igual que en presupuestos.
- [Cambiar el tipo no recalcula nada] → no hay saldo calculado aún; las transacciones futuras
  deberán respetar `admiteSaldoNegativo()`.
- [Editar una cuenta cerrada] → permitido por simplicidad; revisable si se quiere bloquear.
