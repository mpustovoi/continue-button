package com.mineblock11.continuebutton.mixin;

import com.mineblock11.continuebutton.ContinueButtonMod;
import it.unimi.dsi.fastutil.longs.LongComparators;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Mixin(value = TitleScreen.class, priority = 1001)
public class MixinTitleScreen extends Screen {
    private final MultiplayerServerListPinger serverListPinger = new MultiplayerServerListPinger();
    private LevelSummary level = null;
    private ServerInfo serverInfo = null;
    private boolean isFirstRender = true;
    private ArrayList<LevelSummary> levels = new ArrayList<>();

    protected MixinTitleScreen(Text title) {
        super(title);
    }

    @Inject(at = @At("HEAD"), method = "initWidgetsNormal")
    public void drawMenuButton(int y, int spacingY, CallbackInfo info) {
            ButtonWidget continueButton = new ButtonWidget(this.width / 2 - 100, y, 98, 20, Text.translatable("continuebutton.continueButtonTitle"), button -> {
                if (ContinueButtonMod.wasLastWorldLocal) {
                    button.active = false;
                    LevelStorage levelStorage = this.client.getLevelStorage();
                    if (levels.isEmpty()) {
                        CreateWorldScreen.create(client, this);
                    } else {
                        levels.sort(Comparator.comparingLong(LevelSummary::getLastPlayed));
                        level = levels.get(0);

                        if (!level.isLocked()) {
                            if (level.isFutureLevel()) {
                                this.client.setScreen(new ConfirmScreen((bl) -> {
                                    if (bl) {
                                        try {
                                            start();
                                        } catch (Exception var3) {
                                            this.client.setScreen(new NoticeScreen(() -> {
                                                this.client.setScreen(this);
                                            }, Text.translatable("selectWorld.futureworld.error.title"), Text.translatable("selectWorld.futureworld.error.text")));
                                        }
                                    } else {
                                        this.client.setScreen(this);
                                    }

                                }, Text.translatable("selectWorld.versionQuestion"), Text.translatable("selectWorld.versionWarning", this.level.getVersion(), Text.translatable("selectWorld.versionJoinButton"), ScreenTexts.CANCEL)));
                            } else {
                                start();
                            }

                        }
                    }
                } else {
                    ConnectScreen.connect(this, this.client, ServerAddress.parse(serverInfo.address), serverInfo);
                }

            }, (button, matrixStack, i, j) -> {
                if (ContinueButtonMod.wasLastWorldLocal) {
                    if (level == null) {
                        List<OrderedText> list = new ArrayList<>();
                        list.add(Text.translatable("selectWorld.create").formatted(Formatting.GRAY).asOrderedText());
                        this.renderOrderedTooltip(matrixStack, list, i, j);
                    } else {
                        List<OrderedText> list = new ArrayList<>();
                        list.add(Text.translatable("menu.singleplayer").formatted(Formatting.GRAY).asOrderedText());
                        list.add(Text.literal(level.getDisplayName()).asOrderedText());
                        this.renderOrderedTooltip(matrixStack, list, i, j);
                    }
                } else if (serverInfo != null) {
                    List<OrderedText> list = new ArrayList<>(this.client.textRenderer.wrapLines(serverInfo.label, 270));
                    list.add(0, Text.literal(serverInfo.name).formatted(Formatting.GRAY).asOrderedText());
                    this.renderOrderedTooltip(matrixStack, list, i, j);
                }
            });
            Screens.getButtons(this).add(continueButton);
        }

    private void start() {
        this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (this.client.getLevelStorage().levelExists(this.level.getName())) {
            this.client.setScreenAndRender(new MessageScreen(Text.translatable("selectWorld.data_read")));
            try {
                LevelStorage.Session session = this.client.getLevelStorage().createSession(this.level.getName());
                this.client.startIntegratedServer(this.level.getName(), session, this.client.getResourcePackManager(), this.client.createIntegratedServerLoader().createSaveLoader(session, false));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "init()V")
    public void initAtHead(CallbackInfo info) {
        isFirstRender = true;
    }

    @Inject(at = @At("TAIL"), method = "init()V")
    public void init(CallbackInfo info) {
        for (ClickableWidget button : Screens.getButtons(this)) {
            if (button.visible && !button.getMessage().equals(Text.translatable("continuebutton.continueButtonTitle"))) {
                button.x = this.width / 2 + 2;
                button.setWidth(98);
                break;
            }
        }
    }

    private void atFirstRender() {
        new Thread(() -> {
            if (ContinueButtonMod.wasLastWorldLocal) {
                LevelStorage levelStorage = this.client.getLevelStorage();
                levels = null;
                try {
                    this.levels = new ArrayList<>(levelStorage.loadSummaries(levelStorage.getLevelList()).get());
                } catch (LevelStorageException e) {
                    e.printStackTrace();
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (levels.isEmpty()) {
                    level = null;
                } else {
                    levels.sort(Comparator.comparingLong(LevelSummary::getLastPlayed));
                    level = levels.get(0);
                }
            } else {
                ServerList serverList = new ServerList(this.client);
                ServerInfo serverInList = null;
                for (int i = 0; i < serverList.size(); i++) {
                    if (serverList.get(i).address.equalsIgnoreCase(ContinueButtonMod.serverAddress)) {
                        serverInList = serverList.get(i);
                    }
                }

                if (serverInList == null) {
                    ContinueButtonMod.wasLastWorldLocal = true;
                    ContinueButtonMod.serverName = "";
                    ContinueButtonMod.serverAddress = "";
                    ContinueButtonMod.saveConfig();
                } else {
                    serverInfo = serverInList;

                    ContinueButtonMod.wasLastWorldLocal = false;
                    ContinueButtonMod.serverName = serverInfo.name;
                    ContinueButtonMod.serverAddress = serverInfo.address;
                    ContinueButtonMod.saveConfig();

                    serverInfo.label = Text.literal("multiplayer.status.pinging");
                    try {
                        serverListPinger.add(serverInfo, () -> {
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    @Inject(at = @At("HEAD"), method = "render")
    public void renderAtHead(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo info) {
        if (isFirstRender) {
            isFirstRender = false;
            atFirstRender();
        }
    }

    @Inject(at = @At("RETURN"), method = "tick()V")
    public void tick(CallbackInfo info) {
        serverListPinger.tick();
    }

    @Inject(at = @At("RETURN"), method = "removed()V")
    public void removed(CallbackInfo info) {
        serverListPinger.cancel();
    }
}