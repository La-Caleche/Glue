package fr.lacaleche.glue.shaper;

import fr.lacaleche.glue.extension.VoxelSetExtension;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.CubePointRange;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GlueVoxelShape extends VoxelShape {

    public GlueVoxelShape(VoxelShape baseShape) {
        super(((VoxelSetExtension) baseShape).glue$getShape());
    }

    @Override
    public DoubleList getCoords(Direction.Axis axis) {
        return new CubePointRange(this.shape.getSize(axis));
    }

}
