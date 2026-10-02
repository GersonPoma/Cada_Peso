# Design

## Context

Hoy el frontend (`/frontend`, generado con Angular 21 en `fundacion-proyecto`) no tiene ningún
pipe de formato propio ni configuración de locale: usar los pipes nativos `date`/`number`/
`currency` de Angular sin `registerLocaleData` lanza un error en tiempo de ejecución para
cualquier locale distinto de `en-US`, y el backend todavía no fija zona horaria, nombre de
aplicación ni política de exposición de errores no capturados (ver proposal.md - Why). Este
change es, igual que `fundacion-proyecto`, trabajo de configuración/fundación con una única
pieza de comportamiento observable nueva: el formato regional de montos y fechas (capacidad
`formato-regional`, ver `specs/formato-regional/spec.md`).

## Goals / Non-Goals

**Goals:**
- Nombrar el producto de forma consistente en frontend (pestaña) y backend
  (`spring.application.name`).
- Formatear montos y fechas según la región del navegador sin configuración manual y sin el
  error en tiempo de ejecución de Angular por falta de `registerLocaleData`.
- Garantizar que una fecha de negocio sin hora (`LocalDate`) nunca se muestre desplazada un día
  por conversión de zona horaria, mientras que un instante exacto (`Instant`) sí se convierte a
  la hora local del usuario.
- Dejar la configuración operativa básica del backend (zona horaria, logging SQL, exposición de
  errores) resuelta antes de implementar features de negocio.
- Que el idioma del documento HTML (`lang`) sea consistente con que la interfaz es en español, y
  dejar documentada la convención de qué idioma usa cada capa de mensajes al usuario (validación
  de formularios en el frontend, validación por defecto del backend, excepciones de negocio).

**Non-Goals:**
- No se implementan formularios ni pantallas de negocio en este change (`Sin features de
  negocio`, ver proposal.md); la estrategia de validación de formularios (Angular `Validators` en
  español replicando las reglas del backend) queda documentada en `AGENTS.md` como convención
  para que la siga el primer change que agregue un formulario real.
- No se implementa selección manual de idioma/región por el usuario (eso, si se necesita, es un
  change futuro).
- No se traduce la UI a múltiples idiomas (el idioma de la interfaz ya es español por
  convención del dominio; este change solo cubre el *formato* de números/fechas, no el idioma de
  las etiquetas).
- No se introduce ninguna librería de i18n de terceros (ngx-translate, Angular i18n, etc.): solo
  la API `Intl` nativa del navegador.
- No se separan perfiles de Spring (`dev`/`prod`); `spring.jpa.show-sql=true` aplica a todos los
  entornos en esta etapa, igual que el resto de `application.properties` hasta ahora.

## Decisions

**Región de formato centralizada en una función propia (`regionUsuario()`), no en cada pipe.**
Se crea `shared/formato/region-usuario.ts` con una función que lee `navigator.language`, valida
que sea un identificador de locale soportado por el motor JS con
`Intl.NumberFormat.supportedLocalesOf(...)`, y devuelve `en-US` si no hay valor o no es válido.
Ambos pipes (`monto`, `fecha`) la llaman internamente. Alternativa descartada: resolver la
región de forma independiente dentro de cada pipe — se descarta porque duplicaría la lógica de
validación/fallback y arriesgaría que un pipe se actualice y el otro no.

**Dos pipes puros propios en `shared/formato/` (`MontoPipe` con nombre `monto`, `FechaPipe` con
nombre `fecha`), basados en `Intl.NumberFormat`/`Intl.DateTimeFormat`, en vez de los pipes
`date`/`number`/`currency` de Angular.**
Los pipes nativos de Angular dependen de `LOCALE_ID` y de datos de locale registrados con
`registerLocaleData` (sin eso, lanzan `NG0701` para cualquier locale que no sea `en-US`
compilado por defecto). Usar `Intl` directamente evita esa dependencia: el soporte de locales
del navegador ya cubre todas las regiones sin registrar nada. Ambos pipes son puros (`pure:
true`, el valor por defecto) porque, dado el mismo valor de entrada y la misma región detectada,
el resultado es siempre el mismo. Alternativa descartada: usar los pipes nativos de Angular con
`registerLocaleData` para cada región que se quiera soportar — se descarta porque obligaría a
listar de antemano cada región soportada y a cargar sus datos de locale, cuando el objetivo es
soportar automáticamente cualquier región sin mantenimiento.

