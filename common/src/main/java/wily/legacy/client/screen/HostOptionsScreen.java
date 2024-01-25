package wily.legacy.client.screen;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChangeDifficultyPacket;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import wily.legacy.LegacyMinecraft;
import wily.legacy.LegacyMinecraftClient;
import wily.legacy.client.LegacyOptions;
import wily.legacy.init.LegacyGameRules;
import wily.legacy.network.ServerDisplayInfoSync;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static wily.legacy.client.screen.LoadSaveScreen.GAME_MODEL_LABEL;

public class HostOptionsScreen extends PanelVListScreen{
    public static final Component HOST_OPTIONS = Component.translatable("legacy.menu.host_options");
    public static final Component PLAYERS_INVITE = Component.translatable("legacy.menu.players_invite");
    protected final Component title;
    protected float alpha = getDefaultOpacity();
    protected boolean shouldFade = false;

    public static final List<GameRules.Key<GameRules.BooleanValue>> WORLD_RULES = List.of(GameRules.RULE_DOFIRETICK,GameRules.RULE_DAYLIGHT,GameRules.RULE_KEEPINVENTORY,GameRules.RULE_DOMOBSPAWNING,GameRules.RULE_MOBGRIEFING, LegacyGameRules.GLOBAL_MAP_PLAYER_ICON);
    public static final List<GameRules.Key<GameRules.BooleanValue>> OTHER_RULES = List.of(GameRules.RULE_WEATHER_CYCLE,GameRules.RULE_DOMOBLOOT,GameRules.RULE_DOBLOCKDROPS,GameRules.RULE_NATURAL_REGENERATION);

