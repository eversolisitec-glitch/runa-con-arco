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
		public float tamano = 1.8f;      // media anchura en bloques
		public float altura = 2.4f;      // altura del centro sobre los pies
		public float distancia = 1.0f;   // distancia detrás de la espalda
		public float brillo = 1.0f;      // 1.0 normal; 0.5 tenue; 1.5 más intenso
		public String color = "#FFFFFF"; // tinte: #FFFFFF = colores originales de la runa
		public String runa = "";         // nombre del archivo .runa en la carpeta runas ("" = la predeterminada)
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("runaarco.json");

	// Valores ya listos para usar
	public static float tamano = 1.8f, altura = 2.4f, distancia = 1.0f, brillo = 1.0f;
	public static int rojo = 255, verde = 255, azul = 255;
	public static String runa = "";

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
			brillo = clamp(d.brillo, 0f, 3f);
			setColorRgb(parseColor(d.color));
			runa = d.runa == null ? "" : d.runa.trim();
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
			d.brillo = brillo;
			d.color = String.format("#%02X%02X%02X", rojo, verde, azul);
			d.runa = runa;
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
