# Tasks

Rutas desde `frontend/src/app/`. Comandos desde `frontend/` (`npm test`, `npx ng build`). Antes de
empezar: `git pull` y revisar que ningún archivo de la tabla "se tocan" del design haya cambiado.
Si hace falta tocar `AuthService` o `SesionService`, parar y avisar.

## 1. Reglas y campo compartidos (refactor sin cambio de comportamiento)

- [x] 1.1 Antes de mover nada, correr `npm test` y anotar que `registro.page.spec.ts`,
  `login.page.spec.ts` y `campo-contrasena.component.spec.ts` pasan (línea base).
- [x] 1.2 Mover `features/auth/components/campo-contrasena.component.{ts,html,scss,spec.ts}` a
  `shared/campo-contrasena/` con `git mv`; actualizar la importación en
  `features/auth/pages/login.page.ts` y `registro.page.ts`. Verificar con `npm test` que los tres
  specs de la línea base pasan **sin cambiar ninguna aserción**.
- [x] 1.3 Agregar la entrada opcional `mensajesExtra` (mezclada con los mensajes propios, tras
  ellos) a `shared/campo-contrasena/campo-contrasena.component.ts`; test en
  `campo-contrasena.component.spec.ts` (mismo directorio): una clave extra muestra su texto y sin
  la entrada el comportamiento es el actual.
- [x] 1.4 Crear `shared/validacion/contrasena-nueva.validator.ts` (test
  `contrasena-nueva.validator.spec.ts`): validadores `sobreTextoRecortado(required)`,
  `minLength(8)` y `maxBytesUtf8(72)`. Verificar con el test: vacía, solo espacios, `1234567`,
  72 ASCII, 36 `ñ` (válida), 37 `ñ` y `ñ` + 71 `a` (73 bytes) inválidas, espacios conservados.
- [x] 1.5 Crear `shared/validacion/nombre-persona.validator.ts` (test
  `nombre-persona.validator.spec.ts`): validadores `required`, `minLength(2)`, `maxLength(100)`
  sobre texto recortado más el mapa de mensajes `El nombre es obligatorio` / `...al menos 2
  caracteres` / `...no puede superar 100 caracteres`. Verificar: vacío, `"  A  "`, 2, 100 y 101
  caracteres.
- [x] 1.6 Cambiar `features/auth/pages/registro.page.ts` para usar las reglas de 1.4 y 1.5 en
  lugar de sus copias (nombre y contraseña; el apellido conserva las suyas). Verificar que
  `registro.page.spec.ts` pasa sin tocar aserciones y que `npx ng build` compila.
- [x] 1.7 Comparar los specs de registro y login antes y después del refactor: correr
  `registro.page.spec.ts` y `login.page.spec.ts` (`npx ng test --include` de cada uno) antes de
  1.2 y de nuevo tras 1.6, y verificar que los resultados son iguales (mismo número de tests y
  todos pasan) **sin modificar ninguna aserción**. Si alguna aserción tiene que cambiar para que
  pasen, detenerse y avisar antes de seguir.

## 2. Nombre de la moneda

- [x] 2.1 Crear `shared/formato/nombre-moneda.ts` (test `nombre-moneda.spec.ts`): función pura
  `nombreDeMoneda(codigo)` con `Intl.DisplayNames`, primera letra en mayúscula, `CODIGO · Nombre`,
  y solo el código si no hay nombre, el código es inválido o `DisplayNames` no existe. Verificar
  con el test (regiones fijadas como en `region-usuario.spec.ts`): `es-BO`/`USD` →
  `USD · Dólar estadounidense`, `en-US`/`USD` → `USD · US Dollar`, código desconocido y valor
  inválido (`'US'`, `''`) → solo el código sin lanzar.

## 3. Servicio, modelos y validadores del perfil

- [x] 3.1 Crear los modelos `features/perfil/models/perfil-response.model.ts`,
  `cambiar-nombre-request.model.ts` y `cambiar-contrasena-request.model.ts` (interfaces; sin test
  propio, los ejercita 3.2).
- [x] 3.2 Crear `features/perfil/services/perfil.service.ts` (test `perfil.service.spec.ts`):
  `obtener()` → `GET /api/v1/usuarios/yo`; `cambiarNombre()` → `PUT` con `{ nombre }`;
  `cambiarContrasena()` → `POST /api/v1/usuarios/yo/contrasena` con solo
  `{ contrasenaActual, contrasenaNueva }` y sin leer cuerpo del `204`. Verificar con el test:
  método, URL, cuerpo exacto (sin confirmación) y que no se escribe nada en `console`.
- [x] 3.3 Crear `features/perfil/services/cambio-contrasena.validator.ts` (test
  `cambio-contrasena.validator.spec.ts`): `coincideCon(otroControl)` y `distintaDe(otroControl)`
  que se revalidan al cambiar el otro control. Verificar: confirmación distinta/igual, nueva
  igual/distinta de la actual (exacta, `Secreta123` ≠ `secreta123`), revalidación cruzada.

## 4. Componentes de la pantalla

- [x] 4.1 Crear `features/perfil/components/datos-perfil.component.{ts,html,scss}` (test
  `datos-perfil.component.spec.ts`): `<dl>` de solo lectura con correo, apellido, fecha (pipe
  `fecha`), teléfono (`No registrado` si es `null`), moneda predeterminada (`nombreDeMoneda`) y
  presupuesto actual `Nombre (MONEDA)` desde `PresupuestoActivoService` (se omite si no hay).
  Verificar con el test los escenarios `Datos mostrados` y `Sin controles de edición`.
