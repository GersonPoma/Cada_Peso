import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatButtonToggle, MatButtonToggleGroup } from '@angular/material/button-toggle';
import { MatCheckbox } from '@angular/material/checkbox';
import { MatFormField, MatHint, MatLabel } from '@angular/material/form-field';
import { MatIcon } from '@angular/material/icon';
import { MatOption } from '@angular/material/core';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSelect } from '@angular/material/select';
import {
  ETIQUETAS_SEPARADOR,
  ETIQUETAS_SEPARADOR_DECIMAL,
  ETIQUETAS_SEPARADOR_MILES,
  FORMATOS_FECHA,
  MapeoImportacion,
  ModoMonto,
  SEPARADORES,
  SEPARADORES_DECIMAL,
  SEPARADORES_MILES,
} from '../models/mapeo-importacion.model';
import {
  AVISO_VISTA_PREVIA_DEL_SERVIDOR,
  MENSAJES_ERROR_ARCHIVO,
  MENSAJES_ERROR_MAPEO,
  MENSAJE_VISTA_PREVIA_DESACTUALIZADA,
} from '../services/mensajes-importacion';
import { SugerenciasMapeo } from '../services/sugerencias-mapeo';
import { AnalisisArchivo, FILAS_DE_MUESTRA } from '../services/validacion-archivo';
import { validarMapeo } from '../services/validacion-mapeo';

/**
 * Paso 2: elegir las columnas. Muestra las primeras filas leídas en el navegador (nada se envió
 * todavía) y el mapeo con sugerencias editables. Las columnas se muestran desde 1; el mapeo guarda
 * los índices del servidor (desde 0). Solo presenta: emite cada cambio y la página decide.
 */
@Component({
  selector: 'app-paso-mapeo',
  imports: [
    MatButton,
    MatButtonToggle,
    MatButtonToggleGroup,
    MatCheckbox,
    MatFormField,
    MatHint,
    MatIcon,
    MatLabel,
    MatOption,
    MatProgressSpinner,
    MatSelect,
  ],
  templateUrl: './paso-mapeo.component.html',
  styleUrl: './paso-mapeo.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasoMapeoComponent {
  readonly mapeo = input.required<MapeoImportacion>();
  readonly analisis = input.required<AnalisisArchivo>();
  readonly sugerencias = input<SugerenciasMapeo | null>(null);
  /** La vista previa está en camino: el botón queda deshabilitado. */
  readonly pidiendo = input(false);
  /** Hay una vista previa del mapeo vigente a la que se puede volver sin pedirla otra vez. */
  readonly hayVistaVigente = input(false);
  /** Hay una vista previa de un mapeo anterior: hay que pedirla de nuevo. */
  readonly vistaDesactualizada = input(false);
  /** Mensaje fijo de un error de la última petición. */
  readonly errorServidor = input<string | null>(null);

  readonly cambio = output<Partial<MapeoImportacion>>();
  readonly pedirVistaPrevia = output<void>();
  readonly volverAVistaPrevia = output<void>();
  readonly volver = output<void>();

  protected readonly separadores = SEPARADORES;
  protected readonly etiquetasSeparador = ETIQUETAS_SEPARADOR;
  protected readonly formatosFecha = FORMATOS_FECHA;
  protected readonly decimales = SEPARADORES_DECIMAL;
  protected readonly etiquetasDecimal = ETIQUETAS_SEPARADOR_DECIMAL;
  protected readonly miles = SEPARADORES_MILES;
  protected readonly etiquetasMiles = ETIQUETAS_SEPARADOR_MILES;
  protected readonly aviso = AVISO_VISTA_PREVIA_DEL_SERVIDOR;
  protected readonly textoDesactualizada = MENSAJE_VISTA_PREVIA_DESACTUALIZADA;

  /** Índices (desde 0) de las columnas detectadas. */
  protected readonly indices = computed(() =>
    Array.from({ length: this.analisis().columnas }, (_, i) => i),
  );
  /** Las primeras filas del archivo. */
  protected readonly muestra = computed(() => this.analisis().registros.slice(0, FILAS_DE_MUESTRA));

  protected readonly erroresMapeo = computed(() =>
    validarMapeo(this.mapeo()).map((error) => MENSAJES_ERROR_MAPEO[error]),
  );
  protected readonly errorArchivo = computed(() => {
    const error = this.analisis().error;
    return error === null ? null : MENSAJES_ERROR_ARCHIVO[error];
  });
  protected readonly puedePedir = computed(
    () => !this.pidiendo() && this.errorArchivo() === null && this.erroresMapeo().length === 0,
  );

  /** Si el valor de un control es el que se sugirió (se marca `Sugerido` hasta que se cambie). */
  protected esSugerido<K extends keyof SugerenciasMapeo>(campo: K): boolean {
    const sugerencias = this.sugerencias();
    return sugerencias !== null && this.mapeo()[campo] === sugerencias[campo];
  }

  protected cambiarModo(modo: ModoMonto): void {
    this.cambio.emit({ modoMonto: modo });
  }
}
