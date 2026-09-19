package com.lumi.chat.chat;

/** Понимание запроса: маршрутизация в действия (арты / персонаж / серия / обычный чат). */
public final class IntentRouter {
    private IntentRouter() {}

    public static final String T_CHAT = "chat";
    public static final String T_ART = "art";
    public static final String T_CHAR = "char";
    public static final String T_CAST = "cast";
    public static final String T_SCHEDULE = "schedule";

    public static class Intent {
        public String type = T_CHAT;
        public String query = "";
        public String anime = "";
    }

    private static final String[] ART_NOUNS = {
            "арт", "арте", "арты", "артов", "артом", "картинк", "пикч", "фото", "фотк", "фотограф",
            "изображени", "обои", "wallpaper", "постер", "вайфу", "waifu", "neko", "неко", "китсуне", "kitsune"
    };
    private static final String[] ART_VERBS = {
            "нарисуй", "нарисовать", "сгенерируй", "сгенерь", "сделай арт", "сделай картинку",
            "найди", "покажи", "дай", "скинь", "пришли", "хочу", "принеси", "imagine", "draw"
    };
    private static final String[] CHAR_HINTS = {
            "кто такая", "кто такой", "расскажи о персонаже", "расскажи про персонажа",
            "расскажи о", "расскажи про", "биография", "био ", "персонаж", "аниме девочка", "героиня", "герой"
    };
    private static final String[] SCHED_HINTS = {
            "когда новая серия", "когда выйдет", "когда выходит", "новая серия", "новые серии",
            "следующая серия", "расписание", "вышла серия", "серия выйдет", "когда серия"
    };
    private static final String[] STOP = {
            "найди", "покажи", "дай", "скинь", "пришли", "пожалуйста", "плиз", "мне", "срочно",
            "хочу", "хочется", "нарисуй", "сгенерируй", "сгенерь", "сделай", "принеси", "еще", "ещё",
            "картинку", "картинки", "картинок", "фотки", "фоток", "фото", "арты", "артов", "арт",
            "обои", "wallpaper", "изображения", "изображение", "пикчи", "пикч", "постеры", "постер",
            "в", "во", "из", "с", "со", "про", "для", "и", "на", "аниме", "anime", "персонажа", "персонаж",
            "кто", "такая", "такой", "расскажи", "о", "об", "биография", "когда", "серия", "серии",
            "новая", "новые", "выйдет", "выходит", "расписание", "girl", "boy", "wallpapers", "hd", "качество"
    };

    /** Главный роутер. lastType/lastQuery — для «ещё». */
    public static Intent route(String text, String lastType, String lastQuery) {
        String norm = normalize(text);

        if (norm.matches("(еще|ещё|давай еще|давай ещё|more|еще раз|ещё раз|еще давай|давай еще раз)")) {
            Intent i = new Intent();
            i.type = (lastType == null || T_CHAT.equals(lastType)) ? T_ART : lastType;
            i.query = lastQuery == null ? "" : lastQuery;
            return i;
        }

        if (norm.contains("запомни") || norm.contains("меня зовут")) {
            return chatIntent(); // обработает чат, а память сохранит фрагмент
        }

        if (containsAny(norm, SCHED_HINTS)) {
            Intent i = new Intent();
            i.type = T_SCHEDULE;
            i.query = cleanQuery(norm, STOP);
            return i;
        }

        boolean artNoun = containsAny(norm, ART_NOUNS);
        boolean artVerb = containsAny(norm, ART_VERBS);
        if (artNoun || (artVerb && norm.length() < 80)) {
            Intent i = new Intent();
            i.type = T_ART;
            i.query = cleanQuery(norm, STOP);
            return i;
        }

        if (containsAny(norm, CHAR_HINTS)) {
            Intent i = new Intent();
            i.type = T_CHAR;
            i.query = cleanQuery(norm, STOP);
            return i;
        }

        if (norm.startsWith("персонажи из") || norm.contains("все персонажи")) {
            Intent i = new Intent();
            i.type = T_CAST;
            i.query = cleanQuery(norm.replace("персонажи из", "").replace("все персонажи", ""), STOP);
            return i;
        }

        return chatIntent();
    }

    private static Intent chatIntent() {
        Intent i = new Intent();
        i.type = T_CHAT;
        return i;
    }

    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replace('ё', 'е').replaceAll("[!\\?\\.,;:()\"«»…]+", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean containsAny(String hay, String[] needles) {
        for (String n : needles) if (hay.contains(n)) return true;
        return false;
    }

    /** Чистит служебные слова, оставляя суть запроса. */
    public static String cleanQuery(String norm, String[] stop) {
        String[] words = norm.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            boolean isStop = false;
            for (String s : stop) {
                if (w.equals(s) || (w.length() > 3 && w.startsWith(s) && s.length() > 3)) { isStop = true; break; }
            }
            if (!isStop && !w.trim().isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(w.trim());
            }
        }
        return sb.toString().trim();
    }
}
