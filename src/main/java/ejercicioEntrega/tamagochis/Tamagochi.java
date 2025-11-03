package ejercicioEntrega.tamagochis;

import java.util.Random;
import java.util.Scanner;

/**
 * Define los posibles estados de un Tamagotchi. Solo puede estar en un estado a
 * la vez.
 */
enum Estado {
	OCIOSO, // Ocioso, disponible para acciones
	COMIENDO, // Comiendo
	JUGANDO, // Jugando
	LIMPIANDO, // Limpiándose
	MUERTO // Muerto
}

/**
 * La clase Tamagotchi implementa Runnable para tener su "propia vida" en un
 * hilo separado. Gestiona sus propias necesidades (suciedad, vida) de forma
 * autónoma.
 */
class Tamagotchi implements Runnable {
	// --- Constantes de Simulación ---
	private static final long TIEMPO_DE_VIDA_MS = 5 * 60 * 1000; // 5 minutos de vida
	private static final long INTERVALO_SUCIEDAD_MS = 20 * 1000; // Se ensucia cada 20 seg
	private static final long DURACION_LIMPIEZA_MS = 5 * 1000; // El baño dura 5 seg
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
	private Integer nivelSuciedad;

	public Tamagotchi(String id, long velocidadComerMs) {
		this.id = id;
		this.velocidadComerMs = velocidadComerMs;
		this.estaVivo = true;
		this.estado = Estado.OCIOSO;
		this.nivelSuciedad = 0;
		this.horaNacimiento = System.currentTimeMillis();
		this.ultimaVezSucia = this.horaNacimiento;
	}

	@Override
	public void run() {
		while (this.estaVivo) {
			try {
				long ahora = System.currentTimeMillis();
				boolean debeMorirPorEdad = (ahora - horaNacimiento > TIEMPO_DE_VIDA_MS);
				boolean debeMorirPorSuciedad = (this.nivelSuciedad >= SUCIEDAD_MAXIMA);

				if (debeMorirPorEdad || debeMorirPorSuciedad) {
					if (this.estado == Estado.OCIOSO) {
						if (debeMorirPorEdad) {
							imprimir("ha muerto de viejo.");
						} else {
							imprimir("ha muerto de suciedad.");
						}
						morir();
						continue;

					} else {
						imprimir("¡Debería morir de " + (debeMorirPorEdad ? "viejo" : "suciedad") + ", pero estoy "
								+ this.estado + "! Esperaré a terminar.");
					}
				}
				if (this.estaVivo && this.estado != Estado.LIMPIANDO
						&& (ahora - ultimaVezSucia > INTERVALO_SUCIEDAD_MS)) {
					int suciedadActual = this.nivelSuciedad + 1;
					this.nivelSuciedad = suciedadActual;
					this.ultimaVezSucia = ahora;
					imprimir("Mi nivel de suciedad es " + suciedadActual);
					if (suciedadActual == NIVEL_AVISO_SUCIEDAD) {
						imprimir("¡AVISO! ¡Empiezo a estar muy sucio!");
					}
				}
				Thread.sleep(1000);

			} catch (InterruptedException e) {
				imprimir("Hilo de vida interrumpido.");
				this.estaVivo = false;
			}
		}
		imprimir("hilo de vida ha terminado.");
	}

	/**
	 * Acción: Alimentar al Tamagotchi (VERSIÓN SIMPLE SIN SYNCHRONIZED) Esta acción
	 * se ejecuta de forma asíncrona en un nuevo hilo para no bloquear al cuidador y
	 * simular el tiempo de comida.
	 *
	 * @param comida El nombre de la comida (ej. "una manzana")
	 * @return true si pudo empezar a comer, false si estaba ocupado o muerto.
	 */
	public boolean alimentar(String comida) {
		if (!this.estaVivo) {
			imprimir("está muerto, no puede comer.");
			return false;
		}
		if (this.estado != Estado.OCIOSO) {
			imprimir("está ocupado (" + this.estado + "). No puede comer.");
			return false;
		}
		this.estado = Estado.COMIENDO;
		try {
			imprimir("¡Empieza a comer " + comida + "! 🍎 (Tardará " + (velocidadComerMs / 1000.0) + "s)");
			Thread.sleep(this.velocidadComerMs);
			// Tarea B: Informar que terminó
			imprimir("¡Terminó de comer! ¡Qué rico!");

		} catch (InterruptedException e) {
			// Esto pasa si el programa se cierra de golpe
			imprimir("¡Le interrumpieron la comida!");

		} finally {
			if (this.estado == Estado.COMIENDO) {
				this.estado = Estado.OCIOSO;
			}
		}
		return true;
	}

