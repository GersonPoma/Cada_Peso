/** Ingresos, gastos y neto (ingresos menos gastos) de un mes `yyyy-MM`, en milésimas. */
export interface MesIngresosGastosResponse {
  mes: string;
  ingresos: number;
  gastos: number;
  neto: number;
}

/** `GET .../reportes/ingresos-gastos`: mes a mes y totales del rango. */
export interface IngresosGastosResponse {
  desde: string;
  hasta: string;
  ingresos: number;
  gastos: number;
  neto: number;
  meses: MesIngresosGastosResponse[];
}
