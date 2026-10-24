package com.github.tvbox.osc.player;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.player.subtitle.ExternalSubtitle;
import com.github.tvbox.osc.player.subtitle.ExternalSubtitleLoader;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.SeniorMode;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.studio.osc.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.MediaTrack;
import xyz.doikki.videoplayer.player.VideoView;

/** Owns subtitle/track choices for one playback page; track indices never survive an episode. */
public final class PlaybackOptionsManager {
    public static final int REQUEST_SUBTITLE = 7012;
    private static final int AUDIO = 1;
    private static final int SUBTITLE = 2;
    private final Activity activity;
    private final VideoView video;
    private final TextView subtitleView;
    private final JSONObject config;
    private final Runnable persist;
    private final Runnable dialogOpened;
    private final Runnable dialogClosed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExternalSubtitleLoader loader;
    private final List<Source> sources = new ArrayList<>();
    private final Set<String> failedSources = new HashSet<>();
    private Map<String, String> headers = Collections.emptyMap();
    private AbstractPlayer boundPlayer;
    private ExternalSubtitle externalSubtitle;
    private SelectDialog<String> dialog;
    private String nativeText = "";
    private String activeSource = "";
    private boolean active;
    private boolean paused;
    private boolean released;
    private boolean audioApplied;
    private boolean subtitleApplied;
    private boolean loadingExternal;
    private boolean audioFallbackNotified;
    private boolean subtitleFallbackNotified;
    private boolean manualSubtitle;
    private long session;
    private long nextMetadataCheck;
    private long readySince;
    private long localRequestSession = -1;

    public PlaybackOptionsManager(Activity activity, VideoView video, TextView subtitleView,
                                  JSONObject config, Runnable persist, Runnable dialogOpened,
                                  Runnable dialogClosed) {
        this.activity = activity;
        this.video = video;
        this.subtitleView = subtitleView;
        this.config = config;
        this.persist = persist;
        this.dialogOpened = dialogOpened;
        this.dialogClosed = dialogClosed;
        loader = new ExternalSubtitleLoader(activity);
        applyTextSize();
    }

