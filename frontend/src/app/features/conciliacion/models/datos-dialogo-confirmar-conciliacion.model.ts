/** Resumen que muestra la confirmación antes de reconciliar. Montos en milésimas. */
export interface DatosDialogoConfirmarConciliacion {
  /** Nombre de la cuenta. */
  cuenta: string;
  /** Fecha del extracto, `yyyy-MM-dd`. */
  fecha: string;
  saldoExtracto: number;
  /** Ajuste que se pide crear, o `null` si se reconcilia sin ajuste. */
  ajuste: number | null;
  /** Código ISO 4217 de la moneda del presupuesto. */
  moneda: string;
}
