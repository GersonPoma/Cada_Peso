import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { CuentaResumen } from '../models/cuenta-resumen.model';

/**
 * Lectura de las cuentas del presupuesto para esta feature (solo GET). Duplica a propósito una
 * pequeña parte de la feature de cuentas: una feature no importa de otra.
 */
@Injectable({ providedIn: 'root' })
export class CuentaLecturaService {
  private readonly http = inject(HttpClient);

  /** Todas las cuentas, abiertas y cerradas. */
  listar(presupuestoId: number): Observable<CuentaResumen[]> {
    const params = new HttpParams().set('incluirCerradas', true);
    return this.http.get<CuentaResumen[]>(`${urlBaseApi}/presupuestos/${presupuestoId}/cuentas`, {
      params,
    });
  }
}
