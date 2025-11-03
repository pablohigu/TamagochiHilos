package ejercicioEntrega.tamagochis;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Define los posibles estados de un Tamagotchi.
 * Solo puede estar en un estado a la vez.
 */
enum Estado {
    OCIOSO,   // Ocioso, disponible para acciones
    COMIENDO, // Comiendo
    JUGANDO,  // Jugando
    LIMPIANDO,// Limpiándose
    MUERTO    // Muerto
}

/**
 * La clase Tamagotchi implementa Runnable para tener su "propia vida" en un hilo separado.
 * Gestiona sus propias necesidades (suciedad, vida) de forma autónoma.
 */
class Tamagotchi implements Runnable {
    // --- Constantes de Simulación ---
    private static final long TIEMPO_DE_VIDA_MS = 5 * 60 * 1000;      // 5 minutos de vida
    private static final long INTERVALO_SUCIEDAD_MS = 20 * 1000;    // Se ensucia cada 20 seg
    private static final long DURACION_LIMPIEZA_MS = 5 * 1000;       // El baño dura 5 seg
    private static final int SUCIEDAD_MAXIMA = 10;
    private static final int NIVEL_AVISO_SUCIEDAD = 5;

    // --- Atributos de Instancia ---
    private final String id;
    private final long velocidadComerMs; // Tiempo único que tarda en comer
    private final long horaNacimiento;
    private long ultimaVezSucia;

    // --- Variables de Estado (volatile/atomic para seguridad entre hilos) ---
    private volatile boolean estaVivo;
    private volatile Estado estado;
    private final AtomicInteger nivelSuciedad; // Nivel de suciedad (0-10)

    // Objeto para sincronizar acciones (asegurar una a la vez)
    // Este es el "pestillo" del baño
    private final Object bloqueoAccion = new Object();

    /**
     * Constructor para crear un nuevo Tamagotchi.
     * @param id El identificador único.
     * @param velocidadComerMs El tiempo (en ms) que tarda en comer.
     */
    public Tamagotchi(String id, long velocidadComerMs) {
        this.id = id;
        this.velocidadComerMs = velocidadComerMs;
        this.estaVivo = true;
        this.estado = Estado.OCIOSO;
        this.nivelSuciedad = new AtomicInteger(0);
        this.horaNacimiento = System.currentTimeMillis();
        this.ultimaVezSucia = this.horaNacimiento;
        imprimir("ha nacido! 🎉");
    }

    /**
     * El "ciclo de vida" principal del Tamagotchi.
     * Este método se ejecuta en su propio hilo.
     */
    @Override
    public void run() {
        while (this.estaVivo) {
            try {
                long ahora = System.currentTimeMillis();
                // 1. Comprobar muerte por edad
                if (ahora - horaNacimiento > TIEMPO_DE_VIDA_MS) {
                    imprimir("ha muerto de viejo. 😢");
                    morir();
                    continue; // Salir del bucle
                }
                // 2. Comprobar si se ensucia (solo si está vivo)
                // Usamos 'synchronized' para evitar que se ensucie MIENTRAS se limpia
                synchronized (bloqueoAccion) {
                    if (estado != Estado.LIMPIANDO && ahora - ultimaVezSucia > INTERVALO_SUCIEDAD_MS) {
                        aumentarSuciedad();
                        this.ultimaVezSucia = ahora; // Reiniciar el temporizador de suciedad
                    }
                }
                // Dormir el hilo para no consumir CPU innecesariamente
                Thread.sleep(1000); // Comprueba su estado cada segundo
            } catch (InterruptedException e) {
                // Si el hilo es interrumpido (ej. por un forceKill), morirá.
                imprimir("fue interrumpido y morirá.");
                this.estaVivo = false;
            }
        }
        imprimir("hilo de vida ha terminado.");
    }

