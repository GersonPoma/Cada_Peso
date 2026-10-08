import { CuentaLectura } from '../models/cuenta-lectura.model';
import { GeneracionResponse } from '../models/generacion-response.model';
import {
  FrecuenciaProgramada,
  TransaccionProgramadaResponse,
} from '../models/transaccion-programada-response.model';

/*
 * Cómo se muestra una programada, en funciones puras. Ninguna calcula fechas de ocurrencias: la
 * próxima fecha es siempre la del servidor (el calendario vive solo en el backend).
 */

export const TEXTOS_FRECUENCIA: Readonly<Record<FrecuenciaProgramada, string>> = {
  DIARIA: 'Cada día',
  SEMANAL: 'Cada semana',
  CADA_2_SEMANAS: 'Cada 2 semanas',
  MENSUAL: 'Cada mes',
  CADA_3_MESES: 'Cada 3 meses',
  ANUAL: 'Cada año',
};

/** Las frecuencias en el orden del selector. */
export const FRECUENCIAS = Object.keys(TEXTOS_FRECUENCIA) as FrecuenciaProgramada[];

export function textoFrecuencia(frecuencia: FrecuenciaProgramada): string {
  return TEXTOS_FRECUENCIA[frecuencia];
}

export type EstadoProgramada = 'error' | 'pausada' | 'finalizada' | 'activa';

/** Estado de una programada con texto e ícono (además del color del tono). */
export interface PresentacionEstado {
  estado: EstadoProgramada;
  texto: string;
  icono: string;
  tono: 'error' | 'neutro' | 'ok';
}

/**
 * Estado en este orden: activa con `ultimoError` → `No se pudo generar`; pausada; sin próxima
 * fecha → `Finalizada`; si no, `Activa`. Una pausada con error se ve `Pausada`: reanudar limpia
 * el error.
 */
export function estadoProgramada(
  programada: Pick<TransaccionProgramadaResponse, 'activa' | 'ultimoError' | 'proximaFecha'>,
): PresentacionEstado {
  if (programada.activa && programada.ultimoError !== null) {
    return { estado: 'error', texto: 'No se pudo generar', icono: 'error', tono: 'error' };
  }
  if (!programada.activa) {
    return { estado: 'pausada', texto: 'Pausada', icono: 'pause_circle', tono: 'neutro' };
  }
  if (programada.proximaFecha === null) {
    return { estado: 'finalizada', texto: 'Finalizada', icono: 'event_available', tono: 'neutro' };
  }
  return { estado: 'activa', texto: 'Activa', icono: 'schedule', tono: 'ok' };
}

export type TipoMonto = 'salida' | 'entrada';

/** Un monto negativo es una salida; uno positivo, una entrada. */
export function tipoMonto(monto: number): TipoMonto {
  return monto < 0 ? 'salida' : 'entrada';
}

/** El monto en positivo (milésimas), como se escribe en el formulario. */
export function montoAbsoluto(monto: number): number {
  return Math.abs(monto);
}

/** El resultado de `Generar ahora`, con singular y plural. */
export function textoResultadoGeneracion(resultado: GeneracionResponse): string {
  const { generadas, plantillasConError } = resultado;
  const partes = [
    generadas === 0
      ? 'No había ocurrencias pendientes'
      : generadas === 1
        ? 'Se generó 1 transacción'
        : `Se generaron ${generadas} transacciones`,
  ];
  if (plantillasConError > 0) {
    partes.push(
      plantillasConError === 1
        ? '1 programada no se pudo generar'
        : `${plantillasConError} programadas no se pudieron generar`,
    );
  }
  return partes.join('. ') + '.';
}

/**
 * `true` si conviene avisar la regla de fin de mes: frecuencia en meses (mensual, cada 3 meses o
 * anual) y día de inicio 29, 30 o 31. Solo describe la regla; no calcula fechas.
 */
export function necesitaAvisoFinDeMes(
  frecuencia: FrecuenciaProgramada | null,
  fechaInicio: Date | null,
): boolean {
  const enMeses =
    frecuencia === 'MENSUAL' || frecuencia === 'CADA_3_MESES' || frecuencia === 'ANUAL';
  return enMeses && fechaInicio !== null && fechaInicio.getDate() >= 29;
}

/** Lo que muestra cada programada en la lista. */
export interface FilaProgramada {
  programada: TransaccionProgramadaResponse;
  cuenta: string;
  beneficiario: string;
  categoria: string;
  tipo: 'Salida' | 'Entrada';
  monto: number;
  frecuencia: string;
  estado: PresentacionEstado;
}

/** Arma una fila; una cuenta o categoría que ya no está en las listas se muestra con `—`. */
export function filaProgramada(
  programada: TransaccionProgramadaResponse,
  cuentas: ReadonlyMap<number, CuentaLectura>,
  categorias: ReadonlyMap<number, string>,
): FilaProgramada {
  return {
    programada,
    cuenta: cuentas.get(programada.cuentaId)?.nombre ?? '—',
    beneficiario: programada.beneficiario ?? '—',
    categoria:
      programada.categoriaId === null
        ? 'Sin categoría'
        : (categorias.get(programada.categoriaId) ?? '—'),
    tipo: tipoMonto(programada.monto) === 'salida' ? 'Salida' : 'Entrada',
    monto: montoAbsoluto(programada.monto),
    frecuencia: textoFrecuencia(programada.frecuencia),
    estado: estadoProgramada(programada),
  };
}
