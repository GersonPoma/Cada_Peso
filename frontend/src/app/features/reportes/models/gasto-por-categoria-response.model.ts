/** Gasto neto de una categoría en el rango (milésimas) y su porcentaje del total. */
export interface CategoriaGastoResponse {
  categoriaId: number;
  nombre: string;
  oculta: boolean;
  total: number;
  /** Centésimas de punto porcentual (`5283` = 52,83 %). */
  porcentaje: number;
}

/** Gasto de un grupo: su total es la suma de sus categorías. */
export interface GrupoGastoResponse {
  grupoId: number;
  nombre: string;
  total: number;
  porcentaje: number;
  categorias: CategoriaGastoResponse[];
}

/** El gasto sin categoría: siempre presente, aunque valga cero. */
export interface SinCategoriaGastoResponse {
  total: number;
  porcentaje: number;
}

/** `GET .../reportes/gasto-por-categoria`: grupos en el orden del árbol. */
export interface GastoPorCategoriaResponse {
  desde: string;
  hasta: string;
  total: number;
  grupos: GrupoGastoResponse[];
  sinCategoria: SinCategoriaGastoResponse;
}
