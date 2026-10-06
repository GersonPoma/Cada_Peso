/** Cuerpo de `PUT /categorias/{id}`: solo nombre y nota. */
export interface ActualizarCategoriaRequest {
  nombre: string;
  nota: string | null;
}
