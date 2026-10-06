import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { MatButton, MatIconButton } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import {
  MatList,
  MatListItem,
  MatListItemLine,
  MatListItemMeta,
  MatListItemTitle,
} from '@angular/material/list';
import { MatMenu, MatMenuContent, MatMenuItem, MatMenuTrigger } from '@angular/material/menu';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSlideToggle } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { EMPTY, Observable, catchError, filter, forkJoin, switchMap, tap } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO, leerProblemaApi } from '../../../shared/api/problema-api';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { DialogoCuentaComponent } from '../components/dialogo-cuenta.component';
import { CuentaResponse } from '../models/cuenta-response.model';
import { DatosDialogoCuenta } from '../models/datos-dialogo-cuenta.model';
import { SaldoCuentaResponse } from '../models/saldo-cuenta-response.model';
import { ETIQUETAS_TIPO_CUENTA } from '../models/tipo-cuenta.model';
import { CuentaService } from '../services/cuenta.service';

type Estado = 'cargando' | 'listo' | 'error';

/** Una cuenta con sus saldos (en milésimas; `0` si la API no informó el saldo). */
interface FilaCuenta {
  cuenta: CuentaResponse;
  saldo: number;
  saldoConciliado: number;
}

interface Seccion {
  titulo: string;
  filas: FilaCuenta[];
}

/**
 * Cuentas del presupuesto activo con sus saldos, agrupadas en "En el presupuesto",
 * "Seguimiento" y, a pedido, "Cerradas". Permite crear, editar, cerrar y reabrir cuentas; tras
 * cada operación vuelve a pedir la lista y los saldos.
 */
@Component({
  selector: 'app-cuentas',
  imports: [
    MatButton,
    MatIcon,
    MatIconButton,
    MatList,
    MatListItem,
    MatListItemLine,
    MatListItemMeta,
    MatListItemTitle,
    MatMenu,
    MatMenuContent,
    MatMenuItem,
    MatMenuTrigger,
    MatProgressSpinner,
    MatSlideToggle,
    MontoPipe,
  ],
  templateUrl: './cuentas.page.html',
  styleUrl: './cuentas.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CuentasPage {
  private readonly servicio = inject(CuentaService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly etiquetas = ETIQUETAS_TIPO_CUENTA;
  protected readonly moneda = computed(() => this.presupuestoActivo.presupuesto()?.moneda ?? null);

  protected readonly estado = signal<Estado>('cargando');
  protected readonly incluirCerradas = signal(false);
  private readonly recargas = signal(0);
  private readonly cuentas = signal<CuentaResponse[]>([]);
  private readonly saldos = signal<SaldoCuentaResponse[]>([]);

  private readonly filas = computed<FilaCuenta[]>(() => {
    const porCuenta = new Map(this.saldos().map((saldo) => [saldo.cuentaId, saldo]));
    return this.cuentas().map((cuenta) => {
      const saldo = porCuenta.get(cuenta.id);
      return {
        cuenta,
        saldo: saldo?.saldo ?? 0,
        saldoConciliado: saldo?.saldoConciliado ?? 0,
      };
    });
  });

  private readonly enPresupuesto = computed(() =>
    this.filas().filter((fila) => !fila.cuenta.cerrada && fila.cuenta.enPresupuesto),
  );

  protected readonly secciones = computed<Seccion[]>(() =>
    [
      { titulo: 'En el presupuesto', filas: this.enPresupuesto() },
      {
        titulo: 'Seguimiento',
        filas: this.filas().filter((fila) => !fila.cuenta.cerrada && !fila.cuenta.enPresupuesto),
      },
      { titulo: 'Cerradas', filas: this.filas().filter((fila) => fila.cuenta.cerrada) },
    ].filter((seccion) => seccion.filas.length > 0),
  );

  /** Suma entera, en milésimas, de las cuentas abiertas que están en el presupuesto. */
  protected readonly total = computed(() =>
    this.enPresupuesto().reduce((suma, fila) => suma + fila.saldo, 0),
  );

  protected readonly sinCuentas = computed(() => this.filas().length === 0);

  constructor() {
    const peticion = computed(() => ({
      presupuestoId: this.presupuestoActivo.presupuesto()?.id ?? null,
      incluirCerradas: this.incluirCerradas(),
      recargas: this.recargas(),
    }));
    toObservable(peticion)
      .pipe(
        filter(({ presupuestoId }) => presupuestoId !== null),
        tap(() => this.estado.set('cargando')),
        // switchMap descarta una respuesta vieja si cambia el interruptor o se recarga.
        switchMap(({ presupuestoId, incluirCerradas }) =>
          forkJoin([
            this.servicio.listar(presupuestoId as number, incluirCerradas),
            this.servicio.saldos(presupuestoId as number),
          ]).pipe(
            catchError((error: unknown) => {
              this.alFallarCarga(error);
              return EMPTY;
            }),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(([cuentas, saldos]) => {
        this.cuentas.set(cuentas);
        this.saldos.set(saldos);
        this.estado.set('listo');
      });
  }

  protected recargar(): void {
    this.recargas.update((valor) => valor + 1);
  }

  protected cambiarCerradas(incluir: boolean): void {
    this.incluirCerradas.set(incluir);
  }

  protected agregar(): void {
    this.abrirDialogo({ modo: 'crear' });
  }

  protected editar(cuenta: CuentaResponse): void {
    this.abrirDialogo({ modo: 'editar', cuenta });
  }

  protected cerrar(cuenta: CuentaResponse): void {
    this.ejecutar((presupuestoId) => this.servicio.cerrar(presupuestoId, cuenta.id));
  }

  protected reabrir(cuenta: CuentaResponse): void {
    this.ejecutar((presupuestoId) => this.servicio.reabrir(presupuestoId, cuenta.id));
  }

  private ejecutar(operacion: (presupuestoId: number) => Observable<CuentaResponse>): void {
    const presupuestoId = this.presupuestoActivo.presupuesto()?.id;
    if (presupuestoId === undefined) {
      return;
    }
    operacion(presupuestoId).subscribe({
      next: () => this.recargar(),
      error: (error: unknown) => {
        if (leerProblemaApi(error)?.status !== 401) {
          this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
        }
      },
    });
  }

  private abrirDialogo(datos: DatosDialogoCuenta): void {
    this.dialog
      .open<DialogoCuentaComponent, DatosDialogoCuenta, CuentaResponse>(DialogoCuentaComponent, {
        data: datos,
        width: '440px',
      })
      .afterClosed()
      .subscribe((cuenta) => {
        if (cuenta) {
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
    this.snackBar
      .open(MENSAJE_ERROR_GENERICO, 'Reintentar', { duration: 6000 })
      .onAction()
      .subscribe(() => this.recargar());
  }
}
