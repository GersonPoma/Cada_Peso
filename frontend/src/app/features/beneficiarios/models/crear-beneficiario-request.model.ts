/** Cuerpo de `POST .../beneficiarios` (`CrearBeneficiarioRequest` del backend). */
export interface CrearBeneficiarioRequest {
  nombre: string;
  categoriaId: number | null;
}
