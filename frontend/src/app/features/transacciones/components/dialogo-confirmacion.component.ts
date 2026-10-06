import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogTitle,
} from '@angular/material/dialog';
import { DatosDialogoConfirmacion } from '../models/datos-dialogos-transacciones.model';

/** Pregunta de confirmación; se cierra con `true` si se confirma. */
@Component({
  selector: 'app-dialogo-confirmacion',
  imports: [MatButton, MatDialogActions, MatDialogClose, MatDialogContent, MatDialogTitle],
  template: `
    <h2 mat-dialog-title>{{ datos.titulo }}</h2>
    <mat-dialog-content>
      <p>{{ datos.mensaje }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" [mat-dialog-close]="false">Cancelar</button>
      <button mat-flat-button type="button" [mat-dialog-close]="true">
        {{ datos.confirmar }}
      </button>
    </mat-dialog-actions>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoConfirmacionComponent {
  protected readonly datos = inject<DatosDialogoConfirmacion>(MAT_DIALOG_DATA);
}
