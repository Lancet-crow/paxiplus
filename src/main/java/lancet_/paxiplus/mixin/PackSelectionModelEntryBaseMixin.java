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

@Mixin(targets = "net.minecraft.client.gui.screens.packs.PackSelectionModel.EntryBase")
public class PackSelectionModelEntryBaseMixin {
    @Shadow @Final private Pack pack;

    @Definition(id = "i", local = @Local(type = int.class))
    @Expression("i >= 0")
    @ModifyExpressionValue(method = "canMoveDown", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean stopIfPaxiDown(boolean original){
        return original && pack.getPackSource() != PaxiPackSource.PACK_SOURCE_PAXI;
    }

    @Definition(id = "i", local = @Local(type = int.class))
    @Expression("i > 0")
    @ModifyExpressionValue(method = "canMoveUp", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean stopIfPaxiUp(boolean original){
        return original && pack.getPackSource() != PaxiPackSource.PACK_SOURCE_PAXI;
    }
}
