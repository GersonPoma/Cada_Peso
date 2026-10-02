import { Pipe, PipeTransform } from '@angular/core';
import { regionUsuario } from './region-usuario';

const PATRON_INSTANTE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z$/;
const PATRON_FECHA_LOCAL = /^\d{4}-\d{2}-\d{2}$/;

const OPCIONES_FECHA_HORA: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  hour: 'numeric',
  minute: '2-digit',
};

const OPCIONES_FECHA: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: 'long',
  day: 'numeric',
};

@Pipe({
  name: 'fecha',
})
export class FechaPipe implements PipeTransform {
  transform(valor: string | null | undefined): string {
    if (valor === null || valor === undefined) {
      return '';
    }

    if (PATRON_INSTANTE.test(valor)) {
      const instante = new Date(valor);
      return new Intl.DateTimeFormat(regionUsuario(), OPCIONES_FECHA_HORA).format(instante);
    }

    if (PATRON_FECHA_LOCAL.test(valor)) {
      const [anio, mes, dia] = valor.split('-').map(Number);
      const fecha = new Date(Date.UTC(anio, mes - 1, dia));
      return new Intl.DateTimeFormat(regionUsuario(), {
        ...OPCIONES_FECHA,
        timeZone: 'UTC',
      }).format(fecha);
    }

    throw new Error(`Formato de fecha no reconocido: ${valor}`);
  }
}
