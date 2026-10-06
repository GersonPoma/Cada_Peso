## 1. Dominio y persistencia

- [x] 1.1 Crear `AsignacionMensual` (`com.presupuesto.asignacion.entity`, extiende `EntidadBase`,
  `@Table(name = "asignaciones_mensuales")` con la restricción única
  `uk_asignaciones_mensuales_categoria_mes (categoria_id, mes)`, `@SuperBuilder`, setters
  bloqueados, métodos `fijarAsignado(long)` y `sumarAsignado(long)`, y validación `@PrePersist`
  y `@PreUpdate` de que `mes` es día 1) y su test `AsignacionMensualTest` (mismo paquete).
  Verificar: ambos métodos, sin setters por reflexión, `mes` distinto de día 1 rechazado.
- [x] 1.2 Crear `AsignacionMensualRepository` (`com.presupuesto.asignacion.repository`:
  `findByCategoriaIdAndMes` y la proyección de asignaciones del
  presupuesto con mes ≤ un tope como `(categoriaId, mes, asignado)`) y
  `AsignacionMensualRepositoryTest` (mismo paquete, `@Transactional`). Verificar: acotado por
  presupuesto y por mes, y la restricción única con `saveAndFlush` dentro de
  `assertThrows(DataIntegrityViolationException.class, ...)`; la misma categoría en otro mes y
  el mismo mes en otra categoría sí se guardan.
- [x] 1.3 Crear `ActividadMensualRepository` (`com.presupuesto.asignacion.repository`,
  `extends Repository<Transaccion, Long>`: `actividadSimple`, `actividadDividida`,
  `ingresosSinCategoria`, `saldosInicialesPositivos`, con proyecciones de interfaz) y
  `ActividadMensualRepositoryTest` (mismo paquete, `@Transactional`). Verificar con números
  concretos: agrupa por categoría y mes calendario (31 de enero y 1 de febrero en meses
  distintos), incluye subtransacciones, ignora cuentas con `enPresupuesto` falso, cuenta cuentas
  cerradas, respeta `fecha ≤ hasta`, no mezcla presupuestos; ingresos solo con monto > 0, sin
  categoría y sin subtransacciones; saldos iniciales sin tarjeta, sin negativos y sin cuentas
  fuera del presupuesto.

## 2. DTO

- [x] 2.1 Crear `AsignarRequest` (`com.presupuesto.asignacion.dto.request`, record con `asignado`
  `@NotNull Long`) y `AsignarRequestTest` (mismo paquete). Verificar: nulo inválido; 0, negativo
  y positivo válidos.
- [x] 2.2 Crear `MoverDineroRequest` (`com.presupuesto.asignacion.dto.request`, record con
  `origenId`, `destinoId` `@NotNull` y `monto` `@NotNull @Positive`) y `MoverDineroRequestTest`
  (mismo paquete). Verificar: ids nulos, monto nulo, 0 y negativo inválidos; monto 1 válido.
- [x] 2.3 Crear `CategoriaMesResponse`, `GrupoMesResponse`, `MesPresupuestoResponse` y
  `AsignacionActualizadaResponse` (`com.presupuesto.asignacion.dto.response`, cada uno con su
  `desde(...)`; `sobregastada` = `disponible < 0`; `mes` como `yyyy-MM`) y sus tests
  `CategoriaMesResponseTest`, `GrupoMesResponseTest`, `MesPresupuestoResponseTest` y
  `AsignacionActualizadaResponseTest` (mismo paquete). Verificar: todos los campos mapeados,
  `sobregastada` en −1, 0 y 1, y totales.

## 3. Cálculo y services

- [x] 3.1 Crear `CalculoMensual` (`com.presupuesto.asignacion.service`, package-private, puro:
  disponible acumulado, sobregasto no arrastrado, `listoParaAsignar`) y `CalculoMensualTest`
  (mismo paquete). Verificar con los ejemplos A, B, C y el negativo de la spec: arrastre
  positivo, sobregasto que no se arrastra y sí baja el listo de los meses siguientes (una sola
  vez), meses intermedios sin datos, mes anterior al primer dato, actividad sin asignación,
  listo negativo, sin datos.
- [x] 3.2 Crear `MesParametro` (`com.presupuesto.asignacion.service`, package-private: interpreta
  `yyyy-MM` y rango 2000–2100 con `DatosInvalidosException`) y `MesParametroTest` (mismo
  paquete). Verificar: `2026-13`, `2026-1`, `2026-00`, `26-01`, `enero`, `1999-12`, `2101-01`,
  vacío y nulo dan 400; `2000-01`, `2100-12` y `2026-01` son válidos.
