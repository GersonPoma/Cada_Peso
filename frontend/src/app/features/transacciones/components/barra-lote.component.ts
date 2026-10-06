import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatOptgroup, MatOption } from '@angular/material/core';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatSelect } from '@angular/material/select';
import { GrupoCategoriasResumen } from '../models/grupo-categorias-resumen.model';

/**
 * Barra de acciones en lote sobre las filas seleccionadas: Aprobar, Categorizar (con su
 * categoría) y Borrar. Solo emite; la página filtra las filas no aplicables y confirma.
 */
@Component({
  selector: 'app-barra-lote',
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatFormField,
    MatIcon,
    MatLabel,
    MatOptgroup,
    MatOption,
    MatSelect,
  ],
  templateUrl: './barra-lote.component.html',
  styleUrl: './barra-lote.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BarraLoteComponent {
  readonly cantidad = input.required<number>();
  readonly grupos = input<GrupoCategoriasResumen[]>([]);

  readonly aprobar = output<void>();
  readonly categorizar = output<number>();
  readonly borrar = output<void>();
  readonly limpiar = output<void>();

  protected readonly categoria = new FormControl<number | null>(null);

  protected pedirCategorizar(): void {
    const categoriaId = this.categoria.value;
    if (categoriaId !== null) {
      this.categorizar.emit(categoriaId);
    }
  }
}
