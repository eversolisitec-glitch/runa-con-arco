package com.runaarco;

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
 *   runa.json -> opcional: { "nombre": "...", "cuadros": 12, "ticksPorFrame": 2, "interpolar": true }
 */
public class RunaLibrary {
	public static final String PREDETERMINADA = "(predeterminada)";
	private static final String[] EJEMPLOS = {"runa_arco.runa", "circulo_cian.runa", "circulo_violeta.runa"};
	private static final int MAX_FRAMES = 128;

	/** Lo que necesita el dibujo. aspecto = alto / ancho de cada cuadro. */
	public record Runa(Identifier[] frames, float ticksPorFrame, boolean interpolar, float aspecto) {}

	private static final Path DIR = FabricLoader.getInstance().getGameDir().resolve("runas");

	private static Runa porDefecto;
	private static Runa actual;
	private static String cargada = null;
	private static long mtimeCargada = -1;
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

	public static Runa obtener() {
		return actual != null ? actual : predeterminada();
	}

	/** Se llama cada fotograma: recarga si cambió el nombre elegido o el archivo. */
	public static void sincronizar(String nombre) {
		String n = nombre == null ? "" : nombre;
		boolean revisarArchivo = false;
		if (++contador >= 120) {
			contador = 0;
			revisarArchivo = !n.isEmpty();
		}
		boolean cambioNombre = !Objects.equals(n, cargada);
		if (!cambioNombre && !revisarArchivo) return;

		if (n.isEmpty() || n.equals(PREDETERMINADA)) {
			actual = predeterminada();
			cargada = n;
			return;
		}
		Path f = DIR.resolve(n);
		try {
			long m = Files.getLastModifiedTime(f).toMillis();
			if (!cambioNombre && m == mtimeCargada) return;
			actual = cargarArchivo(f);
			mtimeCargada = m;
		} catch (Exception e) {
			System.err.println("[runaarco] No se pudo cargar " + n + ": " + e.getMessage());
			actual = predeterminada();
			mtimeCargada = -1;
		}
		cargada = n;
	}

	private static Runa predeterminada() {
		if (porDefecto == null) {
			Identifier[] f = new Identifier[12];
			for (int i = 0; i < 12; i++) {
				f[i] = Identifier.fromNamespaceAndPath(RunaArcoClient.MOD_ID,
						String.format("textures/rune/frame_%02d.png", i));
			}
			porDefecto = new Runa(f, 2f, true, 1f);
		}
		return porDefecto;
	}

	private static Runa cargarArchivo(Path file) throws Exception {
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

			Identifier[] ids = new Identifier[n];
			var tm = Minecraft.getInstance().getTextureManager();
			for (int i = 0; i < n; i++) {
				BufferedImage sub = img.getSubimage(0, i * fh, w, fh);
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ImageIO.write(sub, "png", bos);
				NativeImage ni = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));
				// ids fijos: al recargar se reemplazan las texturas anteriores
				Identifier id = Identifier.fromNamespaceAndPath(RunaArcoClient.MOD_ID, String.format("dyn/f_%03d", i));
				tm.register(id, crearTextura(ni, "runaarco/f" + i));
				ids[i] = id;
			}
			return new Runa(ids, Math.max(0.1f, ticks), interpolar, (float) fh / (float) w);
		}
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
