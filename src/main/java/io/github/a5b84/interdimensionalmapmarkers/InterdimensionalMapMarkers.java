package io.github.a5b84.interdimensionalmapmarkers;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.GameRules;
import net.minecraft.world.GameRules.Category;
import net.minecraft.world.GameRules.IntRule;

public class InterdimensionalMapMarkers implements ModInitializer {

    public static final int VANILLA_MAP_UPDATE_PACKET_INTERVAL = 5;

    public static final GameRules.Key<IntRule> MAP_UPDATE_INTERVAL = GameRuleRegistry.register(
            "mapUpdateInterval", Category.UPDATES,
            GameRuleFactory.createIntRule(VANILLA_MAP_UPDATE_PACKET_INTERVAL, 1, 10));

    @Override
    public void onInitialize() {
        // Registers the game rule through the field initializer
    }

}
