import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { SesionService } from '../../../core/sesion/sesion.service';
import { RegistroRequest } from '../models/registro-request.model';
import { TokenResponse } from '../models/token-response.model';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  const token: TokenResponse = {
    token: 'abc',
    tipo: 'Bearer',
    expiraEn: '2026-10-07T12:00:00Z',
  };
  const registro: RegistroRequest = {
    email: 'ana@ejemplo.com',
    contrasena: 'secreta123',
    nombre: 'Ana',
    apellido: 'Rojas',
    fechaNacimiento: '1990-05-20',
  };

  let servicio: AuthService;
  let backend: HttpTestingController;
  let sesion: SesionService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(AuthService);
    backend = TestBed.inject(HttpTestingController);
    sesion = TestBed.inject(SesionService);
  });

  afterEach(() => {
    backend.verify();
    localStorage.clear();
  });

  it('registrar hace POST /api/v1/auth/registro y guarda el token y su expiración', () => {
    let recibido: TokenResponse | undefined;

    servicio.registrar(registro).subscribe((respuesta) => (recibido = respuesta));
    const peticion = backend.expectOne('/api/v1/auth/registro');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(registro);
    peticion.flush(token);

    expect(recibido).toEqual(token);
    expect(sesion.sesion()).toEqual({ token: 'abc', expiraEn: '2026-10-07T12:00:00Z' });
  });

  it('iniciarSesion hace POST /api/v1/auth/login y guarda el token y su expiración', () => {
    const credenciales = { email: 'ana@ejemplo.com', contrasena: 'secreta123' };

    servicio.iniciarSesion(credenciales).subscribe();
    const peticion = backend.expectOne('/api/v1/auth/login');
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(credenciales);
    peticion.flush(token);

    expect(sesion.sesion()).toEqual({ token: 'abc', expiraEn: '2026-10-07T12:00:00Z' });
  });

  it('ante un error no guarda ninguna sesión', () => {
    servicio.registrar(registro).subscribe({ error: () => undefined });
    backend
      .expectOne('/api/v1/auth/registro')
      .flush({ codigo: 'EMAIL_YA_REGISTRADO' }, { status: 409, statusText: 'Conflict' });

    expect(sesion.haySesion()).toBe(false);
    expect(localStorage.length).toBe(0);
  });
});
