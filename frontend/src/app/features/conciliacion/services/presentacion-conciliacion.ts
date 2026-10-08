import { AbstractControl, ValidationErrors } from '@angular/forms';
import { aFechaNegocio } from '../../../shared/fecha/fecha-negocio';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { EstadoConciliacionResponse } from '../models/estado-conciliacion-response.model';

/** Cómo se pinta la diferencia, además de su texto. */
export type TonoDiferencia = 'cuadra' | 'falta' | 'sobra';

const formato = new MontoPipe();

/**
 * Texto de la diferencia con el extracto (en milésimas), para no depender solo del color:
 * 0 cuadra, positiva es lo que le falta a lo conciliado y negativa lo que sobra.
 */
export function textoDiferencia(
  diferencia: number,
  moneda: string,
): { texto: string; tono: TonoDiferencia } {
  if (diferencia === 0) {
    return { texto: 'Cuadra con el extracto', tono: 'cuadra' };
  }
  if (diferencia > 0) {
    return {
      texto: `A lo conciliado le faltan ${formato.transform(diferencia, moneda)}`,
      tono: 'falta',
    };
  }
  return {
    texto: `Lo conciliado supera al extracto en ${formato.transform(-diferencia, moneda)}`,
    tono: 'sobra',
  };
}

/**
 * Cuántas transacciones `NO_CONCILIADA` de la lista del estado tienen fecha hasta la del
 * extracto. `minimo` indica que la API recortó la lista (trae las más recientes), así que puede
 * faltar alguna antigua: el conteo es "al menos".
 */
export function noConciliadasHasta(estado: EstadoConciliacionResponse): {
  cantidad: number;
  minimo: boolean;
} {
  return {
    cantidad: estado.noConciliadas.filter((t) => t.fecha <= estado.fecha).length,
    minimo: estado.noConciliadas.length < estado.totalNoConciliadas,
  };
}

/** Fecha local de hoy a medianoche (nunca a partir de UTC). */
export function hoy(): Date {
  const ahora = new Date();
  return new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate());
}

/** Error `futura` si la fecha elegida es posterior a hoy en hora local. */
export function noFutura(control: AbstractControl): ValidationErrors | null {
  const valor: unknown = control.value;
  if (!(valor instanceof Date) || isNaN(valor.getTime())) {
    return null;
  }
  return (aFechaNegocio(valor) as string) > (aFechaNegocio(hoy()) as string)
    ? { futura: true }
    : null;
}
