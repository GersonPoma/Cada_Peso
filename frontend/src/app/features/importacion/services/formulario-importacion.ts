import { MapeoImportacion } from '../models/mapeo-importacion.model';

/** Un mapeo al que le falta una columna que el servidor exige: no debería llegar a enviarse. */
export class MapeoIncompletoError extends Error {
  constructor(campo: string) {
    super(`Falta ${campo}`);
    this.name = 'MapeoIncompletoError';
  }
}

function indice(campo: string, valor: number | null): string {
  if (valor === null) {
    throw new MapeoIncompletoError(campo);
  }
  return String(valor);
}

/**
 * Arma el cuerpo `multipart/form-data` de la vista previa o de la importación: la parte `archivo`
 * y el mapeo como campos de texto. `separador`, `separadorDecimal` y `separadorMiles` van por el
 * NOMBRE del enum; `formatoFecha`, por su ETIQUETA (`dd/MM/yyyy`). El monto va en `columnaMonto`
 * o en `columnaDebito` y `columnaCredito`, nunca en las tres. `columnaMemo` solo si se eligió y
 * `omitirInvalidas` solo cuando se pasa (al importar). Los índices ya son los del servidor
 * (desde 0). El `Content-Type` NO se fija a mano: el navegador agrega el separador (boundary).
 */
export function armarFormulario(
  archivo: File,
  mapeo: MapeoImportacion,
  omitirInvalidas?: boolean,
): FormData {
  const formulario = new FormData();
  formulario.append('archivo', archivo, archivo.name);
  formulario.append('separador', mapeo.separador);
  formulario.append('tieneEncabezado', String(mapeo.tieneEncabezado));
  formulario.append('columnaFecha', indice('la columna de la fecha', mapeo.columnaFecha));
  formulario.append('formatoFecha', mapeo.formatoFecha);
  if (mapeo.modoMonto === 'UNA_COLUMNA') {
    formulario.append('columnaMonto', indice('la columna del monto', mapeo.columnaMonto));
  } else {
    formulario.append('columnaDebito', indice('la columna de débito', mapeo.columnaDebito));
    formulario.append('columnaCredito', indice('la columna de crédito', mapeo.columnaCredito));
  }
  formulario.append('separadorDecimal', mapeo.separadorDecimal);
  formulario.append('separadorMiles', mapeo.separadorMiles);
  formulario.append(
    'columnaDescripcion',
    indice('la columna de la descripción', mapeo.columnaDescripcion),
  );
  if (mapeo.columnaMemo !== null) {
    formulario.append('columnaMemo', String(mapeo.columnaMemo));
  }
  if (omitirInvalidas !== undefined) {
    formulario.append('omitirInvalidas', String(omitirInvalidas));
  }
  return formulario;
}
