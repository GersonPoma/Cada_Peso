/** Una categoría de `GET .../meses/{mes}`: solo lo que usa Inicio. */
export interface CategoriaMesResumen {
  categoriaId: number;
  sobregastada: boolean;
}

export interface GrupoMesResumen {
  categorias: CategoriaMesResumen[];
}

/** `GET .../meses/{mes}?incluirOcultas=true`: solo lo que usa Inicio. */
export interface MesResumen {
  mes: string;
  /** Milésimas con signo; considera siempre todo el presupuesto. */
  listoParaAsignar: number;
  grupos: GrupoMesResumen[];
}
