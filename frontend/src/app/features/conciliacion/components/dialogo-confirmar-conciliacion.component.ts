import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { FechaPipe } from '../../../shared/formato/fecha.pipe';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { DatosDialogoConfirmarConciliacion } from '../models/datos-dialogo-confirmar-conciliacion.model';

export const AVISO_IRREVERSIBLE =
  'Las transacciones conciliadas hasta esta fecha quedarán reconciliadas: ya no se podrán ' +
  'editar, mover, borrar ni cambiar de estado. Esto no se puede deshacer.';

/**
 * Confirmación antes de reconciliar: resumen (fecha, saldo y ajuste) y aviso de que no se puede
 * deshacer. El foco empieza en `Cancelar`; se cierra con `true` solo si se confirma.
 */
@Component({
  selector: 'app-dialogo-confirmar-conciliacion',
  imports: [
    FechaPipe,
    MatButton,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MontoPipe,
  ],
  templateUrl: './dialogo-confirmar-conciliacion.component.html',
  styleUrl: './dialogo-confirmar-conciliacion.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoConfirmarConciliacionComponent {
  protected readonly datos = inject<DatosDialogoConfirmarConciliacion>(MAT_DIALOG_DATA);
  protected readonly aviso = AVISO_IRREVERSIBLE;
}
