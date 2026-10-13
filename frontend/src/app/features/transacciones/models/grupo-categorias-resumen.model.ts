import { CategoriaResumen } from './categoria-resumen.model';

/** Un grupo del árbol de categorías, tal como lo lee esta feature. */
export interface GrupoCategoriasResumen {
  id: number;
  nombre: string;
  oculto: boolean;
  /** `NORMAL` o `PAGOS_TARJETA`; el backend lo envía siempre (aditivo). */
  tipo?: 'NORMAL' | 'PAGOS_TARJETA';
  categorias: CategoriaResumen[];
}
