package io.github.a5b84.interdimensionalmapmarkers.mixin;

import net.minecraft.item.map.MapIcon.Type;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MapState.class)
public abstract class MapStateMixin {

    @Shadow public RegistryKey<World> dimension;

    @Unique private World mapWorld;
    @Unique private World markerWorld;
    @Unique private double coordinateScale;
    @Unique private double markerRotation;



    /** Allows updating icons of players in other dimensions */
    @Redirect(method = "update",
        at = @At(value = "INVOKE", target = "net/minecraft/world/World.getRegistryKey()Lnet/minecraft/util/registry/RegistryKey;"))
    private RegistryKey<World> playerDimensionProxy(World world) {
        return dimension;
    }

    /** Stores the method parameters and adjusts the X coordinate */
    @ModifyVariable(method = "addIcon", at = @At("HEAD"), ordinal = 0)
    private double adjustX(double x, Type type, WorldAccess world, String key, double _x, double z, double rotation) {
        markerWorld = world instanceof World ? (World) world : null;
        markerRotation = rotation;
        coordinateScale = getCoordinateScale();
        return x * coordinateScale;
    }

    /** Adjusts the Z coordinate */
    @ModifyVariable(method = "addIcon", at = @At("HEAD"), ordinal = 1)
    private double adjustZ(double z) {
        return z * coordinateScale;
    }

    /** Increases the off map distance */
    @ModifyConstant(method = "addIcon", constant = @Constant(floatValue = 320))
    private float adjustOffMapDistance(float offMapDistance) {
        return offMapDistance * Math.max((float) coordinateScale, 1);
    }

    /** Recolors the icons of player in other dimensions */
    @ModifyArg(method = "addIcon",
        at = @At(value = "INVOKE", target = "net/minecraft/item/map/MapIcon.<init>(Lnet/minecraft/item/map/MapIcon$Type;BBBLnet/minecraft/text/Text;)V"), index = 0)
    private Type adjustMarkerType(Type type, byte x, byte z, byte rotation, Text text) {
        // Check for player icons
        if (type != Type.PLAYER
                && type != Type.PLAYER_OFF_MAP
                && type != Type.PLAYER_OFF_LIMITS
                || markerWorld == null) {
            return type;
        }

        final RegistryKey<World> markerDimension = markerWorld.getRegistryKey();
        if (markerDimension == dimension) return type;

        if (markerDimension == World.OVERWORLD) return Type.FRAME; // Green marker
        if (markerDimension == World.NETHER) return Type.RED_MARKER;
        if (markerDimension == World.END) return Type.BLUE_MARKER;

        return type;
    }

    /** Recolors player markers from other dimensions */
    @ModifyArg(method = "addIcon",
        at = @At(value = "INVOKE", target = "net/minecraft/item/map/MapIcon.<init>(Lnet/minecraft/item/map/MapIcon$Type;BBBLnet/minecraft/text/Text;)V"), index = 3)
    private byte adjustMarkerRotation(Type type, byte x, byte z, byte rotation, Text text) {
        if (rotation == 0
                && type != Type.PLAYER_OFF_MAP
                && type != Type.PLAYER_OFF_LIMITS
                && (x == -128 || x == 127 || z == -128 || z == 127)) {
            // Recalculate the rotation if the marker was off-map
            markerRotation += markerRotation < 0 ? -8 : 8;
            return (byte) (markerRotation * 16 / 360);
        }

        return rotation;
    }



    @Unique private double getCoordinateScale() {
        if (markerWorld == null) return 1;

        if (mapWorld == null || mapWorld.getRegistryKey() != dimension) {
            //noinspection ConstantConditions
            mapWorld = markerWorld.getServer().getWorld(dimension);
            if (mapWorld == null) return 1;
        }

        return markerWorld.getDimension().getCoordinateScale()
            / mapWorld.getDimension().getCoordinateScale();
    }

}
