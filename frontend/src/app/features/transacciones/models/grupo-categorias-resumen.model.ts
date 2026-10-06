import { CategoriaResumen } from './categoria-resumen.model';

/** Un grupo del árbol de categorías, tal como lo lee esta feature. */
export interface GrupoCategoriasResumen {
  id: number;
  nombre: string;
  oculto: boolean;
  categorias: CategoriaResumen[];
}
