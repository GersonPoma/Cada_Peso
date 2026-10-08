import { CuentaLectura } from './cuenta-lectura.model';
import { GrupoCategoriasLectura } from './grupo-categorias-lectura.model';
import { TransaccionProgramadaResponse } from './transaccion-programada-response.model';

/** Datos del diálogo de crear o editar: `original` es `null` al crear. */
export interface DatosDialogoProgramada {
  cuentas: CuentaLectura[];
  grupos: GrupoCategoriasLectura[];
  original: TransaccionProgramadaResponse | null;
}

/** Cómo se cerró el diálogo: guardó, o hay que recargar porque algo ya no existe. */
export type ResultadoDialogoProgramada = 'guardada' | 'recargar';
