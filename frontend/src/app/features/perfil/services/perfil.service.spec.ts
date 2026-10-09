import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PerfilResponse } from '../models/perfil-response.model';
import { PerfilService } from './perfil.service';

const PERFIL: PerfilResponse = {
  email: 'ana@ejemplo.com',
  rol: 'USUARIO',
  nombre: 'Ana',
  apellido: 'Pérez',
  fechaNacimiento: '2003-03-05',
  telefono: null,
  monedaPredeterminada: 'BOB',
};

describe('PerfilService', () => {
  let servicio: PerfilService;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(PerfilService);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    backend.verify();
    vi.restoreAllMocks();
  });

  it('obtener hace GET a /api/v1/usuarios/yo y devuelve el cuerpo', () => {
    let recibido: PerfilResponse | undefined;

    servicio.obtener().subscribe((perfil) => (recibido = perfil));
    const peticion = backend.expectOne('/api/v1/usuarios/yo');
    peticion.flush(PERFIL);

    expect(peticion.request.method).toBe('GET');
    expect(recibido).toEqual(PERFIL);
  });

  it('cambiarNombre hace PUT a /api/v1/usuarios/yo con solo el nombre y devuelve los datos', () => {
    let recibido: PerfilResponse | undefined;

    servicio.cambiarNombre({ nombre: 'María José' }).subscribe((perfil) => (recibido = perfil));
    const peticion = backend.expectOne('/api/v1/usuarios/yo');
    peticion.flush({ ...PERFIL, nombre: 'María José' });

    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ nombre: 'María José' });
    expect(recibido?.nombre).toBe('María José');
  });

  it('cambiarContrasena hace POST con exactamente las dos contraseñas, sin recortar', () => {
    let terminado = false;

    servicio
      .cambiarContrasena({ contrasenaActual: 'secreta123', contrasenaNueva: ' otraClave456 ' })
      .subscribe({ complete: () => (terminado = true) });
    const peticion = backend.expectOne('/api/v1/usuarios/yo/contrasena');
    peticion.flush(null, { status: 204, statusText: 'No Content' });

    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({
      contrasenaActual: 'secreta123',
      contrasenaNueva: ' otraClave456 ',
    });
    expect(Object.keys(peticion.request.body as object)).toEqual([
      'contrasenaActual',
      'contrasenaNueva',
    ]);
    expect(terminado).toBe(true);
  });

  it('no escribe nada en la consola al cambiar la contraseña', () => {
    const espias = (['log', 'info', 'warn', 'error', 'debug'] as const).map((metodo) =>
      vi.spyOn(console, metodo).mockImplementation(() => undefined),
    );

    servicio
      .cambiarContrasena({ contrasenaActual: 'secreta123', contrasenaNueva: 'otraClave456' })
      .subscribe();
    backend
      .expectOne('/api/v1/usuarios/yo/contrasena')
      .flush(null, { status: 204, statusText: 'No Content' });

    for (const espia of espias) {
      expect(espia).not.toHaveBeenCalled();
    }
  });
});
