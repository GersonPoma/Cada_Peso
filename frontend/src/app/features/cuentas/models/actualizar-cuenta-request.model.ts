import { TipoCuenta } from './tipo-cuenta.model';

/** Cuerpo de `PUT /cuentas/{id}`: `enPresupuesto` y `saldoInicial` no se editan. */
export interface ActualizarCuentaRequest {
  nombre: string;
  tipo: TipoCuenta;
}
