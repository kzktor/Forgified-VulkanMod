package net.vulkanmod.mixin.render.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.vulkanmod.interfaces.ModelPartCubeMixed;
import net.vulkanmod.render.model.CubeModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ModelPart.Cube.class)
public class ModelPartCubeM implements ModelPartCubeMixed {

    CubeModel cube;

    // The vanilla Cube constructor MUTATES its x/y/z parameter locals after storing min/max
    // (subtracts grow, and the mirror branch swaps x with maxX), so reading f/g/h here at RETURN
    // gives grow-adjusted, mirror-swapped values — CubeModel.setVertices then re-applies both,
    // inflating cubes by grow twice and un-mirroring mirrored parts (the "Strider tentacles
    // flipped up" corruption that got this mixin disabled on 2026-06-22). 1.21.x avoids it by
    // injecting before the mutation, but Forge 1.20.1's Mixin 0.8.5 only allows constructor
    // injection at RETURN. Instead recover the pristine origin from the minX/minY/minZ fields,
    // which vanilla assigns from the untouched parameters first; all other parameters are never
    // mutated.
    @Inject(method = "<init>", at = @At("RETURN"))
    private void getVertices(int i, int j, float f, float g, float h, float k, float l, float m, float n, float o, float p, boolean bl, float q, float r, Set<Direction> set, CallbackInfo ci) {
        ModelPart.Cube self = (ModelPart.Cube) (Object) this;
        CubeModel cube = new CubeModel();
        cube.setVertices(i, j, self.minX, self.minY, self.minZ, k, l, m, n, o, p, bl, q, r, set);
        this.cube = cube;
    }


    @Override
    public CubeModel getCubeModel() {
        return this.cube;
    }
}
