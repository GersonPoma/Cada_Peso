import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarRef, TextOnlySnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { proveerMaterial } from '../../../core/material/proveer-material';
import { CLAVE_SESION, SesionService } from '../../../core/sesion/sesion.service';
import { MENSAJE_ERROR_GENERICO } from '../../../shared/api/problema-api';
import { DialogoPresupuestoComponent } from '../components/dialogo-presupuesto.component';
import { PresupuestoResponse } from '../models/presupuesto-response.model';
import { RedireccionPresupuestoPage } from './redireccion-presupuesto.page';

const URL_PRESUPUESTOS = '/api/v1/presupuestos';

function presupuesto(id: number, nombre: string): PresupuestoResponse {
  return {
    id,
    nombre,
    moneda: 'BOB',
    fechaCreacion: '2026-10-06T12:00:00Z',
    fechaActualizacion: '2026-10-06T12:00:00Z',
  };
}

describe('RedireccionPresupuestoPage', () => {
  let fixture: ComponentFixture<RedireccionPresupuestoPage>;
  let backend: HttpTestingController;
  let navegar: ReturnType<typeof vi.spyOn>;
  let navegarUrl: ReturnType<typeof vi.spyOn>;
  let abrirAviso: ReturnType<typeof vi.spyOn>;
  let accionAviso: Subject<void>;

  const elemento = () => fixture.nativeElement as HTMLElement;
  const texto = () => elemento().textContent ?? '';
  const botonCon = (contenido: string) =>
    Array.from(elemento().querySelectorAll('button')).find(
      (b) => b.textContent?.trim() === contenido,
    ) as HTMLButtonElement | undefined;

  async function estable(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        ...proveerMaterial(),
      ],
    });
    TestBed.inject(SesionService).iniciar('abc', '2099-01-01T00:00:00Z');
    backend = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    navegarUrl = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    accionAviso = new Subject<void>();
    abrirAviso = vi.spyOn(TestBed.inject(MatSnackBar), 'open').mockReturnValue({
      onAction: () => accionAviso.asObservable(),
    } as MatSnackBarRef<TextOnlySnackBar>);
    fixture = TestBed.createComponent(RedireccionPresupuestoPage);
    await estable();
  });

  afterEach(() => {
    backend.verify();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('mientras carga muestra el spinner y la cabecera', () => {
    expect(elemento().querySelector('mat-progress-spinner')).not.toBeNull();
    expect(elemento().querySelector('app-cabecera')?.textContent).toContain('Cada Peso');
    backend.expectOne(URL_PRESUPUESTOS);
  });

  it('con un presupuesto lleva a él reemplazando / en el historial', async () => {
    backend.expectOne(URL_PRESUPUESTOS).flush([presupuesto(7, 'Casa')]);
    await estable();

    expect(navegar).toHaveBeenCalledWith(['/presupuestos', 7], { replaceUrl: true });
  });

  it('con varios presupuestos lleva al primero de la lista', async () => {
    backend.expectOne(URL_PRESUPUESTOS).flush([presupuesto(3, 'casa'), presupuesto(1, 'Viajes')]);
    await estable();

    expect(navegar).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith(['/presupuestos', 3], { replaceUrl: true });
  });

  describe('sin presupuestos', () => {
    beforeEach(async () => {
      backend.expectOne(URL_PRESUPUESTOS).flush([]);
      await estable();
    });

    it('no navega y ofrece crear el primero', () => {
      expect(navegar).not.toHaveBeenCalled();
      expect(texto()).toContain('Todavía no tienes presupuestos');
      expect(botonCon('Crear mi primer presupuesto')).toBeDefined();
      expect(elemento().querySelector('mat-progress-spinner')).toBeNull();
    });

    it('al crear el primero abre el diálogo en modo crear y lleva al nuevo', async () => {
      const abrirDialogo = vi
        .spyOn(TestBed.inject(MatDialog), 'open')
        .mockReturnValue({ afterClosed: () => of(presupuesto(9, 'Casa')) } as MatDialogRef<
          unknown,
          unknown
        >);

      botonCon('Crear mi primer presupuesto')?.click();

      expect(abrirDialogo).toHaveBeenCalledWith(
        DialogoPresupuestoComponent,
        expect.objectContaining({ data: { modo: 'crear' } }),
      );
      expect(navegar).toHaveBeenCalledWith(['/presupuestos', 9], { replaceUrl: true });
    });

    it('si se cancela el diálogo no navega', () => {
      vi.spyOn(TestBed.inject(MatDialog), 'open').mockReturnValue({
        afterClosed: () => of(undefined),
      } as MatDialogRef<unknown, unknown>);

      botonCon('Crear mi primer presupuesto')?.click();

      expect(navegar).not.toHaveBeenCalled();
    });
  });

  describe('error al cargar', () => {
    beforeEach(async () => {
      backend
        .expectOne(URL_PRESUPUESTOS)
        .flush({}, { status: 500, statusText: 'Internal Server Error' });
      await estable();
    });

    it('muestra el aviso genérico con Reintentar y conserva la sesión', () => {
      expect(abrirAviso).toHaveBeenCalledWith(MENSAJE_ERROR_GENERICO, 'Reintentar', {
        duration: 6000,
      });
      expect(texto()).toContain('No pudimos cargar tus presupuestos.');
      expect(TestBed.inject(SesionService).haySesion()).toBe(true);
    });

    it('Reintentar en el aviso vuelve a pedir la lista y lleva al presupuesto', async () => {
      accionAviso.next();
      backend.expectOne(URL_PRESUPUESTOS).flush([presupuesto(7, 'Casa')]);
      await estable();

      expect(navegar).toHaveBeenCalledWith(['/presupuestos', 7], { replaceUrl: true });
    });

    it('el botón Reintentar de la página vuelve a pedir la lista', async () => {
      botonCon('Reintentar')?.click();
      await estable();

      expect(elemento().querySelector('mat-progress-spinner')).not.toBeNull();
      backend.expectOne(URL_PRESUPUESTOS).flush([]);
    });
  });

  it('un 401 no muestra ningún aviso', async () => {
    backend
      .expectOne(URL_PRESUPUESTOS)
      .flush({ codigo: 'NO_AUTENTICADO' }, { status: 401, statusText: 'Unauthorized' });
    await estable();

    expect(abrirAviso).not.toHaveBeenCalled();
  });

  it('Cerrar sesión borra la sesión y lleva a /login', async () => {
    backend.expectOne(URL_PRESUPUESTOS).flush([]);
    await estable();

    botonCon('Cerrar sesión')?.click();

    expect(localStorage.getItem(CLAVE_SESION)).toBeNull();
    expect(TestBed.inject(SesionService).haySesion()).toBe(false);
    expect(navegarUrl).toHaveBeenCalledWith('/login');
  });
});
