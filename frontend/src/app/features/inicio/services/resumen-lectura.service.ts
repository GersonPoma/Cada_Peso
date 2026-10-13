import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { CuentaResumen, SaldoResumen } from '../models/cuenta-resumen.model';
import { IngresosGastosResumen } from '../models/ingresos-gastos-resumen.model';
import { MesResumen } from '../models/mes-resumen.model';
import { MetasMesResumen } from '../models/metas-resumen.model';
import { PaginaResumen } from '../models/pagina-resumen.model';

/**
 * Lecturas de Inicio (solo GET). Duplica a propósito una pequeña parte de otras features: una
 * feature no importa de otra. Cada método recibe el id del presupuesto.
 */
@Injectable({ providedIn: 'root' })
export class ResumenLecturaService {
  private readonly http = inject(HttpClient);

  private url(presupuestoId: number): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}`;
  }

  /** El mes con las categorías ocultas: `listoParaAsignar` ya las incluye. */
  mes(presupuestoId: number, mes: string): Observable<MesResumen> {
    const params = new HttpParams().set('incluirOcultas', true);
    return this.http.get<MesResumen>(`${this.url(presupuestoId)}/meses/${mes}`, { params });
  }

  /** Las cuentas abiertas del presupuesto. */
  cuentas(presupuestoId: number): Observable<CuentaResumen[]> {
    return this.http.get<CuentaResumen[]>(`${this.url(presupuestoId)}/cuentas`);
  }

  saldos(presupuestoId: number): Observable<SaldoResumen[]> {
    return this.http.get<SaldoResumen[]>(`${this.url(presupuestoId)}/transacciones/saldos`);
  }

  metas(presupuestoId: number, mes: string): Observable<MetasMesResumen> {
    return this.http.get<MetasMesResumen>(`${this.url(presupuestoId)}/meses/${mes}/metas`);
  }

  /** Una página de una transacción: solo importa `totalElementos`. */
  sinAprobar(presupuestoId: number): Observable<PaginaResumen> {
    const params = new HttpParams().set('soloSinAprobar', true).set('page', 0).set('size', 1);
    return this.http.get<PaginaResumen>(`${this.url(presupuestoId)}/transacciones`, { params });
  }

  ingresosGastos(presupuestoId: number, mes: string): Observable<IngresosGastosResumen> {
    const params = new HttpParams().set('desde', mes).set('hasta', mes);
    return this.http.get<IngresosGastosResumen>(
      `${this.url(presupuestoId)}/reportes/ingresos-gastos`,
      { params },
    );
  }
}