**`MontoPipe` recibe milésimas + código de moneda ISO 4217 y usa
`Intl.NumberFormat(region, { style: 'currency', currency: codigoMoneda })`.**
Convierte milésimas a la unidad monetaria completa dividiendo entre 1000 antes de formatear
(consistente con la convención de dinero en milésimas de `AGENTS.md`). La región ya resuelve el
separador de miles/decimales y la posición del símbolo de moneda sin configuración adicional. Si
el monto en milésimas recibido es `null` o `undefined` (p. ej. mientras una plantilla todavía no
tiene datos cargados), el pipe devuelve una cadena vacía sin lanzar ningún error. Un código de
moneda inválido no se valida aparte: se deja que `Intl.NumberFormat` lance su propio `RangeError`
nativo, ya que es un string con formato inválido (no una ausencia de valor) y no hace falta
duplicar esa validación.

**`FechaPipe` distingue el formato del string de entrada (Instant vs. `LocalDate`), no el tipo
TypeScript, porque ambos llegan como `string` desde el JSON de la API.**
Si el valor recibido es `null` o `undefined` (p. ej. mientras una plantilla todavía no tiene
datos cargados), el pipe devuelve una cadena vacía sin lanzar ningún error, antes de intentar
reconocer ningún formato. Si el string coincide con `yyyy-MM-ddTHH:mm:ss...Z` (un instante ISO-8601 con zona UTC), se
interpreta como un momento exacto: `new Date(valor)` (el parser nativo de `Date` interpreta
correctamente el sufijo `Z`) y se formatea con `Intl.DateTimeFormat(region, opciones)` **sin**
fijar `timeZone` explícito, de modo que el navegador aplique automáticamente la zona horaria
local del usuario. Si el string coincide exactamente con `yyyy-MM-dd` (un `LocalDate`, p. ej.
fecha de una transacción o mes del presupuesto), se parsean año/mes/día por separado
(`valor.split('-')`), se construyen con `Date.UTC(anio, mes - 1, dia)` y se formatean con
`Intl.DateTimeFormat(region, { ...opciones, timeZone: 'UTC' })`: fijar `timeZone: 'UTC'`
explícitamente evita que el navegador vuelva a aplicar su propia zona horaria sobre un valor que
ya no debe desplazarse. Esta es la causa raíz del bug que motiva este change (ver proposal.md -
Why): sin ninguna de las dos técnicas, un `Date` construido a partir de `"2026-10-01"` se
interpreta como medianoche UTC y, en cualquier zona horaria con desfase negativo, se muestra
como el día anterior. Un string no nulo que no coincide con ninguno de los dos formatos hace que
el pipe lance un error (no se captura ni se devuelve un valor por defecto): los dos formatos
están garantizados por cómo el backend serializa `Instant`/`LocalDate` (ver convención de
`AGENTS.md` sobre `LocalDate` vs `Instant`), así que un string con otro formato indica un error
de integración que debe ser visible, no silenciado. Esto es distinto de recibir `null`/`undefined`
(ver arriba): la ausencia de valor es un caso esperado (dato todavía no cargado), mientras que un
string presente con forma irreconocible es un error de integración.

**Favicon SVG inline (círculo verde `#2E7D32` + "C" blanca), sin herramienta de generación de
imágenes.**
Un SVG de 16×16 con un `<circle>` de fondo y un `<text>` centrado es suficiente para el nivel de
detalle pedido y se escribe a mano como XML; no se necesita ninguna herramienta de diseño ni
dependencia nueva. Se elimina `public/favicon.ico` (el que genera `ng new`) para que no quede un
favicon sin usar y para que no haya ambigüedad sobre cuál usa `index.html`.

**Zona horaria UTC fijada explícitamente en el arranque de la JVM (`TimeZone.setDefault(...)`
como primera línea de `main`), además de `hibernate.jdbc.time_zone=UTC`.**
Spring Boot no tiene una propiedad `application.properties` que fije la zona horaria por
defecto de la JVM completa (afecta a cualquier código que use `TimeZone.getDefault()`, no solo a
Hibernate); la forma estándar de garantizarlo antes de que cualquier otro componente (driver
JDBC, Hibernate, Jackson) se inicialice es llamarlo como la primera instrucción de
`BackendApplication.main()`, antes de `SpringApplication.run(...)`. La propiedad
`hibernate.jdbc.time_zone=UTC` se agrega además, a nivel de Hibernate, para que las columnas
`TIMESTAMP` se lean/escriban en UTC incluso si en el futuro algún componente cambiara la zona
horaria de la JVM en tiempo de ejecución; ambas son redundantes a propósito. Esto es coherente
con que los instantes exactos se persisten y viajan en UTC, y el frontend es el único
responsable de convertirlos a la zona horaria del usuario solo al mostrarlos (ver `FechaPipe`
arriba).

