/** Cuerpo de `POST .../transferencias` (`CrearTransferenciaRequest` del backend). */
export interface CrearTransferenciaRequest {
  cuentaOrigenId: number;
  cuentaDestinoId: number;
  /** `LocalDate`, `yyyy-MM-dd`. */
  fecha: string;
  /** Milésimas, siempre positivo: el signo lo pone cada pata. */
  monto: number;
  /** Solo según la regla de categoría; sin la clave si la categoría no aplica. */
  categoriaId?: number | null;
  memo: string | null;
}
