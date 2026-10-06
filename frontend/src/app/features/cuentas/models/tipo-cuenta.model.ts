/** Tipo de cuenta, igual que el enum `TipoCuenta` del backend. */
export type TipoCuenta =
  'CORRIENTE' | 'AHORRO' | 'EFECTIVO' | 'TARJETA_CREDITO' | 'INVERSION' | 'PRESTAMO';

/** Orden en que se ofrecen los tipos al elegir. */
export const TIPOS_CUENTA: readonly TipoCuenta[] = [
  'CORRIENTE',
  'AHORRO',
  'EFECTIVO',
  'TARJETA_CREDITO',
  'INVERSION',
  'PRESTAMO',
];

/** Cómo se muestra cada tipo. */
export const ETIQUETAS_TIPO_CUENTA: Readonly<Record<TipoCuenta, string>> = {
  CORRIENTE: 'Corriente',
  AHORRO: 'Ahorro',
  EFECTIVO: 'Efectivo',
  TARJETA_CREDITO: 'Tarjeta de crédito',
  INVERSION: 'Inversión',
  PRESTAMO: 'Préstamo',
};

/** Tipos que admiten un saldo inicial negativo (deudas), igual que en el backend. */
export const TIPOS_CON_SALDO_NEGATIVO: readonly TipoCuenta[] = ['TARJETA_CREDITO', 'PRESTAMO'];
