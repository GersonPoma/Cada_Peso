/** Cuerpo de `POST /api/v1/auth/registro` (`RegistroRequest` del backend). */
export interface RegistroRequest {
  email: string;
  contrasena: string;
  nombre: string;
  apellido: string;
  /** `LocalDate` del backend, con formato `yyyy-MM-dd`. */
  fechaNacimiento: string;
  telefono?: string;
  /** Código ISO 4217; si se omite, el backend usa `BOB`. */
  monedaPredeterminada?: string;
}
