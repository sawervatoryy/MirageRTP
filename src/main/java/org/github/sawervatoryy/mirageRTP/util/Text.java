package org.github.sawervatoryy.mirageRTP.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Преобразование строк вида {@code &f}, {@code &#00ACFF} в Adventure-компоненты. */
public final class Text {

    private static final LegacyComponentSerializer SERIALIZER =
            LegacyComponentSerializer.builder().character('&').hexColors().build();

    private Text() {
    }

    public static Component parse(String raw) {
        return SERIALIZER.deserialize(raw == null ? "" : raw)
                .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Подставляет пары {@code ключ, значение} в плейсхолдеры вида {@code {ключ}}. */
    public static String apply(String raw, Object... kv) {
        String out = raw == null ? "" : raw;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            out = out.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return out;
    }
}