`TimeZone.setDefault(...)` en `main()` no alcanza para que los tests corran en UTC: los tests de
`@SpringBootTest` arrancan el contexto llamando directamente a `SpringApplication.run(...)` (vía
el test runner de Spring Boot), sin pasar por el método `main()` de `BackendApplication` — así
que esa línea nunca se ejecuta durante los tests. Para que los tests corran bajo la misma zona
horaria UTC que la aplicación real, se configura `maven-surefire-plugin` en el `pom.xml` con
`<argLine>-Duser.timezone=UTC</argLine>`, que fija la zona horaria por defecto de la JVM forkeada
por Surefire desde su arranque, antes de que corra cualquier test.

**`spring.web.error.include-stacktrace=never` e `include-message=never` como refuerzo del mismo
contrato de `ManejadorGlobalExcepciones`, no como mecanismo nuevo.**
Estas propiedades solo afectan al manejador de errores por defecto de Spring Boot
(`DefaultErrorAttributes`), que entra en juego únicamente para errores que no llegan a
`ManejadorGlobalExcepciones` (por ejemplo, errores producidos fuera del dispatcher de Spring
MVC). Se agregan para que ese camino de error, aunque infrecuente, respete la misma garantía de
no exponer detalles internos que ya aplica a los errores manejados (`fundacion-proyecto`).

Verificar este camino de error requiere permitir `/error` en `SecurityConfig` (`comun/seguridad`)
y usar un cliente HTTP real contra un servidor levantado de verdad, no `MockMvc`. El forward
interno del contenedor servlet a `/error` (el que dispara `DefaultErrorAttributes` y, por lo
tanto, estas propiedades) no ocurre en `MockMvc`: su dispatcher simulado nunca reenvía la
petición a `/error` como lo haría un contenedor real, así que una prueba `MockMvc` contra una
ruta no mapeada no ejercita el camino que estas propiedades protegen. Por eso la prueba usa
`@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` con un cliente HTTP
real (ej. `TestRestTemplate`) contra el servidor embebido real. Además, como
`anyRequest().authenticated()` de `SecurityConfig` protege cualquier ruta —incluidas las no
mapeadas— y el forward a `/error` vuelve a pasar por la cadena de filtros de seguridad, hace
falta: (a) que la ruta de prueba original sea una ya permitida sin autenticación (p. ej. una
ruta inexistente bajo `/api/v1/auth/**`), para que la petición original no se bloquee con 401
antes de llegar a 404; y (b) agregar `/error` a los patrones permitidos sin autenticación de
`SecurityConfig`, para que el forward interno a `/error` tampoco se bloquee con 401 — si no, la
respuesta observada sería el `ProblemDetail` de `NO_AUTENTICADO` de `fundacion-proyecto`, no el
comportamiento de `DefaultErrorAttributes` que este change quiere verificar.

**`spring.jpa.show-sql=true` + `spring.jpa.properties.hibernate.format_sql=true`, sin separar
perfiles todavía.**
Ayuda a depurar durante el desarrollo de las features de negocio que siguen a este change.
Aplica a todos los entornos por ahora, igual que el resto de `application.properties`; separar
un perfil `dev` que active esto solo en desarrollo queda como mejora futura si el proyecto
introduce perfiles de Spring.

**Mensajes de validación por defecto en el idioma de la JVM de cada máquina, sin
configuración.**
Los mensajes de validación por defecto del backend (Jakarta Validation: `@NotNull`, `@Size`,
etc.) usan el idioma de la JVM de cada máquina, sin ninguna configuración adicional, y son solo
un respaldo: el frontend valida y muestra sus propios mensajes en español (ver decisión
siguiente), así que en el flujo normal un usuario no debería llegar a verlos. No se configura
`spring.web.locale`/`spring.web.locale-resolver`: esas propiedades solo fijan el
`LocaleResolver` de Spring MVC, y no afectan a Hibernate Validator, que interpola sus mensajes
por defecto con el locale por defecto de la JVM, así que fijarlas no cambiaría el idioma de esos
mensajes. Tampoco se toca `Locale.setDefault()` de la JVM (que afectaría también a formateo
numérico/de fechas en el backend) solo para uniformar un mensaje de respaldo. Esto es
independiente de los mensajes de negocio propios (`ReglaNegocioException` y el resto de la
jerarquía de `comun/excepcion`), que siguen redactándose en español como el resto del dominio.

