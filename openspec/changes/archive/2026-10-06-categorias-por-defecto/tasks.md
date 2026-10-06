# Tasks

Comandos desde `backend/` con `.\mvnw.cmd` y JDK 21.

## 1. Evento en `presupuesto`

- [x] 1.1 Crear el record `PresupuestoCreadoEvento(Presupuesto presupuesto)` en
  `com.presupuesto.presupuesto.evento`, con su test `PresupuestoCreadoEventoTest` en
  `com.presupuesto.presupuesto.evento`; verificar con
  `.\mvnw.cmd test -Dtest=PresupuestoCreadoEventoTest`.
- [x] 1.2 Inyectar `ApplicationEventPublisher` en `PresupuestoService`
  (`com.presupuesto.presupuesto.service`) y publicar el evento al final de `guardarNuevo(...)`,
  después de guardar, de modo que lo cubran `crear` y `crearInicial`; ampliar
  `PresupuestoServiceTest` (mismo paquete) para verificar que se publica una vez con el
  presupuesto guardado y que no se publica si el nombre ya existe (409). Verificar con
  `.\mvnw.cmd test -Dtest=PresupuestoServiceTest`.

## 2. Árbol inicial en `categoria`

- [x] 2.1 Crear `CategoriasIniciales` en `com.presupuesto.categoria.service` con los 4 grupos y
  sus categorías en el orden indicado en el spec, y `CategoriasInicialesTest` en el mismo
  paquete (sin nombres repetidos dentro de un grupo, sin grupos repetidos, nombres de 1 a 100
  caracteres); verificar con `.\mvnw.cmd test -Dtest=CategoriasInicialesTest`.
- [x] 2.2 Crear `CategoriasInicialesListener` en `com.presupuesto.categoria.service`
  (`@EventListener` síncrono sobre `PresupuestoCreadoEvento`) que guarda grupos y categorías con
  orden consecutivo desde 0 y sin ocultos, y `CategoriasInicialesListenerTest` (Mockito) en el
  mismo paquete: número de grupos y categorías, nombres, normalizados, orden y presupuesto
  asignados, y que una excepción del repositorio se propaga. Verificar con
  `.\mvnw.cmd test -Dtest=CategoriasInicialesListenerTest`.

## 3. Ajuste de tests existentes

- [x] 3.1 Ajustar `CategoriaIntegracionTest` y `GrupoCategoriaIntegracionTest`
  (`com.presupuesto.categoria.controller`) con nombres que no choquen con el árbol inicial y
  conteos relativos a él; revisar también `PresupuestoIntegracionTest`
  (`com.presupuesto.presupuesto.controller`) y los demás tests que registren o creen
  presupuestos. Verificar con `.\mvnw.cmd test` sin fallos.

## 4. Integración y atomicidad

- [x] 4.1 Crear `ArbolInicialIntegracionTest` en `com.presupuesto.categoria.controller`: registro
  deja "Mi presupuesto" con los 4 grupos y sus categorías en orden (GET `/categorias`); `POST` de
  presupuesto también; dos presupuestos del mismo usuario con árboles propios; un `POST` con
  nombre repetido (409) no crea árbol. Verificar con
  `.\mvnw.cmd test -Dtest=ArbolInicialIntegracionTest`.
- [x] 4.2 Ampliar `RegistroAtomicidadTest` (`com.presupuesto.auth.controller`): si falla la
  creación de las categorías no queda el usuario ni el presupuesto (y limpiar al terminar); y
  un caso equivalente para `POST /presupuestos`. Verificar con
  `.\mvnw.cmd test -Dtest=RegistroAtomicidadTest`.

## 5. Documentación y verificación final

- [x] 5.1 Documentar en `AGENTS.md` el subpaquete `evento` (plantilla, tabla de subpaquetes y
  árbol actual) y la regla "presupuesto comunica a otras features con eventos
  (`PresupuestoCreadoEvento`), nunca importándolas"; verificar con
  `grep -rnE "import com\.presupuesto\.(categoria|cuenta)" main/java/com/presupuesto/presupuesto`
  y el equivalente sobre `test/java/com/presupuesto/presupuesto` (desde `backend/src`), ambos con
  cero líneas, más el `grep` de `comun/` con cero líneas.
- [x] 5.2 Ejecutar toda la suite con `.\mvnw.cmd test` y verificar que pasan todos los tests
  existentes y los nuevos, mostrando el total; confirmar que no se hizo commit.
