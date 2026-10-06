/** Cuerpo de `POST /api/v1/auth/login` (`LoginRequest` del backend). */
export interface LoginRequest {
  email: string;
  contrasena: string;
}
