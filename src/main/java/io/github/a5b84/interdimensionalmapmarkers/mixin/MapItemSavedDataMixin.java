package io.github.a5b84.interdimensionalmapmarkers.mixin;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
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

@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataMixin {

  @Shadow public @Final ResourceKey<Level> dimension;

  @Unique private Level mapWorld;
  @Unique private Level markerWorld;
  @Unique private double coordinateScale;
  @Unique private double markerRotation;

  /** Allows updating icons of players in other dimensions */
  @Redirect(
      method = "tickCarriedBy",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/level/Level;dimension()Lnet/minecraft/resources/ResourceKey;"))
  private ResourceKey<Level> playerDimensionProxy(Level world) {
    return dimension;
  }

  /** Stores the method parameters and adjusts the X coordinate */
  @ModifyVariable(method = "addDecoration", at = @At("HEAD"), ordinal = 0, argsOnly = true)
  private double adjustX(
      double x,
      Holder<MapDecorationType> type,
      LevelAccessor worldAccess,
      String key,
      double _x,
      double z,
      double rotation) {
    markerWorld = worldAccess instanceof Level world ? world : null;
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
  @ModifyConstant(
      method = "decorationTypeForPlayerOutsideMap",
      constant = @Constant(floatValue = 320))
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
                  "Lnet/minecraft/world/level/saveddata/maps/MapDecoration;<init>(Lnet/minecraft/core/Holder;BBBLjava/util/Optional;)V"),
      index = 0)
  private Holder<MapDecorationType> adjustMarkerMapDecorationType(
      Holder<MapDecorationType> type, byte x, byte z, byte rotation, Optional<Component> name) {
    // Check for player icons
    // noinspection deprecation (type.is)
    if (markerWorld == null
        || !type.is(MapDecorationTypes.PLAYER)
            && !type.is(MapDecorationTypes.PLAYER_OFF_MAP)
            && !type.is(MapDecorationTypes.PLAYER_OFF_LIMITS)) {
      return type;
    }

    ResourceKey<Level> markerDimension = markerWorld.dimension();
    if (markerDimension == dimension) {
      return type;
    } else if (markerDimension == Level.OVERWORLD) {
      return MapDecorationTypes.FRAME; // Green marker
    } else if (markerDimension == Level.NETHER) {
      return MapDecorationTypes.RED_MARKER;
    } else if (markerDimension == Level.END) {
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
                  "Lnet/minecraft/world/level/saveddata/maps/MapDecoration;<init>(Lnet/minecraft/core/Holder;BBBLjava/util/Optional;)V"),
      index = 3)
  private byte adjustMarkerRotation(
      Holder<MapDecorationType> type, byte x, byte z, byte rotation, Optional<Component> name) {
    //noinspection deprecation (type.matches)
    if (rotation == 0
        && !type.is(MapDecorationTypes.PLAYER_OFF_MAP)
        && !type.is(MapDecorationTypes.PLAYER_OFF_LIMITS)
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

    if (mapWorld == null || mapWorld.dimension() != dimension) {
      //noinspection ConstantConditions
      mapWorld = markerWorld.getServer().getLevel(dimension);
      if (mapWorld == null) {
        return 1;
      }
    }

    return markerWorld.dimensionType().coordinateScale()
        / mapWorld.dimensionType().coordinateScale();
  }
}
