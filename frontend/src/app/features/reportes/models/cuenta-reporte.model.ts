import { TipoCuentaReporte } from './evolucion-saldo-response.model';

/**
 * Lo que esta feature necesita de una cuenta (lectura de `GET /cuentas`). Modelo propio: una
 * feature no importa los modelos de otra.
 */
export interface CuentaReporte {
  id: number;
  nombre: string;
  tipo: TipoCuentaReporte;
  enPresupuesto: boolean;
  cerrada: boolean;
}
