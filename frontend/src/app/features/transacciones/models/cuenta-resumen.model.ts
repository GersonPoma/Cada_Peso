/**
 * Lo que esta feature necesita de una cuenta (lectura de `GET /cuentas`). Modelo propio: una
 * feature no importa los modelos de otra.
 */
export interface CuentaResumen {
  id: number;
  nombre: string;
  enPresupuesto: boolean;
  cerrada: boolean;
  /** Tipo de la cuenta (`TIPO_CUENTA_TARJETA` para una tarjeta); el backend lo envía siempre. */
  tipo?: string;
}

/** Valor de `CuentaResumen.tipo` de una tarjeta de crédito. */
export const TIPO_CUENTA_TARJETA = 'TARJETA_CREDITO';
