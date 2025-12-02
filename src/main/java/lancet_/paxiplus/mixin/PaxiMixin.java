package lancet_.paxiplus.mixin;

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
import lancet_.paxiplus.PaxiPlus;
import lancet_.paxiplus.interfaces.PackRepositoryTricks;
import lancet_.paxiplus.interfaces.PaxiRepositorySourceTricks;
import lancet_.paxiplus.mixin.accessor.FilePackResourcesAccessor;
import lancet_.paxiplus.mixin.accessor.PackAccessor;
import lancet_.paxiplus.util.PaxiPlusOrdering;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Mixin(value = PaxiRepositorySource.class, remap = false)
public abstract class PaxiMixin extends FolderRepositorySource implements PaxiRepositorySourceTricks {

    @Shadow
    @Final
    private static FileFilter PACK_FILTER;
    @Shadow
    public List<String> orderedPaxiPacks;

    @Shadow
    public List<String> unorderedPaxiPacks;
    @Shadow
    private File ordering;
    @Unique
    private List<String> orderedPacks = new ArrayList<>();
    @Unique
    private List<Pack> builtinPacks = new ArrayList<>();
    @Unique
    private PackRepository packRepository;

    public PaxiMixin(Path path, PackType packType, PackSource packSource) {
        super(path, packType, packSource);
    }

    @Unique
    private static List<Path> toPaths(List<File> files) {
        return files.stream().map(File::toPath).toList();
    }

    @Shadow
    protected abstract Pack.ResourcesSupplier createPackResourcesSupplier(Path path);

    @Shadow
    protected abstract List<File> filesFromNames(String[] packFileNames, FileFilter filter);

    @Override
    public List<String> orderedPacks() {
        return orderedPacks;
    }

    @Unique
    private static final Map<String, String> CUSTOM_TAGS = Map.ofEntries(
            Map.entry("USER_PACKS", "--user--")
    );
    @Unique
    private Map<String, Boolean> orderedTags = new HashMap<>();

    @Override
    public Map<String, String> CUSTOM_TAGS() {
        return CUSTOM_TAGS;
    }

    @Unique
    private List<Pack> afterUserPacks = new ArrayList<>();

    @Override
    public List<Pack> afterUserPacks() {
        return afterUserPacks == null ? new ArrayList<>() : afterUserPacks;
    }

    @Unique
    private boolean isTagPresentAndActive(String tag) {
        String realTag = CUSTOM_TAGS.get(tag);
        return orderedTags.containsKey(realTag) && orderedTags.get(realTag);
    }

    public void loadAfterUserPacksTrick(Consumer<Pack> packAdder, List<Pack> afterUserPacks) {
        for (Pack pack : afterUserPacks) {
            if (pack != null) {
                packAdder.accept(pack);
            }
        }
    }

    public void loadPacksTrick(Consumer<Pack> packAdder, PackRepository packRepository) {
        this.packRepository = packRepository;
        this.loadPacks(packAdder);
        this.packRepository = null;
    }

    @Override
    public void loadPacks(Consumer<Pack> packAdder) {
        if (this.packRepository == null) {
            return;
        }
        File folder = ((FolderRepositorySourceAccessor) this).getFolder().toFile();
        if (!folder.isDirectory()) {
            if (!folder.mkdirs()) {
                PaxiPlus.LOGGER.warn("Couldn't create a folder for packs");
            }
        }

        if (this.ordering != null && !this.ordering.isFile()) {
            PaxiPlusOrdering emptyPackOrdering = new PaxiPlusOrdering(new String[0]);
            try {
                JSON.createJsonFileFromObject(this.ordering.toPath(), emptyPackOrdering);
            } catch (IOException e) {
                PaxiCommon.LOGGER.error("Unable to create default pack ordering file! This shouldn't happen.");
                PaxiCommon.LOGGER.error(e.toString());
            }
        }
        List<Path> packPathsToLoad = toPaths(loadPacksFromFiles());
        for (String packId : this.orderedPacks) {
            Pack pack = null;
            String strippedPackId = packId.replaceFirst("paxi/", "");
            if (packPathsToLoad.stream().anyMatch(path -> path.toString().contains(strippedPackId))) {
                Path packPath = packPathsToLoad.stream().filter(path -> path.toString().contains(strippedPackId)).findFirst().orElse(null);
                if (packPath == null) {
                    PaxiPlus.LOGGER.warn("Couldn't load pack with id from file paths: {}", packId);
                    continue;
                }
                String packName = packPath.getFileName().toString();
                Component packTitle = Component.literal(packName);
                PackRepository packRepository = this.packRepository;
                PackType packType = ((FolderRepositorySourceAccessor) this).getPackType();
                if (packType.equals(PackType.CLIENT_RESOURCES)) {
                    Map<String, Pack> map = ((PackRepositoryTricks) packRepository).getAlreadyAvailablePacks();
                    if (map.values().stream().anyMatch(mapPack -> {
                        try (PackResources packResources = ((PackAccessor) mapPack).paxi_plus$resources().open(mapPack.getId())) {
                            if (packResources instanceof FilePackResources filePackResources) {
                                if (((FilePackResourcesAccessor) filePackResources).file().getName().equals(packName)) {
                                    return true;
                                }
                            }
                        }
                        return false;
                    })) {
                        packTitle = map.get("file/" + strippedPackId).getTitle();
                    }
                }
                pack = Pack.readMetaAndCreate(
                        "paxi/" + packName, packTitle, true,
                        this.createPackResourcesSupplier(packPath),
                        ((FolderRepositorySourceAccessor) this).getPackType(),
                        Pack.Position.TOP, PaxiPackSource.PACK_SOURCE_PAXI
                );
            } else if (this.builtinPacks.stream().anyMatch(builtInPack -> builtInPack.getId().equals(strippedPackId))) {
                Pack builtinPack = this.builtinPacks.stream().filter(builtPack -> builtPack.getId().equals(strippedPackId)).findFirst().orElse(null);
                if (builtinPack == null) {
                    PaxiPlus.LOGGER.info("Couldn't load pack with id from built-in packs: {}", packId);
                    continue;
                }
                pack = Pack.readMetaAndCreate(
                        "paxi/" + builtinPack.getId(), builtinPack.getTitle(), true,
                        ((PackAccessor) builtinPack).paxi_plus$resources(),
                        ((FolderRepositorySourceAccessor) this).getPackType(),
                        Pack.Position.TOP, PaxiPackSource.PACK_SOURCE_PAXI
                );
            } else if (this.orderedTags.containsKey(packId)) {
                orderedTags.replace(packId, false);
            }

            if (pack != null) {
                if (isTagPresentAndActive("USER_PACKS")) {
                    afterUserPacks.add(pack);
                    continue;
                }
                packAdder.accept(pack);
            }
        }
    }

