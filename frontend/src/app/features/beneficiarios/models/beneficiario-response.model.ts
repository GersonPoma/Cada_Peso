/** Beneficiario (`BeneficiarioResponse` del backend). */
export interface BeneficiarioResponse {
  id: number;
  nombre: string;
  /** Categoría que se rellena al elegirlo en una transacción nueva; `null` si no tiene. */
  categoriaPredeterminadaId: number | null;
}
