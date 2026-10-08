/** Tipo de una cuenta (`TipoCuenta` del backend). */
export type TipoCuentaReporte =
  'CORRIENTE' | 'AHORRO' | 'EFECTIVO' | 'TARJETA_CREDITO' | 'INVERSION' | 'PRESTAMO';

/** Entradas, salidas (con signo) y saldo al cierre de un mes, en milésimas. */
export interface MesSaldoResponse {
  mes: string;
  entradas: number;
  salidas: number;
  saldo: number;
}

/** `GET .../reportes/cuentas/{cuentaId}/evolucion-saldo`. */
export interface EvolucionSaldoResponse {
  cuentaId: number;
  nombre: string;
  tipo: TipoCuentaReporte;
  enPresupuesto: boolean;
  cerrada: boolean;
  saldoInicial: number;
  desde: string;
  hasta: string;
  meses: MesSaldoResponse[];
}
