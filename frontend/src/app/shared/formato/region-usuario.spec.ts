import { afterEach, describe, expect, it, vi } from 'vitest';
import { regionUsuario } from './region-usuario';

describe('regionUsuario', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('devuelve la región del navegador cuando navigator.language es válido', () => {
    vi.stubGlobal('navigator', { language: 'es-CL' });

    expect(regionUsuario()).toBe('es-CL');
  });

  it('devuelve en-US cuando navigator.language no está disponible', () => {
    vi.stubGlobal('navigator', {});

    expect(regionUsuario()).toBe('en-US');
  });

  it('devuelve en-US cuando navigator.language no es un identificador de locale válido', () => {
    vi.stubGlobal('navigator', { language: 'esto no es un locale' });

    expect(regionUsuario()).toBe('en-US');
  });
});
