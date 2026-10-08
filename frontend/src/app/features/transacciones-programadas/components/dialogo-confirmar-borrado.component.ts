import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { AVISO_BORRADO } from '../services/mensajes-programada';

/** Lo que muestra la confirmación: el nombre con que la persona reconoce la programada. */
export interface DatosConfirmarBorrado {
  nombre: string;
}

/**
 * Confirmación antes de borrar una programada: aclara que las transacciones que ya generó se
 * conservan. El foco empieza en `Cancelar`; se cierra con `true` solo si se confirma.
 */
@Component({
  selector: 'app-dialogo-confirmar-borrado',
  imports: [MatButton, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle],
  template: `
    <h2 mat-dialog-title>Borrar programada</h2>
    <mat-dialog-content>
      <p>¿Borrar la programada «{{ datos.nombre }}»? Dejará de generar transacciones.</p>
      <p>{{ aviso }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false" cdkFocusInitial>Cancelar</button>
      <button mat-flat-button type="button" [mat-dialog-close]="true">Borrar</button>
    </mat-dialog-actions>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoConfirmarBorradoComponent {
  protected readonly datos = inject<DatosConfirmarBorrado>(MAT_DIALOG_DATA);
  protected readonly aviso = AVISO_BORRADO;
}
