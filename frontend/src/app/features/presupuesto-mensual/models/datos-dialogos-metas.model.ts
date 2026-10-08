/** Datos del diálogo de meta: la categoría y si ya tiene meta (editar) o no (agregar). */
export interface DatosDialogoMeta {
  categoriaId: number;
  nombre: string;
  tieneMeta: boolean;
}

/** Cómo terminó el diálogo de meta; cualquiera de los tres pide recargar el mes. */
export type ResultadoDialogoMeta =
  { tipo: 'guardada' } | { tipo: 'quitada' } | { tipo: 'recargar' };

/** Datos del diálogo de confirmación de las metas. */
export interface DatosDialogoConfirmacionMeta {
  titulo: string;
  mensaje: string;
  confirmar: string;
}

/** Una categoría del mes cargado, para elegir en auto-asignar. */
export interface CategoriaAutoAsignar {
  categoriaId: number;
  nombre: string;
  esPagoTarjeta: boolean;
  tieneMeta: boolean;
}

/** Datos del diálogo de auto-asignar: el mes (`yyyy-MM`) y sus categorías visibles. */
export interface DatosDialogoAutoAsignar {
  mes: string;
  categorias: CategoriaAutoAsignar[];
}

/** Cómo terminó el diálogo de auto-asignar. */
export type ResultadoDialogoAutoAsignar =
  { tipo: 'aplicado'; cambios: number } | { tipo: 'recargar' };
