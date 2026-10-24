import com.github.tvbox.osc.player.subtitle.ExternalSubtitle;

/** Standalone assertions against the production parser; no Android runtime or test library needed. */
public final class ExternalSubtitleTest {
    private static int checks;

    private static void equal(String expected, String actual) {
        checks++;
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected [" + expected + "], got [" + actual + "]");
        }
    }

    private static void invalid(String source) {
        checks++;
        try {
            ExternalSubtitle.parse(source, "invalid.srt");
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Accepted invalid subtitle: " + source);
    }

    public static void main(String[] args) {
        ExternalSubtitle srt = ExternalSubtitle.parse(
                "1\n00:00:01,000 --> 00:00:03,000\n甲\n\n"
                        + "2\n00:00:02,000 --> 00:00:04,000\n乙", "sample.srt");
        equal("", srt.textAt(999));
        equal("甲", srt.textAt(1000));
        equal("甲\n乙", srt.textAt(2500));
        equal("乙", srt.textAt(3000));
        equal("", srt.textAt(4000));
        equal("", srt.textAt(-1));

        ExternalSubtitle vtt = ExternalSubtitle.parse(
                "WEBVTT\n\nNOTE ignored\n00:01.000 --> 00:02.000\nnot a cue\n\n"
                        + "cue-id\n00:01.000 --> 00:02.000 align:center\n"
                        + "<v Speaker><b>Hello &amp; world</b>\n", "sample.vtt");
        equal("Hello & world", vtt.textAt(1500));

        ExternalSubtitle ass = ExternalSubtitle.parse(
                "[Script Info]\nTitle: sample\n[Events]\n"
                        + "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
                        + "Dialogue: 0,0:00:01.00,0:00:02.50,Default,,0,0,0,,{\\b1}Hello\\Nworld, too",
                "sample.ass");
        equal("Hello\nworld, too", ass.textAt(2499));
        equal("", ass.textAt(2500));

        invalid("");
        invalid("<html>not subtitles</html>");
        invalid("00:00:03,000 --> 00:00:02,000\ninvalid range");
        invalid("00:80:00,000 --> 00:81:00,000\ninvalid minutes");
        invalid("00:00:01 --> 00:00:02\ninvalid precision");
        System.out.println("ExternalSubtitle: " + checks + " checks passed");
    }
}
