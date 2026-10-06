import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { UsuarioActualResponse } from '../models/usuario-actual-response.model';
import { UsuarioActualService } from './usuario-actual.service';

describe('UsuarioActualService', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('hace GET a /api/v1/usuarios/yo y devuelve el cuerpo con los siete campos', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const cuerpo: UsuarioActualResponse = {
      email: 'ana@ejemplo.com',
      rol: 'USUARIO',
      nombre: 'Ana',
      apellido: 'Pérez',
      fechaNacimiento: '2003-03-05',
      telefono: null,
      monedaPredeterminada: 'BOB',
    };
    let recibido: UsuarioActualResponse | undefined;

    TestBed.inject(UsuarioActualService)
      .obtener()
      .subscribe((usuario) => (recibido = usuario));
    const peticion = TestBed.inject(HttpTestingController).expectOne('/api/v1/usuarios/yo');
    peticion.flush(cuerpo);

    expect(peticion.request.method).toBe('GET');
    expect(recibido).toEqual(cuerpo);
  });
});
