import { regionUsuario } from '../../../shared/formato/region-usuario';

/*
 * Meses del presupuesto en formato `yyyy-MM`, en funciones puras. El mes actual sale SIEMPRE de
 * la fecha local del navegador (`getFullYear`/`getMonth`), nunca de `toISOString()`: en Bolivia,
 * desde las 20:00 del último día del mes, la fecha en UTC ya es del mes siguiente.
 */

/** Primer mes que admite el backend. */
export const MES_MINIMO = '2000-01';
/** Último mes que admite el backend. */
export const MES_MAXIMO = '2100-12';

const FORMATO_MES = /^(\d{4})-(0[1-9]|1[0-2])$/;

/** Mes local de `ahora` (por defecto, el momento actual). */
export function mesActual(ahora: Date = new Date()): string {
  return formatear(ahora.getFullYear(), ahora.getMonth() + 1);
}

/** `true` si el texto es un `yyyy-MM` real entre `MES_MINIMO` y `MES_MAXIMO`. */
export function esMesValido(texto: string | null | undefined): texto is string {
  return (
    typeof texto === 'string' &&
    FORMATO_MES.test(texto) &&
    texto >= MES_MINIMO &&
    texto <= MES_MAXIMO
  );
}

/** El mes `n` meses después (o antes, con `n` negativo) de `mes`. */
export function sumarMeses(mes: string, n: number): string {
  const [anio, numero] = partes(mes);
  const indice = anio * 12 + (numero - 1) + n;
  return formatear(Math.floor(indice / 12), (((indice % 12) + 12) % 12) + 1);
}

/** El mes en texto largo según la región (ej. `octubre de 2026` en `es-BO`). */
export function textoMes(mes: string, region: string = regionUsuario()): string {
  const [anio, numero] = partes(mes);
  // Fecha local del día 1: no se corre de mes por la zona horaria.
  const fecha = new Date(anio, numero - 1, 1);
  return new Intl.DateTimeFormat(region, { month: 'long', year: 'numeric' }).format(fecha);
}

function partes(mes: string): [number, number] {
  const [anio, numero] = mes.split('-').map(Number);
  return [anio, numero];
}

function formatear(anio: number, numero: number): string {
  return `${String(anio).padStart(4, '0')}-${String(numero).padStart(2, '0')}`;
}
