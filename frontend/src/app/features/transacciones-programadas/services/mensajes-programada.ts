/** Textos de la pantalla de transacciones programadas. */

export const MENSAJE_SIN_PROGRAMADAS = 'No hay transacciones programadas';
export const MENSAJE_ERROR_CARGA = 'No pudimos cargar tus transacciones programadas.';

export const MENSAJE_REGLA_PROGRAMADA =
  'No se pudo guardar: la cuenta está cerrada o la categoría no admite transacciones.';
export const MENSAJE_FECHAS_MONTO = 'Revisa las fechas y el monto';
export const MENSAJE_REFERENCIA_INEXISTENTE =
  'La programada, la cuenta o la categoría ya no existe. Actualizamos los datos.';

export const MENSAJE_REANUDADA = 'Reanudada. No se generan las ocurrencias del período pausado.';
export const MENSAJE_PAUSADA = 'Pausada. No se generará hasta que la reanudes.';
export const MENSAJE_BORRADA = 'Programada borrada. Sus transacciones generadas se conservan.';
export const AVISO_BORRADO = 'Las transacciones que ya generó se conservan.';

export const PREFIJO_ULTIMO_ERROR = 'La última generación falló:';
export const SUGERENCIA_ULTIMO_ERROR = 'Revisa la cuenta y la categoría y pulsa Generar ahora';

export const AVISO_FIN_DE_MES = 'En los meses sin ese día se usa el último día del mes';
export const NOTA_CREAR =
  'Crearla no genera transacciones: las ocurrencias vencidas se crean en la siguiente ' +
  'generación o con Generar ahora, y nacen No conciliada y sin aprobar.';
export const NOTA_EDITAR = 'Los cambios solo afectan a las ocurrencias futuras.';
export const NOTA_SOLO_LECTURA = 'Para cambiarlas, crea otra programada';
