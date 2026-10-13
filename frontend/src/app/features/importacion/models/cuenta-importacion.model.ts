/** Tipo de cuenta, igual que el enum `TipoCuenta` del backend. */
export type TipoCuentaImportacion =
  'CORRIENTE' | 'AHORRO' | 'EFECTIVO' | 'TARJETA_CREDITO' | 'INVERSION' | 'PRESTAMO';

/** Cómo se muestra cada tipo. */
export const ETIQUETAS_TIPO_CUENTA: Readonly<Record<TipoCuentaImportacion, string>> = {
  CORRIENTE: 'Corriente',
  AHORRO: 'Ahorro',
  EFECTIVO: 'Efectivo',
  TARJETA_CREDITO: 'Tarjeta de crédito',
  INVERSION: 'Inversión',
  PRESTAMO: 'Préstamo',
};

/**
 * Lo que esta feature usa de una cuenta (lectura de `GET .../cuentas/{id}`). Modelo propio: una
 * feature no importa los modelos de otra.
 */
export interface CuentaImportacion {
  id: number;
  nombre: string;
  tipo: TipoCuentaImportacion;
  enPresupuesto: boolean;
  cerrada: boolean;
}
