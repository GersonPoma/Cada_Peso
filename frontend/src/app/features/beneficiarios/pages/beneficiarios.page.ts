import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormField, MatLabel, MatPrefix } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import {
  MatCell,
  MatCellDef,
  MatColumnDef,
  MatHeaderCell,
  MatHeaderCellDef,
  MatHeaderRow,
  MatHeaderRowDef,
  MatRow,
  MatRowDef,
  MatTable,
} from '@angular/material/table';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EMPTY, catchError, debounceTime, filter, forkJoin, map, switchMap, tap } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO, leerProblemaApi } from '../../../shared/api/problema-api';
import { DialogoBeneficiarioComponent } from '../components/dialogo-beneficiario.component';
import { BeneficiarioResponse } from '../models/beneficiario-response.model';
import {
  DatosDialogoBeneficiario,
  ResultadoDialogoBeneficiario,
} from '../models/datos-dialogo-beneficiario.model';
import { GrupoCategoriasLectura } from '../models/grupo-categorias-lectura.model';
import { BeneficiarioService } from '../services/beneficiario.service';
import { CategoriaLecturaService } from '../services/categoria-lectura.service';

type Estado = 'cargando' | 'listo' | 'error';

export const ESPERA_FILTRO_MS = 250;

/**
 * Beneficiarios del presupuesto activo con su categoría predeterminada. El buscador filtra la
 * lista ya cargada por prefijo, sin pedir al servidor. Permite crear y editar (no borrar); tras
 * guardar vuelve a pedir la lista.
 */
@Component({
  selector: 'app-beneficiarios',
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatCell,
    MatCellDef,
    MatColumnDef,
    MatFormField,
    MatHeaderCell,
    MatHeaderCellDef,
    MatHeaderRow,
    MatHeaderRowDef,
    MatIcon,
    MatIconButton,
    MatInput,
    MatLabel,
    MatPrefix,
    MatProgressSpinner,
    MatRow,
    MatRowDef,
    MatTable,
  ],
  templateUrl: './beneficiarios.page.html',
  styleUrl: './beneficiarios.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BeneficiariosPage {
  private readonly servicio = inject(BeneficiarioService);
  private readonly categorias = inject(CategoriaLecturaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly columnas = ['nombre', 'categoria', 'acciones'];
  protected readonly estado = signal<Estado>('cargando');
  private readonly recargas = signal(0);
  private readonly beneficiarios = signal<BeneficiarioResponse[]>([]);
  private readonly grupos = signal<GrupoCategoriasLectura[]>([]);

  protected readonly busqueda = new FormControl('', { nonNullable: true });
  /** Texto del buscador, recortado y en minúsculas, 250 ms después de la última tecla. */
  private readonly filtro = toSignal(
    this.busqueda.valueChanges.pipe(
      debounceTime(ESPERA_FILTRO_MS),
      map((texto) => texto.trim().toLocaleLowerCase()),
    ),
    { initialValue: '' },
  );

  private readonly nombresCategoria = computed(
    () => new Map(this.grupos().flatMap((g) => g.categorias.map((c) => [c.id, c.nombre] as const))),
  );

  protected readonly sinBeneficiarios = computed(() => this.beneficiarios().length === 0);
  protected readonly filtrados = computed(() => {
    const filtro = this.filtro();
    return this.beneficiarios().filter((b) => b.nombre.toLocaleLowerCase().startsWith(filtro));
  });

  constructor() {
    const peticion = computed(() => ({
      presupuestoId: this.presupuestoActivo.presupuesto()?.id ?? null,
      recargas: this.recargas(),
    }));
    toObservable(peticion)
      .pipe(
        filter(({ presupuestoId }) => presupuestoId !== null),
        tap(() => this.estado.set('cargando')),
        switchMap(({ presupuestoId }) =>
          forkJoin([
            this.servicio.listar(presupuestoId as number),
            this.categorias.arbol(presupuestoId as number),
          ]).pipe(
            catchError((error: unknown) => {
              this.alFallarCarga(error);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(([beneficiarios, grupos]) => {
        this.beneficiarios.set(beneficiarios);
        this.grupos.set(grupos);
        this.estado.set('listo');
      });
  }

  /** Nombre de la categoría predeterminada, o `—` si no tiene o ya no existe. */
  protected categoria(beneficiario: BeneficiarioResponse): string {
    const id = beneficiario.categoriaPredeterminadaId;
    return (id !== null && this.nombresCategoria().get(id)) || '—';
  }

  protected recargar(): void {
    this.recargas.update((v) => v + 1);
  }

  protected agregar(): void {
    this.abrirDialogo(null);
  }

  protected editar(beneficiario: BeneficiarioResponse): void {
    this.abrirDialogo(beneficiario);
  }

  private abrirDialogo(beneficiario: BeneficiarioResponse | null): void {
    this.dialog
      .open<DialogoBeneficiarioComponent, DatosDialogoBeneficiario, ResultadoDialogoBeneficiario>(
        DialogoBeneficiarioComponent,
        { data: { beneficiario, grupos: this.grupos() }, width: '440px' },
      )
      .afterClosed()
      .subscribe((resultado) => {
        if (resultado) {
          this.recargar();
        }
      });
  }

  private alFallarCarga(error: unknown): void {
    // Con 401 el interceptor ya cerró la sesión y redirigió: no hay nada que avisar.
    if (leerProblemaApi(error)?.status === 401) {
      return;
    }
    this.estado.set('error');
    this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
  }
}
