import { EnvironmentProviders, Provider, inject, provideAppInitializer } from '@angular/core';
import { DateAdapter, MAT_DATE_FORMATS, MAT_DATE_LOCALE } from '@angular/material/core';
import { MatDatepickerIntl } from '@angular/material/datepicker';
import { MatIconRegistry } from '@angular/material/icon';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { regionUsuario } from '../../shared/formato/region-usuario';
import { AdaptadorFechaRegional, FORMATOS_FECHA_REGIONAL } from './adaptador-fecha-regional';
import { CalendarioIntl } from './calendario-intl';
import { PaginadorIntl } from './paginador-intl';

/** Clase CSS de la fuente de Material Symbols Outlined (`material-symbols/outlined.css`). */
export const CLASE_FUENTE_ICONOS = 'material-symbols-outlined';

/**
 * Configuración global de Angular Material: fechas según la región del usuario, textos internos
 * en español e íconos con Material Symbols. Se registra una sola vez en `app.config.ts`.
 */
export function proveerMaterial(): (Provider | EnvironmentProviders)[] {
  return [
    { provide: DateAdapter, useClass: AdaptadorFechaRegional },
    { provide: MAT_DATE_FORMATS, useValue: FORMATOS_FECHA_REGIONAL },
    { provide: MAT_DATE_LOCALE, useFactory: regionUsuario },
    { provide: MatPaginatorIntl, useClass: PaginadorIntl },
    { provide: MatDatepickerIntl, useClass: CalendarioIntl },
    provideAppInitializer(() => {
      inject(MatIconRegistry).setDefaultFontSetClass(CLASE_FUENTE_ICONOS);
    }),
  ];
}
