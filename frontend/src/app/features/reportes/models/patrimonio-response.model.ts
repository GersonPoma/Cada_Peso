/** Activos, pasivos (deudas con el signo cambiado) y patrimonio al cierre de un mes. */
export interface MesPatrimonioResponse {
  mes: string;
  activos: number;
  pasivos: number;
  patrimonio: number;
}

/** `GET .../reportes/patrimonio`: todas las cuentas del presupuesto, mes a mes. */
export interface PatrimonioResponse {
  desde: string;
  hasta: string;
  meses: MesPatrimonioResponse[];
}
