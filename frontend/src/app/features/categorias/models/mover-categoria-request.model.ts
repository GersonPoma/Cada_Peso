/**
 * Cuerpo de `POST /categorias/{id}/mover`: grupo destino y posición desde 0, contando las ocultas
 * (en el mismo grupo de 0 a n-1; en otro grupo de 0 a m, con m las categorías del destino).
 */
export interface MoverCategoriaRequest {
  grupoId: number;
  posicion: number;
}