	/**
	 * Acción: Limpiar al Tamagotchi. ¡BLOQUEA AL CUIDADOR!
	 */
	public boolean limpiar() {
		if (!this.estaVivo) {
			imprimir("Está muerto, no se puede limpiar.");
			return false;
		}
		if (this.estado != Estado.OCIOSO) {
			imprimir("Está ocupado (" + this.estado + "), no se puede limpiar.");
			return false;
		}

		this.estado = Estado.LIMPIANDO;
		try {
			imprimir("¡Hora del baño! 🧼 (Durará " + (DURACION_LIMPIEZA_MS / 1000.0) + "s)");
			Thread.sleep(DURACION_LIMPIEZA_MS); // <-- EL CUIDADOR SE BLOQUEA AQUÍ
			this.nivelSuciedad = 0;
			this.ultimaVezSucia = System.currentTimeMillis(); // Reinicia el contador de suciedad
			imprimir("¡Totalmente limpio!");

		} catch (InterruptedException e) {
			imprimir("¡Le interrumpieron el baño!");
		} finally {
			if (this.estado == Estado.LIMPIANDO) {
				this.estado = Estado.OCIOSO;
			}
		}
		return true;
	}

	/**
	 * Acción: Jugar con el Tamagotchi. ¡BLOQUEA AL CUIDADOR! (Esto es necesario por
	 * el Scanner)
	 *
	 * @param scannerCuidador El Scanner del hilo principal (Caretaker).
	 */
	public void jugar(Scanner scannerCuidador) {
		if (!this.estaVivo) {
			imprimir("Está muerto, no puede jugar.");
			return;
		}
		if (this.estado != Estado.OCIOSO) {
			imprimir("Está ocupado (" + this.estado + "), no puede jugar.");
			return;
		}

		this.estado = Estado.JUGANDO;

		imprimir("¡quiere jugar! 🎲");
		Random rand = new Random();
		boolean acertado = false;

		while (!acertado && this.estaVivo) { // Comprueba si sigue vivo durante el juego
			int a = rand.nextInt(5);
			int b = rand.nextInt(5);
			int resultado = a + b;

			imprimir("¿Cuánto es " + a + " + " + b + "?");
			try {
				// Lee la respuesta del cuidador
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
		if (this.estado == Estado.JUGANDO) {
			this.estado = Estado.OCIOSO;
		}
	}

	/**
	 * Acción: Matar al Tamagotchi (iniciada por el cuidador). Solo funciona si está
	 * en estado OCIOSO.
	 */
	public boolean matar() {
		if (this.estado == Estado.OCIOSO) {
			imprimir("¡Está siendo asesinado por el cuidador! ");
			morir();
			return true;
		} else {
			imprimir("Está ocupado (" + this.estado + "), no se le puede matar ahora.");
			return false;
		}
	}

	/**
	 * Método privado que cambia el estado a MUERTO. Lo usa 'run()' y 'matar()'.
	 */
	private void morir() {
		if (!this.estaVivo)
			return; // Ya estaba muerto
		this.estaVivo = false;
		this.estado = Estado.MUERTO;
	}

	/**
	 * Usado por el cuidador para forzar la muerte al salir del programa.
	 */
	public void forzarMuerte() {
		imprimir("Está siendo forzado a morir por el cuidador.");
		morir();
	}

	// --- MÉTODOS DE ESTADO (Getters) ---

	public boolean estaVivo() {
		return this.estaVivo;
	}

	public String getId() {
		return this.id;
	}

	public Integer getNivelSuciedad() {
		return nivelSuciedad;
	}

	public void setNivelSuciedad(Integer nivelSuciedad) {
		this.nivelSuciedad = nivelSuciedad;
	}

	/**
	 * Devuelve un reporte de estado formateado.
	 */
	public String getEstadoFormateado() {
		double edadSegundos = (System.currentTimeMillis() - this.horaNacimiento) / 1000.0;
		double maxEdadSegundos = TIEMPO_DE_VIDA_MS / 1000.0;

		return String.format("[%s] | Vivo: %-5b | Estado: %-8s | Suciedad: %d/%d | Edad: %.1f / %.1fs", this.id,
				this.estaVivo, this.estado, nivelSuciedad, SUCIEDAD_MAXIMA, edadSegundos, maxEdadSegundos);
	}

	private void imprimir(String mensaje) {
		System.out.printf("[%s] (%s): %s%n", this.id, Thread.currentThread().getName(), mensaje);
	}

}
