import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { ImportacionResponse } from '../models/importacion-response.model';
import { MapeoImportacion } from '../models/mapeo-importacion.model';
import { VistaPrevia } from '../models/vista-previa-response.model';
import { armarFormulario } from './formulario-importacion';

/**
 * Importación de un CSV a una cuenta (`.../cuentas/{cuentaId}/importacion`). Los dos endpoints son
 * `multipart/form-data`: el `Content-Type` lo fija el navegador, que agrega el separador (boundary).
 */
@Injectable({ providedIn: 'root' })
export class ImportacionService {
  private readonly http = inject(HttpClient);

  private url(presupuestoId: number, cuentaId: number): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/cuentas/${cuentaId}/importacion`;
  }

  /** Qué pasaría con cada fila; no guarda nada. */
  vistaPrevia(
    presupuestoId: number,
    cuentaId: number,
    archivo: File,
    mapeo: MapeoImportacion,
  ): Observable<VistaPrevia> {
    return this.http.post<VistaPrevia>(
      `${this.url(presupuestoId, cuentaId)}/vista-previa`,
      armarFormulario(archivo, mapeo),
    );
  }

  /**
   * Crea las filas nuevas (`201`). El servidor vuelve a interpretar el archivo y recalcula los
   * duplicados: no confía en ninguna vista previa anterior.
   */
  importar(
    presupuestoId: number,
    cuentaId: number,
    archivo: File,
    mapeo: MapeoImportacion,
    omitirInvalidas: boolean,
  ): Observable<ImportacionResponse> {
    return this.http.post<ImportacionResponse>(
      this.url(presupuestoId, cuentaId),
      armarFormulario(archivo, mapeo, omitirInvalidas),
    );
  }
}
