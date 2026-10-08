/** Cuerpo de `PUT .../transferencias/{id}` (`ActualizarTransferenciaRequest`); sin cuentas. */
export interface ActualizarTransferenciaRequest {
  /** `LocalDate`, `yyyy-MM-dd`. */
  fecha: string;
  /** Milésimas, siempre positivo. */
  monto: number;
  /** Solo según la regla de categoría; sin la clave si la categoría no aplica. */
  categoriaId?: number | null;
  memo: string | null;
}
