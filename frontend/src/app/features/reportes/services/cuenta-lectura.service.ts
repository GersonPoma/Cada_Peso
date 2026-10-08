import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { CuentaReporte } from '../models/cuenta-reporte.model';

/**
 * Lectura de las cuentas del presupuesto para esta feature (solo GET). Duplica a propósito una
 * pequeña parte de la feature de cuentas: una feature no importa de otra.
 */
@Injectable({ providedIn: 'root' })
export class CuentaLecturaService {
  private readonly http = inject(HttpClient);

  /** Todas las cuentas: abiertas y cerradas, dentro y fuera del presupuesto. */
  listar(presupuestoId: number): Observable<CuentaReporte[]> {
    const params = new HttpParams().set('incluirCerradas', true);
    return this.http.get<CuentaReporte[]>(`${urlBaseApi}/presupuestos/${presupuestoId}/cuentas`, {
      params,
    });
  }
}
