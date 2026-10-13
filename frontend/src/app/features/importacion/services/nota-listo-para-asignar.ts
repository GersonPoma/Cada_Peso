import { CuentaImportacion } from '../models/cuenta-importacion.model';

export const NOTA_CUENTA_DEL_PRESUPUESTO =
  'Las entradas importadas suben Listo para asignar; las salidas no lo bajan hasta que las ' +
  'categorices';
export const NOTA_TARJETA_DE_CREDITO =
  'Importar a una tarjeta de crédito no cambia Listo para asignar';
export const NOTA_CUENTA_DE_SEGUIMIENTO =
  'Importar a una cuenta de seguimiento no cambia Listo para asignar';

/**
 * La nota sobre `Listo para asignar` según la cuenta. La tarjeta de crédito va primero: una tarjeta
 * de seguimiento usa el texto de tarjeta. Los tres textos son fijos.
 */
export function notaListoParaAsignar(cuenta: Pick<CuentaImportacion, 'tipo' | 'enPresupuesto'>) {
  if (cuenta.tipo === 'TARJETA_CREDITO') {
    return NOTA_TARJETA_DE_CREDITO;
  }
  return cuenta.enPresupuesto ? NOTA_CUENTA_DEL_PRESUPUESTO : NOTA_CUENTA_DE_SEGUIMIENTO;
}
