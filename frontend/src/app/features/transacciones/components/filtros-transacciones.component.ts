import { ChangeDetectionStrategy, Component, computed, effect, input, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatOptgroup, MatOption } from '@angular/material/core';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { MatFormField, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatSelect } from '@angular/material/select';
import { debounceTime, distinctUntilChanged, merge } from 'rxjs';
import { aFechaNegocio, deFechaNegocio } from '../../../shared/fecha/fecha-negocio';
import { CuentaResumen } from '../models/cuenta-resumen.model';
import {
  ESTADOS_TRANSACCION,
  ETIQUETAS_ESTADO,
  EstadoTransaccion,
} from '../models/estado-transaccion.model';
import { FiltrosTransacciones } from '../models/filtros-transacciones.model';
import { GrupoCategoriasResumen } from '../models/grupo-categorias-resumen.model';

/** Espera tras la última tecla de la búsqueda antes de pedir la lista. */
export const ESPERA_BUSQUEDA_MS = 300;

/**
 * Panel de filtros de la lista: cuenta, categoría, rango de fechas, estado, "Solo sin aprobar" y
 * búsqueda (con espera). Recibe los filtros de la URL y emite los nuevos; la página decide.
 */
@Component({
  selector: 'app-filtros-transacciones',
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatCheckbox,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
    MatFormField,
    MatIcon,
    MatInput,
    MatLabel,
    MatOptgroup,
    MatOption,
    MatSelect,
    MatSuffix,
  ],
  templateUrl: './filtros-transacciones.component.html',
  styleUrl: './filtros-transacciones.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FiltrosTransaccionesComponent {
  readonly filtros = input.required<FiltrosTransacciones>();
  readonly cuentas = input<CuentaResumen[]>([]);
  readonly grupos = input<GrupoCategoriasResumen[]>([]);

  /** Filtros nuevos, siempre en la página 0. */
  readonly cambiar = output<FiltrosTransacciones>();
  readonly limpiar = output<void>();

  protected readonly estados = ESTADOS_TRANSACCION;
  protected readonly etiquetasEstado = ETIQUETAS_ESTADO;

  protected readonly formulario = new FormGroup({
    cuentaId: new FormControl<number | null>(null),
    categoriaId: new FormControl<number | null>(null),
    desde: new FormControl<Date | null>(null),
    hasta: new FormControl<Date | null>(null),
    estado: new FormControl<EstadoTransaccion | null>(null),
    soloSinAprobar: new FormControl(false, { nonNullable: true }),
    q: new FormControl('', { nonNullable: true }),
  });

  /** Cuentas agrupadas como en el resto de la app. */
  protected readonly gruposCuentas = computed(() => [
    { etiqueta: 'En el presupuesto', cuentas: this.cuentas().filter((c) => c.enPresupuesto) },
    { etiqueta: 'Seguimiento', cuentas: this.cuentas().filter((c) => !c.enPresupuesto) },
  ]);

  constructor() {
    // Los filtros de la URL mandan: el formulario los refleja sin volver a emitir.
    effect(() => {
      const filtros = this.filtros();
      this.formulario.setValue(
        {
          cuentaId: filtros.cuentaId,
          categoriaId: filtros.categoriaId,
          desde: deFechaNegocio(filtros.desde),
          hasta: deFechaNegocio(filtros.hasta),
          estado: filtros.estado,
          soloSinAprobar: filtros.soloSinAprobar,
          q: filtros.q ?? '',
        },
        { emitEvent: false },
      );
    });

    const { q, ...resto } = this.formulario.controls;
    merge(
      ...Object.values(resto).map((control) => control.valueChanges),
      q.valueChanges.pipe(debounceTime(ESPERA_BUSQUEDA_MS), distinctUntilChanged()),
    )
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.emitir());
  }

  private emitir(): void {
    const valor = this.formulario.getRawValue();
    // Una fecha a medio escribir (inválida) no filtra.
    const fecha = (d: Date | null) => (d && !isNaN(d.getTime()) ? aFechaNegocio(d) : null);
    this.cambiar.emit({
      ...this.filtros(),
      cuentaId: valor.cuentaId,
      categoriaId: valor.categoriaId,
      desde: fecha(valor.desde),
      hasta: fecha(valor.hasta),
      estado: valor.estado,
      soloSinAprobar: valor.soloSinAprobar,
      q: valor.q.trim() || null,
      pagina: 0,
    });
  }
}
