package lancet_.paxifix.mixin;

import com.google.common.collect.Sets;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import com.yungnickyoung.minecraft.paxi.PaxiPackSource;
import lancet_.paxifix.interfaces.*;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

@Mixin(Options.class)
public class OptionsMixin {

    @Unique
    private ArrayList<String> packsToUnload = new ArrayList<>();

    @Unique
    private PaxiRepositorySourceTricks paxiSource = null;

    @Inject(method = "loadSelectedResourcePacks",
            at = @At(value = "HEAD"))
    private void messUpWithLoadingSelectedPacks(PackRepository packRepository, CallbackInfo ci){
        packsToUnload.clear();
        Optional<RepositorySource> repoSource = ((PackRepositoryTricks) packRepository).getPaxiRepositorySource();
        repoSource.ifPresent(repositorySource -> this.paxiSource = (PaxiRepositorySourceTricks) repositorySource);
    }

    @Inject(method = "loadSelectedResourcePacks",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/repository/PackRepository;getPack(Ljava/lang/String;)Lnet/minecraft/server/packs/repository/Pack;",
            shift = At.Shift.AFTER))
    private void tryToStopLoading(PackRepository packRepository, CallbackInfo ci,
                                  @Local String string){
        Pack pack = packRepository.getPack(string);
        if (pack == null){
            pack = packRepository.getPack("file/" + string);
            if (pack != null && !string.startsWith("file/") &&
                    !pack.getPackSource().equals(PaxiPackSource.PACK_SOURCE_PAXI)) {
                packsToUnload.add(string);
            }
        }
        else{
            if (!string.startsWith("file/") &&
                    !pack.getPackSource().equals(PaxiPackSource.PACK_SOURCE_PAXI) &&
                    this.paxiSource.orderedPacks().contains(string)){
                packsToUnload.add(string);
            }
        }
    }

    @Inject(method = "loadSelectedResourcePacks",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/repository/PackRepository;setSelected(Ljava/util/Collection;)V"))
    private void unloadPacks(PackRepository packRepository, CallbackInfo ci,
                                  @Local LocalRef<Set<String>> set){
        Set<String> newPacksSet = Sets.newLinkedHashSet();
        newPacksSet.addAll(set.get());
        packsToUnload.forEach((string) -> {
            if(newPacksSet.stream().anyMatch((x) -> x.equals(string))){
                newPacksSet.remove(string);
            }
            else if (newPacksSet.stream().anyMatch((x) -> x.equals("file/" + string))){
                newPacksSet.remove("file/" + string);
            }
        });
        set.set(newPacksSet);
    }
}
