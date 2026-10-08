/** Qué ofrece el aviso de un error: repetir la petición o recargar la página. */
export type AccionAviso = 'reintentar' | 'recargar';

/** Aviso de un error de un reporte, ya decidido por su `codigo`. */
export interface AvisoError {
  mensaje: string;
  accion: AccionAviso;
  /** El reporte de una cuenta que ya no existe: la pestaña recarga las cuentas. */
  cuentaInexistente?: boolean;
}

/** Estado de la carga de un reporte. */
export type EstadoCarga<T> =
  { tipo: 'cargando' } | { tipo: 'listo'; datos: T } | { tipo: 'error'; aviso: AvisoError };
