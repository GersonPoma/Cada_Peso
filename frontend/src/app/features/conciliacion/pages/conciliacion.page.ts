import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButton } from '@angular/material/button';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatOptgroup, MatOption } from '@angular/material/core';
import {
  MatDatepicker,
  MatDatepickerInput,
  MatDatepickerToggle,
} from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatError, MatFormField, MatHint, MatLabel, MatSuffix } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatInput } from '@angular/material/input';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSelect } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  EMPTY,
  Observable,
  Subject,
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  forkJoin,
  map,
  merge,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { CampoMontoComponent } from '../../../shared/calculadora/campo-monto.component';
import { aFechaNegocio } from '../../../shared/fecha/fecha-negocio';
import { FechaPipe } from '../../../shared/formato/fecha.pipe';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import {
  MensajesDeError,
  aplicarErroresDeCampos,
  mensajeDeError,
} from '../../../shared/formulario/errores-formulario';
import { DialogoConfirmarConciliacionComponent } from '../components/dialogo-confirmar-conciliacion.component';
import {
  EstadoHistorial,
  HistorialConciliacionesComponent,
} from '../components/historial-conciliaciones.component';
import { ConciliacionResponse } from '../models/conciliacion-response.model';
import { CrearConciliacionRequest } from '../models/crear-conciliacion-request.model';
import { CuentaConciliacion, ETIQUETAS_TIPO_CUENTA } from '../models/cuenta-conciliacion.model';
import { DatosDialogoConfirmarConciliacion } from '../models/datos-dialogo-confirmar-conciliacion.model';
import { EstadoConciliacionResponse } from '../models/estado-conciliacion-response.model';
import { GrupoCategoriasLectura } from '../models/grupo-categorias-lectura.model';
import { CategoriaLecturaService } from '../services/categoria-lectura.service';
import { ConciliacionService } from '../services/conciliacion.service';
import { CuentaLecturaService } from '../services/cuenta-lectura.service';
import {
  hoy,
  noConciliadasHasta,
  noFutura,
  textoDiferencia,
} from '../services/presentacion-conciliacion';
import { ReglaCategoriaAjuste, reglaCategoriaAjuste } from '../services/regla-categoria-ajuste';

/** Espera tras el último cambio del saldo o la fecha antes de pedir el estado. */
export const ESPERA_ESTADO_MS = 300;

export const MENSAJE_REGLA_CONCILIACION =
  'No se pudo reconciliar: la cuenta está cerrada, falta el ajuste o su categoría no corresponde.';
export const MENSAJE_CUENTA_INEXISTENTE = 'La cuenta ya no existe.';
export const MENSAJE_REFERENCIA_INEXISTENTE =
  'La cuenta o la categoría ya no existe. Actualizamos los datos.';
export const MENSAJE_DATOS_EXTRACTO = 'Revisa el saldo y la fecha del extracto';
export const MENSAJE_SIN_AJUSTE =
  'Para cerrar la conciliación con diferencia, crea el ajuste o revisa tus transacciones';

/** Ayuda de la categoría del ajuste según la regla. */
export const TEXTOS_REGLA_AJUSTE: Readonly<Partial<Record<ReglaCategoriaAjuste, string>>> = {
  obligatoria: 'El ajuste sale del dinero de una categoría',
  opcional: 'Sin categoría cuenta como ingreso',
};

const MENSAJES: Readonly<Record<string, MensajesDeError>> = {
  fecha: {
    required: 'La fecha es obligatoria',
    matDatepickerParse: 'La fecha no es válida',
    futura: 'La fecha no puede ser futura',
  },
  categoriaId: { required: 'La categoría es obligatoria' },
};

type Estado = 'cargando' | 'listo' | 'error';

/** Valores válidos con los que se pide el estado; `clave` los identifica a todos. */
interface Consulta {
  clave: string;
  presupuestoId: number;
  cuentaId: number;
  saldoExtracto: number;
  fecha: string;
}

