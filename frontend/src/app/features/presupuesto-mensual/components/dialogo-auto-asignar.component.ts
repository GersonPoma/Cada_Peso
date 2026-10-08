import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatOption } from '@angular/material/core';
import {
  MAT_DIALOG_DATA,
  MatDialogActions,
  MatDialogClose,
  MatDialogContent,
  MatDialogRef,
  MatDialogTitle,
} from '@angular/material/dialog';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatListOption, MatSelectionList } from '@angular/material/list';
import { MatRadioButton, MatRadioGroup } from '@angular/material/radio';
import { MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import {
  AutoAsignarRequest,
  AutoAsignarResponse,
  EstrategiaAutoAsignar,
} from '../models/auto-asignar.model';
import {
  DatosDialogoAutoAsignar,
  ResultadoDialogoAutoAsignar,
} from '../models/datos-dialogos-metas.model';
import { MetaService } from '../services/meta.service';

export const MENSAJE_SIN_CATEGORIAS = 'Elige al menos una categoría';
export const MENSAJE_DATOS_AUTO_ASIGNAR = 'Revisa la estrategia y las categorías elegidas';
export const MENSAJE_AUTO_ASIGNAR_INEXISTENTE =
  'Una de las categorías ya no existe. Actualizamos el mes.';
export const MENSAJE_SIN_CAMBIOS = 'Ninguna categoría cambia con esta estrategia';
export const MENSAJE_SIN_META = 'Las categorías sin meta no cambian';
export const AYUDA_TODAS = 'No incluye las categorías de pago de tarjeta.';

export const ESTRATEGIAS: readonly { valor: EstrategiaAutoAsignar; texto: string }[] = [
  { valor: 'FALTANTE_META', texto: 'Lo que falta para las metas' },
  { valor: 'ASIGNADO_MES_PASADO', texto: 'Lo asignado el mes pasado' },
  { valor: 'GASTADO_MES_PASADO', texto: 'Lo gastado el mes pasado' },
  { valor: 'PROMEDIO_ASIGNADO', texto: 'Promedio asignado (3 meses)' },
  { valor: 'PROMEDIO_GASTADO', texto: 'Promedio gastado (3 meses)' },
];

type Alcance = 'todas' | 'elegidas';

/** Con `elegidas`, al menos una categoría marcada. */
const conCategorias = (grupo: AbstractControl): ValidationErrors | null => {
  const alcance = grupo.get('alcance')?.value as Alcance;
  const elegidas = (grupo.get('elegidas')?.value as number[] | null) ?? [];
  return alcance === 'elegidas' && elegidas.length === 0 ? { sinCategorias: true } : null;
};

/**
 * Auto-asignar el mes con una estrategia. `Vista previa` pide el cálculo sin guardar
 * (`simular: true`); `Aplicar` envía el mismo cuerpo con `simular: false` y cierra con la cantidad
 * de categorías que cambiaron según la respuesta real. Cambiar la estrategia o el alcance
 * descarta la vista previa.
 */
@Component({
  selector: 'app-dialogo-auto-asignar',
  imports: [
    ReactiveFormsModule,
    MatButton,
    MatDialogActions,
    MatDialogClose,
    MatDialogContent,
    MatDialogTitle,
    MatFormField,
    MatLabel,
    MatListOption,
    MatOption,
    MatRadioButton,
    MatRadioGroup,
    MatSelect,
    MatSelectionList,
    MontoPipe,
  ],
  templateUrl: './dialogo-auto-asignar.component.html',
  styleUrl: './dialogo-auto-asignar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DialogoAutoAsignarComponent {
  protected readonly datos = inject<DatosDialogoAutoAsignar>(MAT_DIALOG_DATA);
  private readonly dialogRef =
    inject<MatDialogRef<DialogoAutoAsignarComponent, ResultadoDialogoAutoAsignar>>(MatDialogRef);
  private readonly servicio = inject(MetaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly moneda = this.presupuestoActivo.presupuesto()?.moneda ?? 'USD';
  protected readonly estrategias = ESTRATEGIAS;
  protected readonly ayudaTodas = AYUDA_TODAS;
  protected readonly mensajeSinCategorias = MENSAJE_SIN_CATEGORIAS;
  protected readonly mensajeSinCambios = MENSAJE_SIN_CAMBIOS;
  protected readonly mensajeSinMeta = MENSAJE_SIN_META;

  protected readonly formulario = new FormGroup(
    {
      estrategia: new FormControl<EstrategiaAutoAsignar | null>(null, Validators.required),
      alcance: new FormControl<Alcance>('todas', { nonNullable: true }),
      elegidas: new FormControl<number[]>([], { nonNullable: true }),
    },
    { validators: conCategorias },
  );

  private readonly valor = toSignal(this.formulario.valueChanges, {
    initialValue: this.formulario.getRawValue(),
  });

  protected readonly alcance = computed(() => this.valor().alcance ?? 'todas');
  /** Mismo criterio que el validador del grupo, para mostrar su texto. */
  protected readonly sinCategorias = computed(
    () => this.alcance() === 'elegidas' && (this.valor().elegidas ?? []).length === 0,
  );

  /** Con `Lo que falta para las metas`, alguna categoría del alcance no tiene meta. */
  protected readonly hayCategoriasSinMeta = computed(() => {
    const { estrategia, alcance, elegidas } = this.valor();
    if (estrategia !== 'FALTANTE_META') {
      return false;
    }
    const enAlcance =
      alcance === 'elegidas'
        ? this.datos.categorias.filter((c) => (elegidas ?? []).includes(c.categoriaId))
        : this.datos.categorias.filter((c) => !c.esPagoTarjeta);
    return enAlcance.some((c) => !c.tieneMeta);
  });

  protected readonly previa = signal<AutoAsignarResponse | null>(null);
  protected readonly enviando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);

  constructor() {
    // Lo mostrado en la vista previa ya no corresponde a lo elegido.
    this.formulario.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.previa.set(null);
      this.errorGeneral.set(null);
    });
  }

  protected vistaPrevia(): void {
    const solicitud = this.solicitud(true);
    if (!solicitud || this.enviando()) {
      return;
    }
    this.empezarEnvio();
    this.servicio.autoAsignar(this.presupuestoId(), this.datos.mes, solicitud).subscribe({
      next: (respuesta) => {
        this.terminarEnvio();
        this.previa.set(respuesta);
      },
      error: (error: unknown) => {
        this.terminarEnvio();
        this.mostrarError(error);
      },
    });
  }

  protected aplicar(): void {
    const solicitud = this.solicitud(false);
    if (!solicitud || this.enviando() || !this.previa()?.cambios.length) {
      return;
    }
    this.empezarEnvio();
    this.servicio.autoAsignar(this.presupuestoId(), this.datos.mes, solicitud).subscribe({
      next: (respuesta) =>
        this.dialogRef.close({ tipo: 'aplicado', cambios: respuesta.cambios.length }),
      error: (error: unknown) => {
        this.terminarEnvio();
        this.mostrarError(error);
      },
    });
  }

  /** El cuerpo de la petición; `categoriaIds` solo con `Elegir categorías`. */
  private solicitud(simular: boolean): AutoAsignarRequest | null {
    const { estrategia, alcance, elegidas } = this.formulario.getRawValue();
    if (this.formulario.invalid || estrategia === null) {
      return null;
    }
    return alcance === 'elegidas'
      ? { estrategia, categoriaIds: [...elegidas], simular }
      : { estrategia, simular };
  }

  private presupuestoId(): number {
    return this.presupuestoActivo.presupuesto()?.id ?? 0;
  }

  private empezarEnvio(): void {
    this.enviando.set(true);
    this.errorGeneral.set(null);
    this.dialogRef.disableClose = true;
  }

  private terminarEnvio(): void {
    this.enviando.set(false);
    this.dialogRef.disableClose = false;
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS) {
      const porCategorias = Object.keys(problema.errores ?? {}).some((campo) =>
        campo.startsWith('categoriaIds'),
      );
      this.errorGeneral.set(porCategorias ? MENSAJE_SIN_CATEGORIAS : MENSAJE_DATOS_AUTO_ASIGNAR);
      return;
    }
    if (problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO) {
      this.snackBar.open(MENSAJE_AUTO_ASIGNAR_INEXISTENTE, 'Cerrar', { duration: 6000 });
      this.dialogRef.close({ tipo: 'recargar' });
      return;
    }
    this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
  }
}
