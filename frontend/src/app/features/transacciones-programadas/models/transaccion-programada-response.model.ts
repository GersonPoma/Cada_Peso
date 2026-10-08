/** Cada cuánto se repite una programada (`FrecuenciaProgramada` del backend). */
export type FrecuenciaProgramada =
  'DIARIA' | 'SEMANAL' | 'CADA_2_SEMANAS' | 'MENSUAL' | 'CADA_3_MESES' | 'ANUAL';

/** Una plantilla de transacción recurrente (`TransaccionProgramadaResponse` del backend). */
export interface TransaccionProgramadaResponse {
  id: number;
  cuentaId: number;
  /** `yyyy-MM-dd`. */
  fechaInicio: string;
  frecuencia: FrecuenciaProgramada;
  fechaFin: string | null;
  /** Milésimas con signo: negativo es una salida. */
  monto: number;
  categoriaId: number | null;
  beneficiario: string | null;
  memo: string | null;
  activa: boolean;
  /** `null` cuando ya no quedan ocurrencias por generar (finalizada). */
  proximaFecha: string | null;
  /** Motivo de la última generación fallida, o `null`. */
  ultimoError: string | null;
  fechaCreacion: string;
  fechaActualizacion: string;
}
