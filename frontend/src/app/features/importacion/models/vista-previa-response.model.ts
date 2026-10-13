/** Qué pasaría con una fila al importar (`EstadoFila` del backend). */
export type EstadoFila = 'NUEVA' | 'DUPLICADA' | 'INVALIDA';

/**
 * Una fila de datos interpretada (`FilaPreviaResponse` del backend). En las `INVALIDA`, la fecha,
 * el monto, el beneficiario y el memo son nulos y `motivo` es un texto fijo del servidor.
 */
export interface FilaPrevia {
  /** Número de fila de DATOS, desde 1 (sin encabezado ni líneas vacías), no de línea. */
  fila: number;
  /** `yyyy-MM-dd`. */
  fecha: string | null;
  /** Milésimas con signo. */
  monto: number | null;
  beneficiario: string | null;
  memo: string | null;
  estado: EstadoFila;
  motivo: string | null;
}

/** Totales de la vista previa (`TotalesResponse` del backend). */
export interface TotalesVistaPrevia {
  total: number;
  nuevas: number;
  duplicadas: number;
  invalidas: number;
  /** Hoy coincide con `nuevas`. */
  sinCategoria: number;
}

/** Respuesta de `POST .../importacion/vista-previa`. */
export interface VistaPrevia {
  totales: TotalesVistaPrevia;
  filas: FilaPrevia[];
}
