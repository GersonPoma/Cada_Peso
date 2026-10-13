/* Textos fijos de Inicio. Nada viene del backend: se eligen por las cifras. */

export const MENSAJE_ERROR_TARJETA = 'No pudimos cargar esta información.';

export const AVISO_LISTO_NEGATIVO = 'Asignaste más de lo que tienes';
export const TEXTO_TODO_ASIGNADO = 'Todo el dinero está asignado';
export const AVISO_NETO_NEGATIVO = 'Gastaste más de lo que ingresó';

export const AVISO_SIN_CUENTAS = 'Aún no tienes cuentas';
export const TEXTO_SIN_MOVIMIENTOS = 'Sin movimientos este mes';
export const TEXTO_SIN_METAS = 'Aún no tienes metas';
export const TEXTO_TODO_APROBADO = 'Todo aprobado';

export function textoSobregastadas(cantidad: number): string {
  if (cantidad === 0) {
    return 'Ninguna categoría sobregastada';
  }
  return cantidad === 1 ? '1 categoría sobregastada' : `${cantidad} categorías sobregastadas`;
}

export function textoSinAprobar(cantidad: number): string {
  if (cantidad === 0) {
    return TEXTO_TODO_APROBADO;
  }
  return cantidad === 1 ? '1 transacción sin aprobar' : `${cantidad} transacciones sin aprobar`;
}

/** `totalMetas` distingue "ninguna pendiente" de "no hay metas". */
export function textoMetasQueFaltan(cantidad: number, totalMetas: number): string {
  if (totalMetas === 0) {
    return TEXTO_SIN_METAS;
  }
  if (cantidad === 0) {
    return 'Ninguna meta pendiente';
  }
  return cantidad === 1 ? '1 meta por cumplir este mes' : `${cantidad} metas por cumplir este mes`;
}
