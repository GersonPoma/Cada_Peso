import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { CumplimientoMetasResponse } from '../models/cumplimiento-metas-response.model';
import { EvolucionSaldoResponse } from '../models/evolucion-saldo-response.model';
import { GastoPorCategoriaResponse } from '../models/gasto-por-categoria-response.model';
import { IngresosGastosResponse } from '../models/ingresos-gastos-response.model';
import { PatrimonioResponse } from '../models/patrimonio-response.model';
import { RangoMeses } from '../models/rango-meses.model';

/** Acceso a `/presupuestos/{id}/reportes`: solo `GET`, con `desde` y `hasta` en `yyyy-MM`. */
@Injectable({ providedIn: 'root' })
export class ReporteService {
  private readonly http = inject(HttpClient);

  gastoPorCategoria(
    presupuestoId: number,
    rango: RangoMeses,
  ): Observable<GastoPorCategoriaResponse> {
    return this.http.get<GastoPorCategoriaResponse>(
      `${this.url(presupuestoId)}/gasto-por-categoria`,
      { params: this.params(rango) },
    );
  }

  ingresosGastos(presupuestoId: number, rango: RangoMeses): Observable<IngresosGastosResponse> {
    return this.http.get<IngresosGastosResponse>(`${this.url(presupuestoId)}/ingresos-gastos`, {
      params: this.params(rango),
    });
  }

  patrimonio(presupuestoId: number, rango: RangoMeses): Observable<PatrimonioResponse> {
    return this.http.get<PatrimonioResponse>(`${this.url(presupuestoId)}/patrimonio`, {
      params: this.params(rango),
    });
  }

  evolucionSaldo(
    presupuestoId: number,
    cuentaId: number,
    rango: RangoMeses,
  ): Observable<EvolucionSaldoResponse> {
    return this.http.get<EvolucionSaldoResponse>(
      `${this.url(presupuestoId)}/cuentas/${cuentaId}/evolucion-saldo`,
      { params: this.params(rango) },
    );
  }

  metas(presupuestoId: number, rango: RangoMeses): Observable<CumplimientoMetasResponse> {
    return this.http.get<CumplimientoMetasResponse>(`${this.url(presupuestoId)}/metas`, {
      params: this.params(rango),
    });
  }

  private url(presupuestoId: number): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/reportes`;
  }

  private params(rango: RangoMeses): HttpParams {
    return new HttpParams().set('desde', rango.desde).set('hasta', rango.hasta);
  }
}
