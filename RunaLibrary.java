package com.runaarco;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lee archivos .runa de la carpeta "runas" de la instancia.
 *
 * Un .runa es un ZIP con:
 *   runa.png  -> tira vertical con los cuadros de la animación (uno debajo de otro, todos del mismo tamaño)
 *   runa.json -> opcional:
 *     { "nombre": "...", "cuadros": 12, "ticksPorFrame": 2, "interpolar": true,
 *       "tinte": "#FFA45B",   // color con el que se ve si NO eliges un color propio (la imagen suele ir en gris)
 *       "halo": "#FF7828",    // color del resplandor de atrás (si falta se calcula solo)
 *       "capas": [ { "archivo": "fondo.png", "giro": 0.5 } ] }  // imágenes que giran detrás (grados por tick)
 */
public class RunaLibrary {
	public static final String PREDETERMINADA = "(predeterminada)";
	private static final String[] EJEMPLOS = {
			"runa_arco.runa", "circulo_cian.runa", "circulo_violeta.runa", "alas_cruz.runa", "fenix.runa", "transmutacion.runa"};
	private static final int MAX_FRAMES = 128;
	private static final int MAX_CAPAS = 8;

	/** Una imagen que gira detrás de la runa. giro = grados por tick. */
	public record Capa(Identifier id, float giro) {}

	/** Lo que necesita el dibujo. aspecto = alto / ancho de cada cuadro. Los colores son RGB. */
	public record Runa(Identifier[] frames, float ticksPorFrame, boolean interpolar, float aspecto,
					   int tinte, int halo, Capa[] capas) {}

	private static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("runas");

	/** Una runa cargada: una para jugadores y otra para mobs. */
	private static final class Slot {
		final String prefijo;
		Runa actual;
		String cargada = null;
		long mtime = -1;
		Slot(String prefijo) { this.prefijo = prefijo; }
	}

	private static final Slot JUGADORES = new Slot("f");
	private static final Slot MOBS = new Slot("m");

	private static Runa porDefecto;
	private static int contador = 0;

	/** Crea la carpeta y copia las runas de ejemplo la primera vez. */
	public static void iniciar() {
		try {
			Files.createDirectories(DIR);
			for (String n : EJEMPLOS) {
				Path dest = DIR.resolve(n);
				if (Files.exists(dest)) continue;
				try (InputStream in = RunaLibrary.class.getResourceAsStream("/assets/runaarco/runas/" + n)) {
					if (in != null) Files.copy(in, dest);
				}
			}
		} catch (Exception e) {
			System.err.println("[runaarco] No se pudo preparar la carpeta runas: " + e.getMessage());
		}
	}

