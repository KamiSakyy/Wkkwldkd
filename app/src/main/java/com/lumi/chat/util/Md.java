package com.lumi.chat.util;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Мини-разметка: **жирный**, `код`, ссылки. */
public final class Md {
    private static final Pattern P = Pattern.compile("(\\*\\*(.+?)\\*\\*)|(`([^`\\n]+)`)|(https?://[^\\s)\\]]+)", Pattern.DOTALL);

    private Md() {}

    public static CharSequence toSpan(String raw) {
        String s = raw == null ? "" : raw;
        SpannableStringBuilder sb = new SpannableStringBuilder();
        Matcher m = P.matcher(s);
        int last = 0;
        while (m.find()) {
            sb.append(s, last, m.start());
            if (m.group(2) != null) {
                int st = sb.length();
                sb.append(m.group(2));
                sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(4) != null) {
                int st = sb.length();
                sb.append(m.group(4));
                sb.setSpan(new TypefaceSpan("monospace"), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new BackgroundColorSpan(0x33FFFFFF), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                int st = sb.length();
                sb.append(m.group());
                sb.setSpan(new URLSpan(m.group()), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            last = m.end();
        }
        sb.append(s, last, s.length());
        return sb;
    }

    /** Чистый текст для TTS/копирования. */
    public static String plain(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("(?s)<think>.*?</think>", "")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("`([^`\\n]+)`", "$1")
                .replaceAll("https?://[^\\s)\\]]+", "ссылка");
    }
}