- [x] 3.3 Crear `CalculadoraMes` (`com.presupuesto.asignacion.service`, package-private
  `@Component`: arma las entradas con los repositorios y llama a `CalculoMensual`) y
  `CalculadoraMesTest` (mismo paquete, Mockito). Verificar: combina actividad simple y dividida
  del mismo par categoría-mes, suma ingresos y saldos iniciales, y pide a los repositorios el
  tope `fin del mes`.
- [x] 3.4 Crear `MesPresupuestoService` (`com.presupuesto.asignacion.service`: `obtener` con
  `incluirOcultas`; llama primero a `PresupuestoService.obtenerDelUsuario`, luego interpreta el
  mes, lee grupos y categorías en su orden con los repositorios de `categoria`, arma los totales
  con las categorías incluidas) y `MesPresupuestoServiceTest` (mismo paquete, Mockito).
  Verificar: 404 por presupuesto antes que el 400 del mes, orden, ocultas, totales del ejemplo
  de la spec, categorías sin datos en 0.
- [x] 3.5 Crear `AsignacionService` (`com.presupuesto.asignacion.service`: `asignar` y
  `moverDinero` como describe el design, atómicos, con el builder y
  `DataIntegrityViolationException` a `ConflictoException`) y `AsignacionServiceTest` (mismo
  paquete, Mockito). Verificar: 404 por presupuesto y por categoría en cada método, mes
  inválido, crear y luego actualizar la misma fila, categoría oculta, mover correcto, mover todo,
  disponible insuficiente y sobregastado (422), mismo origen y destino (400), y que ante
  cualquier rechazo no se guarda nada.

## 4. Controller y pruebas de integración

- [x] 4.1 Crear `AsignacionController` (`com.presupuesto.asignacion.controller`, bajo
  `/api/v1/presupuestos/{presupuestoId}/meses/{mes}`: `PUT /categorias/{categoriaId}`, `GET`
  con `incluirOcultas`, `POST /mover-dinero`) con `@AuthenticationPrincipal UsuarioAutenticado`
  y `@Valid`.
- [x] 4.2 Crear `AsignacionIntegracionTest` (`com.presupuesto.asignacion.controller`,
  `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Transactional`; crea cuentas, categorías y
  transacciones por la API de las otras features). Verificar, comprobando estado **y** `codigo`:
  401 sin token en las tres rutas; aislamiento entre usuarios y entre presupuestos del mismo
  usuario con una aserción de 404 propia por operación (asignar, consultar, mover como origen y
  como destino); categoría ajena e inexistente; categoría oculta; mes inválido en cada ruta;
  asignar crea y luego actualiza (una fila, sin sumar); meses futuros; actividad simple y con
  subtransacciones; cuentas fuera del presupuesto ignoradas y cerradas incluidas; arrastre de un
  mes positivo al siguiente; sobregasto que no se arrastra y sí reduce el `listoParaAsignar` de
  febrero y marzo (ejemplo B: 480000, 440000, 440000); ingresos sin categoría y saldos
  iniciales (ejemplo C); `listoParaAsignar` negativo; `incluirOcultas`; mover dinero (ejemplo
  E, todo el disponible, sin disponible suficiente, origen sobregastado, mismo origen y destino,
  monto 0 y negativo, ids ausentes, categoría ajena, atomicidad: tras un rechazo el mes queda
  igual).

## 5. Documentación y cierre

- [x] 5.1 Actualizar `AGENTS.md` (raíz): agregar `asignacion/` al árbol, a la alternancia del
  `grep` de `comun/` (`usuario|auth|presupuesto|cuenta|categoria|transaccion|asignacion`) y a la
  regla de dependencias.
- [x] 5.2 Verificar dependencias desde `backend/src`, ambos con cero líneas:
  `grep -rnE "import com\.presupuesto\.(usuario|auth|presupuesto|cuenta|categoria|transaccion|asignacion)"
  main/java/com/presupuesto/comun test/java/com/presupuesto/comun`, y
  `grep -rn "import com\.presupuesto\.asignacion"` sobre `main` y `test` de `presupuesto`,
  `cuenta`, `categoria` y `transaccion`. Mostrar la salida de ambos.
- [x] 5.3 Ejecutar la suite completa con `.\mvnw.cmd test` desde `backend/` (JDK 21): los 588
  tests existentes siguen pasando; mostrar el total final (588 + los nuevos), sin fallos.
- [x] 5.4 Revisar el límite de 100 columnas en los archivos nuevos y modificados. No hacer
  commit.
