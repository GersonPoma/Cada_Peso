/** Estado de una meta en un mes (`EstadoMeta` del backend). */
export type EstadoMetaReporte = 'SOBREGASTADA' | 'POSPUESTA' | 'FALTA' | 'FINANCIADA';

/** Tipo de meta (`TipoMeta` del backend). */
export type TipoMetaReporte = 'MONTO_MENSUAL' | 'MONTO_PARA_FECHA' | 'SALDO_OBJETIVO';

/** Cumplimiento de una meta en un mes (milésimas). */
export interface MesMetaResponse {
  mes: string;
  necesidad: number;
  asignado: number;
  gastado: number;
  disponible: number;
  faltante: number;
  estado: EstadoMetaReporte;
  /** `asignado / necesidad` en centésimas, sin tope; `null` si la necesidad es 0. */
  porcentaje: number | null;
}

/** Una meta con sus totales del rango y su detalle mes a mes. */
export interface MetaCumplimientoResponse {
  categoriaId: number;
  nombre: string;
  oculta: boolean;
  tipo: TipoMetaReporte;
  monto: number;
  necesidad: number;
  asignado: number;
  gastado: number;
  porcentaje: number | null;
  meses: MesMetaResponse[];
}

/** `GET .../reportes/metas`: metas en el orden del árbol. */
export interface CumplimientoMetasResponse {
  desde: string;
  hasta: string;
  metas: MetaCumplimientoResponse[];
}
