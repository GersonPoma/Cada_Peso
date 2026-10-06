/**
 * Saldos de una cuenta (`GET /transacciones/saldos`), en milésimas y con el saldo inicial
 * incluido: `saldo` con todas las transacciones y `saldoConciliado` solo con las conciliadas.
 */
export interface SaldoCuentaResponse {
  cuentaId: number;
  saldo: number;
  saldoConciliado: number;
}
