import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { CuentaImportacion } from '../models/cuenta-importacion.model';
import { ImportacionResponse } from '../models/importacion-response.model';
import { PasoResultadoComponent } from './paso-resultado.component';

const CUENTA: CuentaImportacion = {
  id: 5,
  nombre: 'Banco',
  tipo: 'CORRIENTE',
  enPresupuesto: true,
  cerrada: false,
};
const RESULTADO: ImportacionResponse = {
  importadas: 2,
  duplicadas: 3,
  omitidas: 1,
  sinCategoria: 2,
};

describe('PasoResultadoComponent', () => {
  let fixture: ComponentFixture<PasoResultadoComponent>;

  function montar(cuenta: CuentaImportacion = CUENTA) {
    fixture = TestBed.createComponent(PasoResultadoComponent);
    fixture.componentRef.setInput('resultado', RESULTADO);
    fixture.componentRef.setInput('cuenta', cuenta);
    fixture.componentRef.setInput('presupuestoId', 3);
    fixture.detectChanges();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const texto = () => (el().textContent ?? '').replace(/\s+/g, ' ');
  const cifra = (clave: string) =>
    (el().querySelector(`li.${clave}`)?.textContent ?? '').replace(/\s+/g, ' ').trim();

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
  });

  it('muestra las cuatro cifras, con Inválidas omitidas rotulada así', () => {
    montar();

    expect(cifra('importadas')).toBe('2 Importadas');
    expect(cifra('duplicadas')).toBe('3 Duplicadas');
    expect(cifra('omitidas')).toBe('1 Inválidas omitidas');
    expect(cifra('sin-categoria')).toBe('2 Sin categoría');
  });

  it('el resultado se anuncia con aria-live', () => {
    montar();

    const region = el().querySelector('[aria-live]');
    expect(region?.getAttribute('aria-live')).toBe('polite');
    expect(region?.textContent).toContain('Importadas');
  });

  it('la nota depende de la cuenta', () => {
    montar();
    expect(texto()).toContain('suben Listo para asignar');

    montar({ ...CUENTA, tipo: 'TARJETA_CREDITO' });
    expect(texto()).toContain('Importar a una tarjeta de crédito no cambia Listo para asignar');

    montar({ ...CUENTA, enPresupuesto: false });
    expect(texto()).toContain('Importar a una cuenta de seguimiento no cambia Listo para asignar');
  });

  it('el enlace lleva a las transacciones sin aprobar de la cuenta', () => {
    montar();

    const enlace = el().querySelector('a.ver-transacciones') as HTMLAnchorElement;
    expect(enlace.textContent).toContain('Ver transacciones sin aprobar');
    expect(enlace.getAttribute('href')).toBe(
      '/presupuestos/3/transacciones?cuentaId=5&sinAprobar=1',
    );
  });
});
