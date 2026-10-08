import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatOptgroup, MatOption } from '@angular/material/core';
import { MatFormField, MatLabel } from '@angular/material/form-field';
import { MatSelect } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { textoMes } from '../../../shared/fecha/mes';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { CuentaReporte } from '../models/cuenta-reporte.model';
import { RangoMeses } from '../models/rango-meses.model';
import { cargarReporte, mientrasActiva } from '../services/carga-reporte';
import { CuentaLecturaService } from '../services/cuenta-lectura.service';
import {
  MENSAJE_CUENTA_INEXISTENTE,
  MENSAJE_ELEGIR_CUENTA,
  NOTA_SALDO_INICIAL,
} from '../services/mensajes-reporte';
import { fechasDelRango, textoRango } from '../services/rango-reporte';
import { ReporteService } from '../services/reporte.service';
import { EstadoReporteComponent } from './estado-reporte.component';
import { GraficoBarrasMensualesComponent, SerieBarras } from './grafico-barras-mensuales.component';

interface ParametrosEvolucion {
  presupuestoId: number;
  cuentaId: number;
  rango: RangoMeses;
}

/**
 * Evolución del saldo de una cuenta: selector con todas las cuentas (también cerradas y fuera
 * del presupuesto), barras de entradas y salidas con la línea del saldo y la tabla mes a mes. La
 * cuenta elegida vive en la URL (`cuentaId`): la pestaña la recibe y avisa los cambios.
 */
@Component({
  selector: 'app-saldo-cuenta-reporte',
  imports: [
    EstadoReporteComponent,
    GraficoBarrasMensualesComponent,
    MatFormField,
    MatLabel,
    MatOptgroup,
    MatOption,
    MatSelect,
    MatTableModule,
    MontoPipe,
    RouterLink,
  ],
  templateUrl: './saldo-cuenta-reporte.component.html',
  styleUrl: './saldo-cuenta-reporte.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SaldoCuentaReporteComponent {
  private readonly servicio = inject(ReporteService);
  private readonly cuentasServicio = inject(CuentaLecturaService);

  readonly presupuestoId = input.required<number>();
  readonly rango = input.required<RangoMeses>();
  readonly moneda = input.required<string>();
  readonly activa = input(true);
  readonly cuentaId = input<number | null>(null);
  readonly cuentaCambiada = output<number | null>();

  protected readonly mensajeElegir = MENSAJE_ELEGIR_CUENTA;
  protected readonly notaSaldoInicial = NOTA_SALDO_INICIAL;
  protected readonly columnas = ['mes', 'entradas', 'salidas', 'saldo'];
  protected readonly textoMes = textoMes;
  /** Aviso que sobrevive a quitar la cuenta de la URL (ej. la cuenta ya no existe). */
  protected readonly aviso = signal<string | null>(null);
  private readonly intento = signal(0);
  private readonly intentoCuentas = signal(0);

  private readonly parametrosCuentas = mientrasActiva(
    this.activa,
    computed(() => ({ presupuestoId: this.presupuestoId(), intento: this.intentoCuentas() })),
  );

  protected readonly estadoCuentas = toSignal(
    toObservable(this.parametrosCuentas).pipe(
      cargarReporte((p: { presupuestoId: number }) => this.cuentasServicio.listar(p.presupuestoId)),
    ),
    { initialValue: null },
  );

  protected readonly cuentas = computed<CuentaReporte[] | null>(() => {
    const estado = this.estadoCuentas();
    return estado?.tipo === 'listo' ? estado.datos : null;
  });
  protected readonly abiertas = computed(() => (this.cuentas() ?? []).filter((c) => !c.cerrada));
  protected readonly cerradas = computed(() => (this.cuentas() ?? []).filter((c) => c.cerrada));

  /** La cuenta de la URL, solo si está en la lista. */
  protected readonly cuenta = computed(() => {
    const id = this.cuentaId();
    return this.cuentas()?.find((c) => c.id === id) ?? null;
  });

  private readonly parametros = mientrasActiva(
    this.activa,
    computed<(ParametrosEvolucion & { intento: number }) | null>(() => {
      const cuenta = this.cuenta();
      return cuenta === null
        ? null
        : {
            presupuestoId: this.presupuestoId(),
            cuentaId: cuenta.id,
            rango: this.rango(),
            intento: this.intento(),
          };
    }),
  );

  protected readonly estado = toSignal(
    toObservable(this.parametros).pipe(
      cargarReporte(
        (p: ParametrosEvolucion) =>
          this.servicio.evolucionSaldo(p.presupuestoId, p.cuentaId, p.rango),
        'cuenta',
      ),
    ),
    { initialValue: null },
  );

  protected readonly datos = computed(() => {
    const estado = this.estado();
    return estado?.tipo === 'listo' ? estado.datos : null;
  });

  protected readonly meses = computed(() => (this.datos()?.meses ?? []).map((m) => m.mes));

  protected readonly series = computed<SerieBarras[]>(() => {
    const meses = this.datos()?.meses ?? [];
    return [
      { nombre: 'Entradas', valores: meses.map((m) => m.entradas), patron: 'solido' },
      { nombre: 'Salidas', valores: meses.map((m) => m.salidas), patron: 'rayado' },
    ];
  });

  protected readonly saldo = computed(() => ({
    nombre: 'Saldo',
    valores: (this.datos()?.meses ?? []).map((m) => m.saldo),
  }));

  protected readonly descripcion = computed(
    () =>
      `Saldo de ${this.cuenta()?.nombre ?? 'la cuenta'} con sus entradas y salidas por mes, ` +
      `${textoRango(this.rango())}. Las cifras están en la tabla siguiente.`,
  );

  protected readonly enlaceTransacciones = computed(() => ({
    ruta: ['/presupuestos', this.presupuestoId(), 'transacciones'],
    queryParams: { cuentaId: this.cuentaId(), ...fechasDelRango(this.rango()) },
  }));

  constructor() {
    // Un `cuentaId` que no está en la lista se quita de la URL.
    effect(() => {
      const cuentas = this.cuentas();
      const id = this.cuentaId();
      if (cuentas !== null && id !== null && !cuentas.some((c) => c.id === id)) {
        untracked(() => this.cuentaCambiada.emit(null));
      }
    });

    // La cuenta se borró en otra sesión: aviso, recarga de la lista y sin cuenta en la URL.
    effect(() => {
      const estado = this.estado();
      if (estado?.tipo === 'error' && estado.aviso.cuentaInexistente) {
        untracked(() => {
          this.aviso.set(MENSAJE_CUENTA_INEXISTENTE);
          this.intentoCuentas.update((n) => n + 1);
          this.cuentaCambiada.emit(null);
        });
      }
    });
  }

  protected elegir(cuentaId: number): void {
    this.aviso.set(null);
    this.cuentaCambiada.emit(cuentaId);
  }

  protected reintentar(): void {
    this.intento.update((n) => n + 1);
  }

  protected reintentarCuentas(): void {
    this.intentoCuentas.update((n) => n + 1);
  }
}
