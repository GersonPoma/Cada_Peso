import { MetaMesResponse } from '../models/metas-mes-response.model';

/** Tono del indicador: `error` solo para lo urgente; `neutro` para lo que no pide acción. */
export type TonoMeta = 'ok' | 'falta' | 'neutro' | 'error';

/** Cómo se muestra el estado de una meta en una fila del mes. */
export interface PresentacionMeta {
  /** Texto del estado; si hay `monto`, va seguido del monto formateado. */
  texto: string;
  /** Monto (milésimas) que acompaña al texto, o `null`. */
  monto: number | null;
  /** Nombre de Material Symbols. */
  icono: string;
  tono: TonoMeta;
  /** Porcentaje entero de 0 a 100 para la barra. */
  progreso: number;
}

/**
 * Progreso de `asignado / necesidad` en enteros de 0 a 100; lleno con necesidad 0 (pospuesta o
 * nada que financiar). Solo se usa para la barra: el dinero nunca pasa por coma flotante.
 */
export function progresoMeta(asignado: number, necesidad: number): number {
  if (necesidad <= 0) {
    return 100;
  }
  return Math.min(100, Math.max(0, Math.round((asignado * 100) / necesidad)));
}

/**
 * Texto, ícono, tono y progreso de una meta en el mes. En los meses anteriores al actual, `FALTA`
 * se muestra como `Faltaron` en tono neutro: la meta vigente se aplica a todos los meses (no hay
 * historial) y un faltante rojo en un mes viejo parecería un error real.
 */
export function presentacionMeta(meta: MetaMesResponse, mesPasado: boolean): PresentacionMeta {
  const progreso = progresoMeta(meta.asignado, meta.necesidad);
  switch (meta.estado) {
    case 'FINANCIADA':
      return { texto: 'Financiada', monto: null, icono: 'check_circle', tono: 'ok', progreso };
    case 'POSPUESTA':
      return {
        texto: 'Pospuesta este mes',
        monto: null,
        icono: 'pause_circle',
        tono: 'neutro',
        progreso,
      };
    case 'SOBREGASTADA':
      return { texto: 'Sobregastada', monto: null, icono: 'warning', tono: 'error', progreso };
    case 'FALTA':
      return mesPasado
        ? { texto: 'Faltaron', monto: meta.faltante, icono: 'history', tono: 'neutro', progreso }
        : { texto: 'Falta', monto: meta.faltante, icono: 'schedule', tono: 'falta', progreso };
  }
}
