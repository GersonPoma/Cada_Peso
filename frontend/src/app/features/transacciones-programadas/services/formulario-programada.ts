import { AbstractControl, ValidationErrors } from '@angular/forms';
import { aFechaNegocio } from '../../../shared/fecha/fecha-negocio';
import { ActualizarProgramadaRequest } from '../models/actualizar-programada-request.model';
import { CrearProgramadaRequest } from '../models/crear-programada-request.model';
import { GrupoCategoriasLectura } from '../models/grupo-categorias-lectura.model';
import { FrecuenciaProgramada } from '../models/transaccion-programada-response.model';
import { TipoMonto } from './presentacion-programada';

/** Valores del formulario de una programada (`getRawValue()`). */
export interface ValorFormularioProgramada {
  cuentaId: number | null;
  tipo: TipoMonto;
  /** Milésimas en positivo; el tipo le da el signo al enviar. */
  monto: number | null;
  frecuencia: FrecuenciaProgramada;
  fechaInicio: Date | null;
  fechaFin: Date | null;
  categoriaId: number | null;
  beneficiario: string;
  memo: string;
}

/** Cuerpo del `POST`: monto con signo, fechas `yyyy-MM-dd` locales y textos recortados. */
export function aCrearRequest(valor: ValorFormularioProgramada): CrearProgramadaRequest {
  return {
    cuentaId: valor.cuentaId as number,
    fechaInicio: aFechaNegocio(valor.fechaInicio) as string,
    ...aActualizarRequest(valor),
  };
}

/** Cuerpo del `PUT`: sin la cuenta ni la fecha de inicio, que no se editan. */
export function aActualizarRequest(valor: ValorFormularioProgramada): ActualizarProgramadaRequest {
  const signo = valor.tipo === 'salida' ? -1 : 1;
  return {
    monto: (valor.monto ?? 0) * signo,
    categoriaId: valor.categoriaId,
    beneficiario: valor.beneficiario.trim() || null,
    memo: valor.memo.trim() || null,
    frecuencia: valor.frecuencia,
    fechaFin: aFechaNegocio(valor.fechaFin),
  };
}

/**
 * Validador de `fechaFin`: no puede ser anterior a `fechaInicio` del mismo grupo (comparando las
 * fechas locales `yyyy-MM-dd`, también cuando el inicio está deshabilitado al editar).
 */
export function finNoAnteriorAInicio(control: AbstractControl): ValidationErrors | null {
  const fin = control.value as Date | null;
  const inicio = control.parent?.get('fechaInicio')?.value as Date | null | undefined;
  if (!(fin instanceof Date) || !(inicio instanceof Date)) {
    return null;
  }
  if (isNaN(fin.getTime()) || isNaN(inicio.getTime())) {
    return null;
  }
  return (aFechaNegocio(fin) as string) < (aFechaNegocio(inicio) as string)
    ? { finAnterior: true }
    : null;
}

/**
 * Categorías que admite una programada: sin las de pago de tarjeta (`422`) ni las ocultas, salvo
 * la que ya tiene (`actual`), que sigue disponible aunque esté oculta.
 */
export function opcionesCategoria(
  grupos: readonly GrupoCategoriasLectura[],
  actual: number | null,
): GrupoCategoriasLectura[] {
  return grupos
    .map((grupo) => ({
      ...grupo,
      categorias: grupo.categorias.filter(
        (c) => !c.esPagoTarjeta && (c.id === actual || (!c.oculta && !grupo.oculto)),
      ),
    }))
    .filter((grupo) => grupo.categorias.length > 0);
}
