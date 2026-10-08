import { HarnessLoader, TestKey } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, Validators } from '@angular/forms';
import { MatAutocompleteHarness } from '@angular/material/autocomplete/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import { sobreTextoRecortado } from '../../../shared/validacion/sobre-texto-recortado.validator';
import { BeneficiarioSugerido } from '../models/beneficiario-sugerido.model';
import { CampoBeneficiarioComponent } from './campo-beneficiario.component';

const URL = '/api/v1/presupuestos/3/beneficiarios';
const GRUPOS = [
  {
    id: 1,
    nombre: 'Gustos',
    oculto: false,
    categorias: [{ id: 7, nombre: 'Ocio', oculta: false }],
  },
];
const NESTLE = { id: 2, nombre: 'Nestlé', categoriaPredeterminadaId: 99 };
const NETFLIX = { id: 4, nombre: 'Netflix', categoriaPredeterminadaId: 7 };

@Component({
  imports: [CampoBeneficiarioComponent],
  template: `<app-campo-beneficiario
    [control]="beneficiario"
    [grupos]="grupos"
    (elegido)="elegidos.push($event)"
  />`,
})
class AnfitrionDePrueba {
  readonly beneficiario = new FormControl('', {
    nonNullable: true,
    validators: [sobreTextoRecortado(Validators.maxLength(100))],
  });
  readonly grupos = GRUPOS;
  readonly elegidos: BeneficiarioSugerido[] = [];
}

