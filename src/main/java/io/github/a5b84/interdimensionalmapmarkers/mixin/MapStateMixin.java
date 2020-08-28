package io.github.a5b84.interdimensionalmapmarkers.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.item.map.MapIcon.Type;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;

@Mixin(MapState.class)
public abstract class MapStateMixin {

    @Shadow public RegistryKey<World> dimension;

    @Unique private @Nullable World mapWorld;
    @Unique private @Nullable World markerWorld;
    @Unique private double coordinateScale;
    @Unique private double markerRotation;



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

    /** Déplace les marqueurs de joueurs dans les mauvaises dimensions
     * @see MapState#addIcon */
    @ModifyVariable(method = "addIcon", at = @At("HEAD"), ordinal = 0)
    private double adjustX(double x, Type type, @Nullable WorldAccess world, String key, double _x, double z, double rotation) {
        markerWorld = world instanceof World ? (World) world : null;
        markerRotation = rotation;
        coordinateScale = getCoordinateScale();
        return x * coordinateScale;
    }

    /**
     * Déplace les marqueurs de joueurs dans les mauvaises dimensions
     * et enregistre le monde du marqueur et son orientation
     * @see MapState#addIcon
     */
    @ModifyVariable(method = "addIcon", at = @At("HEAD"), ordinal = 1)
    private double adjustZ(double z) {
        return z * coordinateScale;
    }

    /** Augmente la distance max des marqueurs selon les dimensions */
    @ModifyConstant(method = "addIcon", constant = @Constant(floatValue = 320))
    private float adjustOffMapDistance(float d) {
        return d * Math.max((float) coordinateScale, 1);
    }

    /** Recolore les icônes de joueurs dans les mauvaises dimensions
     * @see MapState#addIcon */
    @ModifyArg(method = "addIcon",
        at = @At(value = "INVOKE", target = "net/minecraft/item/map/MapIcon.<init>(Lnet/minecraft/item/map/MapIcon$Type;BBBLnet/minecraft/text/Text;)V"), index = 0)
    private Type adjustMarkerType(Type type, byte x, byte z, byte rotation, @Nullable Text text) {
        // On modifie que les icônes de joueurs
        if (type != Type.PLAYER
                && type != Type.PLAYER_OFF_MAP
                && type != Type.PLAYER_OFF_LIMITS
                || markerWorld == null) {
            return type;
        }

        final RegistryKey<World> markerKey = markerWorld.getRegistryKey();
        if (markerKey == dimension) return type;

        if (markerKey == World.OVERWORLD) return Type.FRAME; // Marqueur vert
        if (markerKey == World.NETHER) return Type.RED_MARKER;
        if (markerKey == World.END) return Type.BLUE_MARKER;

        return type;
    }

    /** Recolore les icônes de joueurs dans les mauvaises dimensions
     * @see MapState#addIcon */
    @ModifyArg(method = "addIcon",
        at = @At(value = "INVOKE", target = "net/minecraft/item/map/MapIcon.<init>(Lnet/minecraft/item/map/MapIcon$Type;BBBLnet/minecraft/text/Text;)V"), index = 3)
    private byte adjustMarkerRotation(Type type, byte x, byte z, byte rotation, @Nullable Text text) {
        if (rotation == 0
                && type != Type.PLAYER_OFF_MAP
                && type != Type.PLAYER_OFF_LIMITS
                && (x == -128 || x == 127 || z == -128 || z == 127)) {
            // Marqueur sans orientation et sur un bord -> il était
            // propablement hors de la carte et c'est pas la vrai orientation
            // -> on la recalcule
            // (Calcul "inspiré" de MapState#addIcon)
            markerRotation += markerRotation < 0 ? -8 : 8;
            return (byte) (markerRotation * 16 / 360);
        }

        // Sinon c'est bon
        return rotation;
    }



    /** Renvoie le ratio de distances entre la dimension en paramètre et celle
     * de la carte */
    @Unique private double getCoordinateScale() {
        if (markerWorld == null) return 1;

        // On récupère le monde de la carte si on a pas le bon
        if (mapWorld == null || mapWorld.getRegistryKey() != dimension) {
            mapWorld = markerWorld.getServer().getWorld(dimension);
            if (mapWorld == null) return 1;
        }

        return markerWorld.getDimension().getCoordinateScale()
            / mapWorld.getDimension().getCoordinateScale();
    }

}
