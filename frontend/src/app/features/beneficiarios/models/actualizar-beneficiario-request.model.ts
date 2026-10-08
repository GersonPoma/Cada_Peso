/** Cuerpo de `PUT .../beneficiarios/{id}` (`ActualizarBeneficiarioRequest` del backend). */
export interface ActualizarBeneficiarioRequest {
  nombre: string;
  /** `null` quita la categoría predeterminada. */
  categoriaId: number | null;
}
