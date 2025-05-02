package lancet_.paxifix.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.yungnickyoung.minecraft.paxi.PaxiRepositorySource;
import com.yungnickyoung.minecraft.paxi.mixin.accessor.FolderRepositorySourceAccessor;
import lancet_.paxifix.PaxiFix;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Mixin(value = PaxiRepositorySource.class, remap = false)
public abstract class PaxiMixin {

    @Shadow private File ordering;

    @Shadow public List<String> orderedPaxiPacks;

    @Shadow public List<String> unorderedPaxiPacks;

    @Inject(method = "loadPacksFromFiles()[Ljava/nio/file/Path;",
            at = @At(value = "INVOKE",
                    target = "Ljava/io/File;listFiles(Ljava/io/FileFilter;)[Ljava/io/File;",
                    shift = At.Shift.AFTER),
            slice = @Slice(
                    from = @At(value = "INVOKE",
                            target = "Lcom/yungnickyoung/minecraft/yungsapi/io/JSON;loadObjectFromJsonFile(Ljava/nio/file/Path;Ljava/lang/Class;)Ljava/lang/Object;"),
                    to = @At(value = "INVOKE",
                            target = "Lcom/yungnickyoung/minecraft/paxi/PaxiRepositorySource$PackOrdering;getOrderedPackNames()[Ljava/lang/String;")
            ),
            cancellable = true,
            remap = false)
    public void paxiFix$notLoadPacksFromFilesIfYouCant(CallbackInfoReturnable<Path[]> cir){
        cir.setReturnValue(new Path[0]);
        cir.cancel();
    }

    @Inject(method = "loadPacksFromFiles()[Ljava/nio/file/Path;",
            at = @At("HEAD"),
            cancellable = true, remap = false)
    public void paxiFix$notLoadPacksFromFilesIfLoadOrderIsEmpty(CallbackInfoReturnable<Path[]> cir){
        this.orderedPaxiPacks.clear();
        this.unorderedPaxiPacks.clear();
        if (this.ordering == null){
            cir.setReturnValue(new Path[0]);
            cir.cancel();
        }
    }

    @Inject(method = "loadPacksFromFiles",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/stream/Stream;flatMap(Ljava/util/function/Function;)Ljava/util/stream/Stream;",
            shift = At.Shift.AFTER))
    private void paxiFix$loadPacksFromFiles(CallbackInfoReturnable<Path[]> cir, @Local(ordinal = 1) LocalRef<List<File>> unorderedPacks) {
        List<File> emptyUnorderedPacks = unorderedPacks.get();
        emptyUnorderedPacks.clear();
        unorderedPacks.set(emptyUnorderedPacks);
    }

    @Inject(method = "filesFromNames", at = @At("HEAD"), cancellable = true)
    private void paxiFix$filesFromNames(String[] packFileNames, FileFilter filter, CallbackInfoReturnable<List<File>> cir) {
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
                PaxiFix.LOGGER.error("Unable to find pack with name {} specified in load ordering JSON file {}! Skipping...", fileName, this.ordering.getName());
            } else if (filter != null && !filter.accept(packFile)) {
                // If the pack file doesn't pass the filter, log an error and skip it
                PaxiFix.LOGGER.error("Attempted to load pack {} but it is not a valid pack format! It may be missing a pack.mcmeta file. Skipping...", fileName);
            } else {
                // If the pack file exists and passes the filter, add it to the list
                packFiles.add(packFile);
            }
        }
        cir.setReturnValue(packFiles);
        cir.cancel();
    }
}