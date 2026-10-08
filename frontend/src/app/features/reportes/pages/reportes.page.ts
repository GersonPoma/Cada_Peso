import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatTab, MatTabContent, MatTabGroup } from '@angular/material/tabs';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { mesActual } from '../../../shared/fecha/mes';
import { GastoReporteComponent } from '../components/gasto-reporte.component';
import { IngresosGastosReporteComponent } from '../components/ingresos-gastos-reporte.component';
import { MetasReporteComponent } from '../components/metas-reporte.component';
import { PatrimonioReporteComponent } from '../components/patrimonio-reporte.component';
import { SaldoCuentaReporteComponent } from '../components/saldo-cuenta-reporte.component';
import { SelectorRangoComponent } from '../components/selector-rango.component';
import { PestanaReporte, RangoMeses } from '../models/rango-meses.model';
import { rangoDesdeUrl } from '../services/rango-reporte';

/** Pestañas en orden, con su valor en el parámetro `reporte` de la URL. */
export const PESTANAS: readonly { valor: PestanaReporte; etiqueta: string }[] = [
  { valor: 'gasto', etiqueta: 'Gasto' },
  { valor: 'ingresos-gastos', etiqueta: 'Ingresos y gastos' },
  { valor: 'patrimonio', etiqueta: 'Patrimonio' },
  { valor: 'saldo', etiqueta: 'Saldo de una cuenta' },
  { valor: 'metas', etiqueta: 'Metas' },
];

/**
 * Reportes del presupuesto activo: un selector de rango compartido y una pestaña por reporte.
 * La pestaña, el rango y la cuenta viven en la URL (`reporte`, `desde`, `hasta`, `cuentaId`),
 * escritos con `replaceUrl` para no llenar el historial. Cada pestaña se crea al elegirla
 * (`matTabContent`) y pide su reporte solo mientras está activa.
 */
@Component({
  selector: 'app-reportes',
  imports: [
    GastoReporteComponent,
    IngresosGastosReporteComponent,
    MatTab,
    MatTabContent,
    MatTabGroup,
    MetasReporteComponent,
    PatrimonioReporteComponent,
    SaldoCuentaReporteComponent,
    SelectorRangoComponent,
  ],
  templateUrl: './reportes.page.html',
  styleUrl: './reportes.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportesPage {
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);

  protected readonly pestanas = PESTANAS;
  /** Mes local de hoy, fijo mientras dura la pantalla. */
  protected readonly mesLocal = mesActual();

  protected readonly presupuestoId = computed(() => this.presupuestoActivo.presupuesto()?.id);
  protected readonly moneda = computed(() => this.presupuestoActivo.presupuesto()?.moneda ?? 'USD');

  private readonly parametros = toSignal(this.ruta.queryParamMap, {
    initialValue: this.ruta.snapshot.queryParamMap,
  });

  private readonly lecturaRango = computed(() =>
    rangoDesdeUrl(this.parametros().get('desde'), this.parametros().get('hasta'), this.mesLocal),
  );
  protected readonly rango = computed(() => this.lecturaRango().rango, {
    equal: (a, b) => a.desde === b.desde && a.hasta === b.hasta,
  });

  protected readonly indice = computed(() => {
    const valor = this.parametros().get('reporte');
    return Math.max(
      0,
      PESTANAS.findIndex((p) => p.valor === valor),
    );
  });

  protected readonly cuentaId = computed(() => {
    const id = Number(this.parametros().get('cuentaId'));
    return Number.isInteger(id) && id > 0 ? id : null;
  });

  constructor() {
    // Sin rango válido en la URL se usa el de por defecto y se escribe en la URL.
    effect(() => {
      const { rango, normalizado } = this.lecturaRango();
      if (normalizado) {
        this.actualizarUrl({ desde: rango.desde, hasta: rango.hasta });
      }
    });
  }

  protected cambiarPestana(indice: number): void {
    if (indice === this.indice()) {
      return;
    }
    this.actualizarUrl({ reporte: PESTANAS[indice]?.valor ?? PESTANAS[0].valor });
  }

  protected cambiarRango(rango: RangoMeses): void {
    this.actualizarUrl({ desde: rango.desde, hasta: rango.hasta });
  }

  protected cambiarCuenta(cuentaId: number | null): void {
    this.actualizarUrl({ cuentaId });
  }

  private actualizarUrl(queryParams: Params): void {
    void this.router.navigate([], {
      relativeTo: this.ruta,
      queryParams,
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }
}
