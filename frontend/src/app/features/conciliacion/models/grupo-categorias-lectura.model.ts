import { CategoriaLectura } from './categoria-lectura.model';

/** Un grupo del árbol de categorías, tal como lo lee esta feature. */
export interface GrupoCategoriasLectura {
  id: number;
  nombre: string;
  oculto: boolean;
  categorias: CategoriaLectura[];
}
