import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  untracked,
  type WritableSignal,
} from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButton } from '@angular/material/button';
import { MatIcon } from '@angular/material/icon';
import { MatProgressSpinner } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { EMPTY, Subject, catchError, filter, map, switchMap } from 'rxjs';
import { PresupuestoActivoService } from '../../../core/presupuesto-activo/presupuesto-activo.service';
import {
  CODIGOS_API,
  MENSAJE_ERROR_GENERICO,
  leerProblemaApi,
} from '../../../shared/api/problema-api';
import { regionUsuario } from '../../../shared/formato/region-usuario';
import { IndicadorPasosComponent } from '../components/indicador-pasos.component';
import { PasoConfirmarComponent } from '../components/paso-confirmar.component';
import { PasoArchivoComponent } from '../components/paso-archivo.component';
import { PasoResultadoComponent } from '../components/paso-resultado.component';
import { PasoMapeoComponent } from '../components/paso-mapeo.component';
import { PasoVistaPreviaComponent } from '../components/paso-vista-previa.component';
import { CuentaImportacion, ETIQUETAS_TIPO_CUENTA } from '../models/cuenta-importacion.model';
import { ImportacionResponse } from '../models/importacion-response.model';
import { CARACTER_SEPARADOR, MapeoImportacion } from '../models/mapeo-importacion.model';
import { VistaPrevia } from '../models/vista-previa-response.model';
import { CuentaLecturaService } from '../services/cuenta-lectura.service';
import { ImportacionService } from '../services/importacion.service';
import {
  MENSAJE_CUENTA_CERRADA,
  MENSAJE_CUENTA_NO_ENCONTRADA,
  MENSAJE_DATOS_INVALIDOS,
  mensajeReglaDeNegocio,
} from '../services/mensajes-importacion';
import { SugerenciasMapeo, sugerirMapeo } from '../services/sugerencias-mapeo';
import {
  AnalisisArchivo,
  ErrorArchivo,
  analizarTexto,
  cargarArchivo,
} from '../services/validacion-archivo';
import { recortarColumnas, validarMapeo } from '../services/validacion-mapeo';

type EstadoCuenta = 'cargando' | 'listo' | 'error';

/** Los pasos del asistente, en orden. */
export type PasoImportacion = 'archivo' | 'mapeo' | 'vista-previa' | 'confirmar' | 'resultado';

const PASOS: readonly PasoImportacion[] = [
  'archivo',
  'mapeo',
  'vista-previa',
  'confirmar',
  'resultado',
];
const NOMBRES_PASOS = ['Archivo', 'Mapeo', 'Vista previa', 'Confirmar', 'Resultado'];

/** El mapeo antes de leer un archivo; las sugerencias reemplazan lo que puedan. */
export const MAPEO_INICIAL: MapeoImportacion = {
  separador: 'COMA',
  tieneEncabezado: true,
  columnaFecha: null,
  formatoFecha: 'dd/MM/yyyy',
  modoMonto: 'UNA_COLUMNA',
  columnaMonto: null,
  columnaDebito: null,
  columnaCredito: null,
  separadorDecimal: 'COMA',
  separadorMiles: 'NINGUNO',
  columnaDescripcion: null,
  columnaMemo: null,
};

/** Un pedido de vista previa con todo lo necesario, atado a la versión del mapeo. */
interface PedidoVistaPrevia {
  version: number;
  presupuestoId: number;
  cuentaId: number;
  archivo: File;
  mapeo: MapeoImportacion;
}

/**
 * Asistente para importar un CSV a una cuenta: archivo, mapeo y vista previa (la confirmación y el
 * resultado se agregan en la etapa B). Lee la cuenta con un servicio propio y no ofrece el asistente
 * si está cerrada. Sin entrada en el menú lateral: se llega desde el menú de la cuenta en Cuentas.
 * El archivo solo vive en memoria; nada se envía hasta pedir la vista previa.
 */