    /**
     * Incrementa la suciedad y comprueba si muere por ello.
     * Este método debe ser llamado desde un contexto seguro (como run() o un bloque sync)
     */
    private void aumentarSuciedad() {
        if (!this.estaVivo) return;

        int suciedadActual = this.nivelSuciedad.incrementAndGet();
        imprimir("nivel de suciedad es " + suciedadActual);
        if (suciedadActual == NIVEL_AVISO_SUCIEDAD) {
            imprimir("¡AVISO! ¡Empiezo a estar muy sucio! 💩");
        } else if (suciedadActual >= SUCIEDAD_MAXIMA) {
            imprimir("ha muerto de suciedad. ¡Qué asco! 🤢");
            morir();
        }
    }

    /**
     * Mata al Tamagotchi, actualizando su estado.
     * Debe ser sincronizado para que el estado se actualice de forma segura.
     */
    private void morir() {
        // Sincronizado para asegurar que el estado se actualiza correctamente
        synchronized (bloqueoAccion) {
            if (!this.estaVivo) return; // Ya estaba muerto
            this.estaVivo = false;
            this.estado = Estado.MUERTO;
        }
    }

    // ====================================================================
    // --- ACCIONES INICIADAS POR EL CUIDADOR ---
    // ====================================================================

    /**
     * Acción: Alimentar al Tamagotchi.
     * Esta acción se ejecuta de forma asíncrona en un nuevo hilo
     * para no bloquear al cuidador y simular el tiempo de comida.
     */
    public boolean alimentar(String comida) {
        // Bloqueo sincronizado para comprobar y cambiar el estado
        synchronized (bloqueoAccion) {
            if (this.estado != Estado.OCIOSO || !this.estaVivo) {
                imprimir("está ocupado (" + this.estado + ") o muerto. No puede comer.");
                return false;
            }
            // Si está ocioso, cambiamos su estado
            this.estado = Estado.COMIENDO;
        }

        // Inicia un *nuevo hilo temporal* solo para la acción de comer
        new Thread(() -> {
            try {
                imprimir("¡Empieza a comer " + comida + "! 🍎 (Tardará " + (velocidadComerMs / 1000.0) + "s)");
                Thread.sleep(this.velocidadComerMs);
                imprimir("¡Terminó de comer! ¡Qué rico!");
            } catch (InterruptedException e) {
                imprimir("¡Le interrumpieron la comida!");
            } finally {
                // Al terminar (o ser interrumpido), vuelve a estar ocioso
                // Esto DEBE estar sincronizado
                synchronized (bloqueoAccion) {
                    if (this.estado == Estado.COMIENDO) {
                        this.estado = Estado.OCIOSO;
                    }
                }
            }
        }, this.id + "-HiloComer").start();

        return true; // La acción se inició con éxito
    }

    /**
     * Acción: Limpiar al Tamagotchi.
     * Funciona de forma asíncrona, similar a alimentar().
     */
    public boolean limpiar() {
        synchronized (bloqueoAccion) {
            if (this.estado != Estado.OCIOSO || !this.estaVivo) {
                imprimir("está ocupado (" + this.estado + ") o muerto. No puede bañarse.");
                return false;
            }
            this.estado = Estado.LIMPIANDO;
        }

        // Hilo temporal para el baño
        new Thread(() -> {
            try {
                imprimir("¡Hora del baño! 🧼 (Durará " + (DURACION_LIMPIEZA_MS / 1000.0) + "s)");
                Thread.sleep(DURACION_LIMPIEZA_MS);
                this.nivelSuciedad.set(0); // La suciedad vuelve a 0
                this.ultimaVezSucia = System.currentTimeMillis(); // Reinicia el contador de suciedad
                imprimir("¡Totalmente limpio!");
            } catch (InterruptedException e) {
                imprimir("¡Le interrumpieron el baño!");
            } finally {
                synchronized (bloqueoAccion) {
                    if (this.estado == Estado.LIMPIANDO) {
                        this.estado = Estado.OCIOSO;
                    }
                }
            }
        }, this.id + "-HiloLimpiar").start();

        return true;
    }

