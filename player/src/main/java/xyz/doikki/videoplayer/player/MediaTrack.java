package xyz.doikki.videoplayer.player;

/** A selectable track. The index is valid only for the current player and media. */
public final class MediaTrack {
    public static final int TYPE_AUDIO = 1;
    public static final int TYPE_SUBTITLE = 2;

    public final int type;
    public final int index;
    public final String language;
    public final String label;
    public final boolean selected;

    public MediaTrack(int type, int index, String language, String label, boolean selected) {
        this.type = type;
        this.index = index;
        this.language = language == null ? "und" : language;
        this.label = label == null ? "" : label;
        this.selected = selected;
    }
}
