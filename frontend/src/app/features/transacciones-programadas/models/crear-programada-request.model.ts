import { FrecuenciaProgramada } from './transaccion-programada-response.model';

/** Cuerpo de `POST .../transacciones-programadas` (`CrearProgramadaRequest`). */
export interface CrearProgramadaRequest {
  cuentaId: number;
  fechaInicio: string;
  frecuencia: FrecuenciaProgramada;
  fechaFin: string | null;
  monto: number;
  categoriaId: number | null;
  beneficiario: string | null;
  memo: string | null;
}
