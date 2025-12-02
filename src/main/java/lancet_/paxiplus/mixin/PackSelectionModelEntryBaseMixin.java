package lancet_.paxiplus.mixin;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.yungnickyoung.minecraft.paxi.PaxiPackSource;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(targets = "net.minecraft.client.gui.screens.packs.PackSelectionModel.EntryBase")
public abstract class PackSelectionModelEntryBaseMixin {
    @Shadow @Final private Pack pack;

    @Shadow protected abstract List<Pack> getSelfList();

    @Definition(id = "i", local = @Local(type = int.class))
    @Definition(id = "list", local = @Local(type = List.class))
    @Definition(id = "size", method = "Ljava/util/List;size()I")
    @Expression("i < list.size() - 1")
    @ModifyExpressionValue(method = "canMoveDown", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean stopIfPaxiDown(boolean original){
        List<Pack> list = getSelfList();
        int i = list.indexOf(pack);
        return original && pack.getPackSource() != PaxiPackSource.PACK_SOURCE_PAXI
                && list.get(i + 1).getPackSource() != PaxiPackSource.PACK_SOURCE_PAXI;
    }

    @Definition(id = "i", local = @Local(type = int.class))
    @Expression("i > 0")
    @ModifyExpressionValue(method = "canMoveUp", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean stopIfPaxiUp(boolean original){
        List<Pack> list = getSelfList();
        int i = list.indexOf(pack);
        return original && pack.getPackSource() != PaxiPackSource.PACK_SOURCE_PAXI
                && list.get(i - 1).getPackSource() != PaxiPackSource.PACK_SOURCE_PAXI;
    }
}
