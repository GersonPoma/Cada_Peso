/** Respuesta de `GET` y `PUT /api/v1/usuarios/yo` (`UsuarioActualResponse` del backend). */
export interface PerfilResponse {
  email: string;
  rol: string;
  nombre: string;
  apellido: string;
  /** `LocalDate` del backend, con formato `yyyy-MM-dd`. */
  fechaNacimiento: string;
  telefono: string | null;
  /** Código ISO 4217 que se propone al crear un presupuesto nuevo; no se puede cambiar. */
  monedaPredeterminada: string;
}
