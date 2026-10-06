import { TipoCuenta } from './tipo-cuenta.model';

/** Cuerpo de `POST /cuentas`. `saldoInicial` en milésimas. */
export interface CrearCuentaRequest {
  nombre: string;
  tipo: TipoCuenta;
  enPresupuesto: boolean;
  saldoInicial: number;
}
