package com.hlysine.create_power_loader.content.andesitechunkloader;


import com.hlysine.create_power_loader.content.AbstractChunkLoaderBlockEntity;
import com.hlysine.create_power_loader.content.LoaderType;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

@MethodsReturnNonnullByDefault
public class AndesiteChunkLoaderBlockEntity extends AbstractChunkLoaderBlockEntity {
    private static final int LOADING_RANGE = 1;

    public AndesiteChunkLoaderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, LoaderType.ANDESITE);
    }

    @Override
    public int getLoadingRange() {
        return LOADING_RANGE;
    }
}
