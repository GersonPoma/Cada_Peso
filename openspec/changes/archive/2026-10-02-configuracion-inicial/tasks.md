# Tasks

## 1. Identidad del frontend

- [x] 1.1 Fijar `<title>Cada Peso</title>` y `lang="es"` en `frontend/src/index.html`; verificar
      con `ng build` que compila sin errores y revisando el HTML generado que contiene ambos
      valores.
- [x] 1.2 Crear `frontend/public/favicon.svg` (16×16, círculo de fondo `#2E7D32` y una "C"
      blanca centrada) y referenciarlo desde `index.html` (`<link rel="icon" type="image/svg+xml"
      href="favicon.svg">`); eliminar `frontend/public/favicon.ico`; verificar que `ng build`
      termina sin errores, que `favicon.ico` ya no existe bajo `public/`, y abriendo el SVG en un
      navegador que la "C" se lee con claridad a un tamaño renderizado de 16×16.
- [x] 1.3 Reemplazar el contenido de ejemplo de `frontend/src/app/app.html` (y los estilos de
      ejemplo asociados en `app.scss`) dejando solo `<router-outlet />`; actualizar
      `app.spec.ts` para que ya no espere el `<h1>` "Hello, frontend" del template de ejemplo;
      verificar que `ng build` y `ng test` pasan sin errores tras el cambio.

## 2. Formato regional: detección de región

- [x] 2.1 Crear `frontend/src/app/shared/formato/region-usuario.ts` con una función
      `regionUsuario()` que lea `navigator.language`, valide el identificador con
      `Intl.NumberFormat.supportedLocalesOf(...)` y devuelva `'en-US'` si no hay valor o no es
      válido; verificar con pruebas unitarias (Vitest) que devuelve la región del navegador
      cuando es válida (ej. `es-CL`) y `'en-US'` cuando `navigator.language` es `undefined` o un
      valor inválido.

## 3. Formato regional: pipe de monto

- [x] 3.1 Crear `frontend/src/app/shared/formato/monto.pipe.ts` (`MontoPipe`, standalone, pipe
      `monto`) que reciba un monto en milésimas y un código de moneda ISO 4217, lo divida entre
      1000 y lo formatee con `Intl.NumberFormat(regionUsuario(), { style: 'currency', currency
      })`; si el monto recibido es `null` o `undefined`, devolver una cadena vacía sin lanzar
      ningún error. Verificar con pruebas unitarias que, por ejemplo, 1500000 milésimas en `USD`
      se formatea como el equivalente a 1500 unidades de esa moneda, y que un monto `null` o
      `undefined` devuelve una cadena vacía; en las aserciones de formato, normalizar los
      espacios no separables que `Intl` puede insertar (`\u00A0`, `\u202F`) a espacios regulares
      antes de comparar, o comparar con `formatToParts()` en vez del string final, para que la
      prueba no dependa de qué carácter de espacio use el motor `Intl` del entorno de ejecución.

## 4. Formato regional: pipe de fecha

- [x] 4.1 Crear `frontend/src/app/shared/formato/fecha.pipe.ts` (`FechaPipe`, standalone, pipe
      `fecha`) que, si el valor recibido es `null` o `undefined`, devuelva una cadena vacía sin
      lanzar ningún error; en otro caso, distinga por la forma del string de entrada: un instante
      ISO-8601 con sufijo `Z` (`Instant`) se parsea con `new Date(valor)` y se formatea con
      `Intl.DateTimeFormat(regionUsuario(), opciones)` sin fijar `timeZone` (se convierte a la
      zona horaria local del navegador); una fecha `yyyy-MM-dd` (`LocalDate`) se parsea
      separando año/mes/día, se construye con `Date.UTC(anio, mes - 1, dia)` y se formatea con
      `timeZone: 'UTC'` fijo (sin conversión de zona horaria); un string no nulo que no coincide
      con ninguno de los dos formatos lanza un error. Verificar con pruebas unitarias que cubran
      los escenarios Dado/Cuando/Entonces de `specs/formato-regional/spec.md`, incluyendo que
      `null`/`undefined` devuelve una cadena vacía; en las aserciones de formato, normalizar los
      espacios no separables que `Intl` puede insertar (`\u00A0`, `\u202F`) a espacios regulares
      antes de comparar, o comparar con `formatToParts()` en vez del string final.
