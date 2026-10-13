import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatAnchor } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { MENSAJE_ERROR_GENERICO, leerProblemaApi } from '../../../shared/api/problema-api';
import { textoMes } from '../../../shared/fecha/mes';
import { MontoPipe } from '../../../shared/formato/monto.pipe';
import { TarjetaResumenComponent } from '../components/tarjeta-resumen.component';
import { UsuarioActualResponse } from '../models/usuario-actual-response.model';
import {
  AVISO_LISTO_NEGATIVO,
  AVISO_NETO_NEGATIVO,
  AVISO_SIN_CUENTAS,
  TEXTO_SIN_MOVIMIENTOS,
  TEXTO_TODO_ASIGNADO,
  textoMetasQueFaltan,
  textoSinAprobar,
  textoSobregastadas,
} from '../services/mensajes-inicio';
import {
  contarMetasQueFaltan,
  contarSobregastadas,
  saldoTotalDelPresupuesto,
} from '../services/resumen-inicio';
import { ResumenInicioStore } from '../services/resumen-inicio.store';
import { UsuarioActualService } from '../services/usuario-actual.service';

const MENSAJE_ERROR_CARGA = 'No pudimos cargar tus datos.';

/**
 * Sección de inicio de un presupuesto: saluda al usuario autenticado y resume el mes actual en seis
 * tarjetas independientes (cada una con su carga, su vacío y su error con `Reintentar`). Las cifras
 * las da el backend; aquí solo se cuentan y se suma el saldo total. La cabecera y el cierre de
 * sesión los pone el layout del presupuesto.
 */
@Component({
  selector: 'app-inicio',
  imports: [MatAnchor, MatIcon, MatProgressSpinner, MontoPipe, RouterLink, TarjetaResumenComponent],
  providers: [ResumenInicioStore],
  templateUrl: './inicio.page.html',
  styleUrl: './inicio.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InicioPage {
  private readonly usuarioActual = inject(UsuarioActualService);
  private readonly snackBar = inject(MatSnackBar);
  protected readonly store = inject(ResumenInicioStore);
  protected readonly presupuesto = inject(PresupuestoActivoService).presupuesto;

  protected readonly usuario = signal<UsuarioActualResponse | null>(null);
  protected readonly cargando = signal(true);
  protected readonly errorCarga = signal<string | null>(null);

  protected readonly mesTexto = textoMes(this.store.periodo);
  protected readonly avisoListoNegativo = AVISO_LISTO_NEGATIVO;
  protected readonly textoTodoAsignado = TEXTO_TODO_ASIGNADO;
  protected readonly avisoNetoNegativo = AVISO_NETO_NEGATIVO;
  protected readonly avisoSinCuentas = AVISO_SIN_CUENTAS;
  protected readonly textoSinMovimientos = TEXTO_SIN_MOVIMIENTOS;

  protected readonly textoSobregastadas = textoSobregastadas;
  protected readonly textoSinAprobar = textoSinAprobar;

  protected readonly sobregastadas = computed(() => {
    const mes = this.store.mes.datos();
    return mes ? contarSobregastadas(mes) : 0;
  });

  protected readonly saldoTotal = computed(() => {
    const datos = this.store.saldo.datos();
    return datos ? saldoTotalDelPresupuesto(datos.cuentas, datos.saldos) : 0;
  });
  protected readonly sinCuentas = computed(() => this.store.saldo.datos()?.cuentas.length === 0);

  protected readonly textoMetas = computed(() => {
    const metas = this.store.metas.datos()?.metas ?? [];
    return textoMetasQueFaltan(contarMetasQueFaltan(metas), metas.length);
  });

  protected readonly sinMovimientos = computed(() => {
    const datos = this.store.ingresosGastos.datos();
    return datos !== null && datos.ingresos === 0 && datos.gastos === 0 && datos.neto === 0;
  });

  constructor() {
    this.usuarioActual
      .obtener()
      .pipe(takeUntilDestroyed(inject(DestroyRef)))
      .subscribe({
        next: (usuario) => {
          this.usuario.set(usuario);
          this.cargando.set(false);
        },
        error: (error: unknown) => {
          this.cargando.set(false);
          // Con 401 el interceptor ya cerró la sesión y redirigió: no hay nada que avisar.
          if (leerProblemaApi(error)?.status === 401) {
            return;
          }
          this.errorCarga.set(MENSAJE_ERROR_CARGA);
          this.snackBar.open(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
        },
      });
  }
}
