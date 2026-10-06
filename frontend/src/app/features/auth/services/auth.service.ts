import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { urlBaseApi } from '../../../core/api-base-url';
import { SesionService } from '../../../core/sesion/sesion.service';
import { LoginRequest } from '../models/login-request.model';
import { RegistroRequest } from '../models/registro-request.model';
import { TokenResponse } from '../models/token-response.model';

/** Registro e inicio de sesión contra el backend; al responder guarda la sesión. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly sesion = inject(SesionService);

  registrar(solicitud: RegistroRequest): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${urlBaseApi}/auth/registro`, solicitud)
      .pipe(tap((respuesta) => this.guardarSesion(respuesta)));
  }

  iniciarSesion(solicitud: LoginRequest): Observable<TokenResponse> {
    return this.http
      .post<TokenResponse>(`${urlBaseApi}/auth/login`, solicitud)
      .pipe(tap((respuesta) => this.guardarSesion(respuesta)));
  }

  private guardarSesion(respuesta: TokenResponse): void {
    this.sesion.iniciar(respuesta.token, respuesta.expiraEn);
  }
}
