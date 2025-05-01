package lancet_.paxifix;

import com.yungnickyoung.minecraft.paxi.PaxiCommon;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;

public class PaxiFix implements ModInitializer {
	public static final String MOD_ID = "paxifix";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
		PaxiCommon.RESOURCE_PACK_DIRECTORY = Paths.get(FabricLoader.getInstance().getGameDir().toString(), "resourcepacks");
		PaxiCommon.DATA_PACK_DIRECTORY = Paths.get(FabricLoader.getInstance().getGameDir().toString(), "datapacks");
        LOGGER.info("Changed Paxi first-getting Resourcepack directory to {}", PaxiCommon.RESOURCE_PACK_DIRECTORY);
        LOGGER.info("Changed Paxi first-getting Datapack directory to {}", PaxiCommon.DATA_PACK_DIRECTORY);
	}
}