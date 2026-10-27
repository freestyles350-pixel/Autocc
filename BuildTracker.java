package dev.autocc.client.build;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

/**
 * Top-right heads-up readout for the auto builder.
 *
 * <p>A build runs with the Auto cc screen closed, so without this there is nothing to look at while
 * it works and nothing to tell the player it has finished. It stays out of the way otherwise:
 * nothing is drawn unless a build is running or paused, or has just finished, and the finished
 * panel clears itself after {@link #COMPLETE_LINGER_MILLIS}.
 */
public final class BuildTracker implements HudElement {
    private static final Identifier ID = Identifier.of("auto-cc", "build_tracker");

    private static final int WIDTH = 152;
    private static final int PAD = 7;
    private static final int MARGIN = 6;
    private static final int TITLE_HEIGHT = 9;
    private static final int ROW_HEIGHT = 9;
    private static final int GAP = 3;
    private static final int BAR_HEIGHT = 5;
    private static final int HEIGHT = PAD + TITLE_HEIGHT + GAP + BAR_HEIGHT + GAP + ROW_HEIGHT + PAD;

    /** How long the completed panel stays up before it takes itself away. */
    private static final long COMPLETE_LINGER_MILLIS = 20_000L;

    private static final int PANEL = 0xE0121212;
    private static final int BORDER = 0xFFEFEFEF;
    private static final int TRACK = 0xFF2E2E2E;
    private static final int FILL = 0xFFF4F4F4;
    private static final int TEXT = 0xFFF4F4F4;
    private static final int MUTED = 0xFF9E9E9E;

    private final BuildEngine engine;
    private long finishedAtMillis;

    public BuildTracker(BuildEngine engine) {
        this.engine = engine;
    }

    /** Draws the tracker at the top right of the screen from now on, above the vanilla HUD. */
    public void register() {
        HudElementRegistry.addLast(ID, this);
    }

    @Override
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }

        BuildEngine.State state = this.engine.state();
        String title;
        if (state == BuildEngine.State.DONE) {
            if (this.finishedAtMillis == 0L) {
                this.finishedAtMillis = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - this.finishedAtMillis > COMPLETE_LINGER_MILLIS) {
                return;
            }
            title = "AUTO BUILD COMPLETE";
        } else if (this.engine.isActive()) {
            this.finishedAtMillis = 0L;
            title = state == BuildEngine.State.PAUSED ? "AUTO BUILD PAUSED" : "AUTO BUILD";
        } else {
            this.finishedAtMillis = 0L;
            return;
        }

        TextRenderer font = client.textRenderer;
        int x = context.getScaledWindowWidth() - WIDTH - MARGIN;
        int y = MARGIN;
        context.fill(x, y, x + WIDTH, y + HEIGHT, PANEL);
        outline(context, x, y);
        context.drawText(font, title, x + PAD, y + PAD, TEXT, false);

        int total = this.engine.planned();
        int confirmed = Math.min(this.engine.confirmed(), total);
        float ratio = this.engine.completion();
        int barY = y + PAD + TITLE_HEIGHT + GAP;
        int barRight = x + WIDTH - PAD;
        context.fill(x + PAD, barY, barRight, barY + BAR_HEIGHT, TRACK);
        int filled = Math.round((barRight - x - PAD) * ratio);
        if (filled > 0) {
            context.fill(x + PAD, barY, x + PAD + filled, barY + BAR_HEIGHT, FILL);
        }

        int rowY = barY + BAR_HEIGHT + GAP;
        String counts = confirmed + " / " + total + "  ·  " + Math.round(ratio * 100.0F) + "%";
        String elapsed = formatDuration(this.engine.elapsedMillis());
        context.drawText(font, counts, x + PAD, rowY, MUTED, false);
        context.drawText(font, elapsed, barRight - font.getWidth(elapsed), rowY, MUTED, false);
    }

    private static void outline(DrawContext context, int x, int y) {
        context.fill(x, y, x + WIDTH, y + 1, BORDER);
        context.fill(x, y + HEIGHT - 1, x + WIDTH, y + HEIGHT, BORDER);
        context.fill(x, y, x + 1, y + HEIGHT, BORDER);
        context.fill(x + WIDTH - 1, y, x + WIDTH, y + HEIGHT, BORDER);
    }

    private static String formatDuration(long millis) {
        long seconds = millis / 1000L;
        if (seconds < 60L) {
            return seconds + "s";
        }
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }
}
