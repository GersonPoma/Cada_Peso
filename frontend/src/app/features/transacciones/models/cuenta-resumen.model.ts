/**
 * Lo que esta feature necesita de una cuenta (lectura de `GET /cuentas`). Modelo propio: una
 * feature no importa los modelos de otra.
 */
export interface CuentaResumen {
  id: number;
  nombre: string;
  enPresupuesto: boolean;
  cerrada: boolean;
}
