package com.runaarco;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuración en config/runaarco.json. También se edita desde el menú (Mod Menu + Cloth Config).
 * Se recarga sola cada pocos segundos si cambias el archivo con el juego abierto.
 */
public class RunaConfig {
	/** Lo que se guarda en el archivo. */
	public static class Datos {
		// --- Jugadores ---
		public float tamano = 1.8f;      // media anchura en bloques
		public float altura = 2.4f;      // altura del centro sobre los pies
		public float distancia = 1.0f;   // distancia detrás de la espalda
		public String runa = "";         // archivo .runa de la carpeta runas ("" = la predeterminada)
		// --- Mobs con arco (esqueletos, etc.) ---
		public boolean mobsActivos = true;
		public float tamanoMobs = 1.4f;
		public float alturaMobs = 2.3f;
		public float distanciaMobs = 0.9f;
		public String runaMobs = "";
		// --- Efectos extra ---
		public boolean flechaPoder = true;       // runa por la que pasa la flecha de un arco con Poder
		public boolean rayosObjetos = true;      // rayos alrededor de objetos encantados (en el suelo)
		public boolean rayosEnMano = true;       // lo mismo, en la mano de jugadores y mobs
		public boolean anillosTridente = true;   // anillos con runas en el tridente encantado
		public float tamanoRayos = 1.0f;         // multiplicador del tamaño de los rayos
		public float tamanoAnillos = 1.0f;       // multiplicador del tamaño de los anillos del tridente
		public float tamanoRunaFlecha = 1.0f;    // multiplicador del tamaño de la runa de la flecha
		public boolean estela = true;            // haz de luz en la trayectoria de la flecha
		public boolean estelaTodas = false;      // false = solo flechas de un arco con Poder
		public float anchoEstela = 0.12f;        // grosor del haz en bloques
		public int duracionEstela = 16;          // ticks que tarda en apagarse el haz
		public String colorEstela = "#BFE8FF";
		public String colorRayo = "#A8E0FF";
		public String colorAnillos = "#7FF0FF";
		// tamaños (1.0 = normal)
		public float escalaRayos = 1.0f;
		public float escalaAnillos = 1.0f;
		public float escalaFlecha = 1.0f;     // runa por la que pasa la flecha
		// posiciones (en bloques)
		public float delanteFlecha = 1.8f;    // distancia delante de los ojos a la que aparece la runa de la flecha
		public float alturaFlecha = 0.0f;     // sube/baja la runa de la flecha
		public float alturaRayos = 0.0f;      // sube/baja los rayos
		public float anillosAdelante = 0.0f;  // desplaza los anillos a lo largo del tridente
		public float manoLado = 0.38f;        // objeto en la mano: hacia el lado
		public float manoAdelante = 0.45f;    //                    hacia delante
		public float manoAltura = 0.0f;       //                    arriba/abajo
		// tridente / objetos en TU mano en primera persona (respecto a la cámara)
		public float fpLado = 0.50f;
		public float fpAltura = -0.30f;
		public float fpAdelante = 0.85f;
		public float fpInclinacion = 45f;     // grados: cuánto apunta el eje hacia arriba respecto a la mirada
		public float fpEscala = 0.35f;        // tamaño de los efectos en primera persona
		public float escalaLanzado = 0.8f;    // tamaño de los anillos en el tridente lanzado
		// estela de luz de la flecha
		public boolean estela = true;
		public boolean estelaSoloPoder = false; // true = solo flechas de un arco con Poder
		public float grosorEstela = 0.12f;
		public float vidaEstela = 25f;          // ticks que tarda en apagarse
		public String colorEstela = "#BFE9FF";
		// --- Común ---
		public float brillo = 1.0f;      // 1.0 normal; 0.5 tenue; 1.5 más intenso
		public boolean colorPersonalizado = false; // false = colores propios de cada runa
		public String color = "#FFFFFF"; // color que se usa cuando colorPersonalizado = true
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("runaarco.json");

