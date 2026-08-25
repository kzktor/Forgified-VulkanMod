package net.vulkanmod.render.model.quad;

import net.minecraft.core.Direction;
import net.vulkanmod.render.chunk.cull.QuadFacing;

/**
 * A {@link QuadView} that additionally exposes the geometry data the FRAPI quad
 * pipeline computes on its own: the light face, the packed face normal and the
 * axis-aligned facing bucket derived from it.
 */
public interface ModelQuadView extends QuadView {

    Direction lightFace();

    QuadFacing getQuadFacing();

    int getNormal();
}
