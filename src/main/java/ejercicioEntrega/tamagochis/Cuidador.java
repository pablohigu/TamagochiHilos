package ejercicioEntrega.tamagochis;

import java.util.ArrayList; // Usamos listas, más simples que Mapas
import java.util.List;
import java.util.Random;
import java.util.Scanner;

public class Cuidador {
	// --- Variables Globales (estáticas) ---
	// Guardamos los Tamagotchis y sus Hilos en listas.
	private static List<Tamagotchi> listaTamagotchis = new ArrayList<>();
	private static List<Thread> listaHilos = new ArrayList<>();
	private static Scanner scanner = new Scanner(System.in);
	private static Random rand = new Random();

	/**
	 * El método MAIN: punto de entrada del programa.
	 */
	public static void main(String[] args) {
		System.out.println("--- Cuidador de Tamagotchis---");
		System.out.print("¿Cuántos Tamagotchis quieres lanzar? ");
		int num = leerNumero();
		// --- 1. Crear y lanzar los Tamagotchis ---
		for (int i = 0; i < num; i++) {
			String id = "Tama-" + i; // El ID será "Tama-0", "Tama-1", etc.
			long velocidadComer = rand.nextInt(5001) + 3000;
			// Creamos el Tamagotchi (el objeto Runnable)
			Tamagotchi t = new Tamagotchi(id, velocidadComer);
			// Creamos el Hilo que ejecutará su vida (su método run())
			Thread hiloVida = new Thread(t, id + "-HiloDeVida");
			// Los guardamos en listas
			listaTamagotchis.add(t);
			listaHilos.add(hiloVida);
			// Esto inicia el método run()
			hiloVida.start();
			System.out.println("¡" + id + " ha sido lanzado!");
		}
		runMenu();
		System.out.println("El cuidador se va. Apagando todos los hilos...");
		for (int i = 0; i < listaTamagotchis.size(); i++) {
			if (listaTamagotchis.get(i).estaVivo()) {
				listaTamagotchis.get(i).forzarMuerte();
			}
			listaHilos.get(i).interrupt();
		}
		scanner.close();
		System.out.println("Programa finalizado.");
	}

	/**
	 * Bucle del menú principal.
	 */
	private static void runMenu() {
		boolean seguir = true;
		while (seguir) {

			// Comprobar si todos han muerto
			if (todosHanMuerto()) {
				System.out.println("\n¡Oh no! Todos tus Tamagotchis han muerto. Fin.");
				seguir = false;
				continue;
			}
			System.out.println("\n--- MENÚ DEL CUIDADOR ---");
			System.out.println("1. Alimentar");
			System.out.println("2. Limpiar");
			System.out.println("3. Jugar");
			System.out.println("4. Ver Estado de Todos");
			System.out.println("5. Matar");
			System.out.println("6. Salir");
			System.out.print("Elige una opción: ");

			String opcion = scanner.nextLine();
			Tamagotchi elegido = null; // Para guardar el Tamagotchi a usar
			// Si la opción es 1, 2, 3 o 5, necesitamos elegir uno primero
			if ("1".equals(opcion) || "2".equals(opcion) || "3".equals(opcion) || "5".equals(opcion)) {
				elegido = seleccionarTamagotchi();
				if (elegido == null) {
					System.out.println("Selección cancelada o inválida.");
					continue; // Vuelve al menú
				}
			}
			switch (opcion) {
			case "1":
				boolean exitoComer = elegido.llamarAlimentar();
				if (exitoComer) {
					System.out.println("¡" + elegido.getId() + " ha recibido la orden de comer!");
				} else {
					System.out.println("¡ERROR! " + elegido.getId() + " está ocupado y no puede comer ahora.");
				}
				break;
			case "2":
				System.out.println("Limpiando a " + elegido.getId() + "...");
				boolean exitoLimpiar = elegido.llamarLimpiar();
				if (exitoLimpiar) {
					System.out.println("¡" + elegido.getId() + " ha recibido la orden de limpiar!");
				} else {
					System.out.println("¡ERROR! " + elegido.getId() + " está ocupado y no puede limpiar ahora.");
				}
				break;
			case "3":
				System.out.println("Jugando con " + elegido.getId() + "...");
				elegido.jugar(scanner); // Le pasamos el Scanner
				break;
			case "4":
				System.out.println("\n--- REPORTE DE ESTADO ---");
				for (Tamagotchi t : listaTamagotchis) {
					// Llama al método que arreglamos
					System.out.println(t.getEstadoFormateado());
				}
				System.out.println("-------------------------");
				break;
			case "5":
				System.out.println("Intentando matar a " + elegido.getId() + "...");
				boolean exito = elegido.matar();
				if (!exito) {
					System.out.println(elegido.getId() + " estaba ocupado y no se ha dejado matar.");
				}
				break;
			case "6":
				seguir = false; // Esto terminará el bucle 'while'
				break;
			default:
				System.out.println("Opción no válida. Inténtalo de nuevo.");
			}
		}
	}

	/**
	 * Método simple para pedir al usuario un número de la lista.
	 */
	private static Tamagotchi seleccionarTamagotchi() {
		System.out.println("¿Con qué Tamagotchi? (Escribe el número):");
		for (int i = 0; i < listaTamagotchis.size(); i++) {
			Tamagotchi t = listaTamagotchis.get(i);
			if (t.estaVivo()) {
				System.out.println(i + " -> " + t.getId());
			}
		}

		int indice = leerNumero(); // Pide un número

		// Comprobamos si el número es válido
		if (indice < 0 || indice >= listaTamagotchis.size()) {
			System.out.println("Error: Ese número no está en la lista.");
			return null;
		}

		Tamagotchi t = listaTamagotchis.get(indice);

		if (!t.estaVivo()) {
			System.out.println("Error: " + t.getId() + " ya está muerto.");
			return null;
		}

		return t; // Devuelve el Tamagotchi elegido
	}

	/**
	 * Método simple para leer un número del scanner.
	 */
	private static int leerNumero() {
		while (true) {
			try {
				String linea = scanner.nextLine();
				return Integer.parseInt(linea); // Convierte el texto a número
			} catch (NumberFormatException e) {
				System.out.print("Error. Introduce un número válido: ");
			}
		}
	}

	/**
	 * Método simple que comprueba si todos los Tamagotchis han muerto.
	 */
	private static boolean todosHanMuerto() {
		if (listaTamagotchis.isEmpty()) {
			return false; // Aún no ha empezado
		}

		for (Tamagotchi t : listaTamagotchis) {
			if (t.estaVivo()) {
				return false; // Si encontramos uno vivo, devuelve false
			}
		}
		return true; // Si el bucle termina, es que todos están muertos
	}
}
