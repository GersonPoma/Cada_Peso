/** Por qué el lector no pudo leer el archivo. */
export type MotivoErrorLector = 'COMILLA_SIN_CERRAR' | 'DEMASIADOS_REGISTROS';

/** El archivo no se puede leer como CSV (el servidor lo respondería con un `400`). */
export class ErrorLectorCsv extends Error {
  constructor(readonly motivo: MotivoErrorLector) {
    super(motivo);
    this.name = 'ErrorLectorCsv';
  }
}

const COMILLA = 34;
const LF = 10;
const CR = 13;

/**
 * Lector de CSV mínimo: port de `LectorCsv.leer` del backend (commit 7c9ed9d), con la misma máquina
 * de estados para que el cliente cuente igual que el servidor:
 * - una comilla solo abre un campo si el campo está vacío y no fue ya comillado; en otro lugar es
 *   un carácter más (`ab"cd` queda igual) y el texto tras una comilla de cierre se concatena
 *   (`"ab"cd` da `abcd`);
 * - dentro de comillas, `""` es una comilla y los separadores y saltos son parte del campo;
 * - `LF`, `CRLF` y `CR` terminan el registro, y las líneas completamente vacías se ignoran;
 * - una comilla sin cerrar es un error;
 * - se corta en cuanto hay más de `maxRegistros` registros (el encabezado cuenta como registro);
 *   con `soloPrimeros` no es un error: devuelve los primeros `maxRegistros` y deja de leer (para
 *   mostrar una muestra de un archivo grande).
 *
 * Los campos se acumulan por tramos (`slice`) y no carácter a carácter: en archivos de 2 MiB es
 * mucho más rápido y no cambia el resultado.
 */
export function leerRegistros(
  texto: string,
  separador: string,
  maxRegistros = Number.POSITIVE_INFINITY,
  soloPrimeros = false,
): string[][] {
  const codigoSeparador = separador.charCodeAt(0);
  const registros: string[][] = [];
  let campos: string[] = [];
  let campo = '';
  let entreComillas = false;
  let campoComillado = false;
  let conContenido = false;
  const n = texto.length;

  const agregar = (): void => {
    registros.push(campos);
    if (!soloPrimeros && registros.length > maxRegistros) {
      throw new ErrorLectorCsv('DEMASIADOS_REGISTROS');
    }
  };

  for (let i = 0; i < n; i++) {
    const c = texto.charCodeAt(i);
    if (entreComillas) {
      if (c === COMILLA) {
        if (i + 1 < n && texto.charCodeAt(i + 1) === COMILLA) {
          campo += '"';
          i++;
        } else {
          entreComillas = false;
        }
      } else {
        let j = i + 1;
        while (j < n && texto.charCodeAt(j) !== COMILLA) {
          j++;
        }
        campo += texto.slice(i, j);
        i = j - 1;
      }
    } else if (c === COMILLA && campo.length === 0 && !campoComillado) {
      entreComillas = true;
      campoComillado = true;
      conContenido = true;
    } else if (c === codigoSeparador) {
      campos.push(campo);
      campo = '';
      campoComillado = false;
      conContenido = true;
    } else if (c === LF || c === CR) {
      if (c === CR && i + 1 < n && texto.charCodeAt(i + 1) === LF) {
        i++;
      }
      if (conContenido) {
        campos.push(campo);
        agregar();
        campos = [];
        if (soloPrimeros && registros.length >= maxRegistros) {
          return registros;
        }
      }
      campo = '';
      campoComillado = false;
      conContenido = false;
    } else {
      let j = i + 1;
      while (j < n) {
        const d = texto.charCodeAt(j);
        if (d === codigoSeparador || d === LF || d === CR || d === COMILLA) {
          break;
        }
        j++;
      }
      campo += texto.slice(i, j);
      i = j - 1;
      conContenido = true;
    }
  }
  if (entreComillas) {
    throw new ErrorLectorCsv('COMILLA_SIN_CERRAR');
  }
  if (conContenido) {
    campos.push(campo);
    agregar();
  }
  return registros;
}
