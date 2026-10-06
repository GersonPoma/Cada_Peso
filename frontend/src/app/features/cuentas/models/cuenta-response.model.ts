import { TipoCuenta } from './tipo-cuenta.model';

/** Respuesta de `/cuentas` (`CuentaResponse` del backend). Montos en milésimas. */
export interface CuentaResponse {
  id: number;
  nombre: string;
  tipo: TipoCuenta;
  enPresupuesto: boolean;
  saldoInicial: number;
  cerrada: boolean;
  /** `Instant` del backend, ISO-8601 en UTC. */
  fechaCreacion: string;
  /** `Instant` del backend, ISO-8601 en UTC. */
  fechaActualizacion: string;
}
