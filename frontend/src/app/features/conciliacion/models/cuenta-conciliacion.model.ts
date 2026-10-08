/** Tipo de cuenta, igual que el enum `TipoCuenta` del backend. */
export type TipoCuentaConciliacion =
  'CORRIENTE' | 'AHORRO' | 'EFECTIVO' | 'TARJETA_CREDITO' | 'INVERSION' | 'PRESTAMO';

/** Cómo se muestra cada tipo. */
export const ETIQUETAS_TIPO_CUENTA: Readonly<Record<TipoCuentaConciliacion, string>> = {
  CORRIENTE: 'Corriente',
  AHORRO: 'Ahorro',
  EFECTIVO: 'Efectivo',
  TARJETA_CREDITO: 'Tarjeta de crédito',
  INVERSION: 'Inversión',
  PRESTAMO: 'Préstamo',
};

/** Lo que esta feature usa de una cuenta (lectura de `GET .../cuentas/{id}`). */
export interface CuentaConciliacion {
  id: number;
  nombre: string;
  tipo: TipoCuentaConciliacion;
  enPresupuesto: boolean;
  cerrada: boolean;
}