/** Resultado de una consulta del estado, atado a los valores con que se pidió. */
type ResultadoEstado =
  | { clave: string; tipo: 'cargando' }
  | { clave: string; tipo: 'listo'; estado: EstadoConciliacionResponse }
  | { clave: string; tipo: 'error' };

/**
 * Conciliación de una cuenta contra el saldo de su extracto: diferencia mientras se escribe,
 * ajuste opcional con su categoría, confirmación antes de reconciliar e historial. Una cuenta
 * cerrada muestra solo el historial. Sin entrada propia en el menú lateral: se llega desde
 * Cuentas y Transacciones.
 */
@Component({
  selector: 'app-conciliacion',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    CampoMontoComponent,
    FechaPipe,
    HistorialConciliacionesComponent,
    MatButton,
    MatCheckbox,
    MatDatepicker,
    MatDatepickerInput,
    MatDatepickerToggle,
    MatError,
    MatFormField,
    MatHint,
    MatIcon,
    MatInput,
    MatLabel,
    MatOptgroup,
    MatOption,
    MatProgressSpinner,
    MatSelect,
    MatSuffix,
    MontoPipe,
  ],
  templateUrl: './conciliacion.page.html',
  styleUrl: './conciliacion.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConciliacionPage {
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly servicio = inject(ConciliacionService);
  private readonly cuentas = inject(CuentaLecturaService);
  private readonly categorias = inject(CategoriaLecturaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly etiquetas = ETIQUETAS_TIPO_CUENTA;
  protected readonly textosRegla = TEXTOS_REGLA_AJUSTE;
  protected readonly mensajeSinAjuste = MENSAJE_SIN_AJUSTE;

  protected readonly presupuestoId = computed(() => this.presupuestoActivo.presupuesto()?.id);
  protected readonly moneda = computed(() => this.presupuestoActivo.presupuesto()?.moneda ?? 'USD');
  private readonly cuentaId = toSignal(
    this.ruta.paramMap.pipe(map((params) => Number(params.get('cuentaId')))),
    { initialValue: Number(this.ruta.snapshot.paramMap.get('cuentaId')) },
  );

  protected readonly estado = signal<Estado>('cargando');
  protected readonly cuenta = signal<CuentaConciliacion | null>(null);
  private readonly grupos = signal<GrupoCategoriasLectura[]>([]);
  private readonly recargas = signal(0);

  protected readonly estadoHistorial = signal<EstadoHistorial>('cargando');
  protected readonly historial = signal<ConciliacionResponse[]>([]);
  private readonly recargasHistorial = signal(0);

  protected readonly formulario = new FormGroup({
    saldoExtracto: new FormControl<number | null>(null, Validators.required),
    fecha: new FormControl<Date | null>(hoy(), [Validators.required, noFutura]),
    crearAjuste: new FormControl(false, { nonNullable: true }),
    categoriaId: new FormControl<number | null>(null),
  });

  /** Cada `Reintentar` (o recarga) vuelve a pedir el estado aunque los valores no cambien. */
  private readonly reconsultas = new Subject<void>();
  private intento = 0;
  /** Consulta que corresponde a los valores actuales, o `null` si no son válidos. */
  private readonly consultaActual = signal<Consulta | null>(null);
  private readonly resultado = toSignal(this.consultasDeEstado(), { initialValue: null });
  private readonly crearAjuste = toSignal(this.formulario.controls.crearAjuste.valueChanges, {
    initialValue: false,
  });

  /** Lo que muestra el bloque del estado: solo un resultado de los valores actuales. */
  protected readonly vista = computed<'sin-datos' | 'cargando' | 'listo' | 'error'>(() => {
    const consulta = this.consultaActual();
    if (consulta === null) {
      return 'sin-datos';
    }
    const resultado = this.resultado();
    if (resultado === null || resultado.clave !== consulta.clave) {
      return 'cargando';
    }
    return resultado.tipo;
  });

  /** El estado vigente (de los valores actuales), si ya llegó. */
  protected readonly estadoVigente = computed(() => {
    const resultado = this.resultado();
    return this.vista() === 'listo' && resultado?.tipo === 'listo' ? resultado.estado : null;
  });

  /** Último estado recibido: decide la regla aunque se esté consultando otro. */
  private readonly ultimoEstado = signal<EstadoConciliacionResponse | null>(null);

  protected readonly diferencia = computed(() => {
    const estado = this.estadoVigente();
    return estado === null ? null : textoDiferencia(estado.diferencia, this.moneda());
  });
  protected readonly pendientes = computed(() => {
    const estado = this.estadoVigente();
    return estado === null ? null : noConciliadasHasta(estado);
  });

  protected readonly regla = computed<ReglaCategoriaAjuste>(() => {
    const cuenta = this.cuenta();
    const estado = this.ultimoEstado();
    return cuenta === null || estado === null
      ? 'sin-ajuste'
      : reglaCategoriaAjuste(cuenta, estado.diferencia);
  });
  protected readonly muestraCategoria = computed(
    () => this.crearAjuste() && (this.regla() === 'obligatoria' || this.regla() === 'opcional'),
  );

  /** Grupos y categorías que admite el ajuste: sin ocultas ni de pago de tarjeta. */
  protected readonly opcionesCategoria = computed(() =>
    this.grupos()
      .filter((grupo) => !grupo.oculto)
      .map((grupo) => ({
        ...grupo,
        categorias: grupo.categorias.filter((c) => !c.oculta && !c.esPagoTarjeta),
      }))
      .filter((grupo) => grupo.categorias.length > 0),
  );

  private readonly formularioValido = toSignal(
    this.formulario.statusChanges.pipe(map((estado) => estado === 'VALID')),
    { initialValue: this.formulario.valid },
  );

  /** Con diferencia y sin el ajuste marcado no se puede reconciliar (sería un `422`). */
  protected readonly faltaAjuste = computed(() => {
    const estado = this.estadoVigente();
    return estado !== null && estado.diferencia !== 0 && !this.crearAjuste();
  });

  protected readonly puedeReconciliar = computed(() => {
    const cuenta = this.cuenta();
    return (
      cuenta !== null &&
      !cuenta.cerrada &&
      this.formularioValido() &&
      this.estadoVigente() !== null &&
      !this.faltaAjuste()
    );
  });

  protected readonly enviando = signal(false);
  protected readonly errorGeneral = signal<string | null>(null);
  protected readonly conciliacionCreada = signal<ConciliacionResponse | null>(null);

  constructor() {
    this.cargarCuenta();
    this.cargarHistorial();

    // El estado de la consulta se recuerda para decidir la regla mientras llega otro.
    effect(() => {
      const estado = this.estadoVigente();
      if (estado !== null) {
        untracked(() => this.ultimoEstado.set(estado));
      }
    });

    // La categoría del ajuste: obligatoria, opcional o vacía según la regla.
    effect(() => {
      const regla = this.regla();
      const muestra = this.muestraCategoria();
      untracked(() => this.aplicarRegla(regla, muestra));
    });

    // Los valores del saldo y la fecha, y la cuenta abierta, deciden la consulta.
    effect(() => {
      const cuenta = this.cuenta();
      const presupuestoId = this.presupuestoId();
      untracked(() => this.actualizarConsulta(cuenta, presupuestoId));
    });
    merge(
      this.formulario.controls.saldoExtracto.valueChanges,
      this.formulario.controls.fecha.valueChanges,
    )
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.actualizarConsulta(this.cuenta(), this.presupuestoId()));
    this.reconsultas.pipe(takeUntilDestroyed()).subscribe(() => {
      this.intento++;
      this.actualizarConsulta(this.cuenta(), this.presupuestoId());
    });
  }

  protected mensaje(campo: keyof typeof MENSAJES): string | null {
    return mensajeDeError(this.formulario.get(campo)?.errors ?? null, MENSAJES[campo]);
  }

  /** `queryParams` del enlace a las no conciliadas de la cuenta hasta la fecha del extracto. */
  protected filtroPendientes(estado: EstadoConciliacionResponse) {
    return { cuentaId: estado.cuentaId, estado: 'NO_CONCILIADA', hasta: estado.fecha };
  }

  protected recargar(): void {
    this.recargas.update((v) => v + 1);
  }

  protected recargarHistorial(): void {
    this.recargasHistorial.update((v) => v + 1);
  }

  protected reintentarEstado(): void {
    this.reconsultas.next();
  }

  protected reconciliar(): void {
    const cuenta = this.cuenta();
    const estado = this.estadoVigente();
    const presupuestoId = this.presupuestoId();
    if (!this.puedeReconciliar() || this.enviando() || !cuenta || !estado || !presupuestoId) {
      return;
    }
    const request = this.request(estado);
    this.dialog
      .open<DialogoConfirmarConciliacionComponent, DatosDialogoConfirmarConciliacion, boolean>(
        DialogoConfirmarConciliacionComponent,
        {
          data: {
            cuenta: cuenta.nombre,
            fecha: request.fecha,
            saldoExtracto: request.saldoExtracto,
            ajuste: request.crearAjuste ? estado.diferencia : null,
            moneda: this.moneda(),
          },
          width: '440px',
        },
      )
      .afterClosed()
      .subscribe((confirmado) => {
        if (confirmado) {
          this.enviar(presupuestoId, cuenta.id, request);
        }
      });
  }

  private request(estado: EstadoConciliacionResponse): CrearConciliacionRequest {
    const valor = this.formulario.getRawValue();
    const crearAjuste = valor.crearAjuste && estado.diferencia !== 0;
    return {
      saldoExtracto: valor.saldoExtracto as number,
      fecha: aFechaNegocio(valor.fecha) as string,
      crearAjuste,
      // Sin la clave cuando la categoría no aplica.
      ...(crearAjuste && this.muestraCategoria() ? { categoriaId: valor.categoriaId } : {}),
    };
  }

  private enviar(presupuestoId: number, cuentaId: number, request: CrearConciliacionRequest) {
    this.enviando.set(true);
    this.errorGeneral.set(null);
    this.conciliacionCreada.set(null);
    this.servicio.crear(presupuestoId, cuentaId, request).subscribe({
      next: (conciliacion) => {
        this.enviando.set(false);
        this.conciliacionCreada.set(conciliacion);
        this.reconsultas.next();
        this.recargarHistorial();
      },
      error: (error: unknown) => {
        this.enviando.set(false);
        this.mostrarError(error);
      },
    });
  }

  private mostrarError(error: unknown): void {
    const problema = leerProblemaApi(error);
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.DATOS_INVALIDOS) {
      if (!problema.errores) {
        this.errorGeneral.set(MENSAJE_DATOS_EXTRACTO);
      } else if (aplicarErroresDeCampos(this.formulario, problema.errores).length > 0) {
        this.avisar(MENSAJE_ERROR_GENERICO);
      }
      return;
    }
    if (problema?.codigo === CODIGOS_API.REGLA_NEGOCIO_VIOLADA) {
      this.errorGeneral.set(MENSAJE_REGLA_CONCILIACION);
      return;
    }
    if (problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO) {
      this.avisar(MENSAJE_REFERENCIA_INEXISTENTE);
      this.recargar();
      this.recargarHistorial();
      this.reconsultas.next();
      return;
    }
    this.avisar(MENSAJE_ERROR_GENERICO);
  }

  private aplicarRegla(regla: ReglaCategoriaAjuste, muestra: boolean): void {
    const { crearAjuste, categoriaId } = this.formulario.controls;
    if (regla === 'sin-ajuste' && crearAjuste.value) {
      crearAjuste.setValue(false);
    }
    categoriaId.setValidators(regla === 'obligatoria' && muestra ? Validators.required : null);
    if (!muestra && categoriaId.value !== null) {
      categoriaId.setValue(null, { emitEvent: false });
    }
    categoriaId.updateValueAndValidity();
  }

  private actualizarConsulta(cuenta: CuentaConciliacion | null, presupuestoId?: number): void {
    const { saldoExtracto, fecha } = this.formulario.controls;
    const valida =
      cuenta !== null &&
      !cuenta.cerrada &&
      presupuestoId !== undefined &&
      saldoExtracto.value !== null &&
      fecha.valid &&
      fecha.value !== null;
    if (!valida) {
      this.consultaActual.set(null);
      return;
    }
    const fechaNegocio = aFechaNegocio(fecha.value) as string;
    const saldo = saldoExtracto.value as number;
    this.consultaActual.set({
      clave: [presupuestoId, cuenta.id, saldo, fechaNegocio, this.intento].join('|'),
      presupuestoId,
      cuentaId: cuenta.id,
      saldoExtracto: saldo,
      fecha: fechaNegocio,
    });
  }

  /**
   * Una sola consulta 300 ms después del último cambio; `switchMap` descarta la respuesta de una
   * consulta anterior y la vista, además, solo usa el resultado de los valores actuales.
   */
  private consultasDeEstado(): Observable<ResultadoEstado | null> {
    return toObservable(this.consultaActual).pipe(
      startWith(null),
      debounceTime(ESPERA_ESTADO_MS),
      distinctUntilChanged((a, b) => a?.clave === b?.clave),
      switchMap((consulta) =>
        consulta === null
          ? of(null)
          : this.servicio
              .estado(
                consulta.presupuestoId,
                consulta.cuentaId,
                consulta.saldoExtracto,
                consulta.fecha,
              )
              .pipe(
                map((estado): ResultadoEstado => ({
                  clave: consulta.clave,
                  tipo: 'listo',
                  estado,
                })),
                catchError(() => of<ResultadoEstado>({ clave: consulta.clave, tipo: 'error' })),
                startWith<ResultadoEstado>({ clave: consulta.clave, tipo: 'cargando' }),
              ),
      ),
    );
  }

  private cargarCuenta(): void {
    const peticion = computed(() => ({
      presupuestoId: this.presupuestoId(),
      cuentaId: this.cuentaId(),
      recargas: this.recargas(),
    }));
    toObservable(peticion)
      .pipe(
        filter(({ presupuestoId }) => presupuestoId !== undefined),
        tap(() => this.estado.set('cargando')),
        switchMap(({ presupuestoId, cuentaId }) =>
          forkJoin([
            this.cuentas.obtener(presupuestoId as number, cuentaId),
            this.categorias.arbol(presupuestoId as number),
          ]).pipe(
            catchError((error: unknown) => {
              this.alFallarCuenta(error, presupuestoId as number);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(([cuenta, grupos]) => {
        this.cuenta.set(cuenta);
        this.grupos.set(grupos);
        this.estado.set('listo');
      });
  }

  private cargarHistorial(): void {
    const peticion = computed(() => ({
      presupuestoId: this.presupuestoId(),
      cuentaId: this.cuentaId(),
      recargas: this.recargasHistorial(),
    }));
    toObservable(peticion)
      .pipe(
        filter(({ presupuestoId }) => presupuestoId !== undefined),
        tap(() => this.estadoHistorial.set('cargando')),
        switchMap(({ presupuestoId, cuentaId }) =>
          this.servicio.historial(presupuestoId as number, cuentaId).pipe(
            catchError((error: unknown) => {
              if (leerProblemaApi(error)?.status !== 401) {
                this.estadoHistorial.set('error');
              }
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((historial) => {
        this.historial.set(historial);
        this.estadoHistorial.set('listo');
      });
  }

  private alFallarCuenta(error: unknown, presupuestoId: number): void {
    const problema = leerProblemaApi(error);
    // Con 401 el interceptor ya cerró la sesión y redirigió: no hay nada que avisar.
    if (problema?.status === 401) {
      return;
    }
    if (problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO || problema?.status === 404) {
      this.avisar(MENSAJE_CUENTA_INEXISTENTE);
      void this.router.navigate(['/presupuestos', presupuestoId, 'cuentas']);
      return;
    }
    this.estado.set('error');
  }

  private avisar(mensaje: string): void {
    this.snackBar.open(mensaje, 'Cerrar', { duration: 6000 });
  }
}
