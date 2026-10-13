/** `GET .../reportes/ingresos-gastos` de un solo mes: los totales del rango (milésimas). */
export interface IngresosGastosResumen {
  ingresos: number;
  gastos: number;
  neto: number;
}
