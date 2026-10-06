import { OperacionLote } from '../models/operacion-lote.model';
import { TransaccionResponse } from '../models/transaccion-response.model';

/*
 * Qué acciones admite cada transacción, en funciones puras. Replican las reglas del backend para
 * no ofrecer (ni enviar en lote) operaciones destinadas a fallar.
 */

export const MOTIVO_RECONCILIADA = 'Está reconciliada';
export const MOTIVO_TRANSFERENCIA = 'Es parte de una transferencia';

/** Si una acción se muestra, si se puede usar y, si no, por qué. */
export interface EstadoAccion {
  visible: boolean;
  habilitada: boolean;
  motivo: string | null;
}

export interface AccionesTransaccion {
  editar: EstadoAccion;
  duplicar: EstadoAccion;
  mover: EstadoAccion;
  aprobar: EstadoAccion;
  estado: EstadoAccion;
  borrar: EstadoAccion;
}

const disponible: EstadoAccion = { visible: true, habilitada: true, motivo: null };
const oculta: EstadoAccion = { visible: false, habilitada: false, motivo: null };
const bloqueada = (motivo: string): EstadoAccion => ({ visible: true, habilitada: false, motivo });

/** `true` si la transacción es una pata de transferencia. */
export function esTransferencia(transaccion: TransaccionResponse): boolean {
  return transaccion.transaccionParId !== null;
}

/** Acciones del menú de una transacción según su estado y el de su cuenta. */
export function accionesDe(
  transaccion: TransaccionResponse,
  cuentaCerrada: boolean,
): AccionesTransaccion {
  const reconciliada = transaccion.estado === 'RECONCILIADA';
  const pata = esTransferencia(transaccion);
  // Una pata solo admite aprobar y conciliar; una reconciliada no se edita, mueve, borra ni
  // cambia de estado.
  const restringida = pata
    ? bloqueada(MOTIVO_TRANSFERENCIA)
    : reconciliada
      ? bloqueada(MOTIVO_RECONCILIADA)
      : disponible;
  return {
    editar: cuentaCerrada ? oculta : restringida,
    duplicar: pata ? bloqueada(MOTIVO_TRANSFERENCIA) : disponible,
    mover: restringida,
    aprobar: transaccion.aprobada ? oculta : disponible,
    estado: reconciliada ? bloqueada(MOTIVO_RECONCILIADA) : disponible,
    borrar: restringida,
  };
}

/** Separa las transacciones a las que se les puede aplicar la operación en lote. */
export function filtrarLote(
  transacciones: readonly TransaccionResponse[],
  operacion: OperacionLote,
): { aplicables: TransaccionResponse[]; omitidas: TransaccionResponse[] } {
  const aplica = (t: TransaccionResponse): boolean => {
    switch (operacion) {
      case 'APROBAR':
        return true;
      case 'CATEGORIZAR':
        return (
          !esTransferencia(t) && t.subtransacciones.length === 0 && t.estado !== 'RECONCILIADA'
        );
      case 'BORRAR':
        return !esTransferencia(t) && t.estado !== 'RECONCILIADA';
    }
  };
  return {
    aplicables: transacciones.filter(aplica),
    omitidas: transacciones.filter((t) => !aplica(t)),
  };
}
