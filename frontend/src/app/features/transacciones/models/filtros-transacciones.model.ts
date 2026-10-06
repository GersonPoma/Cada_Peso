import { EstadoTransaccion } from './estado-transaccion.model';

/** Filtros y página de la lista, tal como viven en la URL. */
export interface FiltrosTransacciones {
  cuentaId: number | null;
  categoriaId: number | null;
  /** `yyyy-MM-dd`. */
  desde: string | null;
  /** `yyyy-MM-dd`. */
  hasta: string | null;
  estado: EstadoTransaccion | null;
  soloSinAprobar: boolean;
  q: string | null;
  pagina: number;
  tamano: number;
}
