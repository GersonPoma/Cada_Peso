export type EstadoMetaResumen = 'SOBREGASTADA' | 'POSPUESTA' | 'FALTA' | 'FINANCIADA';

/** Una meta de `GET .../meses/{mes}/metas`: solo lo que usa Inicio. */
export interface MetaMesResumen {
  categoriaId: number;
  estado: EstadoMetaResumen;
}

/** `totalFaltante` no se usa: suma también las metas `SOBREGASTADA`. */
export interface MetasMesResumen {
  mes: string;
  metas: MetaMesResumen[];
}
