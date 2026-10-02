# Proposal

## Why

El proyecto hoy se identifica solo como "backend"/"frontend" genéricos (título de pestaña
"Frontend", favicon por defecto de Angular, `spring.application.name=backend`) y no tiene
nombre de producto. Tampoco existe ninguna estrategia de formato regional: si se usaran los
pipes `date`/`number`/`currency` de Angular sin `registerLocaleData`, Angular lanza un error en
tiempo de ejecución para cualquier locale que no sea `en-US`, y aun resolviendo eso, una fecha
sin hora (`LocalDate`, ej. meses del presupuesto o fecha de una transacción) formateada con una
librería que aplica la zona horaria del navegador puede mostrarse con el día anterior (un
`LocalDate` de "2026-10-01" interpretado como medianoche UTC se ve como "30 de septiembre" en
cualquier zona horaria con offset negativo). Antes de construir cualquier feature de negocio que
muestre montos o fechas (transacciones, meses del presupuesto), se necesita nombrar el producto,
dejar la configuración operativa básica del backend (nombre de aplicación, zona horaria, logging,
mensajes de error) y resolver el formato regional de forma correcta y automática.

## What Changes

- Nombrar el producto **"Cada Peso"**: título de la pestaña del navegador en `index.html`,
  `spring.application.name=cada-peso` en el backend, y documentación en `AGENTS.md`.
- Reemplazar el favicon por defecto de Angular (`favicon.ico`) por un `favicon.svg` propio
  (círculo verde `#2E7D32` con una "C" blanca centrada, legible a 16×16), referenciado desde
  `index.html`; eliminar `favicon.ico`.
- Limpiar la plantilla de ejemplo que genera `ng new` en `app.html`, dejando solo
  `<router-outlet />`.
- Fijar `lang="es"` en `index.html`, consistente con que la interfaz (y todos los mensajes al
  usuario) están en español.
- **Nueva capacidad `formato-regional`**: detectar automáticamente la región del usuario con
  `navigator.language` (con `en-US` como fallback) y formatear montos y fechas con pipes propios
  en `shared/` basados en `Intl.NumberFormat`/`Intl.DateTimeFormat` (sin `registerLocaleData` ni
  `LOCALE_ID` fijo), en vez de los pipes `date`/`number`/`currency` de Angular. El pipe de fecha
  distingue explícitamente instantes con hora (`Instant` ISO con `Z`, que sí se convierten a la
  zona horaria del usuario) de fechas sin hora (`LocalDate` `yyyy-MM-dd`, que se formatean sin
  conversión de zona horaria) para que una fecha de negocio nunca se muestre corrida un día.
- Configuración operativa del backend: zona horaria UTC fija en la JVM y
  `hibernate.jdbc.time_zone=UTC` (consistente con que las fechas con hora se persisten y viajan
  en UTC, y el frontend es responsable de convertir a la zona del usuario solo al mostrarlas);
  `spring.web.error.include-stacktrace=never` e `include-message=never` (refuerza, para errores no
  capturados por `ManejadorGlobalExcepciones`, la misma garantía de no exponer detalles internos
  que ya aplica a los errores manejados); `spring.jpa.show-sql=true` con `format_sql` para
  depuración en desarrollo. Los mensajes de validación por defecto de Jakarta Validation no se
  configuran: usan el idioma de la JVM de cada máquina y son solo un respaldo (los mensajes de
  negocio propios seguirán en español vía `ReglaNegocioException`/`CodigoError`).
- Documentar en `AGENTS.md`: el nombre del producto, la regla de formato regional (pipes propios,
  sin `registerLocaleData`), que las fechas de negocio (transacciones, meses del presupuesto)
  se modelan como `LocalDate` mientras que los momentos exactos de auditoría se modelan como
  `Instant`, y la estrategia de validación de formularios: los formularios del frontend validan
  con `Validators` propios de Angular y muestran sus propios mensajes en español al usuario,
  replicando las reglas de validación del `Request` del backend; los mensajes de validación por
  defecto del backend (Jakarta Validation) usan el idioma de la JVM de cada máquina, sin
  configuración, y son solo un respaldo porque el frontend valida y muestra sus propios mensajes
  en español; las excepciones de negocio propias (`CodigoError`) mantienen sus mensajes en
  español.

Sin features de negocio: no se crean entidades, endpoints ni pantallas de negocio en este
change.

## Capabilities

### New Capabilities

- `formato-regional`: comportamiento de formato de montos (milésimas + código de moneda) y
  fechas (`Instant` vs `LocalDate`) en el frontend, detectando automáticamente la región del
  usuario sin configuración manual y sin desfasar fechas sin hora por zona horaria.

### Modified Capabilities

Ninguna (no existen capacidades previas: el change anterior, `fundacion-proyecto`, declaró
`skip_specs: true`).

## Impact

- **Frontend**: `src/index.html` (título, `lang`, favicon), `public/favicon.svg` (nuevo),
  `public/favicon.ico` (eliminado), `src/app/app.html` (limpieza de la plantilla de ejemplo),
  `src/app/shared/` (pipes `monto` y `fecha` nuevos).
- **Backend**: `application.properties` (`spring.application.name`, zona horaria/`time_zone`,
  propiedades de `spring.web.error`, `spring.jpa.show-sql`/`format_sql`), JVM (zona horaria por
  defecto), `comun/seguridad/SecurityConfig` (`/error` permitido sin autenticación) y `pom.xml`
  (`maven-surefire-plugin` con `-Duser.timezone=UTC`).
- **Documentación**: `AGENTS.md` (nombre del producto, regla de formato regional,
  `LocalDate` vs `Instant`).
- **Dependencias externas**: ninguna (usa `Intl` del navegador, ya disponible; Jakarta
  Validation ya está en el `pom.xml` desde `fundacion-proyecto`).
