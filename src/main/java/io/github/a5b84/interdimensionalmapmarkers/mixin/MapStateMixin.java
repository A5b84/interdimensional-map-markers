package io.github.a5b84.interdimensionalmapmarkers.mixin;

import java.util.Optional;
import net.minecraft.item.map.MapDecorationType;
import net.minecraft.item.map.MapDecorationTypes;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Final;
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

  @Shadow public @Final RegistryKey<World> dimension;

  @Unique private World mapWorld;
  @Unique private World markerWorld;
  @Unique private double coordinateScale;
  @Unique private double markerRotation;

  /** Allows updating icons of players in other dimensions */
  @Redirect(
      method = "update",
      at =
          @At(
              value = "INVOKE",
              target =
                  "net/minecraft/world/World.getRegistryKey()Lnet/minecraft/registry/RegistryKey;"))
  private RegistryKey<World> playerDimensionProxy(World world) {
    return dimension;
  }

  /** Stores the method parameters and adjusts the X coordinate */
  @ModifyVariable(method = "addDecoration", at = @At("HEAD"), ordinal = 0, argsOnly = true)
  private double adjustX(
      double x,
      RegistryEntry<MapDecorationType> type,
      WorldAccess worldAccess,
      String key,
      double _x,
      double z,
      double rotation) {
    markerWorld = worldAccess instanceof World world ? world : null;
    markerRotation = rotation;
    coordinateScale = getCoordinateScale();
    return x * coordinateScale;
  }

  /** Adjusts the Z coordinate */
  @ModifyVariable(method = "addDecoration", at = @At("HEAD"), ordinal = 1, argsOnly = true)
  private double adjustZ(double z) {
    return z * coordinateScale;
  }

  /** Increases the off map distance */
  @ModifyConstant(method = "getPlayerMarker", constant = @Constant(floatValue = 320))
  private float adjustOffMapDistance(float offMapDistance) {
    return offMapDistance * Math.max((float) coordinateScale, 1);
  }

  /** Recolors the icons of player in other dimensions */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  @ModifyArg(
      method = "addDecoration",
      at =
          @At(
              value = "INVOKE",
              target =
                  "net/minecraft/item/map/MapDecoration.<init>(Lnet/minecraft/registry/entry/RegistryEntry;BBBLjava/util/Optional;)V"),
      index = 0)
  private RegistryEntry<MapDecorationType> adjustMarkerMapDecorationType(
      RegistryEntry<MapDecorationType> type, byte x, byte z, byte rotation, Optional<Text> name) {
    // Check for player icons
    //noinspection deprecation (type.matches)
    if (markerWorld == null
        || !type.matches(MapDecorationTypes.PLAYER)
            && !type.matches(MapDecorationTypes.PLAYER_OFF_MAP)
            && !type.matches(MapDecorationTypes.PLAYER_OFF_LIMITS)) {
      return type;
    }

    RegistryKey<World> markerDimension = markerWorld.getRegistryKey();
    if (markerDimension == dimension) {
      return type;
    } else if (markerDimension == World.OVERWORLD) {
      return MapDecorationTypes.FRAME; // Green marker
    } else if (markerDimension == World.NETHER) {
      return MapDecorationTypes.RED_MARKER;
    } else if (markerDimension == World.END) {
      return MapDecorationTypes.BLUE_MARKER;
    } else {
      return type;
    }
  }

  /** Recalculates the rotation if the marker was off-map */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  @ModifyArg(
      method = "addDecoration",
      at =
          @At(
              value = "INVOKE",
              target =
                  "net/minecraft/item/map/MapDecoration.<init>(Lnet/minecraft/registry/entry/RegistryEntry;BBBLjava/util/Optional;)V"),
      index = 3)
  private byte adjustMarkerRotation(
      RegistryEntry<MapDecorationType> type, byte x, byte z, byte rotation, Optional<Text> name) {
    //noinspection deprecation (type.matches)
    if (rotation == 0
        && !type.matches(MapDecorationTypes.PLAYER_OFF_MAP)
        && !type.matches(MapDecorationTypes.PLAYER_OFF_LIMITS)
        && (x == -128 || x == 127 || z == -128 || z == 127)) {
      markerRotation += markerRotation < 0 ? -8 : 8;
      return (byte) (markerRotation * 16 / 360);
    }

    return rotation;
  }

  @Unique
  private double getCoordinateScale() {
    if (markerWorld == null) {
      return 1;
    }

    if (mapWorld == null || mapWorld.getRegistryKey() != dimension) {
      //noinspection ConstantConditions
      mapWorld = markerWorld.getServer().getWorld(dimension);
      if (mapWorld == null) {
        return 1;
      }
    }

    return markerWorld.getDimension().coordinateScale() / mapWorld.getDimension().coordinateScale();
  }
}
