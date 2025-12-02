package lancet_.paxiplus.mixin;

import lancet_.paxiplus.interfaces.PackTricks;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Function;

@Mixin(Pack.class)
public class PackMixin implements PackTricks {

    @Unique
    private boolean isAfterUserPack = false;

    @Override
    public boolean isAfterUserPack() {
        return isAfterUserPack;
    }

    @Override
    public void setAfterUserPack(boolean isAfterUserPack) {
        this.isAfterUserPack = isAfterUserPack;
    }

    @Mixin(Pack.Position.class)
    public static abstract class PackPositionMixin {

        @Inject(method = "insert", at = @At(value = "HEAD"), cancellable = true)
        private void makeAfterUserPacksLoad(List<Object> list, Object object, Function<Object, Pack> function, boolean bl, CallbackInfoReturnable<Integer> cir) {
            if (object instanceof Pack pack) {
                if (((PackTricks) pack).isAfterUserPack()) {
                    int i;
                    for (i = 0; i < list.size() - 1; i++) {
                        Pack other_pack = function.apply(list.get(i));
                        if (other_pack.getDefaultPosition() == Pack.Position.TOP && !(((PackTricks) other_pack).isAfterUserPack())
                                && (other_pack.getId().startsWith("file/") || other_pack.getId().startsWith("paxi/"))) {
                            break;
                        }
                    }
                    list.add(i, object);
                    cir.setReturnValue(i);
                }
            }
        }
    }
}
