package fr.lacaleche.glue.testmod.scene;

import fr.lacaleche.glue.history.Command;
import fr.lacaleche.glue.data.components.TransformationComponent;
import net.minecraft.core.BlockPos;

/**
 * Undoable change of one preview block's transform, recorded when a gizmo drag ends.
 * Only the scene preview is affected — the real world never changes.
 */
public class UpdateBlockCommand implements Command {
    private final SceneTestController controller;
    private final BlockPos blockPos;
    private final TransformationComponent oldTransform;
    private final TransformationComponent newTransform;

    public UpdateBlockCommand(SceneTestController controller, BlockPos blockPos, TransformationComponent oldTransform,
            TransformationComponent newTransform) {
        this.controller = controller;
        this.blockPos = blockPos;
        this.oldTransform = oldTransform;
        this.newTransform = newTransform;
    }

    @Override
    public String getLabel() {
        return "Transform block " + blockPos.toShortString();
    }

    @Override
    public void execute() {
        applyProperties(newTransform);
    }

    @Override
    public void undo() {
        applyProperties(oldTransform);
    }

    private void applyProperties(TransformationComponent transform) {
        controller.setGizmo(blockPos, transform);
    }
}
