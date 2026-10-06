import { Params } from '@angular/router';
import { ESTADOS_TRANSACCION, EstadoTransaccion } from '../models/estado-transaccion.model';
import { FiltrosTransacciones } from '../models/filtros-transacciones.model';

/*
 * Filtros y página de la lista en la URL (query params), en funciones puras: la URL es la única
 * fuente de verdad, así recargar o compartir conserva la vista.
 */

export const TAMANOS_PAGINA = [10, 20, 50, 100] as const;
export const TAMANO_POR_DEFECTO = 20;

const FECHA = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;

/** Filtros vacíos en la primera página. */
export function filtrosVacios(tamano: number = TAMANO_POR_DEFECTO): FiltrosTransacciones {
  return {
    cuentaId: null,
    categoriaId: null,
    desde: null,
    hasta: null,
    estado: null,
    soloSinAprobar: false,
    q: null,
    pagina: 0,
    tamano,
  };
}

/** Lee los filtros de los query params; los valores inválidos se descartan. */
export function leerFiltros(params: { get(nombre: string): string | null }): FiltrosTransacciones {
  const tamano = Number(params.get('tamano'));
  const estado = params.get('estado');
  const q = params.get('q')?.trim();
  return {
    cuentaId: idPositivo(params.get('cuentaId')),
    categoriaId: idPositivo(params.get('categoriaId')),
    desde: fecha(params.get('desde')),
    hasta: fecha(params.get('hasta')),
    estado: ESTADOS_TRANSACCION.includes(estado as EstadoTransaccion)
      ? (estado as EstadoTransaccion)
      : null,
    soloSinAprobar: params.get('sinAprobar') === '1',
    q: q ? q : null,
    pagina: Math.max(0, Math.trunc(Number(params.get('pagina')) || 0)),
    tamano: (TAMANOS_PAGINA as readonly number[]).includes(tamano) ? tamano : TAMANO_POR_DEFECTO,
  };
}

/** Query params de unos filtros; omite los vacíos y los valores por defecto. */
export function aQueryParams(filtros: FiltrosTransacciones): Params {
  const params: Params = {};
  if (filtros.cuentaId !== null) params['cuentaId'] = filtros.cuentaId;
  if (filtros.categoriaId !== null) params['categoriaId'] = filtros.categoriaId;
  if (filtros.desde) params['desde'] = filtros.desde;
  if (filtros.hasta) params['hasta'] = filtros.hasta;
  if (filtros.estado) params['estado'] = filtros.estado;
  if (filtros.soloSinAprobar) params['sinAprobar'] = '1';
  if (filtros.q) params['q'] = filtros.q;
  if (filtros.pagina > 0) params['pagina'] = filtros.pagina;
  if (filtros.tamano !== TAMANO_POR_DEFECTO) params['tamano'] = filtros.tamano;
  return params;
}

/** `true` si hay algún filtro aplicado (la página y el tamaño no cuentan). */
export function hayFiltros(filtros: FiltrosTransacciones): boolean {
  return (
    filtros.cuentaId !== null ||
    filtros.categoriaId !== null ||
    filtros.desde !== null ||
    filtros.hasta !== null ||
    filtros.estado !== null ||
    filtros.soloSinAprobar ||
    filtros.q !== null
  );
}

function idPositivo(texto: string | null): number | null {
  if (!texto || !/^\d+$/.test(texto)) {
    return null;
  }
  const valor = Number(texto);
  return Number.isSafeInteger(valor) && valor > 0 ? valor : null;
}

function fecha(texto: string | null): string | null {
  return texto && FECHA.test(texto) ? texto : null;
}
