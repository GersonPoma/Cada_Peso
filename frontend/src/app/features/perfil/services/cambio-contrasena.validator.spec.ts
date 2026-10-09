import { FormControl, FormGroup } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { coincideCon, distintaDe, revalidarAlCambiar } from './cambio-contrasena.validator';

function crear() {
  return new FormGroup({
    actual: new FormControl('', { nonNullable: true }),
    nueva: new FormControl('', { nonNullable: true, validators: [distintaDe('actual')] }),
    confirmacion: new FormControl('', { nonNullable: true, validators: [coincideCon('nueva')] }),
  });
}

describe('coincideCon', () => {
  it('es válido cuando la confirmación es igual a la nueva', () => {
    const f = crear();
    f.controls.nueva.setValue('otraClave456');
    f.controls.confirmacion.setValue('otraClave456');

    expect(f.controls.confirmacion.errors).toBeNull();
  });

  it('marca noCoincide cuando es distinta, también por una mayúscula o un espacio', () => {
    const f = crear();
    f.controls.nueva.setValue('otraClave456');

    f.controls.confirmacion.setValue('otraClave457');
    expect(f.controls.confirmacion.errors).toEqual({ noCoincide: true });

    f.controls.confirmacion.setValue('OtraClave456');
    expect(f.controls.confirmacion.errors).toEqual({ noCoincide: true });

    f.controls.confirmacion.setValue('otraClave456 ');
    expect(f.controls.confirmacion.errors).toEqual({ noCoincide: true });
  });

  it('no opina sobre un valor vacío (eso es de required)', () => {
    const f = crear();
    f.controls.nueva.setValue('otraClave456');
    f.controls.confirmacion.setValue('');

    expect(f.controls.confirmacion.errors).toBeNull();
  });

  it('sin formulario padre no falla', () => {
    const solo = new FormControl('x', { validators: [coincideCon('nueva')] });

    expect(solo.errors).toBeNull();
  });

  it('se revalida cuando cambia la nueva si se enlaza con revalidarAlCambiar', () => {
    const f = crear();
    revalidarAlCambiar(f.controls.nueva, f.controls.confirmacion);
    f.controls.nueva.setValue('otraClave456');
    f.controls.confirmacion.setValue('otraClave456');
    expect(f.controls.confirmacion.errors).toBeNull();

    f.controls.nueva.setValue('otraClave4567');

    expect(f.controls.confirmacion.errors).toEqual({ noCoincide: true });
  });
});

describe('distintaDe', () => {
  it('marca igualALaActual cuando la nueva es idéntica a la actual', () => {
    const f = crear();
    f.controls.actual.setValue('secreta123');
    f.controls.nueva.setValue('secreta123');

    expect(f.controls.nueva.errors).toEqual({ igualALaActual: true });
  });

  it('distingue mayúsculas: Secreta123 no es igual a secreta123', () => {
    const f = crear();
    f.controls.actual.setValue('secreta123');
    f.controls.nueva.setValue('Secreta123');

    expect(f.controls.nueva.errors).toBeNull();
  });

  it('no opina sobre un valor vacío', () => {
    const f = crear();

    expect(f.controls.nueva.errors).toBeNull();
  });

  it('se revalida al cambiar la actual si se enlaza con revalidarAlCambiar', () => {
    const f = crear();
    revalidarAlCambiar(f.controls.actual, f.controls.nueva);
    f.controls.nueva.setValue('secreta123');
    f.controls.actual.setValue('otra-cosa-1');
    expect(f.controls.nueva.errors).toBeNull();

    f.controls.actual.setValue('secreta123');

    expect(f.controls.nueva.errors).toEqual({ igualALaActual: true });
  });

  it('dejar de enlazar detiene la revalidación', () => {
    const f = crear();
    const suscripcion = revalidarAlCambiar(f.controls.actual, f.controls.nueva);
    f.controls.nueva.setValue('secreta123');
    suscripcion.unsubscribe();

    f.controls.actual.setValue('secreta123');

    expect(f.controls.nueva.errors).toBeNull();
  });
});
