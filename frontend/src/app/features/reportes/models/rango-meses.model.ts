/** Rango de meses `yyyy-MM`, ambos inclusivos. */
export interface RangoMeses {
  desde: string;
  hasta: string;
}

/** Atajos del selector de rango. */
export type TipoAtajo = 'este-mes' | 'ultimos-3' | 'ultimos-6' | 'ultimos-12' | 'este-anio';

/** Pestañas de la pantalla, tal como viven en el parámetro `reporte` de la URL. */
export type PestanaReporte = 'gasto' | 'ingresos-gastos' | 'patrimonio' | 'saldo' | 'metas';