- [x] 4.2 Crear `features/perfil/components/formulario-nombre.component.{ts,html,scss}` (test
  `formulario-nombre.component.spec.ts`): reglas de 1.5, contador `n/100` del largo recortado,
  botón `Guardar nombre` deshabilitado si inválido/igual/enviando, `PUT` recortado, aviso
  `Nombre actualizado`, salida con el perfil actualizado, `DATOS_INVALIDOS` en el campo, genérico
  en otros errores y silencio ante 401. Verificar con el test cada escenario de `Cambio de nombre`
  y `Errores del cambio de nombre` (incluido doble clic).
- [x] 4.3 Crear `features/perfil/components/formulario-contrasena.component.{ts,html,scss}` (test
  `formulario-contrasena.component.spec.ts`): tres `app-campo-contrasena` con `autocomplete`
  correcto y `mensajesExtra`, campo de usuario oculto con el correo, validadores de 1.4 y 3.3,
  botón `Cambiar contraseña` deshabilitado si inválido/enviando, cuerpo exacto sin recortar ni
  confirmación. Verificar con el test: campos y autocompletado, obligatorios, corta, multibyte
  (36/37 `ñ`), confirmación distinta y revalidación, nueva igual a la actual sin petición.
- [x] 4.4 En `formulario-contrasena.component.ts`, resultado del envío: `204` →
  `SesionService.cerrar()`, `Router.navigateByUrl('/login')`, formulario vaciado y, solo si la
  navegación resuelve en `true`, el aviso (decisión 5 del design); `422` →
  `La contraseña actual es incorrecta` en la actual (vaciada, con foco), nueva y confirmación
  conservadas; `400` por campo con foco en el primer error; clave sin campo/otro/red → genérico;
  401 → silencio. Verificar con el test (interceptor real `sesionInterceptor` y espía de
  `console`): `204` borra la sesión, navega a `/login`, avisa solo después de navegar (si la
  navegación devuelve `false` no hay aviso) y no hay otra petición; `422` no borra la sesión ni
  redirige;
  `401` sí; el error se quita al editar; `detail` nunca se muestra; ningún `console.*`; el
  almacenamiento local no contiene las contraseñas.

## 5. Pantalla, ruta y acceso

- [x] 5.1 Crear `features/perfil/pages/perfil.page.{ts,html,scss}` (test `perfil.page.spec.ts`):
  título `Mi perfil`, carga con indicador, error `No pudimos cargar tus datos.` con
  `role="alert"` y `Reintentar`, 401 silencioso, sin botón `Cerrar sesión`, una columna apilada,
  y al guardar el nombre actualiza el que muestra. Verificar con el test los escenarios de
  `Datos del perfil`, `Accesibilidad y pantallas estrechas` y `Cerrar sesión sigue en la
  cabecera`.
- [x] 5.2 Agregar a `app.routes.ts` la ruta hija `perfil` (`loadComponent`, sin
  `data: seccion(...)`) **al final** de `children`, después de `beneficiarios`. Verificar con un
  test en `perfil.page.spec.ts` (o `app.routes.spec.ts` si ya existe) que `/presupuestos/3/perfil`
  muestra la pantalla y que `secciones` del layout sigue con las seis entradas; y con `git diff`
  que el cambio son solo esas líneas.
- [x] 5.3 Agregar el botón `Mi perfil` (`a mat-icon-button routerLink="perfil"`, ícono
  `account_circle`, `aria-label="Mi perfil"`) en `features/presupuestos/pages/
  layout-presupuesto.page.html`, entre el engranaje y `Cerrar sesión`, sin cambiar el selector
  ni el `.ts`. Verificar con un test en `layout-presupuesto.page.spec.ts`: el botón existe con su
  etiqueta, apunta a `perfil` y el menú lateral conserva sus enlaces sin `Perfil`.
- [x] 5.4 Agregar a `features/inicio/pages/inicio.page.spec.ts` un test: dos aperturas de
  `InicioPage` consultan `GET /usuarios/yo` cada una y la segunda muestra el nombre nuevo.
  Verificar con `npm test`.

## 6. Documentación y comprobaciones finales

- [x] 6.1 Actualizar `AGENTS.md`: `shared/campo-contrasena/` y las dos reglas en el árbol y la
  lista de `shared/`, `perfil/` en el árbol de `features/`, y una sección "Perfil (frontend)" con
  la ruta, el punto de entrada, el cierre local sin invalidar el token (24 h), que un 422 no cierra
  la sesión y que no hay límite de intentos. Verificar releyendo contra el spec.
- [x] 6.2 Comprobar manualmente en el navegador (con el backend local de `8135e83`): cambio de
  nombre y `Hola, ...` en Inicio; contraseña incorrecta (422, sigue en la pantalla); contraseña
  correcta (aviso y `/login`); iniciar sesión con la nueva; 360 px sin desborde de la cabecera
  (si desborda, ajuste mínimo en `layout-presupuesto.page.scss` y avisar); recorrido con teclado
  y lector de pantalla del foco y los avisos.
- [x] 6.3 Ejecutar `npm test` y `npx ng build` y verificar que pasan completos; que ningún
  archivo nuevo supera 100 columnas; y que `git status` solo muestra los archivos de las tablas
  del design. Correr `openspec validate perfil-frontend --strict`.
