package com.fongmi.quickjs.utils;

import android.text.TextUtils;

import com.fongmi.quickjs.bean.Cache;
import com.fongmi.quickjs.bean.Info;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parser {

    private static final Pattern TAG = Pattern.compile("<(/)?([a-zA-Z][a-zA-Z0-9:_-]*)((?:\\s+[^<>]*?)?)(/?)>");
    private static final Pattern STEP = Pattern.compile("\\s*(>)?\\s*([^\\s>]+)");
    private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+)))?");
    private static final Pattern ATTR_EXPR = Pattern.compile("^\\s*([\\w-]+)\\s*(?:(=|\\*=|\\^=|\\$=|~=|\\|=)\\s*(.*))?$");
    private static final Pattern INNER_TAG = Pattern.compile("<[^>]*>");
    private static final List<String> VOID = Arrays.asList("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr");

    private final Cache cache = new Cache();

    public Info getParseInfo(String text) {
        Info info = new Info();
        try {
            JsonObject object = Json.parse(text).getAsJsonObject();
            info.setUrl(Json.safeString(object, "url"));
            info.setExt(Json.safeString(object, "ext"));
            info.setMsg(Json.safeString(object, "msg"));
        } catch (Exception ignored) {
        }
        return info;
    }

    public List<String> parseDomForArray(String html, String rule) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(rule)) return result;
        String[] parts = rule.split("&&");
        String selector = parts[0].trim();
        for (int i = 1; i < parts.length - 1; i++) selector = selector + " " + parts[i].trim();
        String attr = parts.length > 1 ? parts[parts.length - 1].trim() : "";
        for (Element element : cache.getPdfa(html).select(selector)) result.add(extract(element, attr));
        return result;
    }

    public List<String> parseDomForList(String html, String rule, String st, String rt, String url) {
        List<String> result = new ArrayList<>();
        if (TextUtils.isEmpty(rule)) return result;
        String selector = rule.replace("&&", " ");
        for (Element element : cache.getPdfh(html).select(selector)) result.add(extract(element, spec(st)));
        return result;
    }

    public String parseDomForUrl(String html, String rule, String url) {
        if (TextUtils.isEmpty(rule)) return "";
        String selector = rule;
        String attr = "";
        int index = rule.indexOf(';');
        if (index != -1) {
            selector = rule.substring(0, index);
            attr = rule.substring(rule.lastIndexOf(';') + 1);
        }
        if (selector.contains("&&")) {
            String[] parts = selector.split("&&");
            selector = parts[0].trim();
            for (int i = 1; i < parts.length - 1; i++) selector = selector + " " + parts[i].trim();
            attr = parts[parts.length - 1].trim();
        }
        List<Element> elements = cache.getPdfh(html).select(selector);
        if (elements.isEmpty()) return "";
        return extract(elements.get(0), spec(attr));
    }

    public String parseHikerToJq(String rule, boolean first) {
        return rule;
    }

    private List<Element> parseOneRule(Element root, String rule, List<Element> out) {
        List<Element> result = out == null ? new ArrayList<Element>() : out;
        if (root == null || TextUtils.isEmpty(rule)) return result;
        result.addAll(root.select(rule));
        return result;
    }

    private static String spec(String attr) {
        if (TextUtils.isEmpty(attr)) return "";
        int index = attr.lastIndexOf("&&");
        return index == -1 ? attr.trim() : attr.substring(index + 2).trim();
    }

    private static String extract(Element element, String spec) {
        if (element == null) return "";
        if (TextUtils.isEmpty(spec)) return element.outerHtml();
        String value = spec.trim();
        if (value.startsWith("attr=")) value = value.substring(5).trim();
        if ("text".equalsIgnoreCase(value)) return element.text();
        if ("html".equalsIgnoreCase(value)) return element.html();
        if ("outerHtml".equalsIgnoreCase(value)) return element.outerHtml();
        String attr = element.attr(value);
        return attr == null ? "" : attr;
    }

    public static Element parseDom(String html) {
        String source = clean(html);
        Element root = new Element(source, "#root");
        root.start = 0;
        root.innerStart = 0;
        root.innerEnd = source.length();
        root.end = source.length();
        Deque<Element> stack = new ArrayDeque<>();
        stack.push(root);
        Matcher matcher = TAG.matcher(source);
        while (matcher.find()) {
            String name = matcher.group(2).toLowerCase(Locale.ROOT);
            if ("/".equals(matcher.group(1))) {
                Element target = null;
                for (Element element : stack) if (element != root && element.name.equals(name)) {
                    target = element;
                    break;
                }
                if (target == null) continue;
                while (stack.size() > 1 && stack.peek() != target) stack.pop().close(matcher.start(), matcher.start());
                stack.pop().close(matcher.start(), matcher.end());
            } else {
                Element element = new Element(source, name);
                element.start = matcher.start();
                element.innerStart = matcher.end();
                parseAttr(element, matcher.group(3));
                Element parent = stack.peek();
                element.parent = parent;
                parent.children.add(element);
                if ("/".equals(matcher.group(4)) || VOID.contains(name)) element.close(matcher.end(), matcher.end());
                else stack.push(element);
            }
        }
        while (stack.size() > 1) stack.pop().close(source.length(), source.length());
        return root;
    }

    private static String clean(String html) {
        String source = html == null ? "" : html;
        source = source.replaceAll("(?is)<!--.*?-->", "");
        source = source.replaceAll("(?is)<script[^>]*>.*?</script>", "");
        source = source.replaceAll("(?is)<style[^>]*>.*?</style>", "");
        return source;
    }

    private static void parseAttr(Element element, String text) {
        if (TextUtils.isEmpty(text)) return;
        Matcher matcher = ATTRIBUTE.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(2) != null ? matcher.group(2) : matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            element.attrs.put(matcher.group(1).toLowerCase(Locale.ROOT), value == null ? "" : value);
        }
    }

    private static boolean matches(Element element, String simple) {
        if (TextUtils.isEmpty(simple) || "*".equals(simple)) return true;
        int index = 0;
        StringBuilder tag = new StringBuilder();
        while (index < simple.length() && isNameChar(simple.charAt(index))) {
            tag.append(simple.charAt(index));
            index++;
        }
        String tagName = tag.toString();
        if (tagName.length() > 0 && !"*".equals(tagName) && !element.name.equals(tagName.toLowerCase(Locale.ROOT))) return false;
        while (index < simple.length()) {
            char c = simple.charAt(index);
            if (c == '.') {
                int next = nextIndex(simple, index + 1);
                if (!hasClass(element, simple.substring(index + 1, next))) return false;
                index = next;
            } else if (c == '#') {
                int next = nextIndex(simple, index + 1);
                String id = element.attr("id");
                if (id == null || !id.equals(simple.substring(index + 1, next))) return false;
                index = next;
            } else if (c == '[') {
                int end = simple.indexOf(']', index);
                if (end == -1) return false;
                if (!matchAttr(element, simple.substring(index + 1, end))) return false;
                index = end + 1;
            } else {
                index++;
            }
        }
        return true;
    }

    private static boolean matchAttr(Element element, String expr) {
        Matcher matcher = ATTR_EXPR.matcher(expr);
        if (!matcher.find()) return false;
        String name = matcher.group(1);
        String op = matcher.group(2) == null ? "" : matcher.group(2);
        String value = unquote(matcher.group(3));
        String actual = element.attr(name);
        if (actual == null) return false;
        if (op.isEmpty()) return true;
        if (value == null) value = "";
        if ("=".equals(op)) return value.equals(actual);
        if ("*=".equals(op)) return actual.contains(value);
        if ("^=".equals(op)) return actual.startsWith(value);
        if ("$=".equals(op)) return actual.endsWith(value);
        if ("|=".equals(op)) return actual.equals(value) || actual.startsWith(value + "-");
        if ("~=".equals(op)) for (String part : actual.split("\\s+")) if (part.equals(value)) return true;
        return false;
    }

    private static String unquote(String value) {
        if (value == null) return null;
        String text = value.trim();
        if (text.length() >= 2 && (text.charAt(0) == '"' || text.charAt(0) == '\'')) text = text.substring(1, text.length() - 1);
        return text;
    }

    private static boolean hasClass(Element element, String cls) {
        String value = element.attr("class");
        if (value == null || cls.isEmpty()) return false;
        for (String part : value.split("\\s+")) if (part.equals(cls)) return true;
        return false;
    }

    private static int nextIndex(String text, int from) {
        int index = from;
        while (index < text.length() && isNameChar(text.charAt(index))) index++;
        return index;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '*';
    }

    public static class Element {

        private final String source;
        private final String name;
        private final Map<String, String> attrs = new HashMap<>();
        private final List<Element> children = new ArrayList<>();
        private Element parent;
        private int start;
        private int innerStart;
        private int innerEnd;
        private int end;

        private Element(String source, String name) {
            this.source = source;
            this.name = name;
        }

        private void close(int innerEnd, int end) {
            this.innerEnd = innerEnd;
            this.end = end;
        }

        public String tag() {
            return name;
        }

        public Map<String, String> attrs() {
            return attrs;
        }

        public String attr(String key) {
            return key == null ? null : attrs.get(key.toLowerCase(Locale.ROOT));
        }

        public Element parent() {
            return parent;
        }

        public List<Element> children() {
            return children;
        }

        public String html() {
            if (source == null || innerEnd < innerStart) return "";
            return source.substring(innerStart, Math.min(innerEnd, source.length()));
        }

        public String outerHtml() {
            if (source == null || end < start) return "";
            return source.substring(start, Math.min(end, source.length()));
        }

        public String text() {
            String text = INNER_TAG.matcher(html()).replaceAll("");
            return text.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").trim();
        }

        public List<Element> select(String selector) {
            List<Element> result = new ArrayList<>();
            if (TextUtils.isEmpty(selector)) return result;
            for (String group : selector.split(",")) {
                String path = group.trim();
                if (path.isEmpty()) continue;
                List<Element> current = new ArrayList<>();
                current.add(this);
                Matcher matcher = STEP.matcher(path);
                while (matcher.find() && !current.isEmpty()) {
                    boolean child = ">".equals(matcher.group(1));
                    String simple = matcher.group(2);
                    List<Element> next = new ArrayList<>();
                    for (Element base : current) {
                        List<Element> scope = new ArrayList<>();
                        if (child) scope.addAll(base.children);
                        else collect(base, scope);
                        for (Element candidate : scope) if (matches(candidate, simple)) next.add(candidate);
                    }
                    current = next;
                }
                result.addAll(current);
            }
            return result;
        }

        private static void collect(Element element, List<Element> out) {
            for (Element child : element.children) {
                out.add(child);
                collect(child, out);
            }
        }
    }
}
