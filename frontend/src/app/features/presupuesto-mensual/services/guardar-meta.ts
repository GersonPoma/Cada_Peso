import { aFechaNegocio } from '../../../shared/fecha/fecha-negocio';
import { GuardarMetaRequest } from '../models/guardar-meta-request.model';
import { FrecuenciaMeta, TipoMeta } from '../models/meta-response.model';

/** Valor completo del formulario de meta (incluidos los controles deshabilitados). */
export interface ValorFormularioMeta {
  tipo: TipoMeta;
  /** Milésimas. */
  monto: number | null;
  frecuencia: FrecuenciaMeta;
  diaSemana: number | null;
  intervaloDias: number | null;
  fechaInicio: Date | null;
  fechaObjetivo: Date | null;
}

/**
 * Arma el cuerpo de `PUT .../meta` con `tipo`, `monto` y solo los campos del tipo y de la
 * frecuencia; las fechas como `yyyy-MM-dd` locales (`aFechaNegocio`). Los valores que quedaron de
 * otro tipo no se envían.
 */
export function aGuardarMetaRequest(valor: ValorFormularioMeta): GuardarMetaRequest {
  const solicitud: GuardarMetaRequest = { tipo: valor.tipo, monto: valor.monto ?? 0 };
  if (valor.tipo === 'MONTO_MENSUAL') {
    solicitud.frecuencia = valor.frecuencia;
    if (valor.frecuencia === 'SEMANAL' && valor.diaSemana !== null) {
      solicitud.diaSemana = valor.diaSemana;
    }
    if (valor.frecuencia === 'PERSONALIZADA') {
      if (valor.intervaloDias !== null) {
        solicitud.intervaloDias = valor.intervaloDias;
      }
      const fechaInicio = aFechaNegocio(valor.fechaInicio);
      if (fechaInicio !== null) {
        solicitud.fechaInicio = fechaInicio;
      }
    }
  }
  if (valor.tipo === 'MONTO_PARA_FECHA') {
    const fechaObjetivo = aFechaNegocio(valor.fechaObjetivo);
    if (fechaObjetivo !== null) {
      solicitud.fechaObjetivo = fechaObjetivo;
    }
  }
  return solicitud;
}
