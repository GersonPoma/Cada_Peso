import { FormControl } from '@angular/forms';
import { describe, expect, it } from 'vitest';
import { maxBytesUtf8 } from './max-bytes-utf8.validator';

describe('maxBytesUtf8', () => {
  const validador = maxBytesUtf8(72);

  it('acepta 72 letras a', () => {
    expect(validador(new FormControl('a'.repeat(72)))).toBeNull();
  });

  it('acepta 36 letras ñ, que ocupan 72 bytes', () => {
    expect(validador(new FormControl('ñ'.repeat(36)))).toBeNull();
  });

  it('rechaza una ñ y 71 letras a: 72 caracteres pero 73 bytes', () => {
    const texto = 'ñ' + 'a'.repeat(71);

    expect(texto).toHaveLength(72);
    expect(validador(new FormControl(texto))).toEqual({
      maxBytesUtf8: { maximo: 72, actual: 73 },
    });
  });

  it.each(['', null])('acepta el valor vacío %j', (valor) => {
    expect(validador(new FormControl(valor))).toBeNull();
  });
});
