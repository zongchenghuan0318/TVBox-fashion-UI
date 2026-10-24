package com.github.tvbox.osc.player.subtitle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable, plain-text timeline shared by the IJK, Exo and system players. */
public final class ExternalSubtitle {
    private static final Pattern TIMING = Pattern.compile(
            "^\\s*((?:\\d+:)?\\d{2}:\\d{2}[.,]\\d{3})\\s+-->\\s+((?:\\d+:)?\\d{2}:\\d{2}[.,]\\d{3})(?:\\s+.*)?$");
    private final List<Cue> cues;
    private final long[] latestEnd;

    private ExternalSubtitle(List<Cue> source) {
        Collections.sort(source, new Comparator<Cue>() {
            @Override
            public int compare(Cue left, Cue right) {
                return Long.compare(left.start, right.start);
            }
        });
        cues = Collections.unmodifiableList(new ArrayList<>(source));
        latestEnd = new long[cues.size()];
        long max = 0;
        for (int i = 0; i < cues.size(); i++) {
            max = Math.max(max, cues.get(i).end);
            latestEnd[i] = max;
        }
    }

    public static ExternalSubtitle parse(String text, String name) throws IllegalArgumentException {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("字幕文件为空");
        }
        String normalized = text.replace("\uFEFF", "").replace("\r\n", "\n").replace('\r', '\n');
        String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        List<Cue> result;
        if (lowerName.endsWith(".ass") || lowerName.endsWith(".ssa")
                || normalized.toLowerCase(Locale.ROOT).contains("[events]")) {
            result = parseAss(normalized);
        } else {
            result = parseTimedText(normalized);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("未找到有效字幕，请选择 SRT、VTT、ASS 或 SSA 文件");
        }
        return new ExternalSubtitle(result);
    }

    /** End times are exclusive; includes overlapping cues in original chronological order. */
    public String textAt(long positionMs) {
        if (positionMs < 0) return "";
        int left = 0;
        int right = cues.size();
        while (left < right) {
            int mid = (left + right) >>> 1;
            if (cues.get(mid).start <= positionMs) left = mid + 1;
            else right = mid;
        }
        List<String> visible = new ArrayList<>();
        for (int i = left - 1; i >= 0 && latestEnd[i] > positionMs; i--) {
            Cue cue = cues.get(i);
            if (positionMs < cue.end) visible.add(cue.text);
        }
        StringBuilder result = new StringBuilder();
        for (int i = visible.size() - 1; i >= 0; i--) {
            if (result.length() != 0) result.append('\n');
            result.append(visible.get(i));
        }
        return result.toString();
    }

    private static List<Cue> parseTimedText(String text) {
        List<Cue> result = new ArrayList<>();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.equals("NOTE") || line.startsWith("NOTE ") || line.equals("STYLE") || line.equals("REGION")) {
                while (i + 1 < lines.length && !lines[i + 1].trim().isEmpty()) i++;
                continue;
            }
            Matcher timing = TIMING.matcher(line);
            if (!timing.matches()) {
                if (line.contains("-->")) throw new IllegalArgumentException("字幕时间格式无效");
                continue;
            }
            long start = timestamp(timing.group(1), false);
            long end = timestamp(timing.group(2), false);
            StringBuilder body = new StringBuilder();
            while (i + 1 < lines.length && !lines[i + 1].trim().isEmpty()) {
                if (body.length() != 0) body.append('\n');
                body.append(lines[++i]);
            }
            addCue(result, start, end, plainText(body.toString()));
        }
        return result;
    }

    private static List<Cue> parseAss(String text) {
        List<Cue> result = new ArrayList<>();
        boolean inEvents = false;
        Map<String, Integer> columns = new HashMap<>();
        String[] standard = {"layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text"};
        for (int i = 0; i < standard.length; i++) columns.put(standard[i], i);
        int columnCount = standard.length;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            String lower = line.toLowerCase(Locale.ROOT);
            if (line.startsWith("[") && line.endsWith("]")) {
                inEvents = lower.equals("[events]");
                continue;
            }
            if (!inEvents) continue;
            if (lower.startsWith("format:")) {
                String[] names = line.substring(line.indexOf(':') + 1).split(",");
                columns.clear();
                columnCount = names.length;
                for (int i = 0; i < names.length; i++) columns.put(names[i].trim().toLowerCase(Locale.ROOT), i);
            } else if (lower.startsWith("dialogue:")) {
                if (!columns.containsKey("start") || !columns.containsKey("end") || !columns.containsKey("text")
                        || columns.get("text") != columnCount - 1) {
                    throw new IllegalArgumentException("不支持此 ASS 字幕字段格式");
                }
                String[] values = line.substring(line.indexOf(':') + 1).split(",", columnCount);
                if (values.length != columnCount) throw new IllegalArgumentException("ASS 字幕内容不完整");
                String body = values[columns.get("text")].replaceAll("\\{[^}]*}", "")
                        .replace("\\N", "\n").replace("\\n", "\n").replace("\\h", " ");
                addCue(result, timestamp(values[columns.get("start")].trim(), true),
                        timestamp(values[columns.get("end")].trim(), true), plainText(body));
            }
        }
        return result;
    }

    private static long timestamp(String value, boolean ass) {
        try {
            String[] parts = value.replace(',', '.').split(":");
            if (parts.length != 2 && parts.length != 3) throw new NumberFormatException();
            long hours = parts.length == 3 ? Long.parseLong(parts[0]) : 0;
            int minutes = Integer.parseInt(parts[parts.length - 2]);
            String[] secondParts = parts[parts.length - 1].split("\\.");
            if (secondParts.length != 2 || secondParts[1].length() != (ass ? 2 : 3)) throw new NumberFormatException();
            int seconds = Integer.parseInt(secondParts[0]);
            int fraction = Integer.parseInt(secondParts[1]);
            if (hours < 0 || hours > 9999 || minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59
                    || fraction < 0) throw new NumberFormatException();
            return ((hours * 60 + minutes) * 60 + seconds) * 1000 + fraction * (ass ? 10 : 1);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("字幕时间格式无效");
        }
    }

    private static void addCue(List<Cue> result, long start, long end, String text) {
        if (end <= start) throw new IllegalArgumentException("字幕结束时间必须晚于开始时间");
        if (!text.isEmpty()) result.add(new Cue(start, end, text));
    }

    private static String plainText(String text) {
        return text.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&amp;", "&").trim();
    }

    private static final class Cue {
        final long start;
        final long end;
        final String text;

        Cue(long start, long end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }
}
