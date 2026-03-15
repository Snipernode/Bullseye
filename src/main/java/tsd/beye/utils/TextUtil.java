package tsd.beye.utils;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.md_5.bungee.api.ChatColor;

public final class TextUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");

    private TextUtil() {
    }

    public static String colorize(String msg) {
        if (msg == null) {
            return "";
        }

        Matcher match = HEX_PATTERN.matcher(msg);
        while (match.find()) {
            String color = msg.substring(match.start(), match.end());
            msg = msg.replace(color, String.valueOf(ChatColor.of(color)));
            match = HEX_PATTERN.matcher(msg);
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public static List<String> colorize(List<String> lines) {
        return lines.stream().map(TextUtil::colorize).collect(Collectors.toList());
    }
}
