package io.github.a5b84.interdimensionalmapmarkers.mixin;

import java.util.Map;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.item.map.MapIcon;
import net.minecraft.item.map.MapIcon.Type;
import net.minecraft.item.map.MapState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

@Mixin(MapState.class)
public abstract class MapStateMixin {

    @Shadow public int xCenter;
    @Shadow public int zCenter;
    @Shadow public RegistryKey<World> dimension;
    @Shadow public boolean unlimitedTracking;
    @Shadow public byte scale;
    @Shadow public @Final Map<String, MapIcon> icons;

    private @Nullable World mapWorld;



    /**
     * Permet de mettre à jour les icônes de joueurs dans les mauvaises
     * dimensions
     * @see MapState#update
     */
    @Redirect(method = "update",
        at = @At(value = "INVOKE", target = "net/minecraft/world/World.getRegistryKey()Lnet/minecraft/util/registry/RegistryKey;"))
    private RegistryKey<World> playerDimensionProxy(World world) {
        return dimension;
    }


    /** Déplace et recolore les icônes de joueurs
     * @see MapState#addIcon */
    @Inject(method = "addIcon", at = @At("HEAD"), cancellable = true)
    private void onAddIcon(Type type, @Nullable WorldAccess world, String key, double x, double z, double rotation, @Nullable Text text, CallbackInfo ci) {
        // On touche que les icônes de joueurs dans la mauvaise dimension
        if (!isPlayerMarker(type)
                || !(world instanceof World)
                || dimension == ((World) world).getRegistryKey()) {
            return;
        }

        ci.cancel();
        final int mapScale = 1 << scale;
        final float dimScale = getDimensionScale((World) world);
        // Coordonnées en pixel avec (0, 0) au centre
        final float mapX = (float) (x * dimScale - xCenter) / mapScale;
        final float mapY = (float) (z * dimScale - zCenter) / mapScale;
        rotation += rotation < 0 ? -8 : 8; // Pour arrondir
        byte mapRot = (byte) (rotation * 16 / 360);
        type = adjustMarkerType(type, (World) world);

        if (mapX >= -63 && mapY >= -63 && mapX <= 63 && mapY <= 63) {
            // Rotation random dans le Nether
            // (volé à Mojang) TODO enlever
            if (dimension == World.NETHER && world != null) {
                final int t = (int) (world.getLevelProperties().getTimeOfDay() / 10);
                mapRot = (byte) (t * (t * 34187121 + 121) >> 15 & 15);
            }

        } else if (type == Type.PLAYER) {
            // Icône différente selon la distance (si le type à pas changé)
            if (Math.abs(mapX) < 320 && Math.abs(mapY) < 320) {
                type = Type.PLAYER_OFF_MAP;
                mapRot = 0;
            } else if (unlimitedTracking) {
                type = Type.PLAYER_OFF_LIMITS;
                mapRot = 0;
            } else {
                icons.remove(key);
                return;
            }
        }

        final byte iconX = clampToByte(mapX * 2 + .5f);
        final byte iconY = clampToByte(mapY * 2 + .5f);
        icons.put(key, new MapIcon(type, iconX, iconY, mapRot, text));
        // TODO injecter ailleurs pour enlever le code volé
    }



    /** Renvoie le monde de la carte */
    @Unique private @Nullable World getWorld(MinecraftServer server) {
        if (mapWorld == null || mapWorld.getRegistryKey() != dimension) {
            return mapWorld = server.getWorld(dimension);
        }
        return mapWorld;
    }

    @Unique private Type adjustMarkerType(Type currType, World markerWorld) {
        final RegistryKey<World> markerKey = markerWorld.getRegistryKey();
        if (markerKey == dimension) return currType;

        if (markerKey == World.OVERWORLD) return Type.FRAME; // Marqueur vert
        if (markerKey == World.NETHER) return Type.RED_MARKER;
        if (markerKey == World.END) return Type.BLUE_MARKER;

        return currType;
    }

    /** Renvoie le ratio de distances entre la dimension en paramètre et celle
     * de la carte. */
    @Unique private float getDimensionScale(World world) {
        final World mapWorld = getWorld(world.getServer());
        if (mapWorld == null) return 1;
        return (float) (world.getDimension().getCoordinateScale() / mapWorld.getDimension().getCoordinateScale());
    }

    /** Vérifie si une icône est celle du joueur */
    @Unique private static boolean isPlayerMarker(Type type) {
        return type == Type.PLAYER
            || type == Type.PLAYER_OFF_MAP
            || type == Type.PLAYER_OFF_LIMITS
            || type == Type.FRAME
            || type == Type.RED_MARKER
            || type == Type.BLUE_MARKER;
    }

    @Unique private static byte clampToByte(float x) {
        return (byte) MathHelper.clamp(x, Byte.MIN_VALUE, Byte.MAX_VALUE);
    }

}