@Component({
  selector: 'app-importacion',
  imports: [
    RouterLink,
    MatButton,
    MatIcon,
    MatProgressSpinner,
    IndicadorPasosComponent,
    PasoArchivoComponent,
    PasoConfirmarComponent,
    PasoMapeoComponent,
    PasoResultadoComponent,
    PasoVistaPreviaComponent,
  ],
  templateUrl: './importacion.page.html',
  styleUrl: './importacion.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImportacionPage {
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cuentas = inject(CuentaLecturaService);
  private readonly servicio = inject(ImportacionService);
  private readonly presupuestoActivo = inject(PresupuestoActivoService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly etiquetas = ETIQUETAS_TIPO_CUENTA;
  protected readonly nombresPasos = NOMBRES_PASOS;
  protected readonly mensajeCerrada = MENSAJE_CUENTA_CERRADA;

  protected readonly presupuestoId = computed(() => this.presupuestoActivo.presupuesto()?.id);
  protected readonly moneda = computed(() => this.presupuestoActivo.presupuesto()?.moneda ?? '');
  private readonly cuentaId = toSignal(
    this.ruta.paramMap.pipe(map((params) => Number(params.get('cuentaId')))),
    { initialValue: Number(this.ruta.snapshot.paramMap.get('cuentaId')) },
  );

  // La cuenta.
  protected readonly estado = signal<EstadoCuenta>('cargando');
  protected readonly cuenta = signal<CuentaImportacion | null>(null);
  private readonly recargas = signal(0);

  // El asistente.
  protected readonly paso = signal<PasoImportacion>('archivo');
  protected readonly indicePaso = computed(() => PASOS.indexOf(this.paso()));
  protected readonly archivo = signal<File | null>(null);
  private readonly texto = signal<string | null>(null);
  protected readonly leyendo = signal(false);
  protected readonly errorCarga = signal<ErrorArchivo | null>(null);
  protected readonly sugerencias = signal<SugerenciasMapeo | null>(null);
  protected readonly mapeo = signal<MapeoImportacion>(MAPEO_INICIAL);
  private lecturaActual = 0;

  /** Solo cambia (y se recalcula el análisis) con otro archivo, otro separador u otro encabezado. */
  private readonly separador = computed(() => this.mapeo().separador);
  private readonly tieneEncabezado = computed(() => this.mapeo().tieneEncabezado);
  protected readonly analisis = computed<AnalisisArchivo | null>(() => {
    const texto = this.texto();
    return texto === null
      ? null
      : analizarTexto(texto, CARACTER_SEPARADOR[this.separador()], this.tieneEncabezado());
  });

  // La vista previa.
  private readonly versionMapeo = signal(0);
  protected readonly pidiendo = signal(false);
  protected readonly vistaPrevia = signal<{ datos: VistaPrevia; version: number } | null>(null);
  protected readonly errorServidor = signal<string | null>(null);
  protected readonly hayVistaVigente = computed(
    () => this.vistaPrevia()?.version === this.versionMapeo(),
  );
  protected readonly vistaDesactualizada = computed(() => {
    const vista = this.vistaPrevia();
    return vista !== null && vista.version !== this.versionMapeo();
  });
  private readonly pedidos = new Subject<PedidoVistaPrevia | 'cancelar'>();

  // La importación.
  protected readonly importando = signal(false);
  protected readonly errorImportacion = signal<string | null>(null);
  protected readonly resultado = signal<ImportacionResponse | null>(null);

  constructor() {
    this.cargarCuenta();
    this.recibirVistaPrevia();
    // Una columna que ya no existe (por ejemplo, al cambiar el separador) no puede enviarse.
    effect(() => {
      const analisis = this.analisis();
      if (analisis === null || analisis.error !== null) {
        return;
      }
      untracked(() => {
        const mapeo = this.mapeo();
        const recortado = recortarColumnas(mapeo, analisis.columnas);
        if (recortado !== mapeo) {
          this.mapeo.set(recortado);
          this.versionMapeo.update((v) => v + 1);
        }
      });
    });
  }

  protected recargarCuenta(): void {
    this.recargas.update((valor) => valor + 1);
  }

  // ---------------------------------------------------------------- paso 1: el archivo

  /**
   * Lee el archivo elegido sin bloquear: la lectura es asíncrona y, si mientras tanto se elige otro,
   * la anterior se descarta. Con el texto decodificado se sugiere el mapeo; nada se envía.
   */
  protected async elegirArchivo(archivo: File): Promise<void> {
    const lectura = ++this.lecturaActual;
    this.archivo.set(archivo);
    this.texto.set(null);
    this.errorCarga.set(null);
    this.sugerencias.set(null);
    this.leyendo.set(true);
    this.descartarVistaPrevia();
    const resultado = await cargarArchivo(archivo);
    if (lectura !== this.lecturaActual) {
      return;
    }
    this.leyendo.set(false);
    if ('error' in resultado) {
      this.errorCarga.set(resultado.error);
      return;
    }
    const sugeridas = sugerirMapeo(resultado.texto, regionUsuario());
    this.sugerencias.set(sugeridas);
    this.mapeo.set({ ...MAPEO_INICIAL, ...sugeridas });
    this.versionMapeo.update((v) => v + 1);
    this.texto.set(resultado.texto);
  }

  protected irA(paso: PasoImportacion): void {
    this.paso.set(paso);
    // El foco pasa al título del paso cuando ya se pintó.
    setTimeout(() =>
      this.anfitrion.nativeElement.querySelector<HTMLElement>('h2.titulo-paso')?.focus(),
    );
  }

  // ---------------------------------------------------------------- paso 2: el mapeo

  protected alCambiarMapeo(cambio: Partial<MapeoImportacion>): void {
    this.mapeo.update((mapeo) => ({ ...mapeo, ...cambio }));
    this.versionMapeo.update((v) => v + 1);
    this.errorServidor.set(null);
    // Editar el mapeo cancela la petición en camino: su respuesta ya no corresponde.
    if (this.pidiendo()) {
      this.pedidos.next('cancelar');
      this.pidiendo.set(false);
    }
  }

  // ---------------------------------------------------------------- paso 3: la vista previa

  protected pedirVistaPrevia(): void {
    const archivo = this.archivo();
    const presupuestoId = this.presupuestoId();
    const analisis = this.analisis();
    if (
      !archivo ||
      presupuestoId === undefined ||
      this.pidiendo() ||
      analisis === null ||
      analisis.error !== null ||
      validarMapeo(this.mapeo()).length > 0
    ) {
      return;
    }
    this.errorServidor.set(null);
    this.pidiendo.set(true);
    this.pedidos.next({
      version: this.versionMapeo(),
      presupuestoId,
      cuentaId: this.cuentaId(),
      archivo,
      mapeo: this.mapeo(),
    });
  }

  // ---------------------------------------------------------------- paso 4: confirmar

  /** Un solo envío a la vez: mientras `importando` es verdadero se ignoran más pedidos. */
  protected importar(omitirInvalidas: boolean): void {
    const archivo = this.archivo();
    const presupuestoId = this.presupuestoId();
    if (!archivo || presupuestoId === undefined || this.importando() || !this.hayVistaVigente()) {
      return;
    }
    this.errorImportacion.set(null);
    this.importando.set(true);
    const hayInvalidas = (this.vistaPrevia()?.datos.totales.invalidas ?? 0) > 0;
    this.servicio
      .importar(presupuestoId, this.cuentaId(), archivo, this.mapeo(), omitirInvalidas)
      .subscribe({
        next: (respuesta) => {
          this.importando.set(false);
          this.resultado.set(respuesta);
          this.irA('resultado');
        },
        error: (error: unknown) =>
          this.alFallarImportacion(error, hayInvalidas && !omitirInvalidas),
      });
  }

  private alFallarImportacion(error: unknown, hayInvalidasSinOmitir: boolean): void {
    this.importando.set(false);
    const problema = leerProblemaApi(error);
    // Con 401 el interceptor ya cerró la sesión y redirigió.
    if (problema?.status === 401) {
      return;
    }
    switch (problema?.codigo) {
      case CODIGOS_API.DATOS_INVALIDOS:
        this.errorImportacion.set(MENSAJE_DATOS_INVALIDOS);
        return;
      case CODIGOS_API.REGLA_NEGOCIO_VIOLADA:
        this.explicarReglaDeNegocio(hayInvalidasSinOmitir, this.errorImportacion);
        return;
      case CODIGOS_API.RECURSO_NO_ENCONTRADO:
        this.snackBar.open(MENSAJE_CUENTA_NO_ENCONTRADA, 'Cerrar', { duration: 6000 });
        this.recargarCuenta();
        return;
      default:
        this.errorImportacion.set(MENSAJE_ERROR_GENERICO);
    }
  }

  // ---------------------------------------------------------------- internos

  private descartarVistaPrevia(): void {
    if (this.pidiendo()) {
      this.pedidos.next('cancelar');
      this.pidiendo.set(false);
    }
    this.vistaPrevia.set(null);
    this.errorServidor.set(null);
  }

  /** Cada pedido cancela el anterior (`switchMap`); una respuesta atrasada nunca se muestra. */
  private recibirVistaPrevia(): void {
    this.pedidos
      .pipe(
        switchMap((pedido) =>
          pedido === 'cancelar'
            ? EMPTY
            : this.servicio
                .vistaPrevia(pedido.presupuestoId, pedido.cuentaId, pedido.archivo, pedido.mapeo)
                .pipe(
                  map((datos) => ({ datos, version: pedido.version })),
                  catchError((error: unknown) => {
                    this.alFallarVistaPrevia(error);
                    return EMPTY;
                  }),
                ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe(({ datos, version }) => {
        this.pidiendo.set(false);
        this.vistaPrevia.set({ datos, version });
        if (version === this.versionMapeo()) {
          this.irA('vista-previa');
        }
      });
  }

  private alFallarVistaPrevia(error: unknown): void {
    this.pidiendo.set(false);
    const problema = leerProblemaApi(error);
    // Con 401 el interceptor ya cerró la sesión y redirigió.
    if (problema?.status === 401) {
      return;
    }
    switch (problema?.codigo) {
      case CODIGOS_API.DATOS_INVALIDOS:
        this.errorServidor.set(MENSAJE_DATOS_INVALIDOS);
        return;
      case CODIGOS_API.REGLA_NEGOCIO_VIOLADA:
        this.explicarReglaDeNegocio(false, this.errorServidor);
        return;
      case CODIGOS_API.RECURSO_NO_ENCONTRADO:
        this.snackBar.open(MENSAJE_CUENTA_NO_ENCONTRADA, 'Cerrar', { duration: 6000 });
        this.recargarCuenta();
        return;
      default:
        this.errorServidor.set(MENSAJE_ERROR_GENERICO);
    }
  }

  /**
   * El `422` sirve para varias reglas con el mismo código. Antes de empezar la cuenta estaba abierta,
   * así que se vuelve a leer para saber si se cerró en otra sesión; el asistente sigue abierto.
   */
  private explicarReglaDeNegocio(
    hayInvalidasSinOmitir: boolean,
    destino: WritableSignal<string | null>,
  ): void {
    const presupuestoId = this.presupuestoId();
    const mostrar = (cuentaCerrada: boolean): void =>
      destino.set(mensajeReglaDeNegocio({ cuentaCerrada, hayInvalidasSinOmitir }));
    if (presupuestoId === undefined) {
      mostrar(false);
      return;
    }
    this.cuentas.obtener(presupuestoId, this.cuentaId()).subscribe({
      next: (cuenta) => mostrar(cuenta.cerrada),
      error: () => mostrar(false),
    });
  }

  private cargarCuenta(): void {
    toObservable(
      computed(() => ({
        presupuestoId: this.presupuestoId(),
        cuentaId: this.cuentaId(),
        recargas: this.recargas(),
      })),
    )
      .pipe(
        filter(({ presupuestoId }) => presupuestoId !== undefined),
        switchMap(({ presupuestoId, cuentaId }) => {
          this.estado.set('cargando');
          return this.cuentas.obtener(presupuestoId as number, cuentaId).pipe(
            catchError((error: unknown) => {
              this.alFallarCuenta(error);
              return EMPTY;
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((cuenta) => {
        this.cuenta.set(cuenta);
        this.estado.set('listo');
      });
  }

  private alFallarCuenta(error: unknown): void {
    const problema = leerProblemaApi(error);
    if (problema?.status === 401) {
      return;
    }
    if (problema?.status === 404 || problema?.codigo === CODIGOS_API.RECURSO_NO_ENCONTRADO) {
      this.snackBar.open(MENSAJE_CUENTA_NO_ENCONTRADA, 'Cerrar', { duration: 6000 });
      void this.router.navigate(['/presupuestos', this.presupuestoId(), 'cuentas']);
      return;
    }
    this.estado.set('error');
  }
}
