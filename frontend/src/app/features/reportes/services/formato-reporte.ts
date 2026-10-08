import { regionUsuario } from '../../../shared/formato/region-usuario';
import { EstadoMetaReporte, TipoMetaReporte } from '../models/cumplimiento-metas-response.model';

/*
 * Formato y escalas de los reportes. El dinero y los porcentajes son enteros (milésimas y
 * centésimas de punto); la única división es la conversión a texto al mostrar.
 */

export const TEXTO_SIN_NECESIDAD = 'Sin necesidad';

/**
 * Porcentaje en centésimas de punto como texto de la región, con dos decimales (`5283` es
 * `52,83%` en `es-BO`). `null` (necesidad 0) se lee `Sin necesidad`.
 */
export function textoPorcentaje(
  centesimas: number | null,
  region: string = regionUsuario(),
): string {
  if (centesimas === null) {
    return TEXTO_SIN_NECESIDAD;
  }
  return new Intl.NumberFormat(region, {
    style: 'percent',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(centesimas / 10000);
}

/**
 * `valor` como parte entera de `escala` respecto de `maximo` (por defecto, por mil), entre 0 y
 * `escala`. Exacta mientras `valor * escala` no supere `Number.MAX_SAFE_INTEGER` (montos de hasta
 * 9·10¹² milésimas con la escala por mil).
 */
export function proporcion(valor: number, maximo: number, escala = 1000): number {
  if (maximo <= 0 || valor <= 0) {
    return 0;
  }
  return Math.min(escala, Math.floor((valor * escala) / maximo));
}

/** Eje vertical de un gráfico: extremos enteros que incluyen el 0 y sus marcas. */
export interface EscalaVertical {
  minimo: number;
  maximo: number;
  marcas: number[];
}

/**
 * Escala con un paso "redondo" (1, 2 o 5 × 10ⁿ milésimas) y unas 4 divisiones. Con todos los
 * valores en 0 usa `0..1000` para que el eje no tenga alto cero.
 */
export function escalaVertical(valores: readonly number[]): EscalaVertical {
  const menor = Math.min(0, ...valores);
  const mayor = Math.max(0, ...valores);
  if (menor === 0 && mayor === 0) {
    return { minimo: 0, maximo: 1000, marcas: [0, 1000] };
  }
  const paso = pasoRedondo(Math.ceil((mayor - menor) / 4));
  const minimo = Math.floor(menor / paso) * paso;
  const maximo = Math.ceil(mayor / paso) * paso;
  const marcas: number[] = [];
  for (let marca = minimo; marca <= maximo; marca += paso) {
    marcas.push(marca);
  }
  return { minimo, maximo, marcas };
}

/** El menor 1, 2 o 5 × 10ⁿ que es mayor o igual a `x` (al menos 1). */
export function pasoRedondo(x: number): number {
  let potencia = 1;
  while (potencia * 10 <= x) {
    potencia *= 10;
  }
  for (const multiplo of [1, 2, 5]) {
    if (multiplo * potencia >= x) {
      return multiplo * potencia;
    }
  }
  return 10 * potencia;
}

/** Tono del estado de una meta: `error` solo para lo urgente; `neutro` si no pide acción. */
export type TonoEstadoMeta = 'ok' | 'falta' | 'neutro' | 'error';

/** Texto, ícono y tono del estado de una meta en un mes. */
export interface PresentacionEstadoMeta {
  texto: string;
  icono: string;
  tono: TonoEstadoMeta;
}

/**
 * Estado de una meta en un mes. En los meses anteriores a `mesActual`, `FALTA` se lee `Faltaron`
 * en tono neutro: la meta vigente se aplica a todos los meses (no hay historial) y un faltante
 * rojo en un mes viejo parecería un error real. Mismas palabras que el presupuesto mensual.
 */
export function presentacionEstadoMeta(
  estado: EstadoMetaReporte,
  mes: string,
  mesActual: string,
): PresentacionEstadoMeta {
  switch (estado) {
    case 'FINANCIADA':
      return { texto: 'Financiada', icono: 'check_circle', tono: 'ok' };
    case 'POSPUESTA':
      return { texto: 'Pospuesta', icono: 'pause_circle', tono: 'neutro' };
    case 'SOBREGASTADA':
      return { texto: 'Sobregastada', icono: 'warning', tono: 'error' };
    case 'FALTA':
      return mes < mesActual
        ? { texto: 'Faltaron', icono: 'history', tono: 'neutro' }
        : { texto: 'Falta', icono: 'schedule', tono: 'falta' };
  }
}

export const TEXTOS_TIPO_META: Readonly<Record<TipoMetaReporte, string>> = {
  MONTO_MENSUAL: 'Monto cada cierto tiempo',
  MONTO_PARA_FECHA: 'Monto para una fecha',
  SALDO_OBJETIVO: 'Saldo objetivo',
};

/**
 * Coordenada vertical (entera) de un monto en un eje de `alto` unidades que empieza en `arriba`:
 * el máximo de la escala queda arriba y el mínimo abajo.
 */
export function posicionY(valor: number, escala: EscalaVertical, arriba: number, alto: number) {
  return arriba + Math.round(((escala.maximo - valor) * alto) / (escala.maximo - escala.minimo));
}

/** Centro (entero) de cada uno de `cantidad` meses repartidos en `ancho` desde `izquierda`. */
export function posicionesX(cantidad: number, izquierda: number, ancho: number): number[] {
  return Array.from(
    { length: cantidad },
    (_, i) => izquierda + Math.round(((2 * i + 1) * ancho) / (2 * cantidad)),
  );
}

/** Mes corto para un eje (ej. `oct 26` en `es-BO`). */
export function textoMesCorto(mes: string, region: string = regionUsuario()): string {
  const [anio, numero] = mes.split('-').map(Number);
  const nombre = new Intl.DateTimeFormat(region, { month: 'short' }).format(
    new Date(anio, numero - 1, 1),
  );
  return `${nombre.replace('.', '')} ${String(anio).slice(2)}`;
}

/** Monto compacto para las marcas de un eje (ej. `1,2 mil`); solo para mostrar. */
export function textoEje(milesimas: number, region: string = regionUsuario()): string {
  return new Intl.NumberFormat(region, { notation: 'compact', maximumFractionDigits: 1 }).format(
    milesimas / 1000,
  );
}

/** Cada cuántos meses se rotula el eje para no encimar textos (como mucho 12 rótulos). */
export function pasoRotulos(cantidad: number): number {
  return Math.max(1, Math.ceil(cantidad / 12));
}