	/** Nombres para el menú: primero la predeterminada, luego cada .runa. */
	public static List<String> listar() {
		List<String> out = new ArrayList<>();
		out.add(PREDETERMINADA);
		try (Stream<Path> s = Files.list(DIR)) {
			s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".runa"))
					.map(p -> p.getFileName().toString())
					.sorted()
					.forEach(out::add);
		} catch (Exception ignored) {
		}
		return out;
	}

	public static Runa obtener(boolean mob) {
		Slot s = mob ? MOBS : JUGADORES;
		return s.actual != null ? s.actual : predeterminada();
	}

	/** Se llama cada fotograma: recarga si cambió el nombre elegido o el archivo. */
	public static void sincronizar() {
		boolean revisar = false;
		if (++contador >= 120) {
			contador = 0;
			revisar = true;
		}
		sincronizar(JUGADORES, RunaConfig.runa, revisar);
		sincronizar(MOBS, RunaConfig.runaMobs, revisar);
	}

	private static void sincronizar(Slot s, String nombre, boolean revisarArchivo) {
		String n = nombre == null ? "" : nombre;
		boolean cambioNombre = !Objects.equals(n, s.cargada);
		if (!cambioNombre && !(revisarArchivo && !n.isEmpty())) return;

		if (n.isEmpty() || n.equals(PREDETERMINADA)) {
			s.actual = predeterminada();
			s.cargada = n;
			return;
		}
		Path f = DIR.resolve(n);
		try {
			long m = Files.getLastModifiedTime(f).toMillis();
			if (!cambioNombre && m == s.mtime) return;
			s.actual = cargarArchivo(f, s.prefijo);
			s.mtime = m;
		} catch (Exception e) {
			System.err.println("[runaarco] No se pudo cargar " + n + ": " + e.getMessage());
			s.actual = predeterminada();
			s.mtime = -1;
		}
		s.cargada = n;
	}

	private static Runa predeterminada() {
		if (porDefecto == null) {
			Identifier[] f = new Identifier[12];
			for (int i = 0; i < 12; i++) {
				f[i] = Identifier.fromNamespaceAndPath(RunaArcoClient.MOD_ID,
						String.format("textures/rune/frame_%02d.png", i));
			}
			porDefecto = new Runa(f, 2f, true, 1f, 0xFFFFFF, 0x46E1E1, new Capa[0]);
		}
		return porDefecto;
	}

	private static Runa cargarArchivo(Path file, String prefijo) throws Exception {
		try (ZipFile z = new ZipFile(file.toFile())) {
			ZipEntry pe = z.getEntry("runa.png");
			if (pe == null) throw new IllegalStateException("el archivo no tiene runa.png");

			JsonObject j = new JsonObject();
			ZipEntry je = z.getEntry("runa.json");
			if (je != null) {
				try (InputStreamReader r = new InputStreamReader(z.getInputStream(je), StandardCharsets.UTF_8)) {
					j = JsonParser.parseReader(r).getAsJsonObject();
				}
			}

			BufferedImage img;
			try (InputStream in = z.getInputStream(pe)) {
				img = ImageIO.read(in);
			}
			int w = img.getWidth(), h = img.getHeight();
			int n = j.has("cuadros") ? j.get("cuadros").getAsInt() : Math.max(1, h / w);
			n = Math.max(1, Math.min(n, MAX_FRAMES));
			int fh = h / n;
			float ticks = j.has("ticksPorFrame") ? j.get("ticksPorFrame").getAsFloat() : 2f;
			boolean interpolar = !j.has("interpolar") || j.get("interpolar").getAsBoolean();

			int tinte = j.has("tinte") ? hex(j.get("tinte").getAsString(), 0xFFFFFF) : 0xFFFFFF;
			int halo = j.has("halo") ? hex(j.get("halo").getAsString(), tinte)
					: (j.has("tinte") ? tinte : promedio(img));

			Identifier[] ids = new Identifier[n];
			var tm = Minecraft.getInstance().getTextureManager();
			for (int i = 0; i < n; i++) {
				BufferedImage sub = img.getSubimage(0, i * fh, w, fh);
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ImageIO.write(sub, "png", bos);
				NativeImage ni = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));
				// ids fijos por espacio (f = jugadores, m = mobs): al recargar se reemplazan las anteriores
				Identifier id = Identifier.fromNamespaceAndPath(RunaArcoClient.MOD_ID, String.format("dyn/%s_%03d", prefijo, i));
				tm.register(id, crearTextura(ni, "runaarco/" + prefijo + i));
				ids[i] = id;
			}

			// Capas que giran detrás (opcional)
			List<Capa> capas = new ArrayList<>();
			if (j.has("capas") && j.get("capas").isJsonArray()) {
				JsonArray arr = j.getAsJsonArray("capas");
				for (int c = 0; c < arr.size() && c < MAX_CAPAS; c++) {
					JsonElement el = arr.get(c);
					if (!el.isJsonObject()) continue;
					JsonObject o = el.getAsJsonObject();
					if (!o.has("archivo")) continue;
					ZipEntry ce = z.getEntry(o.get("archivo").getAsString());
					if (ce == null) continue;
					byte[] datos;
					try (InputStream in = z.getInputStream(ce)) {
						datos = in.readAllBytes();
					}
					NativeImage ni = NativeImage.read(new ByteArrayInputStream(datos));
					Identifier id = Identifier.fromNamespaceAndPath(RunaArcoClient.MOD_ID, String.format("dyn/%s_c%02d", prefijo, c));
					tm.register(id, crearTextura(ni, "runaarco/" + prefijo + "c" + c));
					float giro = o.has("giro") ? o.get("giro").getAsFloat() : 0f;
					capas.add(new Capa(id, giro));
				}
			}
			return new Runa(ids, Math.max(0.1f, ticks), interpolar, (float) fh / (float) w,
					tinte, halo, capas.toArray(new Capa[0]));
		}
	}

	private static int hex(String s, int defecto) {
		try {
			String h = s.trim();
			if (h.startsWith("#")) h = h.substring(1);
			return Integer.parseInt(h, 16) & 0xFFFFFF;
		} catch (Exception e) {
			return defecto;
		}
	}

	/** Color medio de los píxeles visibles (para el halo cuando el .runa no lo indica). */
	private static int promedio(BufferedImage img) {
		long r = 0, g = 0, b = 0, peso = 0;
		for (int y = 0; y < img.getHeight(); y += 4) {
			for (int x = 0; x < img.getWidth(); x += 4) {
				int p = img.getRGB(x, y);
				int a = (p >>> 24) & 255;
				if (a < 60) continue;
				r += ((p >> 16) & 255) * (long) a;
				g += ((p >> 8) & 255) * (long) a;
				b += (p & 255) * (long) a;
				peso += a;
			}
		}
		if (peso == 0) return 0xFFFFFF;
		return (int) (r / peso) << 16 | (int) (g / peso) << 8 | (int) (b / peso);
	}

	/** Crea un DynamicTexture sin depender de la forma exacta de su constructor. */
	private static AbstractTexture crearTextura(NativeImage ni, String etiqueta) throws Exception {
		for (Constructor<?> c : DynamicTexture.class.getConstructors()) {
			Class<?>[] pt = c.getParameterTypes();
			Object[] args = new Object[pt.length];
			boolean ok = true, tieneImagen = false;
			for (int i = 0; i < pt.length; i++) {
				if (pt[i] == NativeImage.class) {
					args[i] = ni;
					tieneImagen = true;
				} else if (pt[i] == Supplier.class) {
					Supplier<String> sup = () -> etiqueta;
					args[i] = sup;
				} else if (pt[i] == String.class) {
					args[i] = etiqueta;
				} else {
					ok = false;
				}
			}
			if (ok && tieneImagen) {
				AbstractTexture t = (AbstractTexture) c.newInstance(args);
				try {
					t.getClass().getMethod("upload").invoke(t);
				} catch (Exception ignored) {
				}
				return t;
			}
		}
		throw new IllegalStateException("No hay un constructor compatible de DynamicTexture");
	}
}
