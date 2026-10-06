import { CuentaResumen } from './cuenta-resumen.model';
import { GrupoCategoriasResumen } from './grupo-categorias-resumen.model';
import { TransaccionResponse } from './transaccion-response.model';

/** Datos del diálogo de transacción: crear o editar, con las listas para elegir. */
export interface DatosDialogoTransaccion {
  transaccion: TransaccionResponse | null;
  cuentas: CuentaResumen[];
  grupos: GrupoCategoriasResumen[];
}

/** Cómo terminó el diálogo de transacción. */
export type ResultadoDialogoTransaccion = { tipo: 'guardada' } | { tipo: 'recargar' };

/** Datos del diálogo de mover a otra cuenta. */
export interface DatosDialogoMoverCuenta {
  transaccion: TransaccionResponse;
  cuentas: CuentaResumen[];
}

/** Datos del diálogo de confirmación. */
export interface DatosDialogoConfirmacion {
  titulo: string;
  mensaje: string;
  confirmar: string;
}
