package anticope.rejects.utils.server;

import net.minecraft.SharedConstants;
import net.minecraft.text.Text; // Changed from net.minecraft.network.chat.Component
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class MServerInfo {
    public String name;
    public String address;
    public String playerCountLabel;
    public int playerCount;
    public int playercountMax;
    public String label;
    public long ping;
    public int protocolVersion = SharedConstants.getCurrentVersion().getProtocolVersion();
    public String version = null;
    public List<Text> playerListSummary = Collections.emptyList(); // Changed from Component to Text
    private byte @Nullable [] icon;

    public MServerInfo(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public byte @Nullable [] getIcon() {
        return this.icon;
    }

    public void setIcon(byte @Nullable [] bytes) {
        this.icon = bytes;
    }
}