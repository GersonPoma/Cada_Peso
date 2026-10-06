import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ValidatorFn, Validators } from '@angular/forms';
import { beforeEach, describe, expect, it } from 'vitest';
import { maxBytesUtf8 } from '../../../shared/validacion/max-bytes-utf8.validator';
import { sobreTextoRecortado } from '../../../shared/validacion/sobre-texto-recortado.validator';
import { CampoContrasenaComponent } from './campo-contrasena.component';

describe('CampoContrasenaComponent', () => {
  let fixture: ComponentFixture<CampoContrasenaComponent>;

  async function crear(validadores: ValidatorFn[], valor = ''): Promise<FormControl<string>> {
    const control = new FormControl(valor, { nonNullable: true, validators: validadores });
    fixture = TestBed.createComponent(CampoContrasenaComponent);
    fixture.componentRef.setInput('control', control);
    await fixture.whenStable();
    return control;
  }

  const entrada = () => fixture.nativeElement.querySelector('input') as HTMLInputElement;
  const boton = () => fixture.nativeElement.querySelector('button') as HTMLButtonElement;
  const icono = () => fixture.nativeElement.querySelector('mat-icon') as HTMLElement;
  const error = () =>
    (fixture.nativeElement.querySelector('mat-error') as HTMLElement | null)?.textContent?.trim();

  async function pulsarBoton(): Promise<void> {
    boton().click();
    await fixture.whenStable();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  describe('mostrar y ocultar', () => {
    it('muestra la contraseña oculta por defecto, con el ícono y la etiqueta de mostrar', async () => {
      await crear([]);

      expect(entrada().type).toBe('password');
      expect(icono().textContent?.trim()).toBe('visibility');
      expect(boton().getAttribute('aria-label')).toBe('Mostrar contraseña');
      expect(boton().getAttribute('aria-pressed')).toBe('false');
      expect(boton().type).toBe('button');
    });

    it('al pulsar muestra el texto, con el ícono y la etiqueta de ocultar, sin cambiar el valor', async () => {
      const control = await crear([], 'secreta123');

      await pulsarBoton();

      expect(entrada().type).toBe('text');
      expect(entrada().value).toBe('secreta123');
      expect(icono().textContent?.trim()).toBe('visibility_off');
      expect(boton().getAttribute('aria-label')).toBe('Ocultar contraseña');
      expect(boton().getAttribute('aria-pressed')).toBe('true');
      expect(control.value).toBe('secreta123');
    });

    it('al pulsar otra vez vuelve a ocultarla y el valor sigue igual', async () => {
      const control = await crear([], 'secreta123');

      await pulsarBoton();
      await pulsarBoton();

      expect(entrada().type).toBe('password');
      expect(icono().textContent?.trim()).toBe('visibility');
      expect(control.value).toBe('secreta123');
    });
  });

  describe('mensajes de error', () => {
    const reglasDeRegistro = [
      sobreTextoRecortado(Validators.required),
      Validators.minLength(8),
      maxBytesUtf8(72),
    ];

    it('no muestra ningún error mientras el campo no se haya tocado', async () => {
      await crear(reglasDeRegistro);

      expect(error()).toBeUndefined();
    });

    it('muestra el mensaje de obligatoria con el campo tocado y vacío', async () => {
      const control = await crear(reglasDeRegistro);

      control.markAsTouched();
      await fixture.whenStable();

      expect(error()).toBe('La contraseña es obligatoria');
    });

    it('muestra el mensaje de longitud mínima', async () => {
      const control = await crear(reglasDeRegistro);

      control.setValue('1234567');
      control.markAsTouched();
      await fixture.whenStable();

      expect(error()).toBe('La contraseña debe tener al menos 8 caracteres');
    });

    it('muestra el mensaje de 72 bytes con una ñ y 71 letras a', async () => {
      const control = await crear(reglasDeRegistro);

      control.setValue('ñ' + 'a'.repeat(71));
      control.markAsTouched();
      await fixture.whenStable();

      expect(error()).toBe(
        'La contraseña no puede ocupar más de 72 bytes (la ñ y las vocales con tilde ocupan 2)',
      );
    });

    it('muestra el error que el padre pone a mano, como el que llega del backend', async () => {
      const control = await crear(reglasDeRegistro, 'secreta123');

      control.setErrors({ servidor: 'Mensaje del backend' });
      control.markAsTouched();
      await fixture.whenStable();

      expect(error()).toBe('Mensaje del backend');
    });

    it('con un control que solo tiene required no muestra reglas de longitud', async () => {
      const control = await crear([sobreTextoRecortado(Validators.required)]);

      control.setValue('x');
      control.markAsTouched();
      await fixture.whenStable();

      expect(error()).toBeUndefined();

      control.setValue('');
      await fixture.whenStable();

      expect(error()).toBe('La contraseña es obligatoria');
    });
  });

  it('usa la etiqueta y el autocompletado recibidos', async () => {
    await crear([]);
    fixture.componentRef.setInput('etiqueta', 'Nueva contraseña');
    fixture.componentRef.setInput('autocompletar', 'new-password');
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('mat-label').textContent).toContain(
      'Nueva contraseña',
    );
    expect(entrada().getAttribute('autocomplete')).toBe('new-password');
  });
});
