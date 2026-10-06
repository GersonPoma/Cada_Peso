/** Respuesta de `POST /api/v1/auth/registro` y `POST /api/v1/auth/login` (`TokenResponse`). */
export interface TokenResponse {
  token: string;
  /** Siempre `"Bearer"`. */
  tipo: string;
  /** `Instant` ISO-8601 en UTC en que vence el token (24 horas después de emitirlo). */
  expiraEn: string;
}
