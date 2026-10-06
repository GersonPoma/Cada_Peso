import { SubtransaccionRequest } from './subtransaccion-request.model';

/** Cuerpo de `PUT /transacciones/{id}`: la cuenta no se cambia aquí. */
export interface ActualizarTransaccionRequest {
  fecha: string;
  monto: number;
  categoriaId: number | null;
  beneficiario: string | null;
  memo: string | null;
  subtransacciones: SubtransaccionRequest[];
}