	// Valores ya listos para usar
	public static float tamano = 1.8f, altura = 2.4f, distancia = 1.0f, brillo = 1.0f;
	public static float tamanoMobs = 1.4f, alturaMobs = 2.3f, distanciaMobs = 0.9f;
	public static boolean mobsActivos = true;
	public static boolean colorPersonalizado = false;
	public static boolean flechaPoder = true, rayosObjetos = true, rayosEnMano = true, anillosTridente = true;
	public static int rayoRgb = 0xA8E0FF, anillosRgb = 0x7FF0FF, estelaRgb = 0xBFE8FF;
	public static float tamanoRayos = 1f, tamanoAnillos = 1f, tamanoRunaFlecha = 1f, anchoEstela = 0.12f;
	public static boolean estela = true, estelaTodas = false;
	public static int duracionEstela = 16;
	public static int rojo = 255, verde = 255, azul = 255;
	public static String runa = "";
	public static String runaMobs = "";

	private static long ultimaModificacion = -1;
	private static int contador = 0;

	public static int colorRgb() {
		return (rojo << 16) | (verde << 8) | azul;
	}

	public static void setColorRgb(int c) {
		rojo = (c >> 16) & 255;
		verde = (c >> 8) & 255;
		azul = c & 255;
	}

	public static void cargar() {
		try {
			if (!Files.exists(FILE)) {
				Files.writeString(FILE, GSON.toJson(new Datos()));
			}
			ultimaModificacion = Files.getLastModifiedTime(FILE).toMillis();
			Datos d = GSON.fromJson(Files.readString(FILE), Datos.class);
			if (d == null) d = new Datos();
			tamano = clamp(d.tamano, 0.2f, 20f);
			altura = clamp(d.altura, -5f, 30f);
			distancia = clamp(d.distancia, -10f, 20f);
			runa = d.runa == null ? "" : d.runa.trim();
			mobsActivos = d.mobsActivos;
			tamanoMobs = clamp(d.tamanoMobs, 0.2f, 20f);
			alturaMobs = clamp(d.alturaMobs, -5f, 30f);
			distanciaMobs = clamp(d.distanciaMobs, -10f, 20f);
			runaMobs = d.runaMobs == null ? "" : d.runaMobs.trim();
			brillo = clamp(d.brillo, 0f, 3f);
			colorPersonalizado = d.colorPersonalizado;
			flechaPoder = d.flechaPoder;
			rayosObjetos = d.rayosObjetos;
			rayosEnMano = d.rayosEnMano;
			anillosTridente = d.anillosTridente;
			rayoRgb = parseColor(d.colorRayo);
			anillosRgb = parseColor(d.colorAnillos);
			estelaRgb = parseColor(d.colorEstela);
			escalaRayos = clamp(d.escalaRayos, 0.2f, 5f);
			escalaAnillos = clamp(d.escalaAnillos, 0.2f, 5f);
			escalaFlecha = clamp(d.escalaFlecha, 0.2f, 5f);
			delanteFlecha = clamp(d.delanteFlecha, 0f, 10f);
			alturaFlecha = clamp(d.alturaFlecha, -5f, 5f);
			alturaRayos = clamp(d.alturaRayos, -5f, 5f);
			anillosAdelante = clamp(d.anillosAdelante, -5f, 5f);
			manoLado = clamp(d.manoLado, -3f, 3f);
			manoAdelante = clamp(d.manoAdelante, -3f, 3f);
			manoAltura = clamp(d.manoAltura, -3f, 3f);
			fpLado = clamp(d.fpLado, -3f, 3f);
			fpAltura = clamp(d.fpAltura, -3f, 3f);
			fpAdelante = clamp(d.fpAdelante, 0f, 4f);
			fpInclinacion = clamp(d.fpInclinacion, -180f, 180f);
			fpEscala = clamp(d.fpEscala, 0.05f, 5f);
			escalaLanzado = clamp(d.escalaLanzado, 0.1f, 5f);
			estela = d.estela;
			estelaSoloPoder = d.estelaSoloPoder;
			grosorEstela = clamp(d.grosorEstela, 0.01f, 1f);
			vidaEstela = clamp(d.vidaEstela, 2f, 200f);
			estelaRgb = parseColor(d.colorEstela);
			tamanoRayos = clamp(d.tamanoRayos, 0.1f, 6f);
			tamanoAnillos = clamp(d.tamanoAnillos, 0.1f, 6f);
			tamanoRunaFlecha = clamp(d.tamanoRunaFlecha, 0.1f, 6f);
			fpLado = clamp(d.fpLado, -3f, 3f);
			fpAltura = clamp(d.fpAltura, -3f, 3f);
			fpAdelante = clamp(d.fpAdelante, 0f, 4f);
			fpInclinacion = clamp(d.fpInclinacion, -180f, 180f);
			fpEscala = clamp(d.fpEscala, 0.05f, 5f);
			escalaLanzado = clamp(d.escalaLanzado, 0.1f, 5f);
			estela = d.estela;
			estelaTodas = d.estelaTodas;
			anchoEstela = clamp(d.anchoEstela, 0.01f, 2f);
			duracionEstela = Math.max(2, Math.min(200, d.duracionEstela));
			setColorRgb(parseColor(d.color));
		} catch (Exception e) {
			// Si el archivo tiene un error, se conservan los valores anteriores
			System.err.println("[runaarco] No se pudo leer runaarco.json: " + e.getMessage());
		}
	}

