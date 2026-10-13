/** Una cuenta de `GET .../cuentas`: solo lo que usa Inicio. */
export interface CuentaResumen {
  id: number;
  enPresupuesto: boolean;
  cerrada: boolean;
}

/** Un saldo de `GET .../transacciones/saldos` (milésimas con signo). */
export interface SaldoResumen {
  cuentaId: number;
  saldo: number;
}

/** Las cuentas y sus saldos juntos: el saldo total necesita las dos respuestas. */
export interface CuentasConSaldos {
  cuentas: CuentaResumen[];
  saldos: SaldoResumen[];
}
