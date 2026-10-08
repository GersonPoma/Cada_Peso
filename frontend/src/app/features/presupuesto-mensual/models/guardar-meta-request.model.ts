import { FrecuenciaMeta, TipoMeta } from './meta-response.model';

/**
 * Cuerpo de `PUT /categorias/{categoriaId}/meta` (`GuardarMetaRequest` del backend). Solo se
 * envían los campos del tipo (y de la frecuencia); `monto` en milésimas, mayor que 0; las fechas
 * en `yyyy-MM-dd`.
 */
export interface GuardarMetaRequest {
  tipo: TipoMeta;
  monto: number;
  frecuencia?: FrecuenciaMeta;
  /** 1 (lunes) a 7 (domingo). */
  diaSemana?: number;
  /** 2 a 365. */
  intervaloDias?: number;
  fechaInicio?: string;
  fechaObjetivo?: string;
}
