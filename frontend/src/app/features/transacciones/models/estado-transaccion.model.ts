/** Estado de conciliación (`EstadoTransaccion` del backend). */
export type EstadoTransaccion = 'NO_CONCILIADA' | 'CONCILIADA' | 'RECONCILIADA';

/** Estados en el orden en que se ofrecen en los filtros. */
export const ESTADOS_TRANSACCION: readonly EstadoTransaccion[] = [
  'NO_CONCILIADA',
  'CONCILIADA',
  'RECONCILIADA',
];

/** Texto de cada estado. */
export const ETIQUETAS_ESTADO: Readonly<Record<EstadoTransaccion, string>> = {
  NO_CONCILIADA: 'No conciliada',
  CONCILIADA: 'Conciliada',
  RECONCILIADA: 'Reconciliada',
};
