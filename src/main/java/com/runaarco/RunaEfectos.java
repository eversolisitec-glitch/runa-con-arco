package com.runaarco;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Efectos extra:
 *  - Flecha: runa por la que pasa (arco con Poder) y estela de luz que sigue su trayectoria.
 *  - Objetos encantados: rayos que giran alrededor y un resplandor.
 *  - Tridente encantado: anillos grandes con letras de runas.
 * Tamaños y posiciones se ajustan en RunaConfig (menú o config/runaarco.json).
 */
final class RunaEfectos {
	private RunaEfectos() {}

	static final double DISTANCIA_MAX = 40.0;
	static final int MAX_OBJETOS = 64;
	static final float VIDA_DISPARO = 30f;      // ticks que dura la runa de la flecha

	private static final Identifier TEX_GLOW = id("textures/rune/glow.png");
	private static final Identifier TEX_BANDA = id("textures/rune/banda.png");
	private static final Identifier TEX_HAZ = id("textures/rune/haz.png");
	private static final Identifier[] RAYO = {
			id("textures/rune/rayo_0.png"), id("textures/rune/rayo_1.png"),
			id("textures/rune/rayo_2.png"), id("textures/rune/rayo_3.png")};

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(RunaArcoClient.MOD_ID, path);
	}

	private record Disparo(Vec3 centro, float yaw, float pitch, float inicio, boolean mob) {}
	private record Objeto(Vec3 pos, float yaw, float pitch, boolean tridente, int semilla,
						  float escala, float escalaAnillo) {}
	private record Punto(Vec3 pos, float t) {}

	private static final List<Disparo> DISPAROS = new ArrayList<>();
	private static final List<Objeto> OBJETOS = new ArrayList<>();
	private static final Map<Integer, List<Punto>> ESTELAS = new HashMap<>();
	private static final Set<Integer> PODER_IDS = new HashSet<>();
	private static Set<Integer> flechasAnteriores = new HashSet<>();
	// tridentes: quién tenía uno encantado en la mano hace poco, y cuáles lanzados ya se identificaron
	private static final Map<Integer, Float> TRI_RECIENTE = new HashMap<>();
	private static final Set<Integer> TRI_DECIDIDOS = new HashSet<>();
	private static final Set<Integer> TRI_ENCANTADOS = new HashSet<>();
	private static float ahora = 0f;

	// ---------------------------------------------------------------- extracción
	static void extraer(ClientLevel level, float pt, Vec3 cam) {
		ahora = level.getGameTime() + pt;
		OBJETOS.clear();
		List<Entity> flechas = new ArrayList<>();
		List<Entity> tridentes = new ArrayList<>();
		List<LivingEntity> vivos = new ArrayList<>();
		double max2 = DISTANCIA_MAX * DISTANCIA_MAX;
		LivingEntity yo = Minecraft.getInstance().player;

		for (Entity e : level.entitiesForRendering()) {
			if (e.position().distanceToSqr(cam) > max2) continue;
			EntityType<?> tipo = e.getType();

			if (e instanceof ItemEntity ie) {
				ItemStack s = ie.getItem();
				if (RunaConfig.rayosObjetos && s.isEnchanted() && OBJETOS.size() < MAX_OBJETOS) {
					OBJETOS.add(new Objeto(ie.getPosition(pt).add(0, 0.35, 0), 0f, -90f,
							s.is(Items.TRIDENT), e.getId(), 1f, 1f));
				}
			} else if (tipo == EntityType.TRIDENT) {
				tridentes.add(e);
			} else if (tipo == EntityType.ARROW || tipo == EntityType.SPECTRAL_ARROW) {
				flechas.add(e);
			} else if (e instanceof LivingEntity le) {
				vivos.add(le);
				ItemStack s = le.getMainHandItem();
				boolean tri = s.is(Items.TRIDENT) && s.isEnchanted();
				if (tri || (le.getOffhandItem().is(Items.TRIDENT) && le.getOffhandItem().isEnchanted())) {
					TRI_RECIENTE.put(le.getId(), ahora);
				}
				if (RunaConfig.rayosEnMano && s.isEnchanted() && OBJETOS.size() < MAX_OBJETOS) {
					if (!tri || RunaConfig.anillosTridente) {
						boolean primeraPersona = le == yo && cam.distanceToSqr(le.getEyePosition(pt)) < 0.05;
						OBJETOS.add(primeraPersona
								? objetoPrimeraPersona(le, pt, tri, cam)
								: objetoEnMano(le, pt, tri));
					}
				}
			}
		}
		TRI_RECIENTE.values().removeIf(t -> ahora - t > 100f);

		// ---- Tridentes lanzados: los anillos rodean al tridente a lo largo de su trayectoria
		Set<Integer> triVistos = new HashSet<>();
		for (Entity t : tridentes) {
			int id = t.getId();
			triVistos.add(id);
			if (!TRI_DECIDIDOS.contains(id)) {
				// primera vez que se ve: si alguien cerca tenía un tridente encantado hace un momento, es ese
				TRI_DECIDIDOS.add(id);
				for (LivingEntity le : vivos) {
					Float h = TRI_RECIENTE.get(le.getId());
					if (h != null && ahora - h < 40f && le.position().distanceToSqr(t.position()) < 100.0) {
						TRI_ENCANTADOS.add(id);
						break;
					}
				}
			}
			boolean encantado = TRI_ENCANTADOS.contains(id) || esFoil(t);
			if (RunaConfig.anillosTridente && encantado && OBJETOS.size() < MAX_OBJETOS) {
				Vec3 v = t.getDeltaMovement();
				double h = Math.sqrt(v.x * v.x + v.z * v.z);
				float yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
				float pitch = (float) -Math.toDegrees(Math.atan2(v.y, h));
				OBJETOS.add(new Objeto(t.getPosition(pt), yaw, pitch, true, id, 1f, RunaConfig.escalaLanzado));
			}
		}
		TRI_DECIDIDOS.retainAll(triVistos);
		TRI_ENCANTADOS.retainAll(triVistos);

		// ---- Flechas: runa del disparo con Poder y estela de luz
		boolean detectarPoder = RunaConfig.flechaPoder || (RunaConfig.estela && RunaConfig.estelaSoloPoder);
		Set<Integer> vistas = new HashSet<>();
		for (Entity f : flechas) {
			int id = f.getId();
			vistas.add(id);
			Vec3 v = f.getDeltaMovement();

			if (detectarPoder && !flechasAnteriores.contains(id) && v.length() > 0.1) {
				LivingEntity tirador = null;
				double mejor = 8.0 * 8.0;
				for (LivingEntity le : vivos) {
					double d = le.getEyePosition().distanceToSqr(f.position());
					if (d < mejor && (conPoder(le.getMainHandItem()) || conPoder(le.getOffhandItem()))) {
						mejor = d;
						tirador = le;
					}
				}
				if (tirador != null) {
					PODER_IDS.add(id);
					if (RunaConfig.flechaPoder) {
						Vec3 dir = v.scale(1.0 / v.length());
						double h = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
						float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
						float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, h));
						Vec3 centro = tirador.getEyePosition()
								.add(dir.scale(RunaConfig.delanteFlecha))
								.add(0, RunaConfig.alturaFlecha, 0);
						DISPAROS.add(new Disparo(centro, yaw, pitch, ahora, !(tirador instanceof Player)));
					}
				}
			}

			if (RunaConfig.estela && (!RunaConfig.estelaSoloPoder || PODER_IDS.contains(id))
					&& v.lengthSqr() > 0.01) {
				List<Punto> l = ESTELAS.computeIfAbsent(id, k -> new ArrayList<>());
				Vec3 p = f.getPosition(pt);
				if (l.isEmpty() || l.get(l.size() - 1).pos().distanceToSqr(p) > 0.04) {
					l.add(new Punto(p, ahora));
					if (l.size() > 150) l.remove(0);
				}
			}
		}
		PODER_IDS.retainAll(vistas);
		flechasAnteriores = vistas;

		DISPAROS.removeIf(d -> ahora - d.inicio() > VIDA_DISPARO);
		float vida = RunaConfig.vidaEstela;
		Iterator<Map.Entry<Integer, List<Punto>>> it = ESTELAS.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Integer, List<Punto>> en = it.next();
			en.getValue().removeIf(p -> ahora - p.t() > vida);
			if (en.getValue().isEmpty() && !vistas.contains(en.getKey())) it.remove();
		}
	}

	/** Posición aproximada de la mano que sostiene el objeto (ajustable en la configuración). */
	private static Objeto objetoEnMano(LivingEntity le, float pt, boolean tridente) {
		float yaw = le.getViewYRot(pt);
		float pitch = le.getViewXRot(pt);
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad), fz = Math.cos(rad);       // hacia delante
		double rx = -Math.cos(rad), rz = -Math.sin(rad);      // hacia la derecha
		boolean zurdo = String.valueOf(le.getMainArm()).toUpperCase().contains("LEFT");
		double lado = zurdo ? -RunaConfig.manoLado : RunaConfig.manoLado;
		double adelante = RunaConfig.manoAdelante;
		Vec3 base = le.getPosition(pt);
		Vec3 p = new Vec3(base.x + rx * lado + fx * adelante,
				base.y + le.getBbHeight() * 0.58 + RunaConfig.manoAltura,
				base.z + rz * lado + fz * adelante);
		return new Objeto(p, yaw, pitch, tridente, le.getId(), 1f, 1f);
	}

	/**
	 * Objeto en TU mano en primera persona: se coloca respecto a la cámara (ajustable en la configuración)
	 * para que el objeto quede dentro de los rayos o de los anillos.
	 */
	private static Objeto objetoPrimeraPersona(LivingEntity le, float pt, boolean tridente, Vec3 cam) {
		float yaw = le.getViewYRot(pt);
		float pitch = le.getViewXRot(pt);
		double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
		Vec3 f = new Vec3(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));  // adelante
		Vec3 r = new Vec3(-Math.cos(ry), 0.0, -Math.sin(ry));                                           // derecha
		Vec3 u = r.cross(f);                                                                            // arriba
		boolean zurdo = String.valueOf(le.getMainArm()).toUpperCase().contains("LEFT");
		double lado = zurdo ? -RunaConfig.fpLado : RunaConfig.fpLado;
		Vec3 p = cam.add(f.scale(RunaConfig.fpAdelante))
				.add(r.scale(lado))
				.add(u.scale(RunaConfig.fpAltura));
		// el eje del objeto apunta hacia delante y hacia arriba (fpInclinacion grados sobre la mirada)
		return new Objeto(p, yaw, pitch - RunaConfig.fpInclinacion, tridente, le.getId(),
				RunaConfig.fpEscala, RunaConfig.fpEscalaAnillos);
	}

	private static boolean conPoder(ItemStack s) {
		try {
			if (!s.is(Items.BOW)) return false;
			for (var h : s.getEnchantments().keySet()) {
				if (h.getRegisteredName().endsWith(":power")) return true;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	/** ThrownTrident.isFoil() por reflexión, para no depender de en qué paquete está la clase. */
	private static boolean esFoil(Entity e) {
		try {
			return (Boolean) e.getClass().getMethod("isFoil").invoke(e);
		} catch (Throwable t) {
			return false;
		}
	}

	// ---------------------------------------------------------------- dibujo
	static void dibujar(PoseStack ps, SubmitNodeCollector out, Vec3 cam) {
		// 1) Estela de luz de las flechas
		if (!ESTELAS.isEmpty()) {
			float vida = Math.max(1f, RunaConfig.vidaEstela);
			float ancho = RunaConfig.grosorEstela;
			int rgb = RunaConfig.estelaRgb;
			for (List<Punto> l : ESTELAS.values()) {
				for (int i = 1; i < l.size(); i++) {
					Punto a = l.get(i - 1), b = l.get(i);
					float fa = fade((ahora - a.t()) / vida);
					float fb = fade((ahora - b.t()) / vida);
					if (fa <= 0.01f && fb <= 0.01f) continue;
					Vec3 ra = a.pos().subtract(cam), rb = b.pos().subtract(cam);
					cinta(out, ps, ra, rb, ancho * 3f, alfa(fa * 0.45f), alfa(fb * 0.45f), rgb);
					cinta(out, ps, ra, rb, ancho, alfa(fa), alfa(fb), rgb);
				}
			}
		}

		// 2) Runa por la que pasa la flecha
		for (Disparo d : DISPAROS) {
			float edad = ahora - d.inicio();
			if (edad < 0f) continue;
			float a = edad < 3f ? edad / 3f : Math.max(0f, 1f - (edad - 3f) / (VIDA_DISPARO - 3f));
			float crecer = 0.7f + 0.5f * Mth.clamp(edad / 8f, 0f, 1f);
			RunaLibrary.Runa ru = RunaLibrary.obtener(d.mob());
			float base = (d.mob() ? RunaConfig.tamanoMobs : RunaConfig.tamano)
					* 0.9f * crecer * RunaConfig.escalaFlecha;
			int alpha = (int) Math.min(255f, 255f * a * RunaConfig.brillo);
			int rgbRuna = RunaConfig.colorPersonalizado ? RunaConfig.colorRgb() : ru.tinte();
			int rgbHalo = RunaConfig.colorPersonalizado ? RunaConfig.colorRgb() : ru.halo();

			ps.pushPose();
			ps.translate(d.centro().x - cam.x, d.centro().y - cam.y, d.centro().z - cam.z);
			ps.mulPose(Axis.YP.rotationDegrees(-d.yaw()));
			ps.mulPose(Axis.XP.rotationDegrees(d.pitch()));
			RunaArcoClient.dibujarRuna(out, ps, ru, edad, base, alpha, rgbRuna, rgbHalo);
			ps.popPose();
		}

		// 3) Objetos encantados
		for (Objeto o : OBJETOS) {
			ps.pushPose();
			ps.translate(o.pos().x - cam.x, o.pos().y - cam.y, o.pos().z - cam.z);
			if (o.tridente()) {
				anillos(out, ps, o);
			} else {
				ps.translate(0.0, RunaConfig.alturaRayos, 0.0);
				rayos(out, ps, o);
			}
			ps.popPose();
		}
	}

	private static float fade(float t) {
		float x = Mth.clamp(1f - t, 0f, 1f);
		return x * Mth.sqrt(x);
	}

	private static int alfa(float f) {
		return (int) Math.min(255f, 255f * f * RunaConfig.brillo);
	}

	/** Cinta (haz de luz) entre dos puntos, siempre de cara a la cámara. Coordenadas relativas a la cámara. */
	private static void cinta(SubmitNodeCollector out, PoseStack ps, Vec3 a, Vec3 b,
							  float ancho, int alphaA, int alphaB, int rgb) {
		if (alphaA <= 0 && alphaB <= 0) return;
		double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
		double mx = (a.x + b.x) * 0.5, my = (a.y + b.y) * 0.5, mz = (a.z + b.z) * 0.5;
		// perpendicular al segmento y a la línea de visión
		double wx = dy * mz - dz * my, wy = dz * mx - dx * mz, wz = dx * my - dy * mx;
		double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
		if (wl < 1e-6) return;
		float px = (float) (wx / wl * ancho), py = (float) (wy / wl * ancho), pz = (float) (wz / wl * ancho);
		float ax = (float) a.x, ay = (float) a.y, az = (float) a.z;
		float bx = (float) b.x, by = (float) b.y, bz = (float) b.z;
		int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;

		out.submitCustomGeometry(ps, RenderTypes.eyes(TEX_HAZ), (pose, vc) -> {
			float[][] v = {
					{ax + px, ay + py, az + pz, 0, 0, alphaA},
					{ax - px, ay - py, az - pz, 1, 0, alphaA},
					{bx - px, by - py, bz - pz, 1, 1, alphaB},
					{bx + px, by + py, bz + pz, 0, 1, alphaB}};
			for (float[] p : v) {
				vc.addVertex(pose, p[0], p[1], p[2])
						.setColor(cr, cg, cb, (int) p[5])
						.setUv(p[3], p[4])
						.setOverlay(OverlayTexture.NO_OVERLAY)
						.setLight(0xF000F0)
						.setNormal(pose, 0f, 1f, 0f);
			}
		});
	}

	/** Rayos que giran alrededor del objeto y un resplandor. */
	private static void rayos(SubmitNodeCollector out, PoseStack ps, Objeto o) {
		int rgb = RunaConfig.rayoRgb;
		float s = o.semilla();
		float esc = RunaConfig.escalaRayos * o.escala();

		// resplandor ("luz"): tres planos cruzados para que se vea desde cualquier ángulo
		float pul = 0.85f + 0.15f * Mth.sin(ahora * 0.5f + s);
		int aGlow = (int) Math.min(255f, 140f * pul * RunaConfig.brillo);
		for (int i = 0; i < 3; i++) {
			ps.pushPose();
			if (i == 1) ps.mulPose(Axis.YP.rotationDegrees(90f));
			if (i == 2) ps.mulPose(Axis.XP.rotationDegrees(90f));
			RunaArcoClient.quad(out, ps, TEX_GLOW, 0.75f * pul * esc, 0.75f * pul * esc, 0f, aGlow, rgb);
			ps.popPose();
		}

		// arcos de rayo que giran alrededor
		for (int k = 0; k < 3; k++) {
			float flicker = 0.5f + 0.5f * Mth.sin(ahora * 2.3f + k * 1.9f + s)
					* Mth.sin(ahora * 0.9f + k);
			flicker = Mth.clamp(0.3f + flicker, 0.25f, 1f);
			int alpha = (int) Math.min(255f, 255f * flicker * RunaConfig.brillo);
			int variante = ((int) (ahora / 2f) + k + (int) s) & 3;

			ps.pushPose();
			ps.mulPose(Axis.YP.rotationDegrees(ahora * 7f + k * 120f + s * 37f));
			ps.mulPose(Axis.ZP.rotationDegrees(k * 35f - 35f));
			RunaArcoClient.quad(out, ps, RAYO[variante], 0.55f * esc, 0.55f * esc, 0f, alpha, rgb);
			ps.popPose();
		}
	}

	/**
	 * Tres anillos en 3D (bandas con letras de runas) que giran alrededor del eje del tridente.
	 * Al ser cilindros, se ven desde cualquier cámara (primera persona, tercera, de frente...).
	 */
	private static void anillos(SubmitNodeCollector out, PoseStack ps, Objeto o) {
		int rgb = RunaConfig.anillosRgb;
		float s = o.semilla();
		float esc = RunaConfig.escalaAnillos * o.escalaAnillo();
		float pul = 0.88f + 0.12f * Mth.sin(ahora * 0.35f + s);
		int alpha = (int) Math.min(255f, 255f * pul * RunaConfig.brillo);

		ps.pushPose();
		ps.mulPose(Axis.YP.rotationDegrees(-o.yaw()));
		ps.mulPose(Axis.XP.rotationDegrees(o.pitch()));
		ps.translate(0f, 0f, RunaConfig.anillosAdelante * esc);   // desplaza el conjunto a lo largo del tridente

		// brillo suave alrededor (dos planos cruzados para que se vea de lado)
		for (int i = 0; i < 2; i++) {
			ps.pushPose();
			if (i == 1) ps.mulPose(Axis.YP.rotationDegrees(90f));
			RunaArcoClient.quad(out, ps, TEX_GLOW, 0.55f * esc, 0.75f * esc, 0f, (int) (alpha * 0.30f), rgb);
			ps.popPose();
		}

		float[] tam = {0.85f, 1.15f, 0.85f};            // el del medio es algo más grande
		float[] vel = {5f, -7f, 9f};                    // grados por tick, en sentidos alternos
		for (int i = 0; i < 3; i++) {
			float z = (i - 1) * RunaConfig.separacionAnillos * esc
					+ 0.03f * esc * Mth.sin(ahora * 0.2f + i * 2.1f + s);    // sube y baja un poco
			float radio = 0.32f * esc * tam[i];
			float alto = RunaConfig.altoAnillos * esc;
			ps.pushPose();
			ps.translate(0f, 0f, z);
			ps.mulPose(Axis.ZP.rotationDegrees(ahora * vel[i] + s * 20f + i * 40f));
			banda(out, ps, radio, alto, alpha, rgb);
			ps.popPose();
		}
		ps.popPose();
	}

	/** Cilindro abierto (banda) de radio y alto dados, con la textura de runas repetida dos veces. */
	private static void banda(SubmitNodeCollector out, PoseStack ps, float radio, float alto, int alpha, int rgb) {
		final int segmentos = 28;
		final float repeticiones = 2f;
		float b = Math.min(RunaConfig.brillo, 1f);
		int cr = (int) (((rgb >> 16) & 255) * b), cg = (int) (((rgb >> 8) & 255) * b), cb = (int) ((rgb & 255) * b);
		float h = alto * 0.5f;

		out.submitCustomGeometry(ps, RenderTypes.eyes(TEX_BANDA), (pose, vc) -> {
			for (int i = 0; i < segmentos; i++) {
				float a0 = (float) (2 * Math.PI * i / segmentos);
				float a1 = (float) (2 * Math.PI * (i + 1) / segmentos);
				float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
				float u0 = repeticiones * i / segmentos, u1 = repeticiones * (i + 1) / segmentos;
				for (int cara = 0; cara < 2; cara++) {
					float n = cara == 0 ? 1f : -1f;   // cara exterior e interior
					float[][] v = cara == 0
							? new float[][]{{c0, s0, -h, u0, 1}, {c1, s1, -h, u1, 1}, {c1, s1, h, u1, 0}, {c0, s0, h, u0, 0}}
							: new float[][]{{c1, s1, -h, u1, 1}, {c0, s0, -h, u0, 1}, {c0, s0, h, u0, 0}, {c1, s1, h, u1, 0}};
					for (float[] p : v) {
						vc.addVertex(pose, p[0] * radio, p[1] * radio, p[2])
								.setColor(cr, cg, cb, alpha)
								.setUv(p[3], p[4])
								.setOverlay(OverlayTexture.NO_OVERLAY)
								.setLight(0xF000F0)
								.setNormal(pose, p[0] * n, p[1] * n, 0f);
					}
				}
			}
		});
	}
}
