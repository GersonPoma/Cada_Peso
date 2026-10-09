import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { sesionInterceptor } from '../../../core/auth/sesion.interceptor';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { CLAVE_SESION, SesionService } from '../../../core/sesion/sesion.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { FormularioContrasenaComponent } from './formulario-contrasena.component';

const URL = '/api/v1/usuarios/yo/contrasena';
const AVISO = 'Contraseña cambiada, inicia sesión de nuevo';
const ACTUAL = 'secreta123';
const NUEVA = 'otraClave456';

@Component({ template: '<p>Pantalla de inicio de sesión</p>' })
class LoginDePrueba {}

describe('FormularioContrasenaComponent', () => {
  let fixture: ComponentFixture<FormularioContrasenaComponent>;
  let backend: HttpTestingController;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let navegar: ReturnType<typeof vi.spyOn>;

  const campos = () =>
    Array.from(
      fixture.nativeElement.querySelectorAll('app-campo-contrasena input'),
    ) as HTMLInputElement[];
  const [ACTUAL_I, NUEVA_I, CONFIRMACION_I] = [0, 1, 2];
  const boton = () =>
    fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
  const errores = () =>
    Array.from(fixture.nativeElement.querySelectorAll('mat-error') as NodeListOf<HTMLElement>).map(
      (e) => e.textContent?.trim(),
    );
  const errorDe = (indice: number) =>
    (
      campos()[indice].closest('app-campo-contrasena')?.querySelector('mat-error') as HTMLElement
    )?.textContent?.trim();
  const valores = () => campos().map((campo) => campo.value);
  const sesion = () => TestBed.inject(SesionService);

  async function escribir(indice: number, valor: string): Promise<void> {
    campos()[indice].value = valor;
    campos()[indice].dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function tocar(indice: number): Promise<void> {
    campos()[indice].dispatchEvent(new Event('blur'));
    await fixture.whenStable();
  }

  async function completar(actual = ACTUAL, nueva = NUEVA, confirmacion = nueva): Promise<void> {
    await escribir(ACTUAL_I, actual);
    await escribir(NUEVA_I, nueva);
    await escribir(CONFIRMACION_I, confirmacion);
  }

  async function enviar(): Promise<void> {
    boton().click();
    await fixture.whenStable();
  }

  function responder(estado: number, cuerpo: object | null = null): void {
    backend.expectOne(URL).flush(cuerpo, { status: estado, statusText: 'Respuesta' });
  }

  function configurar(rutas: { path: string; component: typeof LoginDePrueba }[] = []): void {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([sesionInterceptor])),
        provideHttpClientTesting(),
        provideRouter(rutas),
        ...proveerMaterial(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    abrirAviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
  }

  async function crear(): Promise<void> {
    sesion().iniciar('token-de-prueba', new Date(Date.now() + 3_600_000).toISOString());
    fixture = TestBed.createComponent(FormularioContrasenaComponent);
    fixture.componentRef.setInput('email', 'ana@ejemplo.com');
    await fixture.whenStable();
  }

  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    backend.verify();
    TestBed.inject(MatSnackBar).dismiss();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  describe('formulario', () => {
    beforeEach(async () => {
      configurar();
      navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      await crear();
    });

    it('tiene tres campos ocultos con sus etiquetas y el autocompletado correcto', () => {
      const etiquetas = Array.from(
        fixture.nativeElement.querySelectorAll(
          'app-campo-contrasena mat-label',
        ) as NodeListOf<HTMLElement>,
      ).map((e) => e.textContent?.trim());

      expect(campos()).toHaveLength(3);
      expect(campos().map((c) => c.type)).toEqual(['password', 'password', 'password']);
      expect(etiquetas).toEqual([
        'Contraseña actual',
        'Contraseña nueva',
        'Confirmar contraseña nueva',
      ]);
      expect(campos().map((c) => c.getAttribute('autocomplete'))).toEqual([
        'current-password',
        'new-password',
        'new-password',
      ]);
      expect(
        fixture.nativeElement.querySelectorAll('app-campo-contrasena button[aria-label]'),
      ).toHaveLength(3);
    });

    it('incluye un campo de usuario oculto con el correo para los gestores de contraseñas', () => {
      const usuario = fixture.nativeElement.querySelector('input[autocomplete="username"]');

      expect(usuario.value).toBe('ana@ejemplo.com');
      expect(usuario.readOnly).toBe(true);
      expect(usuario.getAttribute('aria-hidden')).toBe('true');
      expect(usuario.getAttribute('tabindex')).toBe('-1');
    });

    it('los tres son obligatorios y el botón está deshabilitado', async () => {
      await tocar(ACTUAL_I);
      await tocar(NUEVA_I);
      await tocar(CONFIRMACION_I);

      expect(errores()).toEqual([
        'La contraseña es obligatoria',
        'La contraseña es obligatoria',
        'La contraseña es obligatoria',
      ]);
      await escribir(ACTUAL_I, '        ');
      expect(errorDe(ACTUAL_I)).toBe('La contraseña es obligatoria');
      expect(boton().disabled).toBe(true);
    });

    it('rechaza una contraseña nueva de 7 caracteres', async () => {
      await escribir(NUEVA_I, '1234567');
      await tocar(NUEVA_I);

      expect(errorDe(NUEVA_I)).toBe('La contraseña debe tener al menos 8 caracteres');
    });

    it('acepta 36 letras ñ (72 bytes) y rechaza 37 (74 bytes)', async () => {
      await escribir(NUEVA_I, 'ñ'.repeat(36));
      await tocar(NUEVA_I);
      expect(errorDe(NUEVA_I)).toBeUndefined();

      await escribir(NUEVA_I, 'ñ'.repeat(37));
      expect(errorDe(NUEVA_I)).toBe(
        'La contraseña no puede ocupar más de 72 bytes (la ñ y las vocales con tilde ocupan 2)',
      );
    });

    it('una confirmación distinta muestra el mensaje y se revalida al editar la nueva', async () => {
      await completar(ACTUAL, NUEVA, 'otraClave457');
      await tocar(CONFIRMACION_I);
      expect(errorDe(CONFIRMACION_I)).toBe('Las contraseñas no coinciden');
      expect(boton().disabled).toBe(true);

      await escribir(CONFIRMACION_I, NUEVA);
      expect(errorDe(CONFIRMACION_I)).toBeUndefined();
      expect(boton().disabled).toBe(false);

      await escribir(NUEVA_I, 'otraClave4567');
      expect(errorDe(CONFIRMACION_I)).toBe('Las contraseñas no coinciden');
    });

    it('una nueva igual a la actual muestra el mensaje y no envía ninguna petición', async () => {
      await completar(ACTUAL, ACTUAL, ACTUAL);
      await tocar(NUEVA_I);

      expect(errorDe(NUEVA_I)).toBe('La contraseña nueva debe ser distinta de la actual');
      expect(boton().disabled).toBe(true);
      fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit'));
      await fixture.whenStable();
      backend.expectNone(URL);
    });

    it('con otra capitalización la nueva sí es distinta de la actual', async () => {
      await completar(ACTUAL, 'Secreta123');

      expect(errorDe(NUEVA_I)).toBeUndefined();
      expect(boton().disabled).toBe(false);
    });

    it('envía exactamente las dos contraseñas, sin recortar y sin la confirmación', async () => {
      await completar(ACTUAL, ' otraClave456 ');
      await enviar();

      const peticion = backend.expectOne(URL);
      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({
        contrasenaActual: ACTUAL,
        contrasenaNueva: ' otraClave456 ',
      });
      peticion.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('no envía dos veces con un segundo clic antes de la respuesta', async () => {
      await completar();

      boton().click();
      boton().click();
      await fixture.whenStable();

      const peticiones = backend.match(URL);
      expect(peticiones).toHaveLength(1);
      expect(boton().disabled).toBe(true);
      peticiones[0].flush(null, { status: 204, statusText: 'No Content' });
    });
  });

  describe('éxito (204)', () => {
    beforeEach(async () => {
      configurar();
      navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      await crear();
    });

    it('cierra la sesión local, va a /login, avisa y vacía el formulario', async () => {
      await completar();
      await enviar();

      responder(204);
      await fixture.whenStable();

      expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
      expect(sesion().haySesion()).toBe(false);
      expect(navegar).toHaveBeenCalledWith('/login');
      expect(abrirAviso).toHaveBeenCalledWith(AVISO, 'Cerrar', expect.anything());
      expect(valores()).toEqual(['', '', '']);
    });

    it('avisa solo después de navegar, no antes', async () => {
      let terminarNavegacion: (llego: boolean) => void = () => undefined;
      navegar.mockReturnValue(new Promise<boolean>((resolver) => (terminarNavegacion = resolver)));
      await completar();
      await enviar();

      responder(204);
      await fixture.whenStable();
      expect(navegar).toHaveBeenCalledWith('/login');
      expect(abrirAviso).not.toHaveBeenCalled();

      terminarNavegacion(true);
      await fixture.whenStable();

      expect(abrirAviso).toHaveBeenCalledTimes(1);
    });

    it('si la navegación no llega a /login no muestra el aviso', async () => {
      navegar.mockResolvedValue(false);
      await completar();
      await enviar();

      responder(204);
      await fixture.whenStable();

      expect(abrirAviso).not.toHaveBeenCalled();
    });

    it('no envía ninguna otra petición para invalidar el token anterior', async () => {
      await completar();
      await enviar();

      responder(204);
      await fixture.whenStable();

      backend.verify();
    });
  });

  describe('el aviso sobrevive a la navegación', () => {
    it('sigue visible en /login aunque el formulario ya no exista', async () => {
      configurar([{ path: 'login', component: LoginDePrueba }]);
      await crear();
      await completar();
      await enviar();

      responder(204);
      fixture.destroy();
      await vi.waitFor(() => expect(document.body.textContent).toContain(AVISO));

      expect(TestBed.inject(Router).url).toBe('/login');
    });
  });

  describe('errores', () => {
    beforeEach(async () => {
      configurar();
      navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      await crear();
    });

    it('422 REGLA_NEGOCIO_VIOLADA: mensaje en la actual, vacía, con foco y sin cerrar la sesión', async () => {
      await completar();
      await enviar();

      responder(422, { codigo: 'REGLA_NEGOCIO_VIOLADA', detail: 'Texto interno del servidor' });
      await fixture.whenStable();

      expect(errorDe(ACTUAL_I)).toBe('La contraseña actual es incorrecta');
      expect(valores()).toEqual(['', NUEVA, NUEVA]);
      expect(document.activeElement).toBe(campos()[ACTUAL_I]);
      expect(fixture.nativeElement.textContent).not.toContain('Texto interno del servidor');
      expect(sesion().haySesion()).toBe(true);
      expect(navegar).not.toHaveBeenCalled();
      expect(abrirAviso).not.toHaveBeenCalled();
    });

    it('el error de la actual se quita al escribir en ese campo', async () => {
      await completar();
      await enviar();
      responder(422, { codigo: 'REGLA_NEGOCIO_VIOLADA' });
      await fixture.whenStable();

      await escribir(ACTUAL_I, 'otraActual1');

      expect(errorDe(ACTUAL_I)).toBeUndefined();
      expect(boton().disabled).toBe(false);
    });

    it('400 con errores.contrasenaNueva: mensaje en la nueva, foco y sesión intacta', async () => {
      await completar();
      await enviar();

      responder(400, {
        codigo: 'DATOS_INVALIDOS',
        errores: { contrasenaNueva: 'La contraseña no es segura' },
      });
      await fixture.whenStable();

      expect(errorDe(NUEVA_I)).toBe('La contraseña no es segura');
      expect(document.activeElement).toBe(campos()[NUEVA_I]);
      expect(valores()).toEqual([ACTUAL, NUEVA, NUEVA]);
      expect(sesion().haySesion()).toBe(true);
    });

    it('400 con errores.contrasenaActual: mensaje en la actual y la vacía', async () => {
      await completar();
      await enviar();

      responder(400, {
        codigo: 'DATOS_INVALIDOS',
        errores: { contrasenaActual: 'No puede estar vacía' },
      });
      await fixture.whenStable();

      expect(errorDe(ACTUAL_I)).toBe('No puede estar vacía');
      expect(valores()).toEqual(['', NUEVA, NUEVA]);
      expect(document.activeElement).toBe(campos()[ACTUAL_I]);
    });

    it('una clave de errores sin campo, o sin errores, muestra el aviso genérico', async () => {
      await completar();
      await enviar();
      responder(400, { codigo: 'DATOS_INVALIDOS', errores: { otro: 'x' } });
      await fixture.whenStable();
      expect(abrirAviso).toHaveBeenLastCalledWith(
        MENSAJE_ERROR_GENERICO,
        'Cerrar',
        expect.anything(),
      );

      await enviar();
      responder(400, { codigo: 'DATOS_INVALIDOS' });
      await fixture.whenStable();
      expect(abrirAviso).toHaveBeenCalledTimes(2);
    });

    it('un 500 muestra el aviso genérico, conserva lo escrito y la sesión', async () => {
      await completar();
      await enviar();

      responder(500, { codigo: 'ERROR', detail: 'Detalle interno' });
      await fixture.whenStable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', expect.anything());
      expect(fixture.nativeElement.textContent).not.toContain('Detalle interno');
      expect(valores()).toEqual([ACTUAL, NUEVA, NUEVA]);
      expect(sesion().haySesion()).toBe(true);
      expect(boton().disabled).toBe(false);
    });

    it('un error de red muestra el aviso genérico', async () => {
      await completar();
      await enviar();

      backend.expectOne(URL).error(new ProgressEvent('error'));
      await fixture.whenStable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', expect.anything());
      expect(sesion().haySesion()).toBe(true);
    });

    it('un 401 real lo resuelve el interceptor: cierra la sesión y no avisa', async () => {
      await completar();
      await enviar();

      responder(401, { codigo: 'NO_AUTENTICADO' });
      await fixture.whenStable();

      expect(sesion().haySesion()).toBe(false);
      expect(navegar).toHaveBeenCalledWith('/login');
      expect(abrirAviso).not.toHaveBeenCalled();
    });
  });

  describe('las contraseñas no se conservan ni se registran', () => {
    beforeEach(async () => {
      configurar();
      navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
      await crear();
    });

    it('no escribe nada en la consola en un cambio correcto ni en uno rechazado', async () => {
      const espias = (['log', 'info', 'warn', 'error', 'debug'] as const).map((metodo) =>
        vi.spyOn(console, metodo).mockImplementation(() => undefined),
      );

      await completar();
      await enviar();
      responder(422, { codigo: 'REGLA_NEGOCIO_VIOLADA' });
      await fixture.whenStable();
      await completar();
      await enviar();
      responder(204);
      await fixture.whenStable();

      for (const espia of espias) {
        expect(espia).not.toHaveBeenCalled();
      }
    });

    it('el almacenamiento local no contiene ninguna de las contraseñas', async () => {
      await completar();
      await enviar();
      responder(422, { codigo: 'REGLA_NEGOCIO_VIOLADA' });
      await fixture.whenStable();

      const guardado = JSON.stringify({ ...localStorage });
      expect(guardado).not.toContain(ACTUAL);
      expect(guardado).not.toContain(NUEVA);
      expect(JSON.stringify({ ...sessionStorage })).not.toContain(NUEVA);
    });
  });
});
