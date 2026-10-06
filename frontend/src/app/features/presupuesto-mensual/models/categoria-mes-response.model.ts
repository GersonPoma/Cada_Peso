/** Una categoría en un mes (`CategoriaMesResponse` del backend). Montos en milésimas. */
export interface CategoriaMesResponse {
  categoriaId: number;
  nombre: string;
  oculta: boolean;
  asignado: number;
  actividad: number;
  disponible: number;
  sobregastada: boolean;
}
