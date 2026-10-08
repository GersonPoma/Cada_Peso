import { TipoMeta } from './meta-response.model';

/** Estado de una meta en un mes, en orden de prioridad (`EstadoMeta` del backend). */
export type EstadoMeta = 'SOBREGASTADA' | 'POSPUESTA' | 'FALTA' | 'FINANCIADA';

/** Una meta en un mes con sus cifras ya calculadas (`MetaMesResponse`). Montos en milésimas. */
export interface MetaMesResponse {
  categoriaId: number;
  nombre: string;
  tipo: TipoMeta;
  monto: number;
  necesidad: number;
  asignado: number;
  disponible: number;
  faltante: number;
  estado: EstadoMeta;
}

/** Las metas de un mes (`MetasMesResponse`); `totalFaltante` suma el faltante de `metas`. */
export interface MetasMesResponse {
  /** `yyyy-MM`. */
  mes: string;
  totalFaltante: number;
  metas: MetaMesResponse[];
}