- [x] 4.2 Fijar la zona horaria del entorno de test (ej. `America/New_York` o
      `America/Santiago`, con desfase negativo respecto a UTC) con la variable de entorno `TZ`
      en la configuración o script de Vitest (ej. `vitest.config.ts` → `test.env`, o un script
      npm con `cross-env TZ=...`) antes de que el proceso de test arranque — no asignando
      `process.env.TZ` en tiempo de ejecución dentro del test. Prueba de regresión específica
      del bug que motiva este change: formatear la fecha de negocio `'2026-10-01'` con
      `FechaPipe` y verificar que el resultado muestra el día 1 de octubre, nunca el 30 de
      septiembre; verificar también que un instante como `'2026-10-01T23:30:00Z'` sí se muestra
      convertido a la hora local de esa zona (no en UTC). Confirmar, quitando temporalmente la
      corrección de `LocalDate` (`Date.UTC` + `timeZone: 'UTC'`), que la prueba de la fecha de
      negocio efectivamente falla —para comprobar que la prueba de regresión detecta el bug que
      dice prevenir— y luego restaurar la corrección.

## 5. Backend: identidad y configuración operativa

- [x] 5.1 Fijar `spring.application.name=cada-peso` en `application.properties`; verificar en el
      log de arranque (`.\mvnw.cmd spring-boot:run`) que el nombre de la aplicación aparece como
      `cada-peso`.
- [x] 5.2 Fijar `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` como primera instrucción de
      `BackendApplication.main()`, antes de `SpringApplication.run(...)`; agregar
      `spring.jpa.properties.hibernate.jdbc.time_zone=UTC` a `application.properties`;
      configurar `maven-surefire-plugin` en `pom.xml` con `<argLine>-Duser.timezone=UTC</argLine>`
      para que los tests (que arrancan el contexto de Spring directamente y no ejecutan
      `main()`) corran en la misma zona horaria UTC que la aplicación; verificar con una prueba
      (puede ser una prueba de contexto simple) que `TimeZone.getDefault().getID()` es `"UTC"`
      tanto arrancando la aplicación normalmente como al ejecutar `.\mvnw.cmd test`.
- [x] 5.3 Agregar `spring.web.error.include-stacktrace=never` e `include-message=never` a
      `application.properties`; agregar `/error` a los patrones permitidos sin autenticación de
      `SecurityConfig` (`comun/seguridad`), para que el forward interno del contenedor a `/error`
      no se bloquee con 401; verificar con
      `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)` y un cliente
      HTTP real (ej. `TestRestTemplate`) —no `MockMvc`, que no ejecuta el forward real del
      contenedor a `/error`— que una petición a una ruta pública inexistente (ej. una ruta
      inexistente bajo `/api/v1/auth/**`, ya permitida sin autenticación) devuelve una respuesta
      sin stack trace ni mensaje de excepción interno en el cuerpo.
- [x] 5.4 Agregar `spring.jpa.show-sql=true` y
      `spring.jpa.properties.hibernate.format_sql=true` a `application.properties`; verificar
      arrancando la aplicación (o ejecutando `BackendApplicationTests`) que el log muestra las
      sentencias SQL de Hibernate formateadas en múltiples líneas.

## 6. Documentación de convenciones

- [x] 6.1 Actualizar `AGENTS.md` con: el nombre del producto ("Cada Peso"); la regla de formato
      regional (región detectada automáticamente con fallback `en-US`, uso obligatorio de los
      pipes propios `monto`/`fecha` de `shared/formato/` en vez de los pipes nativos
      `date`/`number`/`currency` de Angular, sin `registerLocaleData` ni `LOCALE_ID` fijo); que
      las fechas de negocio (transacciones, meses del presupuesto) se modelan como `LocalDate`
      mientras que los momentos exactos de auditoría se modelan como `Instant`; y la estrategia
      de validación de formularios: los formularios del frontend validan con `Validators`
      propios de Angular y muestran sus propios mensajes en español al usuario, replicando las
      reglas de validación del `Request` del backend; los mensajes de validación por defecto del
      backend (Jakarta Validation) usan el idioma de la JVM de cada máquina, sin configuración, y
      son solo un respaldo porque el frontend valida y muestra sus propios mensajes en español;
      las excepciones de negocio propias (`CodigoError`) mantienen sus mensajes en español;
      verificar que el archivo existe y cubre cada punto.

## 7. Verificación integral

- [x] 7.1 Con PostgreSQL corriendo, levantar el backend y ejecutar `ng build` y `ng test` en el
      frontend; verificar que todo pasa sin errores, que la pestaña del navegador muestra
      "Cada Peso" con el favicon SVG nuevo al servir el frontend (`ng serve`), y que
      `/actuator/health` sigue respondiendo `UP`.
