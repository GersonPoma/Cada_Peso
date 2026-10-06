/** Respuesta de `GET /api/v1/usuarios/yo` (`UsuarioActualResponse` del backend). */
export interface UsuarioActualResponse {
  email: string;
  rol: string;
  nombre: string;
  apellido: string;
  /** `LocalDate` del backend, con formato `yyyy-MM-dd`. */
  fechaNacimiento: string;
  telefono: string | null;
  monedaPredeterminada: string;
}
