import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { PerfilResponse } from '../models/perfil-response.model';
import { DatosPerfilComponent } from './datos-perfil.component';

const PERFIL: PerfilResponse = {
  email: 'ana@ejemplo.com',
  rol: 'USUARIO',
  nombre: 'Ana',
  apellido: 'Pérez',
  fechaNacimiento: '2003-03-05',
  telefono: null,
  monedaPredeterminada: 'BOB',
};

describe('DatosPerfilComponent', () => {
  let fixture: ComponentFixture<DatosPerfilComponent>;

  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const pares = () =>
    Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('.dato')).map((dato) => [
      dato.querySelector('dt')?.textContent?.trim(),
      dato.querySelector('dd')?.textContent?.replace(/\s+/g, ' ').trim(),
    ]);

  async function crear(perfil: PerfilResponse = PERFIL): Promise<void> {
    fixture = TestBed.createComponent(DatosPerfilComponent);
    fixture.componentRef.setInput('perfil', perfil);
    await fixture.whenStable();
  }

  beforeEach(() => {
    vi.stubGlobal('navigator', { language: 'es-BO' });
    TestBed.configureTestingModule({});
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('muestra correo, apellido, fecha, teléfono ausente y moneda con su nombre', async () => {
    TestBed.inject(PresupuestoActivoService).fijar({ id: 3, nombre: 'Casa', moneda: 'USD' });

    await crear();

    const datos = pares();
    expect(datos).toContainEqual(['Correo', 'ana@ejemplo.com']);
    expect(datos).toContainEqual(['Apellido', 'Pérez']);
    expect(datos).toContainEqual(['Teléfono', 'No registrado']);
    expect(datos).toContainEqual(['Presupuesto actual', 'Casa (USD)']);
    const fecha = datos.find(([etiqueta]) => etiqueta === 'Fecha de nacimiento');
    expect(fecha?.[1]).toContain('2003');
    const moneda = datos.find(([etiqueta]) => etiqueta === 'Moneda predeterminada');
    expect(moneda?.[1]).toContain('BOB · Boliviano');
    expect(moneda?.[1]).toContain('Cada presupuesto tiene su propia moneda');
  });

  it('muestra el teléfono cuando existe', async () => {
    await crear({ ...PERFIL, telefono: '+591 70000000' });

    expect(pares()).toContainEqual(['Teléfono', '+591 70000000']);
  });

  it('omite el presupuesto actual cuando no hay uno fijado, sin fallar', async () => {
    await crear();

    expect(pares().map(([etiqueta]) => etiqueta)).not.toContain('Presupuesto actual');
  });

  it('es solo lectura: no hay controles de entrada ni se muestra el rol', async () => {
    await crear();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('input, textarea, select, button')).toHaveLength(0);
    expect(texto()).not.toContain('USUARIO');
    expect(texto()).not.toContain('Rol');
  });
});
