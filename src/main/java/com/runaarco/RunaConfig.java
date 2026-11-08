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
		public String colorRayo = "#A8E0FF";
		public String colorAnillos = "#7FF0FF";
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
	public static int rayoRgb = 0xA8E0FF, anillosRgb = 0x7FF0FF;
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