    public HostOptionsScreen(Component title) {
        super(s-> Panel.centered(s,250,190,0,20),HOST_OPTIONS);
        this.title = title;
        addPlayerButtons();
        renderableVList.layoutSpacing(l->0);
        panel.dp = 3f;
    }
    public HostOptionsScreen() {
        this(PLAYERS_INVITE);
    }
    public void reloadPlayerButtons(){
        int i = renderableVList.renderables.indexOf(getFocused());
        addPlayerButtons();
        rebuildWidgets();
        if (i>= 0) setFocused((GuiEventListener) renderableVList.renderables.get(i));
    }
    public static void drawPlayerIcon(GameProfile profile, GuiGraphics guiGraphics, int x, int y){
        float[] color = LegacyMinecraftClient.getVisualPlayerColor(LegacyMinecraft.playerVisualIds.getOrDefault(profile.getName(), profile.getName().hashCode()));
        guiGraphics.setColor(color[0],color[1],color[2],1.0f);
        RenderSystem.enableBlend();
        guiGraphics.blitSprite(LegacyMinecraftClient.MAP_PLAYER_SPRITE,x,y, 20,20);
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f,1.0f,1.0f,1.0f);
    }
    protected void addPlayerButtons(){
        addPlayerButtons(true,(profile, b)->{
            if (!minecraft.player.hasPermissions(2)) return;
            AbstractClientPlayer p = (AbstractClientPlayer) minecraft.level.getPlayerByUUID(profile.getId());
            boolean initialVisibility = p != null && p.isInvisible();
            AtomicBoolean invisible = new AtomicBoolean(initialVisibility);
            PanelVListScreen screen = new PanelVListScreen(s-> Panel.centered(s,230,88), HOST_OPTIONS){
                @Override
                protected void init() {
                    panel.init();
                    renderableVList.init(this,panel.x + 8,panel.y + 27,panel.width - 16,panel.height);
                }

                @Override
                public void onClose() {
                    super.onClose();
                    if (initialVisibility != invisible.get()){
                        if (invisible.get()) {
                            minecraft.player.connection.sendCommand("effect give %s minecraft:invisibility infinite 255 true".formatted(profile.getName()));
                            minecraft.player.connection.sendCommand("effect give %s minecraft:resistance infinite 255 true".formatted(profile.getName()));
                        }else {
                            minecraft.player.connection.sendCommand("effect clear %s minecraft:invisibility".formatted(profile.getName()));
                            minecraft.player.connection.sendCommand("effect clear %s minecraft:resistance".formatted(profile.getName()));
                        }
                    }
                }

                @Override
                public boolean isPauseScreen() {
                    return false;
                }
                @Override
                public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
                    panel.render(guiGraphics,i,j,f);
                    drawPlayerIcon(profile,guiGraphics, panel.x + 7,panel.y + 5);
                    guiGraphics.drawString(font,profile.getName(),panel.x + 31, panel.y + 12, 0x404040,false);
                }
            };
            Supplier<GameType> gameType = ()->minecraft.player.connection.getPlayerInfo(profile.getId()).getGameMode();
            List<GameType> gameTypes = Arrays.stream(GameType.values()).toList();
            screen.renderableVList.addRenderable(new TickBox(0,0,initialVisibility,b1-> Component.translatable("legacy.menu.host_options.player.invisible"),b1-> null, b1->invisible.set(b1.selected)));
            screen.renderableVList.addRenderable(new LegacySliderButton<>(0, 0, 230,16, b1 -> b1.getDefaultMessage(GAME_MODEL_LABEL,b1.getValue().getLongDisplayName()),()->Tooltip.create(Component.translatable("selectWorld.gameMode."+gameType.get().getName()+ ".info")),gameType.get(),()->gameTypes, b1->minecraft.getConnection().sendCommand("gamemode %s %s".formatted(b1.objectValue.getName(),profile.getName()))));
            screen.renderableVList.addRenderable(Button.builder(Component.translatable("legacy.menu.host_options.set_player_spawn"),b1-> minecraft.player.connection.sendCommand("spawnpoint %s ~ ~ ~".formatted(profile.getName()))).bounds(0,0,215,20).build());
            screen.parent = HostOptionsScreen.this;
            screen.panel.dp = 3f;
            minecraft.setScreen(screen);
        });
    }
    protected void addPlayerButtons(boolean includeClient, BiConsumer<GameProfile, AbstractButton> onPress){
        renderableVList.renderables.clear();
        for (GameProfile profile : getActualServerProfiles()) {
            if (!includeClient && Objects.equals(profile.getName(), Minecraft.getInstance().player.getGameProfile().getName())) continue;
            renderableVList.addRenderable(new AbstractButton(0,0, 230, 30,Component.literal(profile.getName())) {
                @Override
                protected void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
                    if (isHoveredOrFocused()) shouldFade = true;
                    super.renderWidget(guiGraphics, i, j, f);
                    drawPlayerIcon(profile, guiGraphics,getX() + 6, getY() + 5);
                }
                @Override
                protected void renderScrollingString(GuiGraphics guiGraphics, Font font, int i, int j) {
                    TickBox.renderScrollingString(guiGraphics, font, this.getMessage(), getX() + 68, this.getY(), getX() + getWidth(), this.getY() + this.getHeight(), j,true);
                }
                @Override
                public void onPress() {
                    onPress.accept(profile,this);
                }
                @Override
                protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
                    defaultButtonNarrationText(narrationElementOutput);
                }
            });
        }
    }
    public static List<GameProfile> getActualServerProfiles(){
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer()) return minecraft.getSingleplayerServer().getPlayerList().getPlayers().stream().map(Player::getGameProfile).toList();
        if (minecraft.player != null && !minecraft.player.connection.getOnlinePlayers().isEmpty())
            return minecraft.player.connection.getOnlinePlayers().stream().map(PlayerInfo::getProfile).sorted(Comparator.comparingInt((p -> minecraft.isLocalPlayer(p.getId()) ? 0 : LegacyMinecraft.playerVisualIds.getOrDefault(p.getName(),1)))).toList();
        return Collections.emptyList();
    }

    @Override
    protected void init() {
        LegacyMinecraft.NETWORK.sendToServer(new ServerDisplayInfoSync(0));
        panel.init();
        addHostOptionsButton();
        renderableVList.init(this,panel.x + 10,panel.y + 22,panel.width - 20,panel.height - 8);
    }
    protected void addHostOptionsButton(){
        if (!minecraft.player.hasPermissions(2)) return;
        addRenderableWidget(Button.builder(HOST_OPTIONS,b-> {
            List<String> weathers = List.of("clear","rain","thunder");
            int initialWeather = minecraft.level.isThundering() ? 2 : minecraft.level.isRaining() ? 1 : 0;
            AtomicInteger savedWeather = new AtomicInteger(initialWeather);
            PanelVListScreen screen = new PanelVListScreen(s-> Panel.centered(s,265,200), HOST_OPTIONS){
                @Override
                protected void init() {
                    panel.init();
                    renderableVList.init(this,panel.x + 8,panel.y + 8,panel.width - 16,panel.height);
                }
                public void onClose() {
                    super.onClose();
                    if (initialWeather != savedWeather.get())
                        minecraft.getConnection().sendCommand("weather " + weathers.get(savedWeather.get()));
                }
                public boolean isPauseScreen() {
                    return false;
                }

                @Override
                public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
                    panel.render(guiGraphics,i,j,f);
                }
            };
            screen.parent = this;
            screen.panel.dp = 3f;
            screen.renderableVList.layoutSpacing(l-> 2);
            GameRules gameRules = minecraft.player.clientLevel.getGameRules();
            for (GameRules.Key<GameRules.BooleanValue> key : WORLD_RULES)
                screen.renderableVList.addRenderable(new TickBox(0,0,gameRules.getRule(key).get(),b1-> Component.translatable(key.getDescriptionId()),b1-> null, b1->minecraft.player.connection.sendCommand("gamerule %s %s".formatted(key.getId(), b1.selected))));
            screen.renderableVList.addRenderable(Button.builder(Component.translatable("legacy.menu.host_options.set_day"),b1-> minecraft.player.connection.sendCommand("time set day")).bounds(0,0,215,20).build());
            screen.renderableVList.addRenderable(Button.builder(Component.translatable("legacy.menu.host_options.set_night"),b1-> minecraft.player.connection.sendCommand("time set night")).bounds(0,0,215,20).build());
            screen.renderableVList.addRenderable(new LegacySliderButton<>(0,0, 230,16, b1 -> b1.getDefaultMessage(Component.translatable("options.difficulty"),b1.getValue().getDisplayName()),()-> Tooltip.create(minecraft.level.getDifficulty().getInfo()),minecraft.level.getDifficulty(),()-> Arrays.asList(Difficulty.values()), b1->minecraft.getConnection().send(new ServerboundChangeDifficultyPacket(b1.objectValue))));
            Supplier<GameType> gameType = ()->minecraft.gameMode.getPlayerMode();
            List<GameType> gameTypes = Arrays.stream(GameType.values()).toList();
            screen.renderableVList.addRenderable(new LegacySliderButton<>(0, 0, 230,16, b1 -> b1.getDefaultMessage(GAME_MODEL_LABEL,b1.getValue().getLongDisplayName()),()->Tooltip.create(Component.translatable("selectWorld.gameMode."+gameType.get().getName()+ ".info")),gameType.get(),()->gameTypes, b1->minecraft.getConnection().sendCommand("gamemode " + b1.objectValue.getName())));
            screen.renderableVList.addRenderable(Button.builder(Component.translatable("legacy.menu.host_options.set_world_spawn"),b1-> minecraft.player.connection.sendCommand("setworldspawn")).bounds(0,0,215,20).build());
            screen.renderableVList.addRenderables(SimpleLayoutRenderable.create(240, 12, (l -> ((graphics, i, j, f) -> {}))),SimpleLayoutRenderable.create(240, 12, (l -> ((graphics, i, j, f) -> graphics.drawString(font, Component.translatable("soundCategory.weather"), l.x + 1, l.y + 4, 0x404040,false)))));
            screen.renderableVList.addRenderable(new LegacySliderButton<>(0, 0, 230,16, b1 -> Component.translatable( "legacy.weather_state." + b1.getValue()),()->null,weathers.get(savedWeather.get()),()->weathers, b1->savedWeather.set(weathers.indexOf(b1.getValue()))));
            for (GameRules.Key<GameRules.BooleanValue> key : OTHER_RULES)
                screen.renderableVList.addRenderable(new TickBox(0,0,gameRules.getRule(key).get(),b1-> Component.translatable(key.getDescriptionId()),b1-> null, b1->minecraft.player.connection.sendCommand("gamerule %s %s".formatted(key.getId(), b1.selected))));
            minecraft.setScreen(screen);
            if (minecraft.hasSingleplayerServer() && !minecraft.getSingleplayerServer().isPublished()) return;
            BiFunction<Boolean,Component,AbstractButton> teleportButton = (bol,title)-> Button.builder(title, b1-> minecraft.setScreen(new HostOptionsScreen(title) {
                @Override
                protected void addHostOptionsButton() {
                }

                @Override
                protected void addPlayerButtons() {
                    addPlayerButtons(false,(profile,b1)->{
                        if (bol) minecraft.player.connection.sendCommand("tp %s".formatted(profile.getName()));
                        else minecraft.player.connection.sendCommand("tp %s ~ ~ ~".formatted(profile.getName()));
                    });
                }

                public boolean isPauseScreen() {
                    return false;
                }
            })).bounds(0,0,215,20).build();
            screen.renderableVList.addRenderable(teleportButton.apply(true,Component.translatable("legacy.menu.host_options.teleport_player")));
            screen.renderableVList.addRenderable(teleportButton.apply(false,Component.translatable("legacy.menu.host_options.teleport_me")));
        }).bounds(panel.x, panel.y - 36,250,20).build());
    }
    public boolean isPauseScreen() {
        return false;
    }

    public boolean renderableKeyPressed(int i){
        return getRenderableVList().keyPressed(i, !(!children.isEmpty() && children.get(0) instanceof Button));
    }

    @Override
    public void tick() {
        super.tick();
        if (shouldFade) {
            HostOptionsScreen.this.alpha = Math.min(1.0f, HostOptionsScreen.this.alpha + 0.01f);
            shouldFade = false;
        }else {
            if (alpha > getDefaultOpacity()) alpha -= 0.01f;
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        RenderSystem.setShaderColor(1.0f,1.0f,1.0f, alpha);
        panel.render(guiGraphics,i,j,f);
        RenderSystem.setShaderColor(1.0f,1.0f,1.0f,1.0f);
        guiGraphics.drawString(font,title,panel.x + 11, panel.y + 8, 0x404040, false);
    }

    protected static float getDefaultOpacity() {
        return ((LegacyOptions)Minecraft.getInstance().options).hudOpacity().get().floatValue();
    }
}