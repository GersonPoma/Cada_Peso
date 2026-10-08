/** Lo que decide si el diálogo rellena la categoría con la del beneficiario elegido. */
export interface ContextoCategoriaRecordada {
  /** Se está creando la transacción (no editando). */
  creando: boolean;
  /** Está activo `Dividir`. */
  dividida: boolean;
  /** La persona todavía no tocó el control de categoría. */
  categoriaPristine: boolean;
  /** Valor actual del control de categoría. */
  categoriaActual: number | null;
  /** Categoría predeterminada del beneficiario elegido. */
  sugerida: number | null;
  /** Ids de las categorías del árbol del diálogo, ocultas incluidas. */
  idsExistentes: ReadonlySet<number>;
}

/**
 * Categoría que hay que poner al elegir un beneficiario, o `null` si no hay que tocarla: solo al
 * crear, sin `Dividir`, con la categoría vacía y sin tocar, y si la sugerida existe en el árbol.
 */
export function categoriaRecordada(contexto: ContextoCategoriaRecordada): number | null {
  const { creando, dividida, categoriaPristine, categoriaActual, sugerida, idsExistentes } =
    contexto;
  if (!creando || dividida || !categoriaPristine || categoriaActual !== null) {
    return null;
  }
  return sugerida !== null && idsExistentes.has(sugerida) ? sugerida : null;
}