	public static void guardar() {
		try {
			Datos d = new Datos();
			d.tamano = tamano;
			d.altura = altura;
			d.distancia = distancia;
			d.runa = runa;
			d.mobsActivos = mobsActivos;
			d.tamanoMobs = tamanoMobs;
			d.alturaMobs = alturaMobs;
			d.distanciaMobs = distanciaMobs;
			d.runaMobs = runaMobs;
			d.brillo = brillo;
			d.colorPersonalizado = colorPersonalizado;
			d.flechaPoder = flechaPoder;
			d.rayosObjetos = rayosObjetos;
			d.rayosEnMano = rayosEnMano;
			d.anillosTridente = anillosTridente;
			d.colorRayo = String.format("#%06X", rayoRgb);
			d.colorAnillos = String.format("#%06X", anillosRgb);
			d.colorEstela = String.format("#%06X", estelaRgb);
			d.escalaRayos = escalaRayos;
			d.escalaAnillos = escalaAnillos;
			d.escalaFlecha = escalaFlecha;
			d.delanteFlecha = delanteFlecha;
			d.alturaFlecha = alturaFlecha;
			d.alturaRayos = alturaRayos;
			d.anillosAdelante = anillosAdelante;
			d.manoLado = manoLado;
			d.manoAdelante = manoAdelante;
			d.manoAltura = manoAltura;
			d.fpLado = fpLado;
			d.fpAltura = fpAltura;
			d.fpAdelante = fpAdelante;
			d.fpInclinacion = fpInclinacion;
			d.fpEscala = fpEscala;
			d.escalaLanzado = escalaLanzado;
			d.estela = estela;
			d.estelaSoloPoder = estelaSoloPoder;
			d.grosorEstela = grosorEstela;
			d.vidaEstela = vidaEstela;
			d.colorEstela = String.format("#%06X", estelaRgb);
			d.tamanoRayos = tamanoRayos;
			d.tamanoAnillos = tamanoAnillos;
			d.tamanoRunaFlecha = tamanoRunaFlecha;
			d.fpLado = fpLado;
			d.fpAltura = fpAltura;
			d.fpAdelante = fpAdelante;
			d.fpInclinacion = fpInclinacion;
			d.fpEscala = fpEscala;
			d.escalaLanzado = escalaLanzado;
			d.estela = estela;
			d.estelaTodas = estelaTodas;
			d.anchoEstela = anchoEstela;
			d.duracionEstela = duracionEstela;
			d.color = String.format("#%02X%02X%02X", rojo, verde, azul);
			Files.writeString(FILE, GSON.toJson(d));
			ultimaModificacion = Files.getLastModifiedTime(FILE).toMillis();
		} catch (Exception e) {
			System.err.println("[runaarco] No se pudo guardar runaarco.json: " + e.getMessage());
		}
	}

	/** Se llama en cada fotograma; solo mira el archivo cada ~2 segundos. */
	public static void revisar() {
		if (++contador < 120) return;
		contador = 0;
		try {
			if (!Files.exists(FILE) || Files.getLastModifiedTime(FILE).toMillis() != ultimaModificacion) {
				cargar();
			}
		} catch (Exception ignored) {
		}
	}

	private static int parseColor(String s) {
		try {
			String h = s.trim();
			if (h.startsWith("#")) h = h.substring(1);
			return Integer.parseInt(h, 16) & 0xFFFFFF;
		} catch (Exception e) {
			return 0xFFFFFF;
		}
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}
}
