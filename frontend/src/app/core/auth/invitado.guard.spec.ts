import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { SesionService } from '../sesion/sesion.service';
import { invitadoGuard } from './invitado.guard';

describe('invitadoGuard', () => {
  function ejecutar(): boolean | UrlTree {
    return TestBed.runInInjectionContext(() =>
      invitadoGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    ) as boolean | UrlTree;
  }

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('deja pasar sin sesión', () => {
    expect(ejecutar()).toBe(true);
  });

  it('con sesión lleva a /', () => {
    TestBed.inject(SesionService).iniciar('abc', '2026-10-07T12:00:00Z');

    const resultado = ejecutar();

    expect(resultado).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(resultado as UrlTree)).toBe('/');
  });
});
