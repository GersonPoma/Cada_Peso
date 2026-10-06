import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatOptgroup, MatOption } from '@angular/material/core';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { DatosDialogoMoverCuenta } from '../models/datos-dialogos-transacciones.model';
import { TransaccionResponse } from '../models/transaccion-response.model';
import { TransaccionService } from '../services/transaccion.service';

export const MENSAJE_NO_SE_PUEDE_MOVER =
  'No se pudo mover: la cuenta destino está cerrada o la transacción está reconciliada.';

/** Mover una transacción a otra cuenta abierta. Hace la petición y se cierra con el resultado. */
@Component({
  selector: 'app-dialogo-mover-cuenta',
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatFormField,
    MatLabel,
    MatOptgroup,
    MatOption,
    MatSelect,
  ],
  templateUrl: './dialogo-mover-cuenta.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoMoverCuentaComponent {
  private readonly datos = inject<DatosDialogoMoverCuenta>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<DialogoMoverCuentaComponent, TransaccionResponse>>(MatDialogRef);
  private readonly servicio = inject(TransaccionService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly snackBar = inject(MatSnackBar);

  /** Cuentas abiertas, sin la actual, agrupadas. */
  protected readonly grupos = (() => {
    const cuentas = this.datos.cuentas.filter(
      (c) => !c.cerrada && c.id !== this.datos.transaccion.cuentaId,
    );
    return [
      { etiqueta: 'En el presupuesto', cuentas: cuentas.filter((c) => c.enPresupuesto) },
      { etiqueta: 'Seguimiento', cuentas: cuentas.filter((c) => !c.enPresupuesto) },
    ].filter((grupo) => grupo.cuentas.length > 0);
  })();

  protected readonly destino = new FormControl<number | null>(null, Validators.required);
  protected readonly enviando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  protected enviar(): void {
    const cuentaId = this.destino.value;
    if (cuentaId === null || this.enviando()) {
      return;
    }
    this.enviando.set(true);
    this.errorGeneral.set(null);
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id ?? 0;
    this.servicio.moverCuenta(presupuestoId, this.datos.transaccion.id, cuentaId).subscribe({
      next: (movida) => this.dialogRef.close(movida),
      error: (error: unknown) => {
        this.enviando.set(false);
        const problema = leerProblemaApi(error);
        if (problema?.status === 401) {
          return;
        }
        if (problema?.codigo === CODIGOS_API.REGLA_NEGOCIO_VIOLADA) {
          this.errorGeneral.set(MENSAJE_NO_SE_PUEDE_MOVER);
          return;
        }
        this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
      },
    });
  }
}
