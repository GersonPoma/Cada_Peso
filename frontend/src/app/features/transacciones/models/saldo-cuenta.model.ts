/** Saldos de una cuenta (`SaldoCuentaResponse` del backend), en milésimas. */
export interface SaldoCuenta {
  cuentaId: number;
  saldo: number;
  saldoConciliado: number;
}
