import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { regionUsuario } from '../../../shared/formato/region-usuario';
import { MENSAJES_ERROR_ARCHIVO, MENSAJE_LEYENDO } from '../services/mensajes-importacion';
import { formatearTamano, textoFilasDeDatos } from '../services/presentacion-importacion';
import { AnalisisArchivo, ErrorArchivo } from '../services/validacion-archivo';

/**
 * Paso 1: elegir el CSV. Solo presenta: la página lee el archivo (asíncrono), lo analiza y le pasa
 * el estado. Un `<input type="file">` nativo con etiqueta visible: Material no trae uno.
 */
@Component({
  selector: 'app-paso-archivo',
  imports: [MatButton, MatIcon, MatProgressSpinner],
  templateUrl: './paso-archivo.component.html',
  styleUrl: './paso-archivo.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasoArchivoComponent {
  readonly archivo = input<File | null>(null);
  /** Se está leyendo y analizando el archivo elegido. */
  readonly leyendo = input(false);
  /** Por qué no se pudo leer el archivo (tamaño, vacío o codificación). */
  readonly errorCarga = input<ErrorArchivo | null>(null);
  /** El texto ya leído, con el separador y el encabezado vigentes. */
  readonly analisis = input<AnalisisArchivo | null>(null);

  readonly elegido = output<File>();
  readonly continuar = output<void>();

  protected readonly textoLeyendo = MENSAJE_LEYENDO;
  private readonly region = regionUsuario();

  protected readonly error = computed<ErrorArchivo | null>(
    () => this.errorCarga() ?? this.analisis()?.error ?? null,
  );
  protected readonly mensajeError = computed(() => {
    const error = this.error();
    return error === null ? null : MENSAJES_ERROR_ARCHIVO[error];
  });
  protected readonly resumen = computed(() => {
    const archivo = this.archivo();
    const analisis = this.analisis();
    if (!archivo || !analisis || analisis.error !== null) {
      return null;
    }
    return {
      nombre: archivo.name,
      tamano: formatearTamano(archivo.size, this.region),
      filas: textoFilasDeDatos(analisis.filasDeDatos),
    };
  });
  protected readonly puedeContinuar = computed(
    () => !this.leyendo() && this.error() === null && this.resumen() !== null,
  );

  protected alElegir(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.item(0) ?? null;
    // Permite volver a elegir el mismo archivo (por ejemplo, después de corregirlo).
    entrada.value = '';
    if (archivo) {
      this.elegido.emit(archivo);
    }
  }
}
