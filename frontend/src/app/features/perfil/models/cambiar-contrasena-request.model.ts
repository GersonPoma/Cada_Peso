/**
 * Cuerpo de `POST /api/v1/usuarios/yo/contrasena`. Las contraseñas viajan tal cual, sin recortar.
 * La confirmación de la nueva se valida en la pantalla y no forma parte de la petición.
 */
export interface CambiarContrasenaRequest {
  contrasenaActual: string;
  contrasenaNueva: string;
}
