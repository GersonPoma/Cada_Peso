import { ArbolCategorias } from './arbol-categorias.model';
import { CategoriaResponse } from './categoria-response.model';

/** Datos del diálogo de grupo: crear uno nuevo o renombrar uno existente. */
export type DatosDialogoGrupo =
  { modo: 'crear' } | { modo: 'renombrar'; grupo: { id: number; nombre: string } };

/** Datos del diálogo de categoría: crear una en un grupo o editar una existente. */
export type DatosDialogoCategoria =
  { modo: 'crear'; grupoId: number } | { modo: 'editar'; categoria: CategoriaResponse };

/** Datos del diálogo 'Mover a...': el árbol visible y la categoría que se mueve. */
export interface DatosDialogoMoverCategoria {
  arbol: ArbolCategorias;
  categoria: CategoriaResponse;
}
