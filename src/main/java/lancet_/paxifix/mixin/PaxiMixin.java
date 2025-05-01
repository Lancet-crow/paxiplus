package lancet_.paxifix.mixin;

import com.google.common.io.Files;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.yungnickyoung.minecraft.paxi.PaxiCommon;
import com.yungnickyoung.minecraft.paxi.PaxiRepositorySource;
import com.yungnickyoung.minecraft.paxi.mixin.accessor.FolderRepositorySourceAccessor;
import lancet_.paxifix.PaxiFix;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

@Mixin(value = PaxiRepositorySource.class, remap = false)
public class PaxiMixin {

    @Shadow private File ordering;

    @Shadow @Final private static FileFilter PACK_FILTER;

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
        if (this.ordering == null){
            cir.setReturnValue(new Path[0]);
            cir.cancel();
        }
    }

    @Inject(method = "loadPacksFromFiles()[Ljava/nio/file/Path;",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V",
                    shift = At.Shift.AFTER),
            remap = false
    )
    public void paxiFix$stopLoadingUnorderedPacks(CallbackInfoReturnable<Path[]> cir, @Local(ordinal = 1) LocalRef<List<File>> unorderedPacks){
        unorderedPacks.set(new ArrayList<>());
    }

    @Inject(method = "loadPacks(Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Ljava/nio/file/Path;getFileName()Ljava/nio/file/Path;"),
    slice = @Slice(
            from = @At(value = "INVOKE",
                    target = "Lcom/yungnickyoung/minecraft/paxi/PaxiRepositorySource;loadPacksFromFiles()[Ljava/nio/file/Path;"),
            to = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/repository/Pack;readMetaAndCreate(Ljava/lang/String;Lnet/minecraft/network/chat/Component;ZLnet/minecraft/server/packs/repository/Pack$ResourcesSupplier;Lnet/minecraft/server/packs/PackType;Lnet/minecraft/server/packs/repository/Pack$Position;Lnet/minecraft/server/packs/repository/PackSource;)Lnet/minecraft/server/packs/repository/Pack;")
    ),
            remap = false)
    public void paxiFix$changePackDirectoryIfNotAlready(Consumer<Pack> packAdder, CallbackInfo ci, @Local LocalRef<Path> packPath){
        if (!packPath.get().getParent().getParent().equals(PaxiCommon.BASE_PACK_DIRECTORY.toPath())){
            PaxiFix.LOGGER.warn("Changing pack {} directory...", packPath.get().getFileName());
            Path destinationDirectory = Paths.get(PaxiCommon.BASE_PACK_DIRECTORY.toString(), packPath.get().getParent().getFileName().toString());
            try {
                packPath.set(moveToPath(packPath.get(), destinationDirectory));
            } catch (IOException ex) {
                PaxiFix.LOGGER.error(Arrays.toString(ex.getStackTrace()));
            }
        }
    }

    @Inject(method = "loadPacks(Ljava/util/function/Consumer;)V",
            at = @At("TAIL"),
            remap = false)
    public void paxiFix$returnUnorderedFilesFromPaxi(Consumer<Pack> packAdder, CallbackInfo ci, @Local(ordinal = 0) Path[] packs){
        Path paxiPackFolder = Paths.get(PaxiCommon.BASE_PACK_DIRECTORY.toString(), ((FolderRepositorySourceAccessor)this).getFolder().getFileName().toString());
        File[] allPacks = paxiPackFolder.toFile().listFiles(PACK_FILTER);
        if (allPacks != null){
            Path[] allPackPaths = Arrays.stream(allPacks).map(File::toPath).toArray(Path[]::new);
            Path[] unorderedPackPaths = Arrays.stream(allPackPaths).filter(
                    (file) -> !Arrays.stream(packs).toList().contains(file))
                    .toArray(Path[]::new);
            if (unorderedPackPaths.length > 0){
                Path destinationDirectory = Paths.get(FabricLoader.getInstance().getGameDir().toString(), ((FolderRepositorySourceAccessor)this).getFolder().getFileName().toString());
                PaxiFix.LOGGER.info("Found {} unordered packs", unorderedPackPaths.length);
                PaxiFix.LOGGER.info("Destination directory for unordered packs is: {}", destinationDirectory);
                for(Path packPath : unorderedPackPaths) {
                    try {
                        packPath = moveToPath(packPath, destinationDirectory);
                    } catch (IOException ex) {
                        PaxiFix.LOGGER.error(ex.getMessage());
                    }
                    String packName = packPath.getFileName().toString();
                    Pack resourcePackProfile = Pack.readMetaAndCreate(packName, Component.literal(packName), false, Objects.requireNonNull(FolderRepositorySource.detectPackResources(packPath, false)), ((FolderRepositorySourceAccessor)this).getPackType(), Pack.Position.TOP, PackSource.create(returnSource(), true));
                    if (resourcePackProfile != null) {
                        packAdder.accept(resourcePackProfile);
                    }
                }
            }
        }
    }

    @Inject(method = "filesFromNames([Ljava/lang/String;Ljava/io/FileFilter;)Ljava/util/List;",
            at = @At(value = "INVOKE", target = "Ljava/io/File;exists()Z"), remap = false)
    public void paxiFix$checkPaxiDirectory(String[] packFileNames, FileFilter filter, CallbackInfoReturnable<List<File>> cir, @Local File packFile, @Local ArrayList<File> packFiles){
        Path paxiFile = getPaxifiedFile(packFile);
        PaxiFix.LOGGER.info(String.valueOf(packFile.toPath()));
        if (!paxiFile.toFile().exists() && packFile.equals(paxiFile.toFile())){
            Path fromDirectory = Paths.get(FabricLoader.getInstance().getGameDir().toString(), packFile.getParentFile().toPath().getFileName().toString(), packFile.toPath().getFileName().toString());
            Path destinationDirectory = Paths.get(PaxiCommon.BASE_PACK_DIRECTORY.toString(), packFile.getParentFile().toPath().getFileName().toString());
            try {
                packFile = moveToPath(fromDirectory, destinationDirectory).toFile();
            }
            catch (Exception e){
                PaxiFix.LOGGER.error(e.getMessage());
            }
        }

        if (paxiFile.equals(packFile.toPath()) && packFile.exists()){
            if (filter == null || filter.accept(packFile)) {
                packFiles.add(packFile);
            }
            else{
                PaxiFix.LOGGER.warn("{} adding gone wrong, check its formatting(PACKFILE).", packFile);
            }
        }
        else if (paxiFile.equals(packFile.toPath())){
            if (filter == null || filter.accept(paxiFile.toFile())) {
                packFiles.add(paxiFile.toFile());
            }
            else{
                PaxiFix.LOGGER.warn("{} adding gone wrong, check its formatting(PAXIFILE).", paxiFile);
            }
        }
        else {
            PaxiFix.LOGGER.error("{}, WHAT HAPPENED TO YOU?!", paxiFile);
        }
    }

    @Redirect(method = "filesFromNames([Ljava/lang/String;Ljava/io/FileFilter;)Ljava/util/List;",
    at = @At(value = "INVOKE",
            target = "Lorg/apache/logging/log4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
            remap = false
    )
    public void stopErrorIfNotNeeded(Logger instance, String s, Object o1, Object o2, @Local File packFile){
        if (!getPaxifiedFile(packFile).toFile().exists()){
            PaxiCommon.LOGGER.error("Unable to find pack with name {} specified in load ordering JSON file {}! Skipping...", packFile.getPath(), o2);
        }
    }

    @Unique
    public Path getPaxifiedFile(File packFile){
        Path destinationDirectory = Paths.get(PaxiCommon.BASE_PACK_DIRECTORY.toString(), packFile.getParentFile().toPath().getFileName().toString());
        return destinationDirectory.resolve(packFile.getName());
    }

    @Unique
    public Path moveToPath(Path packPath, Path destinationDirectory) throws IOException {
        Path dpath = destinationDirectory.resolve(packPath.getFileName());
        if (dpath.toFile().exists()) {
            boolean deletedFile = dpath.toFile().delete();
            if (deletedFile){
                PaxiFix.LOGGER.warn("RP already exists in this folder, replacing with new one");
            }
        }
        Files.move(packPath.toFile(), dpath.toFile());
        return dpath;
    }

    @Unique
    private static UnaryOperator<Component> returnSource() {
        return (component) -> Component.translatable("pack.nameAndSource");
    }
}