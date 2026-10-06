import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatOption, MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { CategoriaResponse } from '../models/categoria-response.model';
import { DatosDialogoMoverCategoria } from '../models/datos-dialogos-categorias.model';
import { CategoriaService } from '../services/categoria.service';
import { posicionCategoria } from '../services/reordenamiento';

/** Texto del 409 `CATEGORIA_YA_EXISTE` al mover una categoría a otro grupo. */
export const MENSAJE_CATEGORIA_REPETIDA_DESTINO =
  'Ya hay una categoría con ese nombre en el grupo destino';

/** Un lugar elegible: su texto y el índice visible con la semántica del arrastre del CDK. */
interface Lugar {
  etiqueta: string;
  indice: number;
}

/**
 * "Mover a...": elige el grupo destino y el lugar dentro de él sin arrastrar (teclado y pantallas
 * táctiles). Calcula la posición del backend con `posicionCategoria`, igual que el arrastre, y
 * solo se cierra si el movimiento sale bien (o no cambia nada).
 */
@Component({
  selector: 'app-dialogo-mover-categoria',
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatFormField,
    MatLabel,
    MatOption,
    MatSelect,
  ],
  templateUrl: './dialogo-mover-categoria.component.html',
  styleUrl: './dialogo-mover-categoria.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoMoverCategoriaComponent {
  private readonly datos = inject<DatosDialogoMoverCategoria>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<DialogoMoverCategoriaComponent, CategoriaResponse>>(MatDialogRef);
  private readonly categorias = inject(CategoriaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly categoria = this.datos.categoria;
  protected readonly grupos = this.datos.arbol;

  protected readonly formulario = new FormGroup({
    grupoId: new FormControl(this.categoria.grupoId, { nonNullable: true }),
    indice: new FormControl(0, { nonNullable: true }),
  });

  private readonly grupoElegido = toSignal(this.formulario.controls.grupoId.valueChanges, {
    initialValue: this.formulario.controls.grupoId.value,
  });

  /** "Al principio" y "Después de ..." entre las categorías visibles del destino, sin la propia. */
  protected readonly lugares = computed<Lugar[]>(() => {
    const destino = this.grupos.find((grupo) => grupo.id === this.grupoElegido());
    const otras = (destino?.categorias ?? []).filter((c) => c.id !== this.categoria.id);
    return [
      { etiqueta: 'Al principio', indice: 0 },
      ...otras.map((c, j) => ({ etiqueta: `Después de ${c.nombre}`, indice: j + 1 })),
    ];
  });

  protected readonly enviando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  constructor() {
    // Cada grupo tiene sus propios lugares: al cambiar de grupo se vuelve al principio.
    this.formulario.controls.grupoId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.formulario.controls.indice.setValue(0));
  }

  protected enviar(): void {
    if (this.enviando()) {
      return;
    }
    const { grupoId, indice } = this.formulario.getRawValue();
    const posicion = posicionCategoria(this.grupos, this.categoria.id, grupoId, indice);
    if (posicion === null) {
      this.dialogRef.close();
      return;
    }
    this.enviando.set(true);
    this.errorGeneral.set(null);
    this.dialogRef.disableClose = true;
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id ?? 0;

    this.categorias
      .moverCategoria(presupuestoId, this.categoria.id, { grupoId, posicion })
      .subscribe({
        next: (movida) => this.dialogRef.close(movida),
        error: (error: unknown) => {
          this.enviando.set(false);
          this.dialogRef.disableClose = false;
          this.mostrarError(error);
        },
      });
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.CATEGORIA_YA_EXISTE) {
      this.errorGeneral.set(MENSAJE_CATEGORIA_REPETIDA_DESTINO);
      return;
    }
    this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
  }
}
