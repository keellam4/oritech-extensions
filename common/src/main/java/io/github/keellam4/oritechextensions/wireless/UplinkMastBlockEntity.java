package io.github.keellam4.oritechextensions.wireless;

import io.github.keellam4.oritechextensions.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Uplink Mast: one upgrade slot, for every Cargo Link on its wall. */
public class UplinkMastBlockEntity extends BlockEntity {

    private final SimpleContainer upgrade = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            UplinkMastBlockEntity.this.setChanged();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof LinkUpgradeItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };

    public UplinkMastBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.UPLINK_MAST_ENTITY.get(), pos, state);
    }

    public SimpleContainer upgrade() {
        return upgrade;
    }

    /** 0, {@link LinkUpgradeItem#INFINITE} or {@link LinkUpgradeItem#DIMENSIONAL}. */
    public int upgradeLevel() {
        return upgrade.getItem(0).getItem() instanceof LinkUpgradeItem item ? item.level() : 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!upgrade.getItem(0).isEmpty()) tag.put("upgrade", upgrade.getItem(0).save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        upgrade.setItem(0, tag.contains("upgrade") ? ItemStack.parse(registries, tag.getCompound("upgrade")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY);
    }
}
