/** Resultado de `POST .../importacion` (`ImportacionResponse` del backend, `201`). */
export interface ImportacionResponse {
  /** Filas creadas. */
  importadas: number;
  /** Filas omitidas por duplicadas. */
  duplicadas: number;
  /** Filas INVÁLIDAS omitidas (solo con `omitirInvalidas`; siempre 0 sin él). */
  omitidas: number;
  /** Cuántas de las creadas quedaron sin categoría (hoy, todas). */
  sinCategoria: number;
}