**Validación de formularios duplicada a propósito: `Validators` de Angular en el frontend (en
español) replican las reglas del `Request` del backend; Jakarta Validation en el backend queda
con sus mensajes por defecto en el idioma de la JVM, sin traducir.**
El backend es el límite de seguridad autoritativo y nunca debe confiar en que el cliente validó
correctamente, así que sus anotaciones `@NotNull`/`@Size`/etc. (y sus mensajes) se mantienen
igual que en la decisión anterior. El frontend, por su parte, define sus propios `Validators` de
Angular (nativos o funciones custom) que replican esas mismas reglas únicamente para dar
feedback inmediato y en español al usuario mientras completa un formulario; sus mensajes se
escriben a mano en español en el componente o servicio de validación correspondiente, sin
depender de ningún mensaje que venga del backend. Ningún formulario existe todavía en este
change (`Sin features de negocio`): esta decisión queda documentada en `AGENTS.md` como
convención para que la siga el primer change que agregue un formulario real. Alternativa
descartada: no validar en el frontend y mostrar directamente los mensajes por defecto del
backend — se descarta porque su idioma depende de la JVM de cada máquina y dejaría al usuario
viendo mensajes en un idioma que no controlamos en el camino más
común (un error de validación durante el uso normal de un formulario).

## Risks / Trade-offs

- [Los pipes son puros: si el usuario cambia el idioma del navegador sin recargar la página, el
  formato no se actualiza en vivo] → Aceptado: la detección es automática al cargar la página,
  no reactiva; recargar aplica la región nueva.
- [Dividir milésimas entre 1000 con aritmética de punto flotante de JavaScript puede perder
  precisión para montos muy grandes] → Aceptado solo para visualización; los cálculos de negocio
  siempre ocurren en el backend sobre `long` milésimas, nunca en el frontend.
- [`FechaPipe`/`MontoPipe` reemplazan a los pipes nativos de Angular; cualquier código nuevo
  podría usar por error `date`/`number`/`currency` nativos] → Se documenta como regla explícita
  en `AGENTS.md`.
- [Fijar la zona horaria de la JVM a UTC es un efecto global del proceso] → Aceptado
  conscientemente: es la práctica estándar para apps que persisten en UTC, y evita
  inconsistencias si el proceso corre en servidores con zonas horarias de sistema distintas.
- [`spring.jpa.show-sql=true` en todos los entornos (sin perfiles) expondrá SQL en los logs de
  producción si el proyecto se despliega así tal cual] → Aceptado para esta etapa fundacional sin
  despliegue real todavía; revisar al introducir perfiles de Spring.
- [Los `Validators` de Angular del frontend replican a mano las reglas del `Request` del
  backend, sin ningún esquema compartido entre ambos; pueden desincronizarse si una reglas cambia
  en un lado y no en el otro] → Aceptado para esta etapa: no hay formularios todavía. Cada change
  futuro que agregue un formulario es responsable de mantener ambos lados sincronizados; el
  backend sigue siendo la fuente de verdad de la regla aunque el frontend la duplique para dar
  feedback inmediato.
- [Devolver una cadena vacía para un monto o fecha `null`/`undefined` en vez de lanzar un error
  puede ocultar silenciosamente un dato ausente que debería existir] → Aceptado: en una plantilla
  Angular es el caso esperado mientras los datos todavía se están cargando; si un campo de
  negocio nunca debería ser `null` (p. ej. una fecha de auditoría), eso se garantiza con
  validación en el backend, no en el pipe de presentación.
- [Agregar `/error` a los patrones permitidos sin autenticación de `SecurityConfig` amplía la
  superficie sin autenticación] → Aceptado: es el comportamiento estándar esperado en cualquier
  app Spring Boot (cualquier error, en cualquier ruta, protegida o no, debe poder llegar a
  `/error` sin quedar bloqueado por autenticación); `include-stacktrace=never` e
  `include-message=never` ya garantizan que no se expone nada sensible en esa ruta.
