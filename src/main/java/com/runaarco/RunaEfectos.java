package com.runaarco;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Efectos extra:
 *  - Flecha de un arco con Poder: aparece una runa por la que pasa la flecha.
 *  - Objetos encantados: rayos que giran alrededor y un resplandor.
 *  - Tridente encantado: anillos grandes con letras de runas.
 */
final class RunaEfectos {
	private RunaEfectos() {}

	// ===== AJUSTES =====
	static final double DISTANCIA_MAX = 40.0;
	static final int MAX_OBJETOS = 64;
	static final float VIDA_DISPARO = 30f;      // ticks que dura la runa de la flecha
	static final float DELANTE_DISPARO = 1.8f;  // a cuántos bloques delante de los ojos aparece
	// ===================

	private static final Identifier TEX_GLOW = id("textures/rune/glow.png");
	private static final Identifier TEX_ANILLO = id("textures/rune/anillo.png");
	private static final Identifier[] RAYO = {
			id("textures/rune/rayo_0.png"), id("textures/rune/rayo_1.png"),
			id("textures/rune/rayo_2.png"), id("textures/rune/rayo_3.png")};

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(RunaArcoClient.MOD_ID, path);
	}

	private record Disparo(Vec3 centro, float yaw, float pitch, float inicio, boolean mob) {}
	private record Objeto(Vec3 pos, float yaw, float pitch, boolean tridente, int semilla) {}

	private static final List<Disparo> DISPAROS = new ArrayList<>();
	private static final List<Objeto> OBJETOS = new ArrayList<>();
	private static Set<Integer> flechasAnteriores = new HashSet<>();
	private static float ahora = 0f;

	// ---------------------------------------------------------------- extracción
	static void extraer(ClientLevel level, float pt, Vec3 cam) {
		ahora = level.getGameTime() + pt;
		OBJETOS.clear();
		List<Entity> flechas = new ArrayList<>();
		List<LivingEntity> vivos = new ArrayList<>();
		double max2 = DISTANCIA_MAX * DISTANCIA_MAX;

		for (Entity e : level.entitiesForRendering()) {
			if (e.position().distanceToSqr(cam) > max2) continue;
			EntityType<?> tipo = e.getType();

			if (e instanceof ItemEntity ie) {
				ItemStack s = ie.getItem();
				if (RunaConfig.rayosObjetos && s.isEnchanted() && OBJETOS.size() < MAX_OBJETOS) {
					OBJETOS.add(new Objeto(ie.getPosition(pt).add(0, 0.35, 0), 0f, -90f,
							s.is(Items.TRIDENT), e.getId()));
				}
			} else if (tipo == EntityType.TRIDENT) {
				if (RunaConfig.anillosTridente && esFoil(e) && OBJETOS.size() < MAX_OBJETOS) {
					Vec3 v = e.getDeltaMovement();
					double h = Math.sqrt(v.x * v.x + v.z * v.z);
					float yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
					float pitch = (float) -Math.toDegrees(Math.atan2(v.y, h));
					OBJETOS.add(new Objeto(e.getPosition(pt), yaw, pitch, true, e.getId()));
				}
			} else if (tipo == EntityType.ARROW || tipo == EntityType.SPECTRAL_ARROW) {
				flechas.add(e);
			} else if (e instanceof LivingEntity le) {
				vivos.add(le);
				if (RunaConfig.rayosEnMano && OBJETOS.size() < MAX_OBJETOS) {
					ItemStack s = le.getMainHandItem();
					if (s.isEnchanted()) {
						boolean tri = s.is(Items.TRIDENT);
						if (!tri || RunaConfig.anillosTridente) {
							OBJETOS.add(objetoEnMano(le, pt, tri));
						}
					}
				}
			}
		}

		// Flechas nuevas disparadas desde un arco con Poder
		Set<Integer> vistas = new HashSet<>();
		for (Entity f : flechas) {
			vistas.add(f.getId());
			if (!RunaConfig.flechaPoder || flechasAnteriores.contains(f.getId())) continue;

			Vec3 v = f.getDeltaMovement();
			double len = v.length();
			if (len < 0.1) continue;
			LivingEntity tirador = null;
			double mejor = 8.0 * 8.0;
			for (LivingEntity le : vivos) {
				double d = le.getEyePosition().distanceToSqr(f.position());
				if (d < mejor && (conPoder(le.getMainHandItem()) || conPoder(le.getOffhandItem()))) {
					mejor = d;
					tirador = le;
				}
			}
			if (tirador == null) continue;

			Vec3 dir = v.scale(1.0 / len);
			double h = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
			float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
			float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, h));
			Vec3 centro = tirador.getEyePosition().add(dir.scale(DELANTE_DISPARO));
			DISPAROS.add(new Disparo(centro, yaw, pitch, ahora, !(tirador instanceof Player)));
		}
		flechasAnteriores = vistas;
		DISPAROS.removeIf(d -> ahora - d.inicio() > VIDA_DISPARO);
	}

	/** Posición aproximada de la mano que sostiene el objeto. */
	private static Objeto objetoEnMano(LivingEntity le, float pt, boolean tridente) {
		float yaw = le.getViewYRot(pt);
		float pitch = le.getViewXRot(pt);
		double rad = Math.toRadians(yaw);
		double fx = -Math.sin(rad), fz = Math.cos(rad);       // hacia delante
		double rx = -Math.cos(rad), rz = -Math.sin(rad);      // hacia la derecha
		boolean zurdo = String.valueOf(le.getMainArm()).toUpperCase().contains("LEFT");
		double lado = zurdo ? -0.38 : 0.38;
		Vec3 base = le.getPosition(pt);
		Vec3 p = new Vec3(base.x + rx * lado + fx * 0.45,
				base.y + le.getBbHeight() * 0.58,
				base.z + rz * lado + fz * 0.45);
		return new Objeto(p, yaw, pitch, tridente, le.getId());
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
		// 1) Runa por la que pasa la flecha
		for (Disparo d : DISPAROS) {
			float edad = ahora - d.inicio();
			if (edad < 0f) continue;
			float a = edad < 3f ? edad / 3f : Math.max(0f, 1f - (edad - 3f) / (VIDA_DISPARO - 3f));
			float crecer = 0.7f + 0.5f * Mth.clamp(edad / 8f, 0f, 1f);
			RunaLibrary.Runa ru = RunaLibrary.obtener(d.mob());
			float base = (d.mob() ? RunaConfig.tamanoMobs : RunaConfig.tamano) * 0.9f * crecer;
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

		// 2) Objetos encantados
		for (Objeto o : OBJETOS) {
			ps.pushPose();
			ps.translate(o.pos().x - cam.x, o.pos().y - cam.y, o.pos().z - cam.z);
			if (o.tridente()) {
				anillos(out, ps, o);
			} else {
				rayos(out, ps, o);
			}
			ps.popPose();
		}
	}

	/** Rayos que giran alrededor del objeto y un resplandor. */
	private static void rayos(SubmitNodeCollector out, PoseStack ps, Objeto o) {
		int rgb = RunaConfig.rayoRgb;
		float s = o.semilla();

		// resplandor ("luz"): tres planos cruzados para que se vea desde cualquier ángulo
		float pul = 0.85f + 0.15f * Mth.sin(ahora * 0.5f + s);
		int aGlow = (int) Math.min(255f, 140f * pul * RunaConfig.brillo);
		for (int i = 0; i < 3; i++) {
			ps.pushPose();
			if (i == 1) ps.mulPose(Axis.YP.rotationDegrees(90f));
			if (i == 2) ps.mulPose(Axis.XP.rotationDegrees(90f));
			RunaArcoClient.quad(out, ps, TEX_GLOW, 0.75f * pul, 0.75f * pul, 0f, aGlow, rgb);
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
			RunaArcoClient.quad(out, ps, RAYO[variante], 0.55f, 0.55f, 0f, alpha, rgb);
			ps.popPose();
		}
	}

	/** Tres anillos grandes con letras de runas, girando en sentidos contrarios a lo largo del tridente. */
	private static void anillos(SubmitNodeCollector out, PoseStack ps, Objeto o) {
		int rgb = RunaConfig.anillosRgb;
		float s = o.semilla();
		float pul = 0.85f + 0.15f * Mth.sin(ahora * 0.35f + s);
		int alpha = (int) Math.min(255f, 230f * pul * RunaConfig.brillo);

		ps.pushPose();
		ps.mulPose(Axis.YP.rotationDegrees(-o.yaw()));
		ps.mulPose(Axis.XP.rotationDegrees(o.pitch()));

		RunaArcoClient.quad(out, ps, TEX_GLOW, 1.5f, 1.5f, -0.01f, (int) (alpha * 0.55f), rgb);
		for (int i = 0; i < 3; i++) {
			float radio = (i == 1) ? 1.5f : 1.15f;
			float desplazo = (i - 1) * 0.9f;
			float giro = ahora * ((i % 2 == 0) ? 5f : -7f) + s * 20f;
			ps.pushPose();
			ps.translate(0f, 0f, desplazo);
			ps.mulPose(Axis.ZP.rotationDegrees(giro));
			RunaArcoClient.quad(out, ps, TEX_ANILLO, radio, radio, 0f, alpha, rgb);
			ps.popPose();
		}
		ps.popPose();
	}
}
