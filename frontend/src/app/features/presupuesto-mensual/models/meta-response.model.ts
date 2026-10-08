/** Qué quiere lograr la meta (`TipoMeta` del backend). */
export type TipoMeta = 'MONTO_MENSUAL' | 'MONTO_PARA_FECHA' | 'SALDO_OBJETIVO';

/** Cada cuánto vence una meta `MONTO_MENSUAL` (`FrecuenciaMeta` del backend). */
export type FrecuenciaMeta = 'SEMANAL' | 'MENSUAL' | 'PERSONALIZADA';

/**
 * La meta de una categoría (`MetaResponse` del backend). Los campos que no aplican a su tipo
 * llegan en `null`. `monto` en milésimas; las fechas en `yyyy-MM-dd`.
 */
export interface MetaResponse {
  categoriaId: number;
  tipo: TipoMeta;
  monto: number;
  frecuencia: FrecuenciaMeta | null;
  /** 1 (lunes) a 7 (domingo). */
  diaSemana: number | null;
  intervaloDias: number | null;
  fechaInicio: string | null;
  fechaObjetivo: string | null;
}
