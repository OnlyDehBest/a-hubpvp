package it.onlynelchilling.ahubpvp.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageUtils {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final String DECO_RESET = "<!bold><!italic><!obfuscated><!strikethrough><!underlined>";

    private static final Map<Character, String> COLOR_MAP = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>")
    );

    private static final Map<Character, String> FORMAT_MAP = Map.of(
            'k', "<obfuscated>",
            'l', "<bold>",
            'm', "<strikethrough>",
            'n', "<underlined>",
            'o', "<italic>"
    );

    private MessageUtils() {}

    public static String convert(String input) {
        return toMiniMessage(input);
    }

    public static Component colorize(String message) {
        return MINI.deserialize(toMiniMessage(message));
    }

    public static Component itemText(String message) {
        return colorize(message).decoration(TextDecoration.ITALIC, false);
    }

    public static Component parse(String preConverted) {
        return MINI.deserialize(preConverted);
    }

    public static void send(Player player, String preConverted) {
        player.sendMessage(MINI.deserialize(preConverted));
    }

    public static void send(Player player, String preConverted, String placeholder, String value) {
        player.sendMessage(MINI.deserialize(preConverted.replace(placeholder, value)));
    }

    public static void broadcast(Collection<? extends Player> players, Component component) {
        for (Player player : players) {
            player.sendMessage(component);
        }
    }

    private static String toMiniMessage(String input) {
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, DECO_RESET + "<color:#" + matcher.group(1) + ">");
        }
        matcher.appendTail(sb);
        input = sb.toString();

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            if (input.charAt(i) == '&' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                if (code == 'r') {
                    result.append("<reset>");
                    i++;
                    continue;
                }
                String colorTag = COLOR_MAP.get(code);
                if (colorTag != null) {
                    result.append(DECO_RESET).append(colorTag);
                    i++;
                    continue;
                }
                String formatTag = FORMAT_MAP.get(code);
                if (formatTag != null) {
                    result.append(formatTag);
                    i++;
                    continue;
                }
            }
            result.append(input.charAt(i));
        }
        return result.toString();
    }
}

