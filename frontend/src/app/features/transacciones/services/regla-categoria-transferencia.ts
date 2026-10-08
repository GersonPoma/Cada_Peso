import { CuentaResumen } from '../models/cuenta-resumen.model';

/**
 * Cómo se comporta la categoría de una transferencia según `enPresupuesto` de sus cuentas:
 * - `oculta-presupuesto` / `oculta-seguimiento`: ambas del mismo lado, sin categoría.
 * - `obligatoria`: del presupuesto a una de seguimiento (el dinero sale del presupuesto).
 * - `opcional`: de seguimiento al presupuesto (ingreso sin categoría, reembolso con ella).
 * - `pendiente`: falta alguna de las dos cuentas.
 */
export type ReglaCategoriaTransferencia =
  'oculta-presupuesto' | 'oculta-seguimiento' | 'obligatoria' | 'opcional' | 'pendiente';

/** Regla de categoría de una transferencia (la misma que aplica el backend). */
export function reglaCategoriaTransferencia(
  origen: CuentaResumen | null,
  destino: CuentaResumen | null,
): ReglaCategoriaTransferencia {
  if (!origen || !destino) {
    return 'pendiente';
  }
  if (origen.enPresupuesto === destino.enPresupuesto) {
    return origen.enPresupuesto ? 'oculta-presupuesto' : 'oculta-seguimiento';
  }
  return origen.enPresupuesto ? 'obligatoria' : 'opcional';
}
