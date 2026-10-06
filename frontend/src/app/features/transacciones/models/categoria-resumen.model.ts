/** Lo que esta feature necesita de una categoría (lectura de `GET /categorias`). */
export interface CategoriaResumen {
  id: number;
  nombre: string;
  oculta: boolean;
}
