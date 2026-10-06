import { SubtransaccionRequest } from './subtransaccion-request.model';

/** Cuerpo de `POST /transacciones`. */
export interface CrearTransaccionRequest {
  cuentaId: number;
  fecha: string;
  monto: number;
  categoriaId: number | null;
  beneficiario: string | null;
  memo: string | null;
  aprobada: boolean;
  subtransacciones: SubtransaccionRequest[];
}
