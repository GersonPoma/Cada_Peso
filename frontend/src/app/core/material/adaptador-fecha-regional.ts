import { Injectable } from '@angular/core';
import { MAT_NATIVE_DATE_FORMATS, MatDateFormats, NativeDateAdapter } from '@angular/material/core';

type ParteFecha = 'day' | 'month' | 'year';

interface FormatoRegional {
  orden: ParteFecha[];
  separador: string;
}

const SEPARADORES_ACEPTADOS = ['/', '-', '.'];
const SEPARADOR_POR_DEFECTO = '/';
const PATRON_FECHA_ESCRITA = /^(\d{1,4})[/.-](\d{1,4})[/.-](\d{1,4})$/;

/**
 * Formato de entrada del campo de fecha. `AdaptadorFechaRegional.format` lo reconoce por
 * identidad para mostrar la fecha en el orden de la región.
 */
export const FORMATO_ENTRADA_FECHA = { year: 'numeric', month: '2-digit', day: '2-digit' };

/** Formatos de Material con el formato de entrada propio; el resto, los nativos. */
export const FORMATOS_FECHA_REGIONAL: MatDateFormats = {
  ...MAT_NATIVE_DATE_FORMATS,
  display: { ...MAT_NATIVE_DATE_FORMATS.display, dateInput: FORMATO_ENTRADA_FECHA },
};

/**
 * `DateAdapter` que interpreta y muestra las fechas escritas a mano según el orden de día, mes y
 * año de la región (`MAT_DATE_LOCALE`, es decir, `regionUsuario()`). Una fecha inexistente (ej.
 * `31/02/2003`) es inválida: nunca se ajusta a otro día.
 */
@Injectable()
export class AdaptadorFechaRegional extends NativeDateAdapter {
  private readonly formatosPorLocale = new Map<string, FormatoRegional>();

  override parse(valor: unknown, formato?: unknown): Date | null {
    if (typeof valor === 'number') {
      return new Date(valor);
    }
    if (valor === null || valor === undefined) {
      return null;
    }
    if (typeof valor !== 'string') {
      return this.invalid();
    }
    const texto = valor.trim();
    if (texto === '') {
      return null;
    }
    return this.interpretar(texto);
  }

  override format(fecha: Date, formatoVisual: object): string {
    if (formatoVisual !== FORMATO_ENTRADA_FECHA) {
      return super.format(fecha, formatoVisual);
    }
    if (!this.isValid(fecha)) {
      throw new Error('AdaptadorFechaRegional: no se puede formatear una fecha inválida');
    }
    const valores: Record<ParteFecha, string> = {
      day: String(fecha.getDate()).padStart(2, '0'),
      month: String(fecha.getMonth() + 1).padStart(2, '0'),
      year: String(fecha.getFullYear()).padStart(4, '0'),
    };
    const { orden, separador } = this.formatoRegional();
    return orden.map((parte) => valores[parte]).join(separador);
  }

  private interpretar(texto: string): Date {
    const coincidencia = PATRON_FECHA_ESCRITA.exec(texto);
    if (!coincidencia) {
      return this.invalid();
    }
    const { orden } = this.formatoRegional();
    const grupos = coincidencia.slice(1, 4);
    const valores = {} as Record<ParteFecha, string>;
    orden.forEach((parte, indice) => (valores[parte] = grupos[indice]));

    if (valores.year.length !== 4 || valores.month.length > 2 || valores.day.length > 2) {
      return this.invalid();
    }
    const anio = Number(valores.year);
    const mes = Number(valores.month) - 1;
    const dia = Number(valores.day);

    const fecha = new Date(2000, 0, 1);
    fecha.setFullYear(anio, mes, dia);
    fecha.setHours(0, 0, 0, 0);
    const existe =
      fecha.getFullYear() === anio && fecha.getMonth() === mes && fecha.getDate() === dia;
    return existe ? fecha : this.invalid();
  }

  /** Orden de las partes y separador de la región, calculados una vez por locale. */
  private formatoRegional(): FormatoRegional {
    const locale = String(this.locale);
    let formato = this.formatosPorLocale.get(locale);
    if (!formato) {
      const partes = new Intl.DateTimeFormat(
        locale,
        FORMATO_ENTRADA_FECHA as Intl.DateTimeFormatOptions,
      ).formatToParts(new Date(2003, 2, 5));
      const orden = partes
        .map((parte) => parte.type)
        .filter(
          (tipo): tipo is ParteFecha => tipo === 'day' || tipo === 'month' || tipo === 'year',
        );
      const literal = partes.find((parte) => parte.type === 'literal')?.value.trim() ?? '';
      const separador = SEPARADORES_ACEPTADOS.includes(literal) ? literal : SEPARADOR_POR_DEFECTO;
      formato = { orden, separador };
      this.formatosPorLocale.set(locale, formato);
    }
    return formato;
  }
}
