import { ErrorLectorCsv, leerRegistros } from './lector-csv';

/** Tamaño máximo del archivo, igual que `importacion.max-bytes` del backend (2 MiB). */
export const MAXIMO_BYTES = 2 * 1024 * 1024;

/** Filas de datos máximas, igual que `importacion.max-filas` del backend. */
export const MAXIMO_FILAS = 5000;

/**
 * Lo que impide usar un archivo. El backend responde todo esto con `400 DATOS_INVALIDOS` sin mapa
 * `errores`, así que se previene aquí; los textos viven en `mensajes-importacion.ts`.
 */
export type ErrorArchivo =
  'TAMANO' | 'VACIO' | 'NO_UTF8' | 'DEMASIADAS_FILAS' | 'COMILLA_SIN_CERRAR';

/** El tamaño que ya se sabe sin leer el archivo: demasiado grande o de 0 bytes. */
export function validarTamano(tamano: number): 'TAMANO' | 'VACIO' | null {
  if (tamano === 0) {
    return 'VACIO';
  }
  return tamano > MAXIMO_BYTES ? 'TAMANO' : null;
}

/**
 * Decodifica como UTF-8 estricto, con o sin BOM (el BOM no forma parte del texto), igual que
 * `DecodificadorUtf8` del backend: un byte inválido no se reemplaza en silencio y un carácter NUL
 * (UTF-16 sin BOM decodifica como UTF-8 válido, lleno de NUL) tampoco se acepta. `null` si no es
 * UTF-8.
 */
export function decodificarUtf8(bytes: Uint8Array): string | null {
  try {
    const texto = new TextDecoder('utf-8', { fatal: true }).decode(bytes);
    return texto.includes('\u0000') ? null : texto;
  } catch {
    return null;
  }
}

/** El texto del archivo analizado con un separador y un encabezado. */
export interface AnalisisArchivo {
  /** Registros leídos, con el encabezado si lo hay; vacío si hay un error. */
  registros: string[][];
  /** Filas de datos (sin encabezado); `0` si hay un error. */
  filasDeDatos: number;
  /** Columnas detectadas: las del registro más ancho de las primeras filas. */
  columnas: number;
  error: ErrorArchivo | null;
}

/** Filas del archivo que se miran para contar columnas y mostrar. */
export const FILAS_DE_MUESTRA = 10;

/**
 * Lee el texto con el separador y el encabezado dados y cuenta las filas de datos con el mismo
 * criterio que el servidor (encabezado fuera, líneas vacías ignoradas). El tope es el del servidor:
 * `MAXIMO_FILAS` más el encabezado. Más estricto que el servidor a propósito: sin filas de datos
 * (archivo vacío, solo encabezado o solo BOM) es un error, porque no hay nada que importar.
 */
export function analizarTexto(
  texto: string,
  separador: string,
  tieneEncabezado: boolean,
): AnalisisArchivo {
  const vacio = (error: ErrorArchivo): AnalisisArchivo => ({
    registros: [],
    filasDeDatos: 0,
    columnas: 0,
    error,
  });
  let registros: string[][];
  try {
    registros = leerRegistros(texto, separador, MAXIMO_FILAS + (tieneEncabezado ? 1 : 0));
  } catch (e) {
    if (e instanceof ErrorLectorCsv) {
      return vacio(e.motivo === 'COMILLA_SIN_CERRAR' ? 'COMILLA_SIN_CERRAR' : 'DEMASIADAS_FILAS');
    }
    throw e;
  }
  const filasDeDatos = Math.max(0, registros.length - (tieneEncabezado ? 1 : 0));
  if (filasDeDatos === 0) {
    return vacio('VACIO');
  }
  const columnas = registros
    .slice(0, FILAS_DE_MUESTRA)
    .reduce((maximo, registro) => Math.max(maximo, registro.length), 0);
  return { registros, filasDeDatos, columnas, error: null };
}

/** Resultado de leer y decodificar un archivo elegido. */
export type CargaArchivo = { texto: string } | { error: ErrorArchivo };

/**
 * Valida el tamaño, lee el archivo completo (asíncrono: no bloquea el hilo mientras se lee) y lo
 * decodifica como UTF-8. Cede el hilo antes de decodificar para que se pinte `Leyendo el
 * archivo…`. Nada se guarda ni se envía.
 */
export async function cargarArchivo(archivo: File): Promise<CargaArchivo> {
  const errorDeTamano = validarTamano(archivo.size);
  if (errorDeTamano) {
    return { error: errorDeTamano };
  }
  const bytes = new Uint8Array(await archivo.arrayBuffer());
  await new Promise<void>((listo) => setTimeout(listo));
  const texto = decodificarUtf8(bytes);
  return texto === null ? { error: 'NO_UTF8' } : { texto };
}
