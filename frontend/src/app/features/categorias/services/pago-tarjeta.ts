import { CategoriaResponse } from '../models/categoria-response.model';
import { GrupoCategoriaResponse } from '../models/grupo-categoria-response.model';

/** El grupo de pagos de tarjetas de crédito: el backend lo crea y lo protege. */
export function esGrupoDePagos(grupo: Pick<GrupoCategoriaResponse, 'tipo'>): boolean {
  return grupo.tipo === 'PAGOS_TARJETA';
}

/** La categoría de pago de una tarjeta: el backend la crea, la renombra y la oculta sola. */
export function esCategoriaDePago(categoria: Pick<CategoriaResponse, 'esPagoTarjeta'>): boolean {
  return categoria.esPagoTarjeta === true;
}