    /**
     * Acción: Jugar con el Tamagotchi.
     * ESTA ACCIÓN ES SÍNCRONA: bloquea al hilo del Cuidador
     * porque requiere su input (Scanner) para resolver la suma.
     *
     * @param scannerCuidador El Scanner del hilo principal (Caretaker).
     */
    public void jugar(Scanner scannerCuidador) {
        synchronized (bloqueoAccion) {
            if (this.estado != Estado.OCIOSO || !this.estaVivo) {
                imprimir("está ocupado (" + this.estado + ") o muerto. No puede jugar.");
                return;
            }
            this.estado = Estado.JUGANDO;
        }

        imprimir("¡quiere jugar! 🎲");
        Random rand = new Random();
        boolean acertado = false;

        while (!acertado && this.estaVivo) { // Comprueba si sigue vivo durante el juego
            // Números de 1 cifra (0-4) para que la suma sea < 10
            int a = rand.nextInt(5);
            int b = rand.nextInt(5);
            int resultado = a + b;

            imprimir("¿Cuánto es " + a + " + " + b + "?");
            try {
                // Lee la respuesta del cuidador
                // Esta línea la ejecuta el hilo del Cuidador
                if (!scannerCuidador.hasNextInt()) {
                    imprimir("¡Eso no es un número! ¡Me aburro!");
                    scannerCuidador.next(); // Limpiar el buffer de entrada
                    break; // Salir del bucle de juego
                }
                
                int respuesta = scannerCuidador.nextInt();
                scannerCuidador.nextLine(); // Consumir el newline

                if (respuesta == resultado) {
                    imprimir("¡Correcto! ¡Qué divertido!");
                    acertado = true;
                } else {
                    imprimir("¡Incorrecto! ¡Juguemos otra vez!");
                }
            } catch (Exception e) {
                imprimir("Hubo un error en el juego. Dejo de jugar.");
                break;
            }
        }

        // Al terminar el juego, volver a ocioso
        synchronized (bloqueoAccion) {
            if (this.estado == Estado.JUGANDO) {
                this.estado = Estado.OCIOSO;
            }
        }
    }

    /**
     * Acción: Matar al Tamagotchi.
     * Solo funciona si está en estado OCIOSO.
     * @return true si se pudo matar, false si estaba ocupado.
     */
    public boolean matar() {
        synchronized (bloqueoAccion) {
            if (this.estado == Estado.OCIOSO) {
                imprimir("¡está siendo asesinado por el cuidador! 💀");
                morir();
                return true;
            } else {
                imprimir("está ocupado (" + this.estado + "), no se le puede matar ahora.");
                return false;
            }
        }
    }

    /**
     * Usado por el cuidador para forzar la muerte al salir del programa.
     */
    public void forzarMuerte() {
        imprimir("está siendo forzado a morir por el cuidador.");
        morir();
    }

    // ====================================================================
    // --- MÉTODOS DE ESTADO (Getters) ---
    // ====================================================================

    public boolean estaVivo() {
        return this.estaVivo;
    }

    public String getId() {
        return this.id;
    }

    /**
     * Devuelve un reporte de estado formateado.
     */
    public String getEstadoFormateado() {
        double edadSegundos = (System.currentTimeMillis() - this.horaNacimiento) / 1000.0;
        double maxEdadSegundos = TIEMPO_DE_VIDA_MS / 1000.0;
        
        // Usamos .get() para leer el estado y el nivel de suciedad
        // de forma segura entre hilos.
        return String.format("[%s] | Vivo: %-5b | Estado: %-8s | Suciedad: %d/%d | Edad: %.1f / %.1fs",
                this.id, this.estaVivo, this.estado, this.nivelSuciedad.get(), SUCIEDAD_MAXIMA, edadSegundos, maxEdadSegundos);
    }
    /**
     * Helper para logs, mostrando el ID del Tamagotchi y el hilo que ejecuta.
     */
    private void imprimir(String mensaje) {
        System.out.printf("[%s] (%s): %s%n",
                this.id, Thread.currentThread().getName(), mensaje);
}
}
