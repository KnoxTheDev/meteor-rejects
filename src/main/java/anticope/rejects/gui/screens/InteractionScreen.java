package anticope.rejects.gui.screens;

import anticope.rejects.mixin.EntityAccessor;
import anticope.rejects.modules.InteractionMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.meteorclient.utils.render.PeekScreen;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.StorageMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class InteractionScreen extends Screen {

    public static Entity interactionMenuEntity;

    private final Entity entity;
    private String focusedString = null;
    private int crosshairX, crosshairY, focusedDot = -1;
    private float yaw, pitch;
    private final Map<String, Consumer<Entity>> functions;
    private final Map<String, String> msgs;

    private final int selectedDotColor;
    private final int dotColor;
    private final int backgroundColor;
    private final int borderColor;
    private final int textColor;

    public InteractionScreen(Entity entity) {
        super(Text.literal("Menu Screen"));
        InteractionMenu module = Modules.get().get(InteractionMenu.class);
        if (module == null) throw new IllegalStateException("InteractionMenu module is missing!");

        selectedDotColor = module.selectedDotColor.get().getPacked();
        dotColor = module.dotColor.get().getPacked();
        backgroundColor = module.backgroundColor.get().getPacked();
        borderColor = module.borderColor.get().getPacked();
        textColor = module.textColor.get().getPacked();

        this.entity = entity;
        functions = new HashMap<>();
        msgs = module.messages.get();

        initFunctions();
    }

    private void initFunctions() {
        functions.put("Stats", e -> {
            closeScreen();
            client.setScreen(new StatsScreen(e));
        });

        if (entity instanceof PlayerEntity playerEntity) {
            functions.put("Open Inventory", e -> {
                closeScreen();
                client.setScreen(new InventoryScreen(playerEntity));
            });
        } else if (entity instanceof AbstractHorseEntity || entity instanceof HorseEntity) {
            functions.put("Open Inventory", e -> {
                closeScreen();
                client.player.networkHandler.sendPacket(
                        new net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket(new net.minecraft.util.PlayerInput(false,false,false,false,false,true,false))
                );
                client.player.networkHandler.sendPacket(
                        net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.interact(entity, true, net.minecraft.util.Hand.MAIN_HAND)
                );
                client.player.setSneaking(false);
            });
        } else if (entity instanceof StorageMinecartEntity) {
            functions.put("Open Inventory", e -> {
                closeScreen();
                client.player.networkHandler.sendPacket(
                        net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket.interact(entity, true, net.minecraft.util.Hand.MAIN_HAND)
                );
            });
        } else {
            functions.put("Open Inventory", e -> {
                closeScreen();
                ItemStack container = new ItemStack(Items.CHEST);
                container.setCustomName(e.getName());
                client.setScreen(new PeekScreen(container, getInventory(e)));
            });
        }

        functions.put("Spectate", e -> {
            client.setCameraEntity(e);
            client.player.sendMessage(Text.literal("Sneak to un-spectate."), true);
            MeteorClient.EVENT_BUS.subscribe(new ShiftListener());
            closeScreen();
        });

        if (entity.isGlowing()) {
            functions.put("Remove glow", e -> {
                e.setGlowing(false);
                ((EntityAccessor) e).invokeSetFlag(6, false);
                closeScreen();
            });
        } else {
            functions.put("Glow", e -> {
                e.setGlowing(true);
                ((EntityAccessor) e).invokeSetFlag(6, true);
                closeScreen();
            });
        }

        if (entity.noClip) {
            functions.put("Disable NoClip", e -> {
                entity.noClip = false;
                closeScreen();
            });
        } else {
            functions.put("NoClip", e -> {
                entity.noClip = true;
                closeScreen();
            });
        }

        msgs.forEach((key, script) -> functions.put(key, e -> {
            closeScreen();
            interactionMenuEntity = e;
            var result = org.meteordev.starscript.compiler.Parser.parse(script);
            if (result.hasErrors()) {
                result.errors.forEach(MeteorStarscript::printChatError);
                return;
            }
            try {
                var compiled = org.meteordev.starscript.compiler.Compiler.compile(result);
                var section = MeteorStarscript.ss.run(compiled);
                client.setScreen(new ChatScreen(section.text));
            } catch (org.meteordev.starscript.utils.StarscriptError err) {
                MeteorStarscript.printChatError(err);
            }
        }));

        functions.put("Cancel", e -> closeScreen());
    }

    private ItemStack[] getInventory(Entity e) {
        ItemStack[] stack = new ItemStack[27];
        int index = 0;

        if (e instanceof HorseEntity horse && horse.isWearingBodyArmor()) {
            stack[index++] = Items.SADDLE.getDefaultStack();
        }

        if (e instanceof LivingEntity living) {
            ItemStack handStack = living.getStackInHand(net.minecraft.util.Hand.MAIN_HAND);
            if (handStack != null) stack[index++] = handStack;
        }

        for (; index < 27; index++) stack[index] = Items.AIR.getDefaultStack();
        return stack;
    }

    @Override
    public void init() {
        super.init();
        this.client.mouse.lockCursor();
        yaw = client.player.getYaw();
        pitch = client.player.getPitch();
    }

    @Override
    public void tick() {
        InteractionMenu module = Modules.get().get(InteractionMenu.class);
        if (module != null && module.keybind.get().isPressed()) close();
    }

    private void closeScreen() {
        client.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawDots(context, Math.min(width, height) / 2 * 3 / 4, mouseX, mouseY);
    }

    private void drawDots(DrawContext context, int radius, int mouseX, int mouseY) {
        ArrayList<Point> points = new ArrayList<>();
        String[] keys = functions.keySet().toArray(new String[0]);
        double closestDist = Double.MAX_VALUE;
        focusedDot = -1;

        for (int i = 0; i < keys.length; i++) {
            double angle = i / (double) keys.length * 2 * Math.PI;
            int x = (int) Math.round(radius * Math.cos(angle) + width / 2);
            int y = (int) Math.round(radius * Math.sin(angle) + height / 2);

            points.add(new Point(x, y));
            if (mouseX != 0 || mouseY != 0) {
                double dist = Point.distance(x, y, mouseX, mouseY);
                if (dist < closestDist) {
                    closestDist = dist;
                    focusedDot = i;
                }
            }
        }

        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            drawDot(context, p.x - 4, p.y - 4, (i == focusedDot) ? selectedDotColor : dotColor);
        }

        if (focusedDot >= 0) focusedString = keys[focusedDot];
    }

    private void drawDot(DrawContext context, int x, int y, int color) {
        context.fill(x, y, x + 8, y + 8, color);
    }

    @Override
    public void close() {
        this.client.mouse.unlockCursor();
        if (focusedString != null && functions.containsKey(focusedString)) {
            functions.get(focusedString).accept(entity);
        } else closeScreen();
    }

    private static class ShiftListener {
        @EventHandler
        private void onKey(meteordevelopment.meteorclient.events.meteor.KeyEvent event) {
            if (MeteorClient.mc.options.sneakKey.matchesKey(event.key, 0)
                    || MeteorClient.mc.options.sneakKey.matchesMouse(event.key)) {
                MeteorClient.mc.setCameraEntity(MeteorClient.mc.player);
                event.cancel();
                MeteorClient.EVENT_BUS.unsubscribe(this);
            }
        }
    }
}
