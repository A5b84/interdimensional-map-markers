package io.github.a5b84.interdimensionalmapmarkers.mixin;

import io.github.a5b84.interdimensionalmapmarkers.MapStateAccess;
import net.minecraft.item.map.MapState;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import static io.github.a5b84.interdimensionalmapmarkers.InterdimensionalMapMarkers.MAP_UPDATE_INTERVAL;
import static io.github.a5b84.interdimensionalmapmarkers.InterdimensionalMapMarkers.VANILLA_MAP_UPDATE_PACKET_INTERVAL;

@Mixin(MapState.PlayerUpdateTracker.class)
public class PlayerUpdateTrackerMixin {

    @SuppressWarnings("ShadowTarget")
    @Shadow(remap = false) public MapState field_132;

    /** Changes the map update packet interval */
    @ModifyConstant(method = "getPacket", constant = @Constant(intValue = VANILLA_MAP_UPDATE_PACKET_INTERVAL))
    private int getMapUpdatePacketInterval(int oldInterval) {
        World world = ((MapStateAccess) field_132).getWorld();
        return world != null
                ? world.getGameRules().getInt(MAP_UPDATE_INTERVAL)
                : VANILLA_MAP_UPDATE_PACKET_INTERVAL;
    }

}
