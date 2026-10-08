import { CuentaConciliacion } from '../models/cuenta-conciliacion.model';

/**
 * Cómo se comporta la categoría del ajuste de una conciliación:
 * - `sin-ajuste`: la diferencia es 0, no hay ajuste.
 * - `no-admite`: cuenta fuera del presupuesto; no se muestra ni se envía categoría.
 * - `obligatoria`: cuenta del presupuesto con ajuste negativo, o tarjeta de crédito.
 * - `opcional`: ajuste positivo en otra cuenta del presupuesto (sin categoría es ingreso).
 */
export type ReglaCategoriaAjuste = 'sin-ajuste' | 'no-admite' | 'obligatoria' | 'opcional';

/** Regla de categoría del ajuste (la misma que `exigeCategoria` del backend). */
export function reglaCategoriaAjuste(
  cuenta: Pick<CuentaConciliacion, 'enPresupuesto' | 'tipo'>,
  diferencia: number,
): ReglaCategoriaAjuste {
  if (diferencia === 0) {
    return 'sin-ajuste';
  }
  if (!cuenta.enPresupuesto) {
    return 'no-admite';
  }
  return diferencia < 0 || cuenta.tipo === 'TARJETA_CREDITO' ? 'obligatoria' : 'opcional';
}
