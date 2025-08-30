package lancet_.paxifix.mixin;

import com.google.common.collect.Lists;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yungnickyoung.minecraft.paxi.PaxiCommon;
import com.yungnickyoung.minecraft.paxi.PaxiPackSource;
import com.yungnickyoung.minecraft.paxi.PaxiRepositorySource;
import com.yungnickyoung.minecraft.paxi.mixin.accessor.FolderRepositorySourceAccessor;
import com.yungnickyoung.minecraft.yungsapi.io.JSON;
import lancet_.paxifix.PaxiFix;
import lancet_.paxifix.util.PaxiFixOrdering;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Mixin(value = PaxiRepositorySource.class, remap = false)
public abstract class PaxiMixin {

    @Shadow private File ordering;

    @Shadow public List<String> orderedPaxiPacks;

    @Shadow public List<String> unorderedPaxiPacks;

    @Shadow protected abstract Pack.ResourcesSupplier createPackResourcesSupplier(Path path);

    @Shadow @Final private static FileFilter PACK_FILTER;

    @Shadow protected abstract List<File> filesFromNames(String[] packFileNames, FileFilter filter);

    @Inject(method = "loadPacks",
            at = @At(value = "INVOKE",
            target = "Lcom/yungnickyoung/minecraft/paxi/PaxiRepositorySource;loadPacksFromFiles()[Ljava/nio/file/Path;"),
        cancellable = true, remap = true)
    public void loadPacks(Consumer<Pack> packAdder, CallbackInfo ci) {
        List<Path> packPathsToLoad = toPaths(loadPacksFromFiles());
        for (Path packPath : packPathsToLoad) {
            String packName = packPath.getFileName().toString();
            Pack pack = Pack.readMetaAndCreate(
                    packName, Component.literal(packName), true,
                    this.createPackResourcesSupplier(packPath),
                    ((FolderRepositorySourceAccessor) this).getPackType(),
                    Pack.Position.TOP, PaxiPackSource.PACK_SOURCE_PAXI
            );

            if (pack != null) {
                packAdder.accept(pack);
            }
        }
        ci.cancel();
    }

    @Unique
    private List<File> loadPacksFromFiles() {
        // Reset ordered and unordered pack lists
        this.orderedPaxiPacks.clear();
        this.unorderedPaxiPacks.clear();

        if (this.ordering != null) {
            // If ordering file exists, load any specified files in the specific order
            PaxiFixOrdering packOrdering = null;
            try {
                packOrdering = JSON.loadObjectFromJsonFile(this.ordering.toPath(), PaxiFixOrdering.class);
            } catch (IOException | JsonIOException | JsonSyntaxException e) {
                PaxiCommon.LOGGER.error("Error loading Paxi ordering JSON file {}: {}", this.ordering.getName(), e.toString());
            }

            // Check that we loaded ordering properly
            if (packOrdering == null) {
                // If loading the ordering failed, give error
                PaxiCommon.LOGGER.error("Unable to load ordering JSON file {}! Is it proper JSON formatting? Ignoring load order...", this.ordering.getName());
                return List.of();
            } else if (packOrdering.orderedPackNames() == null) {
                // User probably mistyped the "loadOrder" key - Let them know
                PaxiCommon.LOGGER.error("Unable to find entry with name 'loadOrder' in load ordering JSON file {}! Ignoring load order...", this.ordering.getName());
                return List.of();
            } else {
                // If loading ordering succeeded, we add the ordered packs
                List<File> orderedPacks = this.filesFromNames(packOrdering.orderedPackNames(), PACK_FILTER);
                File[] allPacks = ((FolderRepositorySourceAccessor) this).getFolder().toFile().listFiles(PACK_FILTER);
                List<File> unorderedPacks = allPacks == null
                        ? Lists.newArrayList()
                        : Arrays.stream(allPacks).filter(file -> !orderedPacks.contains(file)).toList();

                orderedPacks.forEach(file -> this.orderedPaxiPacks.add(file.getName()));
                unorderedPacks.forEach(file -> this.unorderedPaxiPacks.add(file.getName()));

                return Stream.of(orderedPacks).flatMap(Collection::stream).toList();
            }
        }
        return List.of();
    }

    @WrapMethod(method = "filesFromNames")
    private List<File> paxiFix$filesFromNames(String[] packFileNames, FileFilter filter, Operation<List<File>> original) {
        ArrayList<File> packFiles = new ArrayList<>();

        for (String fileName : packFileNames) {
            // First, check for the pack as-is, using the base Minecraft folder as the base directory
            File packFile = new File(PaxiFix.BASE_GAME_DIRECTORY, fileName);

            if (!packFile.exists()) {
                // If the pack doesn't exist, check for it in the Paxi datapacks/resourcepacks directory.
                // This is the base Paxi behavior.
                packFile = new File(((FolderRepositorySourceAccessor) this).getFolder().toFile().toString(), fileName);
            }

            if (!packFile.exists()) {
                // If the pack file still doesn't exist, log an error and skip it
                PaxiCommon.LOGGER.error("Unable to find pack with name {} specified in load ordering JSON file {}! Skipping...", fileName, this.ordering.getName());
            } else if (filter != null && !filter.accept(packFile)) {
                // If the pack file doesn't pass the filter, log an error and skip it
                PaxiCommon.LOGGER.error("Attempted to load pack {} but it is not a valid pack format! It may be missing a pack.mcmeta file. Skipping...", fileName);
            } else {
                // If the pack file exists and passes the filter, add it to the list
                packFiles.add(packFile);
            }
        }
        return packFiles;
    }
    @Unique
    private static List<Path> toPaths(List<File> files) {
        return files.stream().map(File::toPath).toList();
    }
}