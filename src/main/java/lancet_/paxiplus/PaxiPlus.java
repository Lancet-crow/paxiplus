package lancet_.paxiplus;

import com.yungnickyoung.minecraft.paxi.PaxiCommon;
import com.yungnickyoung.minecraft.yungsapi.io.JSON;
import lancet_.paxiplus.util.PaxiPlusOrdering;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class PaxiPlus implements ModInitializer {
    public static final String MOD_ID = "paxiplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static File BASE_GAME_DIRECTORY;

    @Override
    public void onInitialize() {
        BASE_GAME_DIRECTORY = FabricLoader.getInstance().getGameDir().toFile();
        LOGGER.info("Loading patches to Paxi...");
        File datapackFolder = PaxiCommon.DATA_PACK_DIRECTORY.toFile();
        createFolderForPacksIfDoesNotExist(datapackFolder);
        File datapackOrdering = PaxiCommon.DATAPACK_ORDERING_FILE;
        createOrderingFileIfDoesNotExist(datapackOrdering);
    }

    private void createFolderForPacksIfDoesNotExist(File folder){
        if (!folder.isDirectory()) {
            if (!folder.mkdirs()){
                PaxiPlus.LOGGER.info("Couldn't create a folder for packs at {} when loading Paxi Plus", folder);
            }
        }
    }

    private void createOrderingFileIfDoesNotExist(File ordering){
        if (ordering.exists()){
            return;
        }
        PaxiPlusOrdering emptyPackOrdering = new PaxiPlusOrdering(new String[0]);
        try {
            JSON.createJsonFileFromObject(ordering.toPath(), emptyPackOrdering);
        } catch (IOException e) {
            PaxiPlus.LOGGER.info("Couldn't create an ordering file {} when loading Paxi Plus", ordering);
        }
    }
}