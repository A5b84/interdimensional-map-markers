package io.github.a5b84.interdimensionalmapmarkers.mixin;

import static net.minecraft.world.dimension.DimensionType.OVERWORLD;
import static net.minecraft.world.dimension.DimensionType.THE_END;
import static net.minecraft.world.dimension.DimensionType.THE_NETHER;

import java.util.Map;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.map.MapIcon;
import net.minecraft.item.map.MapIcon.Type;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IWorld;
import net.minecraft.world.dimension.DimensionType;

@Mixin(MapState.class)
public abstract class MapStateMixin {
    
    @Shadow public int xCenter;
    @Shadow public int zCenter;
    @Shadow public DimensionType dimension;
    @Shadow public boolean unlimitedTracking;
    @Shadow public byte scale;
    @Shadow public @Final Map<String, MapIcon> icons;

    /**
     * Permet de mettre à jour les icônes de joueurs dans les mauvaises
     * dimensions.
     * @see MapState#update */
    @Redirect(method = "update", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerEntity;dimension:Lnet/minecraft/world/dimension/DimensionType;"))
    private DimensionType playerDimensionProxy(PlayerEntity player) {
        return dimension;
    }


    /** Déplace et recolore les icônes de joueurs
     * @see MapState#addIcon */
    @Inject(method = "addIcon", at = @At("HEAD"), cancellable = true)
    private void onAddIcon(Type type, IWorld world, String key, double x, double z, double rotation, Text text, CallbackInfo ci) {
        // On touche que les icônes de joueurs dans la mauvaise dimension
        if (!isPlayerMarker(type)) return;
        if (world == null || dimension == world.getDimension().getType()) return;

        ci.cancel();
        final int mapScale = 1 << scale;
        final float dimScale = (world != null ? getDimScale(world.getDimension().getType()) : 1);
        // Coordonnées en pixel avec (0, 0) au centre
        final float mapX = (float) (x - xCenter) * dimScale / mapScale;
        final float mapY = (float) (z - zCenter) * dimScale / mapScale;
        rotation += rotation < 0 ? -8 : 8; // Pour arrondir
        byte mapRot = (byte) (rotation * 16 / 360);
        type = adjustMarkerType(type, world);

        if (mapX >= -63 && mapY >= -63 && mapX <= 63 && mapY <= 63) {
            // Rotation random dans le Nether
            if (dimension == DimensionType.THE_NETHER && world != null) {
                final int t = (int) (world.getLevelProperties().getTimeOfDay() / 10);
                mapRot = (byte) ((t * t * 34187121 + t * 121) >> 15 & 15);
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

        final byte iconX = clampToByte(mapX * 2 + .5);
        final byte iconY = clampToByte(mapY * 2 + .5);
        icons.put(key, new MapIcon(type, iconX, iconY, mapRot, text));
    }



    @Unique private Type adjustMarkerType(Type currType, IWorld world) {
        if (world == null) return currType;
        final DimensionType dim = world.getDimension().getType();
        if (dim == dimension) return currType;

        if (dim == OVERWORLD) return Type.FRAME; // Marqueur vert
        if (dim == THE_NETHER) return Type.RED_MARKER;
        if (dim == THE_END) return Type.BLUE_MARKER;

        return currType;
    }

    /** Renvoie le ratio de distances entre la dimension en paramètre et celle
     * de la carte. */
    @Unique private float getDimScale(DimensionType worldDim) {
        if (dimension == OVERWORLD && worldDim == THE_NETHER) return 8;
        if (dimension == THE_NETHER && worldDim == OVERWORLD) return 1 / 8;
        return 1;
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

    @Unique private static byte clampToByte(double x) {
        return (byte) MathHelper.clamp(x, Byte.MIN_VALUE, Byte.MAX_VALUE);
    }

}
