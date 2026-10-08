/** Una categoría en un mes (`CategoriaMesResponse` del backend). Montos en milésimas. */
export interface CategoriaMesResponse {
  categoriaId: number;
  nombre: string;
  oculta: boolean;
  asignado: number;
  actividad: number;
  disponible: number;
  sobregastada: boolean;
  /** Es la categoría de pago de una tarjeta de crédito. */
  esPagoTarjeta: boolean;
  /** La tarjeta de una categoría de pago; `null` en las demás. */
  cuentaId: number | null;
}