    @Unique
    private List<File> loadPacksFromFiles() {
        // Reset ordered and unordered pack lists
        this.orderedPaxiPacks.clear();
        this.unorderedPaxiPacks.clear();
        this.orderedPacks.clear();
        this.builtinPacks.clear();
        this.afterUserPacks.clear();
        this.orderedTags.clear();

        if (this.ordering != null) {
            // If ordering file exists, load any specified files in the specific order
            PaxiPlusOrdering packOrdering = null;
            try {
                packOrdering = JSON.loadObjectFromJsonFile(this.ordering.toPath(), PaxiPlusOrdering.class);
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
                this.builtinPacks = paxiPlus$findBuiltinPacks(packOrdering.orderedPackNames());
                List<File> orderedPacks = this.filesFromNames(packOrdering.orderedPackNames(), PACK_FILTER);
                File[] allPacks = ((FolderRepositorySourceAccessor) this).getFolder().toFile().listFiles(PACK_FILTER);
                List<File> unorderedPacks = allPacks == null
                        ? Lists.newArrayList()
                        : Arrays.stream(allPacks).filter(file -> !orderedPacks.contains(file)).toList();

                orderedPacks.forEach(file -> this.orderedPacks.add(file.getName()));
                this.builtinPacks.forEach(pack -> this.orderedPacks.add(pack.getId()));
                List<String> orderedList = new ArrayList<>(Arrays.stream(packOrdering.orderedPackNames()).toList());
                orderedList.removeIf(string -> !this.orderedPacks.contains(string) &&
                        orderedPacks.stream().noneMatch(file -> string.endsWith(file.getName())) && !CUSTOM_TAGS.containsValue(string));
                List<String> finalOrderList = new ArrayList<>();
                for (String packName : orderedList) {
                    Optional<String> foundPackName = this.orderedPacks.stream().filter(string -> string.equals(packName) || packName.endsWith(string)).findFirst();
                    if (foundPackName.isEmpty()) {
                        if (CUSTOM_TAGS.containsValue(packName)) {
                            if (orderedTags.containsKey(packName)) continue;
                            finalOrderList.add(packName);
                            orderedTags.put(packName, true);
                        }
                        continue;
                    }
                    finalOrderList.add(foundPackName.get());
                }
                this.orderedPacks = finalOrderList;
                finalOrderList.replaceAll(string -> CUSTOM_TAGS.containsValue(string) ? string : "paxi/" + string);
                this.orderedPaxiPacks.addAll(finalOrderList);
                unorderedPacks.forEach(file -> this.unorderedPaxiPacks.add(file.getName()));

                return Stream.of(orderedPacks).flatMap(Collection::stream).toList();
            }
        }
        return List.of();
    }

    @WrapMethod(method = "filesFromNames")
    private List<File> paxiPlus$filesFromNames(String[] packFileNames, FileFilter filter, Operation<List<File>> original) {
        ArrayList<File> packFiles = new ArrayList<>();

        for (String fileName : packFileNames) {

            // Skip built-in packs
            if (this.builtinPacks.stream().anyMatch(pack -> pack.getId().equals(fileName))) {
                continue;
            }
            if (CUSTOM_TAGS.containsValue(fileName)) {
                continue;
            }

            // First, check for the pack as-is, using the base Minecraft folder as the base directory
            File packFile = new File(PaxiPlus.BASE_GAME_DIRECTORY, fileName);

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
    private List<Pack> paxiPlus$findBuiltinPacks(String[] packFileNames) {
        List<Pack> builtinPacks = new ArrayList<>();
        Map<String, Pack> map = ((PackRepositoryTricks) this.packRepository).getAlreadyAvailablePacks();
        for (String fileName : packFileNames) {
            if (CUSTOM_TAGS.containsValue(fileName)) {
                continue;
            }
            File packFile = new File(PaxiPlus.BASE_GAME_DIRECTORY, fileName);
            if (packFile.exists()) {
                continue;
            }
            if (map.containsKey(fileName)) {
                Pack pack = map.get(fileName);
                builtinPacks.add(pack);
            } else {
                PaxiPlus.LOGGER.error("Pack {} is not available at the time of loading Paxi, can't find in a map: {}.", fileName,
                        String.join(", ", map.keySet()));
            }
        }
        return builtinPacks;
    }
}