describe('CampoBeneficiarioComponent', () => {
  let fixture: ComponentFixture<AnfitrionDePrueba>;
  let cargador: HarnessLoader;
  let backend: HttpTestingController;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const entrada = () => elemento().querySelector('input') as HTMLInputElement;
  const autocompletado = () => cargador.getHarness(MatAutocompleteHarness);
  const busquedas = () => backend.match((p) => p.url === URL);

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  /** Escribe como la persona (el input dispara `valueChanges`), sin esperar la búsqueda. */
  async function escribir(texto: string): Promise<void> {
    entrada().focus();
    entrada().value = texto;
    entrada().dispatchEvent(new Event('input'));
    await estable();
  }

  /** Deja pasar la espera de las sugerencias. */
  async function esperar(ms = 250): Promise<void> {
    await vi.advanceTimersByTimeAsync(ms);
    await estable();
  }

  async function sugerir(texto: string, lista: BeneficiarioSugerido[]): Promise<void> {
    await escribir(texto);
    await esperar();
    const [peticion] = busquedas();
    peticion.flush(lista);
    await esperar(50);
  }

  /** Textos de las opciones visibles; ninguna si el panel está cerrado (no hay sugerencias). */
  async function textosOpciones(): Promise<string[]> {
    await esperar(50);
    const harness = await autocompletado();
    if (!(await harness.isOpen())) {
      return [];
    }
    return Promise.all((await harness.getOptions()).map((o) => o.getText()));
  }

  beforeEach(async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.stubGlobal('navigator', { language: 'es-BO', userAgent: '' });
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        ...proveerMaterial(),
        { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } },
      ],
    });
    TestBed.inject(PresupuestoActivoService).fijar({ id: 3, nombre: 'Casa', moneda: 'BOB' });
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AnfitrionDePrueba);
    cargador = TestbedHarnessEnvironment.documentRootLoader(fixture);
    await estable();
  });

  afterEach(() => {
    backend.verify();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('hace una sola petición 250 ms después de la última tecla', async () => {
    await escribir('n');
    await esperar(100);
    await escribir('ne');
    await esperar(100);
    await escribir('net');
    await esperar(200);
    expect(busquedas()).toEqual([]);

    await esperar(50);
    const peticiones = busquedas();
    expect(peticiones).toHaveLength(1);
    expect(peticiones[0].request.params.get('q')).toBe('net');
    expect(peticiones[0].request.params.get('limite')).toBe('10');
    peticiones[0].flush([]);
  });

  it('busca con el texto recortado', async () => {
    await escribir('  net ');
    await esperar();

    const [peticion] = busquedas();
    expect(peticion.request.params.get('q')).toBe('net');
    peticion.flush([]);
  });

  it('con el campo vacío o solo espacios no pide nada', async () => {
    entrada().focus();
    await esperar();
    await escribir('   ');
    await esperar();

    expect(busquedas()).toEqual([]);
    expect(await textosOpciones()).toEqual([]);
  });

  it('ignora la respuesta de una búsqueda vieja', async () => {
    await escribir('ne');
    await esperar();
    const [vieja] = busquedas();
    await escribir('net');
    await esperar();
    const [nueva] = busquedas();

    nueva.flush([NETFLIX]);
    expect(vieja.cancelled).toBe(true);
    await estable();

    expect(await textosOpciones()).toEqual(['NetflixOcio']);
  });

  it('un error de red deja la lista vacía y el campo editable', async () => {
    await escribir('net');
    await esperar();
    busquedas()[0].error(new ProgressEvent('error'));
    await estable();

    expect(await textosOpciones()).toEqual([]);
    expect(entrada().disabled).toBe(false);
    expect(elemento().textContent).toContain('Se creará un beneficiario nuevo');
  });

  it('muestra la categoría predeterminada si existe y resalta el prefijo', async () => {
    await sugerir('ne', [NESTLE, NETFLIX]);

    expect(await textosOpciones()).toEqual(['Nestlé', 'NetflixOcio']);
    const resaltados = Array.from(document.querySelectorAll('mat-option strong')).map(
      (e) => e.textContent,
    );
    expect(resaltados).toEqual(['Ne', 'Ne']);
    expect(document.querySelectorAll('mat-option .categoria')).toHaveLength(1);
  });

  it('avisa que se creará un beneficiario si no hay coincidencia exacta', async () => {
    await sugerir('Panadería Sol', []);

    expect(elemento().querySelector('.pista-nuevo')?.textContent).toBe(
      'Se creará un beneficiario nuevo',
    );
  });

  it('no avisa con una coincidencia exacta sin distinguir mayúsculas', async () => {
    await sugerir('  NETFLIX ', [NETFLIX]);

    expect(elemento().querySelector('.pista-nuevo')).toBeNull();
  });

  it('elegir una opción rellena el texto y emite la sugerencia', async () => {
    await sugerir('ne', [NESTLE, NETFLIX]);
    await (await autocompletado()).selectOption({ text: /Netflix/ });
    await estable();

    expect(fixture.componentInstance.beneficiario.value).toBe('Netflix');
    expect(fixture.componentInstance.elegidos).toEqual([NETFLIX]);
    // Elegir cambia el texto y se vuelve a buscar con el nombre completo.
    await esperar();
    busquedas().forEach((p) => p.flush([NETFLIX]));
  });

  it('se elige con las flechas y Enter', async () => {
    await sugerir('ne', [NESTLE, NETFLIX]);
    const harness = await autocompletado();
    const host = await harness.host();
    await host.sendKeys(TestKey.DOWN_ARROW);
    await host.sendKeys(TestKey.DOWN_ARROW);
    await host.sendKeys(TestKey.ENTER);
    await estable();

    expect(fixture.componentInstance.beneficiario.value).toBe('Netflix');
    await esperar();
    busquedas().forEach((p) => p.flush([NETFLIX]));
  });

  it('Escape cierra las sugerencias y conserva el texto', async () => {
    await sugerir('ne', [NESTLE, NETFLIX]);
    const harness = await autocompletado();
    expect(await harness.isOpen()).toBe(true);

    await (await harness.host()).sendKeys(TestKey.ESCAPE);
    await estable();

    expect(await harness.isOpen()).toBe(false);
    expect(fixture.componentInstance.beneficiario.value).toBe('ne');
  });

  it('cuenta los caracteres y marca más de 100', async () => {
    await escribir('  hola ');
    expect(elemento().textContent).toContain('4/100');

    await escribir('a'.repeat(101));
    entrada().dispatchEvent(new Event('blur'));
    await esperar();
    busquedas().forEach((p) => p.flush([]));
    await estable();

    // Con el error visible, Material oculta el contador.
    expect(elemento().querySelector('mat-error')?.textContent).toContain(
      'El beneficiario no puede superar los 100 caracteres',
    );
  });
});
