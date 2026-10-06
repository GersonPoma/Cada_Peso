/** Cuerpo de `PUT /meses/{mes}/categorias/{categoriaId}`: fija (no suma) el asignado del mes. */
export interface AsignarRequest {
  /** Milésimas; puede ser 0 o negativo. */
  asignado: number;
}
