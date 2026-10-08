/** Beneficiario sugerido (`BeneficiarioResponse` del backend), tal como lo lee esta feature. */
export interface BeneficiarioSugerido {
  id: number;
  nombre: string;
  /** Categoría que se usó la última vez con este beneficiario; `null` si no tiene. */
  categoriaPredeterminadaId: number | null;
}
