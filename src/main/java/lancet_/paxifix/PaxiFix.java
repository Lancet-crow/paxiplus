package lancet_.paxifix;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class PaxiFix implements ModInitializer {
	public static final String MOD_ID = "paxifix";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static File BASE_GAME_DIRECTORY;

	@Override
	public void onInitialize() {
		BASE_GAME_DIRECTORY = FabricLoader.getInstance().getGameDir().toFile();
		LOGGER.info("Loading patches to Paxi...");
	}
}