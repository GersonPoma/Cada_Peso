import { esMesValido, sumarMeses, textoMes } from '../../../shared/fecha/mes';
import { regionUsuario } from '../../../shared/formato/region-usuario';
import { RangoMeses, TipoAtajo } from '../models/rango-meses.model';

/*
 * Rango de meses de los reportes, en funciones puras con aritmética entera. El mes local sale de
 * `mesActual()` (`shared/fecha/mes.ts`), que nunca usa la fecha en UTC.
 */

/** Máximo de meses por rango que acepta el backend por defecto. */
export const MAXIMO_MESES = 60;

/** Rango que se usa cuando la URL no trae uno válido. */
export const ATAJO_POR_DEFECTO: TipoAtajo = 'ultimos-6';

export const ATAJOS: readonly { tipo: TipoAtajo; texto: string }[] = [
  { tipo: 'este-mes', texto: 'Este mes' },
  { tipo: 'ultimos-3', texto: 'Últimos 3 meses' },
  { tipo: 'ultimos-6', texto: 'Últimos 6 meses' },
  { tipo: 'ultimos-12', texto: 'Últimos 12 meses' },
  { tipo: 'este-anio', texto: 'Este año' },
];

/** Por qué un rango no es válido. */
export type ErrorRango = 'mal-formado' | 'invertido' | 'excede-maximo';

/** Cantidad de meses del rango, ambos inclusivos (`2026-01` a `2026-01` es 1). */
export function contarMeses(desde: string, hasta: string): number {
  return indice(hasta) - indice(desde) + 1;
}

/** `null` si el rango es válido; si no, el motivo. */
export function validarRango(desde: string, hasta: string): ErrorRango | null {
  if (!esMesValido(desde) || !esMesValido(hasta)) {
    return 'mal-formado';
  }
  if (desde > hasta) {
    return 'invertido';
  }
  return contarMeses(desde, hasta) > MAXIMO_MESES ? 'excede-maximo' : null;
}

/** El rango de un atajo, terminando en `mesLocal` (incluido). */
export function atajo(tipo: TipoAtajo, mesLocal: string): RangoMeses {
  switch (tipo) {
    case 'este-mes':
      return { desde: mesLocal, hasta: mesLocal };
    case 'ultimos-3':
      return { desde: sumarMeses(mesLocal, -2), hasta: mesLocal };
    case 'ultimos-6':
      return { desde: sumarMeses(mesLocal, -5), hasta: mesLocal };
    case 'ultimos-12':
      return { desde: sumarMeses(mesLocal, -11), hasta: mesLocal };
    case 'este-anio':
      return { desde: `${mesLocal.slice(0, 4)}-01`, hasta: mesLocal };
  }
}

/**
 * El rango de los parámetros de la URL, o el de por defecto si falta o no es válido; en ese caso
 * `normalizado` avisa que hay que reescribir la URL.
 */
export function rangoDesdeUrl(
  desde: string | null,
  hasta: string | null,
  mesLocal: string,
): { rango: RangoMeses; normalizado: boolean } {
  if (desde !== null && hasta !== null && validarRango(desde, hasta) === null) {
    return { rango: { desde, hasta }, normalizado: false };
  }
  return { rango: atajo(ATAJO_POR_DEFECTO, mesLocal), normalizado: true };
}

/** Primer día del mes como `yyyy-MM-dd`. */
export function primerDia(mes: string): string {
  return `${mes}-01`;
}

/** Último día del mes como `yyyy-MM-dd`, con bisiestos y sin `Date`. */
export function ultimoDia(mes: string): string {
  const [anio, numero] = mes.split('-').map(Number);
  return `${mes}-${String(diasDelMes(anio, numero)).padStart(2, '0')}`;
}

/** El rango en texto (ej. `enero de 2026 – junio de 2026`); un solo mes, solo ese mes. */
export function textoRango(rango: RangoMeses, region: string = regionUsuario()): string {
  const desde = textoMes(rango.desde, region);
  return rango.desde === rango.hasta ? desde : `${desde} – ${textoMes(rango.hasta, region)}`;
}

/** `queryParams` del enlace a la lista de transacciones con las fechas del rango. */
export function fechasDelRango(rango: RangoMeses): { desde: string; hasta: string } {
  return { desde: primerDia(rango.desde), hasta: ultimoDia(rango.hasta) };
}

function indice(mes: string): number {
  const [anio, numero] = mes.split('-').map(Number);
  return anio * 12 + numero - 1;
}

function diasDelMes(anio: number, mes: number): number {
  if (mes === 2) {
    const bisiesto = (anio % 4 === 0 && anio % 100 !== 0) || anio % 400 === 0;
    return bisiesto ? 29 : 28;
  }
  return [4, 6, 9, 11].includes(mes) ? 30 : 31;
}
