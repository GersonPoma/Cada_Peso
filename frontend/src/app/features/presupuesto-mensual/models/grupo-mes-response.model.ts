import { CategoriaMesResponse } from './categoria-mes-response.model';

/** Un grupo con sus categorías del mes (`GrupoMesResponse` del backend). */
export interface GrupoMesResponse {
  id: number;
  nombre: string;
  orden: number;
  oculto: boolean;
  categorias: CategoriaMesResponse[];
}