    public void setSources(JSONObject info) {
        sources.clear();
        if (info == null) return;
        addSource(info.optString("subt"), "站点字幕", "");
        Object value = info.opt("subs");
        JSONArray array = value instanceof JSONArray ? (JSONArray) value : null;
        if (array == null && value instanceof String) {
            try { array = new JSONArray((String) value); } catch (JSONException ignored) { }
        }
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof JSONObject) {
                    JSONObject sub = (JSONObject) item;
                    String url = sub.optString("url", sub.optString("uri", ""));
                    String name = sub.optString("name", sub.optString("label", "站点字幕 " + (i + 1)));
                    addSource(url, name, sub.optString("lang", sub.optString("language", "")));
                } else if (item instanceof String) {
                    addSource((String) item, "站点字幕 " + (i + 1), "");
                }
            }
        }
    }

    private void addSource(String url, String name, String language) {
        if (TextUtils.isEmpty(url) || "null".equals(url)) return;
        for (Source source : sources) if (source.url.equals(url)) return;
        sources.add(new Source(url, name, normalizeLanguage(language)));
    }

    public void beginSession(Map<String, String> playbackHeaders) {
        stopSession();
        if (released) return;
        headers = playbackHeaders == null ? Collections.emptyMap() : new HashMap<>(playbackHeaders);
        active = true;
        audioApplied = false;
        subtitleApplied = false;
        manualSubtitle = false;
        audioFallbackNotified = false;
        subtitleFallbackNotified = false;
        nextMetadataCheck = 0;
        readySince = 0;
        applyTextSize();
        tick.run();
    }

    public void stopSession() {
        active = false;
        session++;
        localRequestSession = -1;
        if (dialog != null) dialog.dismiss();
        handler.removeCallbacks(tick);
        loader.cancel();
        if (boundPlayer != null) boundPlayer.setSubtitleTextListener(null);
        boundPlayer = null;
        externalSubtitle = null;
        loadingExternal = false;
        nativeText = "";
        activeSource = "";
        failedSources.clear();
        renderText("");
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!active || paused || released) return;
            AbstractPlayer player = video.getMediaPlayer();
            if (player != boundPlayer) bind(player);
            if (player != null) {
                applyPreferences(player);
                if (externalSubtitle != null) {
                    renderText(externalSubtitle.textAt(Math.max(0, video.getCurrentPosition())));
                } else if (!loadingExternal) {
                    renderText(config.optBoolean("subtitle_off", false) ? "" : nativeText);
                }
            }
            handler.postDelayed(this, 250L);
        }
    };

    private void bind(AbstractPlayer player) {
        if (boundPlayer != null) boundPlayer.setSubtitleTextListener(null);
        boundPlayer = player;
        nativeText = "";
        if (player == null) return;
        audioApplied = false;
        subtitleApplied = false;
        nextMetadataCheck = 0;
        final long token = session;
        player.setSubtitleTextListener(text -> handler.post(() -> {
            if (!released && active && token == session && player == boundPlayer) {
                nativeText = text == null ? "" : text;
                if (externalSubtitle == null && !loadingExternal && !paused) {
                    renderText(config.optBoolean("subtitle_off", false) ? "" : nativeText);
                }
            }
        }));
        if (externalSubtitle != null || loadingExternal || config.optBoolean("subtitle_off", false)) {
            player.disableSubtitleTrack();
        }
    }

    private void applyPreferences(AbstractPlayer player) {
        if (audioApplied && (manualSubtitle || subtitleApplied)) return;
        long now = android.os.SystemClock.uptimeMillis();
        int playState = video.getCurrentPlayState();
        if (readySince == 0 && (playState == VideoView.STATE_PREPARED
                || playState == VideoView.STATE_PLAYING || playState == VideoView.STATE_PAUSED
                || playState == VideoView.STATE_BUFFERED)) readySince = now;
        boolean metadataWaitExpired = readySince != 0 && now - readySince >= 3000L;
        if (now < nextMetadataCheck) return;
        nextMetadataCheck = now + 500L;
        if (normalizeLanguage(config.optString("audio_language")).isEmpty()) audioApplied = true;
        List<MediaTrack> audio = tracks(player, AUDIO);
        List<MediaTrack> text = tracks(player, SUBTITLE);
        if (!audioApplied && (!audio.isEmpty() || metadataWaitExpired)) {
            String language = normalizeLanguage(config.optString("audio_language"));
            MediaTrack match = findLanguage(audio, language);
            if (match != null) {
                if (player.selectMediaTrack(match)) audioApplied = true;
            } else {
                // An empty inventory can arrive late: notify once, but keep matching
                // if the decoder subsequently publishes its language metadata.
                audioApplied = !audio.isEmpty();
                if (!language.isEmpty() && !audioFallbackNotified) {
                    audioFallbackNotified = true;
                    toast("本集没有偏好的音轨，已使用默认音轨");
                }
            }
        }
        if (manualSubtitle || subtitleApplied) return;
        if (config.optBoolean("subtitle_off", false)) {
            player.disableSubtitleTrack();
            subtitleApplied = true;
            return;
        }
        String language = normalizeLanguage(config.optString("subtitle_language"));
        Source source = null;
        if (language.isEmpty()) {
            source = defaultSource();
        } else {
            for (Source candidate : sources) {
                if (!failedSources.contains(candidate.url) && languageMatches(language, candidate.language)) {
                    source = candidate;
                    break;
                }
            }
        }
        if (source != null) {
            subtitleApplied = true;
            loadSource(source, false);
            return;
        }
        MediaTrack match = findLanguage(text, language);
        if (match != null) {
            subtitleApplied = player.selectMediaTrack(match);
        } else if (language.isEmpty()) {
            // IJK may expose subtitle tracks without selecting one by default.
            // An absent inventory is retried, including after a failed external load.
            subtitleApplied = selectDefaultSubtitle(player, text);
        } else if (!text.isEmpty() || metadataWaitExpired) {
            if (!subtitleFallbackNotified) {
                subtitleFallbackNotified = true;
                toast("本集没有偏好的字幕，已使用默认字幕");
            }
            Source fallback = defaultSource();
            if (fallback != null) {
                subtitleApplied = true;
                loadSource(fallback, false);
            } else {
                subtitleApplied = selectDefaultSubtitle(player, text);
            }
        }
    }

    public void showAudioTracks() {
        AbstractPlayer player = video.getMediaPlayer();
        if (player == null || !player.supportsMediaTracks()) {
            toast("当前内核不支持音轨选择，请切换 IJK 或 Exo 内核");
            return;
        }
        List<MediaTrack> audio = tracks(player, AUDIO);
        if (audio.isEmpty()) { toast("音轨尚未就绪，或此视频没有可选音轨"); return; }
        List<String> names = new ArrayList<>();
        for (MediaTrack track : audio) names.add(trackName(track) + (track.selected ? "（当前）" : ""));
        showMenu("音轨 · 按语言记忆", names, index -> {
            if (video.getMediaPlayer() != player) { toast("播放内容已变化，请重新选择"); return; }
            MediaTrack selected = audio.get(index);
            if (!player.selectMediaTrack(selected)) { toast("此音轨切换失败"); return; }
            put("audio_language", normalizeLanguage(selected.language));
            audioApplied = true;
            persist.run();
            if (normalizeLanguage(selected.language).isEmpty()) toast("此音轨未标记语言，仅本集使用");
        });
    }

    public void showSubtitles() {
        AbstractPlayer player = video.getMediaPlayer();
        List<MediaTrack> embedded = player == null ? Collections.emptyList() : tracks(player, SUBTITLE);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("关闭字幕" + (config.optBoolean("subtitle_off", false) ? "（当前）" : ""));
        actions.add(() -> {
            manualSubtitle = true;
            clearExternal();
            nativeText = "";
            if (video.getMediaPlayer() != null) video.getMediaPlayer().disableSubtitleTrack();
            put("subtitle_off", true);
            persist.run();
            renderText("");
        });
        labels.add("自动选择字幕");
        actions.add(() -> {
            manualSubtitle = false;
            clearExternal();
            put("subtitle_off", false);
            put("subtitle_language", "");
            subtitleApplied = false;
            nextMetadataCheck = 0;
            if (video.getMediaPlayer() != null) {
                List<MediaTrack> current = tracks(video.getMediaPlayer(), SUBTITLE);
                if (sources.isEmpty() && !current.isEmpty()) video.getMediaPlayer().selectMediaTrack(current.get(0));
            }
            persist.run();
        });
        for (MediaTrack track : embedded) {
            labels.add("内嵌 · " + trackName(track) + (track.selected && activeSource.isEmpty() ? "（当前）" : ""));
            actions.add(() -> {
                if (player != video.getMediaPlayer() || !player.selectMediaTrack(track)) {
                    toast("字幕轨道已变化或切换失败，请重试");
                    return;
                }
                manualSubtitle = true;
                clearExternal();
                put("subtitle_off", false);
                put("subtitle_language", normalizeLanguage(track.language));
                persist.run();
                if (normalizeLanguage(track.language).isEmpty()) toast("此字幕未标记语言，仅本集使用");
            });
        }
        for (Source source : sources) {
            labels.add(source.name + (source.url.equals(activeSource) ? "（当前）" : ""));
            actions.add(() -> {
                manualSubtitle = true;
                put("subtitle_off", false);
                put("subtitle_language", source.language);
                persist.run();
                loadSource(source, true);
            });
        }
        labels.add("选择本地字幕文件…");
        actions.add(this::chooseLocalSubtitle);
        labels.add("字幕字号 · " + config.optInt("subtitle_size", defaultTextSize()));
        actions.add(this::showTextSizes);
        if (embedded.isEmpty() && sources.isEmpty()) {
            labels.add(player != null && !player.supportsMediaTracks()
                    ? "当前内核不支持内嵌字幕（可选择本地文件）"
                    : "当前没有内嵌或站点字幕（可选择本地文件）");
            actions.add(() -> toast("可选择 SRT、VTT、ASS 或 SSA 文件"));
        }
        showMenu("字幕", labels, index -> actions.get(index).run());
    }

    private void chooseLocalSubtitle() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            localRequestSession = session;
            activity.startActivityForResult(intent, REQUEST_SUBTITLE);
        } catch (ActivityNotFoundException | SecurityException ex) {
            localRequestSession = -1;
            toast("设备没有可用的文件选择器，请先安装文件管理器");
        }
    }

    public void chooseLocalSubtitleResult(Uri uri) {
        if (uri == null || released || !active) return;
        if (localRequestSession != session) {
            toast("播放内容已变化，请重新选择字幕文件");
            return;
        }
        localRequestSession = -1;
        manualSubtitle = true;
        put("subtitle_off", false);
        // A chosen file belongs to this episode, not to future episodes.
        put("subtitle_language", "");
        persist.run();
        loadSource(new Source(uri.toString(), "本地字幕", ""), true);
    }

    private void loadSource(Source source, boolean userInitiated) {
        if (userInitiated) failedSources.remove(source.url);
        final long token = session;
        loadingExternal = true;
        externalSubtitle = null;
        nativeText = "";
        activeSource = source.url;
        if (boundPlayer != null) boundPlayer.disableSubtitleTrack();
        renderText("");
        loader.load(source.url, headers, new ExternalSubtitleLoader.Callback() {
            @Override public void onLoaded(ExternalSubtitle subtitle) {
                if (released || !active || token != session) return;
                loadingExternal = false;
                externalSubtitle = subtitle;
                if (boundPlayer != null) boundPlayer.disableSubtitleTrack();
                if (userInitiated) toast("字幕已加载");
            }
            @Override public void onError(String message) {
                if (released || !active || token != session) return;
                loadingExternal = false;
                externalSubtitle = null;
                activeSource = "";
                failedSources.add(source.url);
                manualSubtitle = false;
                subtitleApplied = false;
                subtitleFallbackNotified = true;
                nextMetadataCheck = 0;
                toast(message);
                // Re-enter preference matching when metadata becomes available, while
                // excluding this failed URL for the rest of this playback session.
                if (boundPlayer != null) applyPreferences(boundPlayer);
            }
        });
    }

    private void clearExternal() {
        loader.cancel();
        loadingExternal = false;
        externalSubtitle = null;
        activeSource = "";
        renderText("");
    }

    private Source defaultSource() {
        for (Source source : sources) if (!failedSources.contains(source.url)) return source;
        return null;
    }

    private boolean selectDefaultSubtitle(AbstractPlayer player, List<MediaTrack> embedded) {
        for (MediaTrack track : embedded) if (track.selected) return true;
        return !embedded.isEmpty() && player.selectMediaTrack(embedded.get(0));
    }

    private void showTextSizes() {
        List<String> labels = new ArrayList<>();
        int[] sizes = {18, 22, 26, 30, 34};
        for (int size : sizes) labels.add(size + (size == config.optInt("subtitle_size", defaultTextSize()) ? "（当前）" : ""));
        showMenu("字幕字号", labels, index -> {
            put("subtitle_size", sizes[index]);
            applyTextSize();
            persist.run();
        });
    }

    private interface Choice { void selected(int position); }

    private void showMenu(String title, List<String> labels, Choice choice) {
        if (released || activity.isFinishing() || activity.isDestroyed()) return;
        final long menuSession = session;
        if (dialog != null) dialog.dismiss();
        SelectDialog<String> shown = new SelectDialog<>(activity);
        dialog = shown;
        shown.setTip(title);
        TvRecyclerView list = shown.findViewById(R.id.list);
        // Action menus must allow activating the current choice too. The generic
        // SelectDialogAdapter intentionally ignores it, so use an action adapter.
        list.setAdapter(new RecyclerView.Adapter<ChoiceHolder>() {
            @NonNull @Override public ChoiceHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
                return new ChoiceHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_dialog_select, parent, false));
            }
            @Override public void onBindViewHolder(@NonNull ChoiceHolder holder, int position) {
                ((TextView) holder.itemView.findViewById(R.id.tvName)).setText(labels.get(position));
                holder.itemView.setOnClickListener(view -> {
                    shown.dismiss();
                    if (!released && active && menuSession == session) choice.selected(position);
                });
            }
            @Override public int getItemCount() { return labels.size(); }
        });
        shown.setOnDismissListener(ignored -> {
            if (dialog == shown) dialog = null;
            if (!released && dialogClosed != null) dialogClosed.run();
        });
        if (dialogOpened != null) dialogOpened.run();
        shown.show();
        SeniorMode.apply(shown.getWindow().getDecorView());
        list.post(() -> {
            list.setSelectedPosition(0);
            list.scrollToPosition(0);
            View first = list.getLayoutManager() == null ? null : list.getLayoutManager().findViewByPosition(0);
            if (first != null) first.requestFocus(); else list.requestFocus();
        });
    }

    private static final class ChoiceHolder extends RecyclerView.ViewHolder {
        ChoiceHolder(View item) { super(item); }
    }

    public void onPause() {
        paused = true;
        handler.removeCallbacks(tick);
        renderText("");
    }

    public void onResume() {
        paused = false;
        handler.removeCallbacks(tick);
        if (active && !released) tick.run();
    }

    public void release() {
        if (released) return;
        released = true;
        stopSession();
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
        loader.release();
        handler.removeCallbacksAndMessages(null);
    }

    private void applyTextSize() {
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                Math.max(14, Math.min(40, config.optInt("subtitle_size", defaultTextSize()))));
    }

    private static int defaultTextSize() { return SeniorMode.isEnabled() ? 26 : 22; }

    private void renderText(String text) {
        String displayed = text == null ? "" : text;
        if (!TextUtils.equals(subtitleView.getText(), displayed)) subtitleView.setText(displayed);
        subtitleView.setVisibility(displayed.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private List<MediaTrack> tracks(AbstractPlayer player, int type) {
        try {
            List<MediaTrack> result = player.getMediaTracks(type);
            return result == null ? Collections.emptyList() : result;
        } catch (RuntimeException ex) {
            return Collections.emptyList();
        }
    }

    private static MediaTrack findLanguage(List<MediaTrack> tracks, String language) {
        if (language.isEmpty()) return null;
        for (MediaTrack track : tracks) if (languageMatches(language, normalizeLanguage(track.language))) return track;
        return null;
    }

    private static boolean languageMatches(String preferred, String available) {
        return !preferred.isEmpty() && !available.isEmpty() && preferred.equals(available);
    }

    private static String normalizeLanguage(String language) {
        if (language == null) return "";
        String value = language.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (value.isEmpty() || value.equals("und") || value.equals("unknown") || value.equals("null")) return "";
        switch (value) {
            case "eng": return "en";
            case "jpn": return "ja";
            case "kor": return "ko";
            case "fra": case "fre": return "fr";
            case "deu": case "ger": return "de";
            case "chi": case "zho": case "cmn": return "zh";
            case "zh-cn": case "zh-sg": return "zh-hans";
            case "zh-tw": case "zh-hk": return "zh-hant";
            default: return value;
        }
    }

    private static String trackName(MediaTrack track) {
        String label = TextUtils.isEmpty(track.label) ? "轨道 " + (track.index + 1) : track.label;
        String language = normalizeLanguage(track.language);
        return language.isEmpty() || label.toLowerCase(Locale.ROOT).contains(language) ? label : label + " · " + language;
    }

    private void put(String key, Object value) {
        try { config.put(key, value); } catch (JSONException ignored) { }
    }

    private void toast(String message) {
        if (!released && !activity.isFinishing()) Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }

    private static final class Source {
        final String url;
        final String name;
        final String language;
        Source(String url, String name, String language) {
            this.url = url;
            this.name = TextUtils.isEmpty(name) ? "字幕" : name;
            this.language = language;
        }
    }
}

