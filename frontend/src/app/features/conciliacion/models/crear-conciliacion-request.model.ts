/** Cuerpo de `POST .../conciliacion` (`CrearConciliacionRequest` del backend). */
export interface CrearConciliacionRequest {
  /** Milésimas con signo. */
  saldoExtracto: number;
  /** `yyyy-MM-dd`. */
  fecha: string;
  crearAjuste: boolean;
  /** Solo cuando la categoría del ajuste aplica; `null` es "sin categoría". */
  categoriaId?: number | null;
}
