import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSelectHarness } from '@angular/material/select/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { SesionService } from '../../../core/sesion/sesion.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { RegistroPage } from './registro.page';

declare const process: { env: Record<string, string | undefined> };

/** Zona horaria que fija `src/test-setup.ts` para toda la suite. */
const ZONA_HORARIA_DE_LA_SUITE = 'America/New_York';
const URL_REGISTRO = '/api/v1/auth/registro';
const RESPUESTA_TOKEN = { token: 'abc', tipo: 'Bearer', expiraEn: '2099-10-07T12:00:00Z' };

describe('RegistroPage', () => {
  let fixture: ComponentFixture<RegistroPage>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;
  let navegar: ReturnType<typeof vi.spyOn>;
  let abrirAviso: ReturnType<typeof vi.spyOn>;

  const campo = (etiqueta: string): HTMLElement => {
    const campos = Array.from(
      fixture.nativeElement.querySelectorAll('mat-form-field') as NodeListOf<HTMLElement>,
    );
    const encontrado = campos.find(
      (c) => c.querySelector('mat-label')?.textContent?.trim() === etiqueta,
    );
    if (!encontrado) {
      throw new Error(`No hay un campo con la etiqueta "${etiqueta}"`);
    }
    return encontrado;
  };
  const entrada = (etiqueta: string) => campo(etiqueta).querySelector('input') as HTMLInputElement;
  const error = (etiqueta: string): string | undefined =>
    campo(etiqueta).querySelector('mat-error')?.textContent?.trim();
  const botonEnviar = () =>
    fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;

  async function escribir(etiqueta: string, valor: string): Promise<void> {
    const input = entrada(etiqueta);
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function tocar(etiqueta: string): Promise<void> {
    entrada(etiqueta).dispatchEvent(new Event('blur'));
    await fixture.whenStable();
  }

  /** Escribe un valor, sale del campo y devuelve el mensaje de error que queda visible. */
  async function errorAlEscribir(etiqueta: string, valor: string): Promise<string | undefined> {
    await escribir(etiqueta, valor);
    await tocar(etiqueta);
    return error(etiqueta);
  }

  async function completar(cambios: Partial<Record<string, string>> = {}): Promise<void> {
    const valores: Record<string, string> = {
      Email: 'ana@ejemplo.com',
      Contraseña: 'secreta123',
      Nombre: 'Ana',
      Apellido: 'Pérez',
      'Fecha de nacimiento': '05/03/2003',
      ...cambios,
    };
    for (const [etiqueta, valor] of Object.entries(valores)) {
      await escribir(etiqueta, valor);
    }
  }

  async function enviar(): Promise<void> {
    botonEnviar().click();
    await fixture.whenStable();
  }

  async function crearPagina(region = 'es-BO'): Promise<void> {
    vi.stubGlobal('navigator', { language: region, userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        ...proveerMaterial(),
      ],
    });
    backend = TestBed.inject(HttpTestingController);
    navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    abrirAviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open');
    fixture = TestBed.createComponent(RegistroPage);
    cargador = TestbedHarnessEnvironment.loader(fixture);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    localStorage.clear();
    await crearPagina();
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
    localStorage.clear();
    process.env['TZ'] = ZONA_HORARIA_DE_LA_SUITE;
  });

  describe('formulario vacío', () => {
    it('muestra las etiquetas en español y la cabecera Cada Peso', () => {
      const etiquetas = Array.from(
        fixture.nativeElement.querySelectorAll('mat-label') as NodeListOf<HTMLElement>,
      ).map((e) => e.textContent?.trim());

      expect(etiquetas).toEqual([
        'Email',
        'Contraseña',
        'Nombre',
        'Apellido',
        'Fecha de nacimiento',
        'Teléfono (opcional)',
        'Moneda',
      ]);
      expect(fixture.nativeElement.querySelector('app-cabecera').textContent).toContain(
        'Cada Peso',
      );
    });

    it('deshabilita el botón de enviar', () => {
      expect(botonEnviar().disabled).toBe(true);
    });

    it('al tocar los campos obligatorios muestra que lo son', async () => {
      for (const etiqueta of ['Email', 'Contraseña', 'Nombre', 'Apellido', 'Fecha de nacimiento']) {
        await tocar(etiqueta);
      }

      expect(error('Email')).toBe('El email es obligatorio');
      expect(error('Contraseña')).toBe('La contraseña es obligatoria');
      expect(error('Nombre')).toBe('El nombre es obligatorio');
      expect(error('Apellido')).toBe('El apellido es obligatorio');
      expect(error('Fecha de nacimiento')).toBe('La fecha de nacimiento es obligatoria');
    });
  });

  describe('email', () => {
    it.each([
      ['solo espacios', '   ', 'El email es obligatorio'],
      ['sin formato de email', 'abc', 'Ingresa un email válido'],
      [
        '255 caracteres',
        'a'.repeat(243) + '@ejemplo.com',
        'El email no puede superar 254 caracteres',
      ],
      ['espacios alrededor de un email válido', '  ana@ejemplo.com  ', undefined],
    ])('%s', async (_caso, valor, esperado) => {
      expect(await errorAlEscribir('Email', valor)).toBe(esperado);
    });
  });

  describe('contraseña', () => {
    it.each([
      ['8 espacios', ' '.repeat(8), 'La contraseña es obligatoria'],
      ['7 caracteres', 'abcdefg', 'La contraseña debe tener al menos 8 caracteres'],
      [
        'ñ más 71 letras a (73 bytes)',
        'ñ' + 'a'.repeat(71),
        'La contraseña no puede ocupar más de 72 bytes (la ñ y las vocales con tilde ocupan 2)',
      ],
      ['72 letras a (72 bytes)', 'a'.repeat(72), undefined],
      ['36 letras ñ (72 bytes)', 'ñ'.repeat(36), undefined],
      ['espacios alrededor', ' secreta123 ', undefined],
    ])('%s', async (_caso, valor, esperado) => {
      expect(await errorAlEscribir('Contraseña', valor)).toBe(esperado);
    });
  });

  describe.each([
    ['Nombre', 'nombre'],
    ['Apellido', 'apellido'],
  ])('%s', (etiqueta, palabra) => {
    it.each([
      ['solo espacios', '   ', `El ${palabra} es obligatorio`],
      ['un carácter entre espacios', '  A  ', `El ${palabra} debe tener al menos 2 caracteres`],
      ['101 caracteres', 'a'.repeat(101), `El ${palabra} no puede superar 100 caracteres`],
      ['2 caracteres', 'Al', undefined],
    ])('%s', async (_caso, valor, esperado) => {
      expect(await errorAlEscribir(etiqueta, valor)).toBe(esperado);
    });
  });

  describe('teléfono', () => {
    it.each([
      ['21 caracteres', '1'.repeat(21), 'El teléfono no puede superar 20 caracteres'],
      ['20 caracteres con espacios alrededor', ` ${'1'.repeat(20)} `, undefined],
      ['vacío', '', undefined],
    ])('%s', async (_caso, valor, esperado) => {
      expect(await errorAlEscribir('Teléfono (opcional)', valor)).toBe(esperado);
    });
  });

  describe('fecha de nacimiento', () => {
    it('31/02/2003 en es-BO muestra que la fecha no es válida', async () => {
      expect(await errorAlEscribir('Fecha de nacimiento', '31/02/2003')).toBe(
        'La fecha no es válida',
      );
    });

    it.each([
      ['cumple 18 hoy', '06/10/2008', undefined],
      ['cumple 18 mañana', '07/10/2008', 'Debe tener 18 años o más'],
      ['una fecha futura', '01/01/2030', 'Debe tener 18 años o más'],
    ])('con hoy el 6 de octubre de 2026: %s', async (_caso, valor, esperado) => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 9, 6, 12));

      expect(await errorAlEscribir('Fecha de nacimiento', valor)).toBe(esperado);
    });

    it('el nacido un 29 de febrero tiene 17 años el 28 de febrero y 18 el 1 de marzo', async () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date(2026, 1, 28, 12));
      expect(await errorAlEscribir('Fecha de nacimiento', '29/02/2008')).toBe(
        'Debe tener 18 años o más',
      );

      vi.setSystemTime(new Date(2026, 2, 1, 12));
      await escribir('Fecha de nacimiento', '');
      expect(await errorAlEscribir('Fecha de nacimiento', '29/02/2008')).toBeUndefined();
    });
  });

  describe('moneda', () => {
    it('ofrece BOB por defecto', async () => {
      const moneda = await cargador.getHarness(MatSelectHarness);

      expect(await moneda.getValueText()).toBe('BOB - Boliviano');
    });
  });

  describe('petición de registro', () => {
    it('envía el email recortado y en minúsculas, la contraseña intacta y los textos recortados', async () => {
      await completar({
        Email: '  Ana@Ejemplo.COM ',
        Contraseña: ' secreta123 ',
        Nombre: '  Ana ',
        Apellido: ' Pérez  ',
        'Teléfono (opcional)': '  70123456 ',
      });

      await enviar();

      const peticion = backend.expectOne(URL_REGISTRO);
      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({
        email: 'ana@ejemplo.com',
        contrasena: ' secreta123 ',
        nombre: 'Ana',
        apellido: 'Pérez',
        fechaNacimiento: '2003-03-05',
        telefono: '70123456',
        monedaPredeterminada: 'BOB',
      });
      peticion.flush(RESPUESTA_TOKEN);
    });

    it('omite el teléfono si queda vacío tras recortar', async () => {
      await completar({ 'Teléfono (opcional)': '   ' });

      await enviar();

      const peticion = backend.expectOne(URL_REGISTRO);
      expect('telefono' in peticion.request.body).toBe(false);
      peticion.flush(RESPUESTA_TOKEN);
    });

    it('envía la moneda elegida', async () => {
      await completar();
      const moneda = await cargador.getHarness(MatSelectHarness);
      await moneda.clickOptions({ text: /USD/ });

      await enviar();

      const peticion = backend.expectOne(URL_REGISTRO);
      expect(peticion.request.body.monedaPredeterminada).toBe('USD');
      peticion.flush(RESPUESTA_TOKEN);
    });

    it('escribir 05/03/2003 en es-BO envía 2003-03-05', async () => {
      await completar({ 'Fecha de nacimiento': '05/03/2003' });

      await enviar();

      const peticion = backend.expectOne(URL_REGISTRO);
      expect(peticion.request.body.fechaNacimiento).toBe('2003-03-05');
      peticion.flush(RESPUESTA_TOKEN);
    });

    it.each(['America/New_York', 'Asia/Tokyo'])(
      'el 1 de octubre de 2008 se envía como 2008-10-01 en %s',
      async (zona) => {
        process.env['TZ'] = zona;
        await completar({ 'Fecha de nacimiento': '01/10/2008' });

        await enviar();

        const peticion = backend.expectOne(URL_REGISTRO);
        expect(peticion.request.body.fechaNacimiento).toBe('2008-10-01');
        peticion.flush(RESPUESTA_TOKEN);
      },
    );

    it('deshabilita el botón mientras la petición está pendiente', async () => {
      await completar();

      await enviar();

      expect(botonEnviar().disabled).toBe(true);
      backend.expectOne(URL_REGISTRO).flush(RESPUESTA_TOKEN);
    });
  });

  describe('respuestas del API', () => {
    it('un 201 guarda la sesión, navega a / y no llama al login', async () => {
      await completar();
      await enviar();

      backend
        .expectOne(URL_REGISTRO)
        .flush(RESPUESTA_TOKEN, { status: 201, statusText: 'Created' });
      await fixture.whenStable();

      expect(TestBed.inject(SesionService).token()).toBe('abc');
      expect(navegar).toHaveBeenCalledWith('/');
      backend.expectNone('/api/v1/auth/login');
    });

    it('DATOS_INVALIDOS muestra cada mensaje en su campo y el error se borra al editar', async () => {
      await completar();
      await enviar();

      backend.expectOne(URL_REGISTRO).flush(
        {
          codigo: 'DATOS_INVALIDOS',
          errores: { contrasena: 'Mensaje de contraseña', fechaNacimiento: 'Mensaje de fecha' },
        },
        { status: 400, statusText: 'Bad Request' },
      );
      await fixture.whenStable();

      expect(error('Contraseña')).toBe('Mensaje de contraseña');
      expect(error('Fecha de nacimiento')).toBe('Mensaje de fecha');
      expect(botonEnviar().disabled).toBe(true);

      await escribir('Contraseña', 'otraSecreta1');

      expect(error('Contraseña')).toBeUndefined();
      expect(error('Fecha de nacimiento')).toBe('Mensaje de fecha');
      expect(abrirAviso).not.toHaveBeenCalled();
    });

    it.each([
      ['una clave de errores sin campo', { codigo: 'DATOS_INVALIDOS', errores: { otro: 'x' } }],
      ['un DATOS_INVALIDOS sin errores', { codigo: 'DATOS_INVALIDOS' }],
    ])('%s muestra el aviso genérico', async (_caso, cuerpo) => {
      await completar();
      await enviar();

      backend.expectOne(URL_REGISTRO).flush(cuerpo, { status: 400, statusText: 'Bad Request' });
      await fixture.whenStable();

      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', { duration: 6000 });
    });

    it('EMAIL_YA_REGISTRADO muestra el mensaje en el campo email', async () => {
      await completar();
      await enviar();

      backend
        .expectOne(URL_REGISTRO)
        .flush({ codigo: 'EMAIL_YA_REGISTRADO' }, { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();

      expect(error('Email')).toBe('Ya existe una cuenta con ese email');
      expect(abrirAviso).not.toHaveBeenCalled();
      expect(TestBed.inject(SesionService).haySesion()).toBe(false);
    });

    it.each([
      [
        'un 500',
        (peticion: { flush: Function }) =>
          peticion.flush({}, { status: 500, statusText: 'Internal Server Error' }),
      ],
      [
        'un error de red',
        (peticion: { error: Function }) => peticion.error(new ProgressEvent('error')),
      ],
    ])(
      'ante %s muestra el aviso, conserva lo escrito y habilita el botón',
      async (_caso, fallar) => {
        await completar();
        await enviar();

        fallar(backend.expectOne(URL_REGISTRO) as never);
        await fixture.whenStable();

        expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Cerrar', {
          duration: 6000,
        });
        expect(entrada('Email').value).toBe('ana@ejemplo.com');
        expect(entrada('Nombre').value).toBe('Ana');
        expect(botonEnviar().disabled).toBe(false);
      },
    );
  });

  it('ofrece un enlace a /login', () => {
    const enlace = fixture.nativeElement.querySelector('a') as HTMLAnchorElement;

    expect(enlace.textContent?.trim()).toBe('Inicia sesión');
    expect(enlace.getAttribute('href')).toBe('/login');
  });
});
