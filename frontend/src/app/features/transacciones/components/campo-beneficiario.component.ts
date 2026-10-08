import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import {
  MatAutocomplete,
  MatAutocompleteSelectedEvent,
  MatAutocompleteTrigger,
} from '@angular/material/autocomplete';
import { MatOption } from '@angular/material/core';
import { MatError, MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatInput } from '@angular/material/input';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { mensajeDeError } from '../../../shared/formulario/errores-formulario';
import { BeneficiarioSugerido } from '../models/beneficiario-sugerido.model';
import { GrupoCategoriasResumen } from '../models/grupo-categorias-resumen.model';
import { BeneficiarioLecturaService } from '../services/beneficiario-lectura.service';

export const MAXIMO_BENEFICIARIO = 100;
export const ESPERA_SUGERENCIAS_MS = 250;
export const PISTA_BENEFICIARIO_NUEVO = 'Se creará un beneficiario nuevo';

const MENSAJES = {
  maxlength: `El beneficiario no puede superar los ${MAXIMO_BENEFICIARIO} caracteres`,
};

/** Sugerencias recibidas para un texto buscado. */
interface Resultado {
  q: string;
  lista: BeneficiarioSugerido[];
}

/**
 * Campo de beneficiario con autocompletado por prefijo. Acepta texto libre: elegir una sugerencia
 * solo rellena el texto (el backend vincula o crea el beneficiario al guardar). Pide sugerencias
 * 250 ms después de la última tecla; una respuesta vieja nunca llega (`switchMap`) y un error deja
 * la lista vacía sin avisar.
 */
@Component({
  selector: 'app-campo-beneficiario',
  imports: [
    ReactiveFormsModule,
    MatAutocomplete,
    MatAutocompleteTrigger,
    MatError,
    MatFormField,
    MatHint,
    MatInput,
    MatLabel,
    MatOption,
  ],
  templateUrl: './campo-beneficiario.component.html',
  styleUrl: './campo-beneficiario.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CampoBeneficiarioComponent {
  private readonly lectura = inject(BeneficiarioLecturaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);

  /** Texto del beneficiario; sus validadores (máximo de caracteres) los pone quien lo usa. */
  readonly control = input.required<FormControl<string>>();
  /** Árbol completo de categorías, para nombrar la categoría predeterminada de cada opción. */
  readonly grupos = input<GrupoCategoriasResumen[]>([]);
  readonly elegido = output<BeneficiarioSugerido>();

  protected readonly maximo = MAXIMO_BENEFICIARIO;
  protected readonly pistaNuevo = PISTA_BENEFICIARIO_NUEVO;

  private readonly control$ = toObservable(this.control);

  /** Texto actual del control (con sus cambios de estado, para refrescar el error). */
  private readonly estado = toSignal(
    this.control$.pipe(
      switchMap((control) =>
        control.events.pipe(
          startWith(null),
          map(() => ({ texto: control.value, errores: control.errors })),
        ),
      ),
    ),
    { initialValue: { texto: '', errores: null } },
  );

  private readonly resultado = toSignal(
    this.control$.pipe(
      switchMap((control) => control.valueChanges.pipe(startWith(control.value))),
      map((texto) => texto.trim()),
      debounceTime(ESPERA_SUGERENCIAS_MS),
      distinctUntilChanged(),
      switchMap((q) => {
        const presupuestoId = this.presupuestoActivo.presupuesto()?.id;
        if (!q || presupuestoId === undefined) {
          return of<Resultado>({ q, lista: [] });
        }
        return this.lectura.buscar(presupuestoId, q).pipe(
          map((lista): Resultado => ({ q, lista })),
          catchError(() => of<Resultado>({ q, lista: [] })),
        );
      }),
    ),
    { initialValue: { q: '', lista: [] } as Resultado },
  );

  private readonly nombresCategoria = computed(() => {
    const nombres = new Map<number, string>();
    for (const grupo of this.grupos()) {
      for (const categoria of grupo.categorias) {
        nombres.set(categoria.id, categoria.nombre);
      }
    }
    return nombres;
  });

  /** Cada sugerencia partida en el prefijo buscado (resaltado) y el resto del nombre. */
  protected readonly opciones = computed(() => {
    const { q, lista } = this.resultado();
    const nombres = this.nombresCategoria();
    return lista.map((sugerencia) => ({
      sugerencia,
      coincide: sugerencia.nombre.slice(0, q.length),
      resto: sugerencia.nombre.slice(q.length),
      categoria:
        sugerencia.categoriaPredeterminadaId === null
          ? null
          : (nombres.get(sugerencia.categoriaPredeterminadaId) ?? null),
    }));
  });

  protected readonly largo = computed(() => this.estado().texto.trim().length);

  /** El texto no coincide (sin mayúsculas) con ninguna sugerencia recibida. */
  protected readonly esNuevo = computed(() => {
    const texto = this.estado().texto.trim().toLocaleLowerCase();
    return (
      texto !== '' && !this.resultado().lista.some((s) => s.nombre.toLocaleLowerCase() === texto)
    );
  });

  protected readonly mensaje = computed(() => mensajeDeError(this.estado().errores, MENSAJES));

  protected elegir(evento: MatAutocompleteSelectedEvent): void {
    const sugerencia = this.resultado().lista.find((s) => s.nombre === evento.option.value);
    if (sugerencia) {
      this.elegido.emit(sugerencia);
    }
  }
}
