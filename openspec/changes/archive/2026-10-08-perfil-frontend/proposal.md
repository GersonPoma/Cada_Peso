# Proposal

## Why

El backend ya permite que la persona autenticada cambie su nombre y su contraseña
(`perfil-usuario`), pero la app no ofrece ninguna pantalla para hacerlo: hoy solo puede ver
`Hola, {nombre}` en Inicio. Sin esta pantalla, cambiar una contraseña expuesta o corregir el
nombre exige llamar a la API a mano.

## What Changes

- **Pantalla `Perfil`** en `/presupuestos/:presupuestoId/perfil`, hija del layout del
  presupuesto (hereda cabecera y sesión). Muestra nombre (editable), correo y, en solo lectura,
  apellido, fecha de nacimiento, teléfono, moneda predeterminada (código y nombre, p. ej.
  `USD · Dólar estadounidense`) y el presupuesto actual con su moneda. Con estados de carga y de
  error con `Reintentar`.
- **Formulario de nombre**: recorta antes de enviar, contador `n/100`, botón deshabilitado si es
  inválido, igual al actual o enviando; `PUT /api/v1/usuarios/yo`; aviso `Nombre actualizado`.
  `Inicio` muestra el nombre nuevo al abrirse (consulta `GET /yo` cada vez), sin recargar.
- **Formulario de contraseña**: actual, nueva y confirmación (la confirmación no se envía).
  La nueva usa la misma regla del registro (una única definición compartida). Al recibir `204`
  cierra la sesión local con `SesionService.cerrar()`, lleva a `/login` y avisa
  `Contraseña cambiada, inicia sesión de nuevo`.
- **Errores por `codigo`**: `DATOS_INVALIDOS` con `errores` en su campo;
  `REGLA_NEGOCIO_VIOLADA` como `La contraseña actual es incorrecta` junto a ese campo, sin cerrar
  la sesión; cualquier otro error con el aviso genérico.
- **Punto de entrada**: un botón con ícono `account_circle` y etiqueta accesible `Mi perfil` en
  la cabecera del layout, junto al engranaje. No se toca el menú lateral ni el selector de
  presupuesto.
- **Refactor para no duplicar**: `app-campo-contrasena` pasa de `features/auth/components/` a
  `shared/campo-contrasena/` (una feature no puede importar de otra), y las reglas de contraseña
  nueva y de nombre del registro pasan a `shared/validacion/`. Login y registro siguen igual.
- Documentado: cambiar la contraseña **no invalida** el token anterior en el servidor (JWT
  sin estado, 24 h); cerrar sesión solo borra la sesión de este navegador.

Fuera de alcance: cambiar el correo, eliminar la cuenta, recuperar contraseña, invalidar
tokens, límite de intentos, foto de perfil y cambiar la moneda o el idioma. La moneda se
**muestra** pero no se cambia: el backend no tiene endpoint (`PUT /presupuestos/{id}` ignora la
moneda a propósito). El idioma y el formato no se eligen: se detectan del navegador.
`Cerrar sesión` no se repite en la pantalla de perfil (la cabecera, visible en la misma pantalla,
ya lo ofrece).

**Dependencia**: usa los endpoints de `perfil-usuario`, que hoy existen solo en un commit local
del backend (`8135e83`, no publicado en `origin/main`). Hay que publicar el backend antes de
integrar este change.

## Capabilities

### New Capabilities
- `perfil-frontend`: pantalla de perfil de la persona autenticada con sus datos, el cambio de
  nombre y el cambio de contraseña con cierre de sesión local al terminar.

### Modified Capabilities
- `presupuestos-frontend`: el requisito `Estructura de la pantalla del presupuesto` enumera lo
  que tiene la cabecera; gana el acceso `Mi perfil`.

## Impact

- **Frontend nuevo**: `features/perfil/` (página, dos componentes de formulario, uno de datos,
  servicio y modelos), `shared/campo-contrasena/` (movido), `shared/validacion/` (dos reglas
  extraídas) y `shared/formato/nombre-moneda.ts`.
- **Archivos existentes que se tocan** (detalle en design): `app.routes.ts` (una ruta hija al
  final), `layout-presupuesto.page.html` (un botón) y su spec, `registro.page.ts` y
  `login.page.ts` (rutas de importación y uso de las reglas compartidas), `inicio.page.spec.ts`
  (un test) y `AGENTS.md`.
- **No se tocan**: `AuthService`, `SesionService`, el interceptor, los guards, el menú lateral
  ni el selector de presupuesto.
- **Backend**: ninguno. Sin dependencias nuevas.
