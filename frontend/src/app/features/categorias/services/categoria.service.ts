import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { ActualizarCategoriaRequest } from '../models/actualizar-categoria-request.model';
import { ActualizarGrupoCategoriaRequest } from '../models/actualizar-grupo-categoria-request.model';
import { ArbolCategorias } from '../models/arbol-categorias.model';
import { CategoriaResponse } from '../models/categoria-response.model';
import { CrearCategoriaRequest } from '../models/crear-categoria-request.model';
import { CrearGrupoCategoriaRequest } from '../models/crear-grupo-categoria-request.model';
import { GrupoCategoriaResponse } from '../models/grupo-categoria-response.model';
import { MoverCategoriaRequest } from '../models/mover-categoria-request.model';
import { MoverGrupoCategoriaRequest } from '../models/mover-grupo-categoria-request.model';

/** Grupos de categorías y categorías de un presupuesto. Cada método recibe el id del presupuesto. */
@Injectable({ providedIn: 'root' })
export class CategoriaService {
  private readonly http = inject(HttpClient);

  /** Árbol de grupos con sus categorías, en su `orden`; los ocultos solo si se piden. */
  obtenerArbol(presupuestoId: number, incluirOcultas: boolean): Observable<ArbolCategorias> {
    const params = new HttpParams().set('incluirOcultas', incluirOcultas);
    return this.http.get<ArbolCategorias>(this.urlCategorias(presupuestoId), { params });
  }

  crearGrupo(
    presupuestoId: number,
    solicitud: CrearGrupoCategoriaRequest,
  ): Observable<GrupoCategoriaResponse> {
    return this.http.post<GrupoCategoriaResponse>(this.urlGrupos(presupuestoId), solicitud);
  }

  renombrarGrupo(
    presupuestoId: number,
    id: number,
    solicitud: ActualizarGrupoCategoriaRequest,
  ): Observable<GrupoCategoriaResponse> {
    return this.http.put<GrupoCategoriaResponse>(
      `${this.urlGrupos(presupuestoId)}/${id}`,
      solicitud,
    );
  }

  ocultarGrupo(presupuestoId: number, id: number): Observable<GrupoCategoriaResponse> {
    return this.http.post<GrupoCategoriaResponse>(
      `${this.urlGrupos(presupuestoId)}/${id}/ocultar`,
      null,
    );
  }

  mostrarGrupo(presupuestoId: number, id: number): Observable<GrupoCategoriaResponse> {
    return this.http.post<GrupoCategoriaResponse>(
      `${this.urlGrupos(presupuestoId)}/${id}/mostrar`,
      null,
    );
  }

  moverGrupo(
    presupuestoId: number,
    id: number,
    solicitud: MoverGrupoCategoriaRequest,
  ): Observable<GrupoCategoriaResponse> {
    return this.http.post<GrupoCategoriaResponse>(
      `${this.urlGrupos(presupuestoId)}/${id}/mover`,
      solicitud,
    );
  }

  crearCategoria(
    presupuestoId: number,
    solicitud: CrearCategoriaRequest,
  ): Observable<CategoriaResponse> {
    return this.http.post<CategoriaResponse>(this.urlCategorias(presupuestoId), solicitud);
  }

  editarCategoria(
    presupuestoId: number,
    id: number,
    solicitud: ActualizarCategoriaRequest,
  ): Observable<CategoriaResponse> {
    return this.http.put<CategoriaResponse>(
      `${this.urlCategorias(presupuestoId)}/${id}`,
      solicitud,
    );
  }

  ocultarCategoria(presupuestoId: number, id: number): Observable<CategoriaResponse> {
    return this.http.post<CategoriaResponse>(
      `${this.urlCategorias(presupuestoId)}/${id}/ocultar`,
      null,
    );
  }

  mostrarCategoria(presupuestoId: number, id: number): Observable<CategoriaResponse> {
    return this.http.post<CategoriaResponse>(
      `${this.urlCategorias(presupuestoId)}/${id}/mostrar`,
      null,
    );
  }

  moverCategoria(
    presupuestoId: number,
    id: number,
    solicitud: MoverCategoriaRequest,
  ): Observable<CategoriaResponse> {
    return this.http.post<CategoriaResponse>(
      `${this.urlCategorias(presupuestoId)}/${id}/mover`,
      solicitud,
    );
  }

  private urlCategorias(presupuestoId: number): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/categorias`;
  }

  private urlGrupos(presupuestoId: number): string {
    return `${urlBaseApi}/presupuestos/${presupuestoId}/grupos-categorias`;
  }
}
