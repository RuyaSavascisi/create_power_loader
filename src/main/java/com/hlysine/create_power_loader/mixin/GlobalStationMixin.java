package com.hlysine.create_power_loader.mixin;

import com.hlysine.create_power_loader.content.trains.CPLGlobalStation;
import com.hlysine.create_power_loader.content.trains.StationChunkLoader;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GlobalStation.class)
public class GlobalStationMixin implements CPLGlobalStation {
    @Unique
    public StationChunkLoader cpl$chunkLoader;

    @Override
    @Unique
    public @NotNull StationChunkLoader getLoader() {
        if (cpl$chunkLoader == null)
            cpl$chunkLoader = new StationChunkLoader((GlobalStation) (Object) this);
        return cpl$chunkLoader;
    }

    @Override
    @Unique
    public void setLoader(StationChunkLoader loader) {
        cpl$chunkLoader = loader;
    }

    @Inject(
            at = @At("RETURN"),
            method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;ZLcom/simibubi/create/content/trains/graph/DimensionPalette;)V",
            remap = false
    )
    private void cpl$read(CompoundTag nbt, HolderLookup.Provider registries, boolean migration, DimensionPalette dimensions, CallbackInfo ci) {
        CompoundTag data = nbt.getCompound("CPLData");
        cpl$chunkLoader = StationChunkLoader.read((GlobalStation) (Object) this, data);
    }

    @Inject(
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readUtf()Ljava/lang/String;"),
            method = "read(Lnet/minecraft/network/FriendlyByteBuf;Lcom/simibubi/create/content/trains/graph/DimensionPalette;)V"
    )
    private void cpl$read(FriendlyByteBuf buffer, DimensionPalette dimensions, CallbackInfo ci) {
        CompoundTag nbt = buffer.readNbt();
        if (nbt != null)
            cpl$chunkLoader = StationChunkLoader.read((GlobalStation) (Object) this, nbt);
    }

    @Inject(
            at = @At("RETURN"),
            method = "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Lcom/simibubi/create/content/trains/graph/DimensionPalette;)V",
            remap = false
    )
    private void cpl$write(CompoundTag nbt, HolderLookup.Provider registries, DimensionPalette dimensions, CallbackInfo ci) {
        nbt.put("CPLData", getLoader().write());
    }

    @Inject(
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeUtf(Ljava/lang/String;)Lnet/minecraft/network/FriendlyByteBuf;"),
            method = "write(Lnet/minecraft/network/FriendlyByteBuf;Lcom/simibubi/create/content/trains/graph/DimensionPalette;)V"
    )
    private void cpl$write(FriendlyByteBuf buffer, DimensionPalette dimensions, CallbackInfo ci) {
        buffer.writeNbt(getLoader().write());
    }
}