package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.history.Command;
import net.minecraft.core.BlockPos;

/** An undoable change of one preview block, never a change to the actual world. */
public record UpdateBlockCommand(SceneTestController controller, BlockPos blockPos,
                                 TransformationComponent oldTransform, TransformationComponent newTransform) implements Command {

    @Override
    public String getLabel() {
        return "Transform block " + this.blockPos.toShortString();
    }

    @Override
    public void execute() {
        this.controller.setGizmo(this.blockPos, this.newTransform);
    }

    @Override
    public void undo() {
        this.controller.setGizmo(this.blockPos, this.oldTransform);
    }
}
