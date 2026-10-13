import { CategoriaResponse } from './categoria-response.model';
import { TipoGrupoCategoria } from './grupo-categoria-response.model';

/** Un grupo del árbol con sus categorías (`GrupoCategoriaConCategoriasResponse` del backend). */
export interface GrupoCategoriaConCategoriasResponse {
  id: number;
  nombre: string;
  orden: number;
  oculto: boolean;
  /** El backend lo envía siempre (aditivo). */
  tipo?: TipoGrupoCategoria;
  categorias: CategoriaResponse[];
}
