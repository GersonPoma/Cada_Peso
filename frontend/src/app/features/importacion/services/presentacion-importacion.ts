import { EstadoFila, TotalesVistaPrevia } from '../models/vista-previa-response.model';

/** El tamaño de un archivo para mostrarlo, con el formato numérico de la región. */
export function formatearTamano(bytes: number, region: string): string {
  const formato = (valor: number, unidad: string): string =>
    `${new Intl.NumberFormat(region, { maximumFractionDigits: 1 }).format(valor)} ${unidad}`;
  if (bytes < 1024) {
    return `${new Intl.NumberFormat(region).format(bytes)} bytes`;
  }
  if (bytes < 1024 * 1024) {
    return formato(bytes / 1024, 'KB');
  }
  return formato(bytes / (1024 * 1024), 'MB');
}

/** `1 fila de datos` o `12 filas de datos`. */
export function textoFilasDeDatos(cantidad: number): string {
  return `${cantidad} ${cantidad === 1 ? 'fila' : 'filas'} de datos`;
}

/** Cómo se muestra el estado de una fila: texto e ícono, no solo color. */
export const PRESENTACION_ESTADO: Readonly<
  Record<EstadoFila, { texto: string; icono: string; clase: string }>
> = {
  NUEVA: { texto: 'Nueva', icono: 'add_circle', clase: 'nueva' },
  DUPLICADA: { texto: 'Duplicada', icono: 'content_copy', clase: 'duplicada' },
  INVALIDA: { texto: 'Inválida', icono: 'error', clase: 'invalida' },
};

/** Los filtros de la tabla de la vista previa. */
export type FiltroEstado = 'TODAS' | EstadoFila;

export const FILTROS_ESTADO: readonly { valor: FiltroEstado; texto: string }[] = [
  { valor: 'TODAS', texto: 'Todas' },
  { valor: 'NUEVA', texto: 'Nuevas' },
  { valor: 'DUPLICADA', texto: 'Duplicadas' },
  { valor: 'INVALIDA', texto: 'Inválidas' },
];

/** Tamaños de página de la tabla (paginada en el cliente). */
export const TAMANOS_PAGINA_VISTA_PREVIA = [10, 25, 50, 100] as const;
export const TAMANO_PAGINA_POR_DEFECTO = 25;

/** Los totales de la vista previa como texto: `10 filas en total`, `6 nuevas`, `1 inválida`... */
export function textosTotales(totales: TotalesVistaPrevia): { clave: string; texto: string }[] {
  const plural = (n: number, singular: string, plural: string): string =>
    `${n} ${n === 1 ? singular : plural}`;
  return [
    { clave: 'total', texto: `${plural(totales.total, 'fila', 'filas')} en total` },
    { clave: 'nuevas', texto: plural(totales.nuevas, 'nueva', 'nuevas') },
    { clave: 'duplicadas', texto: plural(totales.duplicadas, 'duplicada', 'duplicadas') },
    { clave: 'invalidas', texto: plural(totales.invalidas, 'inválida', 'inválidas') },
  ];
}
