package xyz.doikki.videoplayer.exo;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.view.Surface;
import android.view.SurfaceHolder;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsCollector;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.util.Clock;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.video.VideoSize;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.MediaTrack;
import xyz.doikki.videoplayer.player.VideoViewManager;


public class ExoMediaPlayer extends AbstractPlayer implements Player.Listener {

    private static final int MIN_BUFFER_MS = 2_000;
    private static final int MAX_BUFFER_MS = 30_000;
    private static final int BUFFER_FOR_PLAYBACK_MS = 1_500;
    private static final int BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_500;

    protected Context mAppContext;
    protected SimpleExoPlayer mInternalPlayer;
    protected MediaSource mMediaSource;
    protected ExoMediaSourceHelper mMediaSourceHelper;

    private PlaybackParameters mSpeedPlaybackParameters;

    private boolean mIsPreparing;

    private LoadControl mLoadControl;
    private RenderersFactory mRenderersFactory;
    private TrackSelector mTrackSelector;

    public ExoMediaPlayer(Context context) {
        mAppContext = context.getApplicationContext();
        mMediaSourceHelper = ExoMediaSourceHelper.getInstance(context);
    }

    @Override
    public void initPlayer() {
        if (mLoadControl == null) {
            mLoadControl = new DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                            MIN_BUFFER_MS,
                            MAX_BUFFER_MS,
                            BUFFER_FOR_PLAYBACK_MS,
                            BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .createDefaultLoadControl();
        }
        mInternalPlayer = new SimpleExoPlayer.Builder(
                mAppContext,
                mRenderersFactory == null ? mRenderersFactory = new DefaultRenderersFactory(mAppContext) : mRenderersFactory,
                mTrackSelector == null ? mTrackSelector = new DefaultTrackSelector(mAppContext) : mTrackSelector,
                new DefaultMediaSourceFactory(mAppContext),
                mLoadControl,
                DefaultBandwidthMeter.getSingletonInstance(mAppContext),
                new AnalyticsCollector(Clock.DEFAULT))
                .build();
        setOptions();

        //播放器日志
        if (VideoViewManager.getConfig().mIsEnableLog && mTrackSelector instanceof MappingTrackSelector) {
            mInternalPlayer.addAnalyticsListener(new EventLogger((MappingTrackSelector) mTrackSelector, "ExoPlayer"));
        }

        mInternalPlayer.addListener(this);
    }

    public void setTrackSelector(TrackSelector trackSelector) {
        mTrackSelector = trackSelector;
    }

    @Override
    public boolean supportsMediaTracks() {
        return mInternalPlayer != null && mTrackSelector instanceof DefaultTrackSelector;
    }

    @Override
    public List<MediaTrack> getMediaTracks(int type) {
        List<MediaTrack> result = new ArrayList<>();
        if (!supportsMediaTracks() || (type != MediaTrack.TYPE_AUDIO && type != MediaTrack.TYPE_SUBTITLE)) {
            return result;
        }
        MappingTrackSelector.MappedTrackInfo mapped =
                ((DefaultTrackSelector) mTrackSelector).getCurrentMappedTrackInfo();
        if (mapped == null) return result;
        int exoType = type == MediaTrack.TYPE_AUDIO ? C.TRACK_TYPE_AUDIO : C.TRACK_TYPE_TEXT;
        for (int renderer = 0; renderer < mapped.getRendererCount(); renderer++) {
            if (mapped.getRendererType(renderer) != exoType || renderer > 127) continue;
            TrackGroupArray groups = mapped.getTrackGroups(renderer);
            TrackSelection selected = mInternalPlayer.getCurrentTrackSelections().get(renderer);
            for (int group = 0; group < groups.length && group < 4096; group++) {
                TrackGroup tracks = groups.get(group);
                for (int track = 0; track < tracks.length && track < 4096; track++) {
                    if (mapped.getTrackSupport(renderer, group, track) != C.FORMAT_HANDLED) continue;
                    Format format = tracks.getFormat(track);
                    String language = format.language == null ? "und" : format.language;
                    String label = format.label;
                    if (label == null || label.trim().isEmpty()) {
                        label = (type == MediaTrack.TYPE_AUDIO ? "音轨 " : "字幕 ") + (result.size() + 1);
                    }
                    if (!"und".equals(language) && !language.isEmpty()) label += " · " + language;
                    boolean isSelected = selected != null && selected.getTrackGroup() == tracks
                            && selected.indexOf(track) >= 0;
                    // Renderer/group/track are encoded only for the currently mapped media.
                    int index = (renderer << 24) | (group << 12) | track;
                    result.add(new MediaTrack(type, index, language, label, isSelected));
                }
            }
        }
        return result;
    }

    @Override
    public boolean selectMediaTrack(MediaTrack track) {
        if (track == null || !supportsMediaTracks()) return false;
        boolean available = false;
        for (MediaTrack candidate : getMediaTracks(track.type)) {
            if (candidate.index == track.index) {
                available = true;
                break;
            }
        }
        if (!available) return false;
        int renderer = (track.index >>> 24) & 127;
        int group = (track.index >>> 12) & 4095;
        int index = track.index & 4095;
        DefaultTrackSelector selector = (DefaultTrackSelector) mTrackSelector;
        MappingTrackSelector.MappedTrackInfo mapped = selector.getCurrentMappedTrackInfo();
        if (mapped == null) return false;
        try {
            DefaultTrackSelector.ParametersBuilder parameters = selector.buildUponParameters();
            int exoType = track.type == MediaTrack.TYPE_AUDIO ? C.TRACK_TYPE_AUDIO : C.TRACK_TYPE_TEXT;
            for (int i = 0; i < mapped.getRendererCount(); i++) {
                if (mapped.getRendererType(i) == exoType) {
                    parameters.clearSelectionOverrides(i).setRendererDisabled(i, i != renderer);
                }
            }
            parameters.setSelectionOverride(renderer, mapped.getTrackGroups(renderer),
                    new DefaultTrackSelector.SelectionOverride(group, index));
            selector.setParameters(parameters);
            if (track.type == MediaTrack.TYPE_SUBTITLE) dispatchSubtitleText("");
            // Exo applies parameters asynchronously; true means a validated selection was submitted.
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public boolean disableSubtitleTrack() {
        if (!supportsMediaTracks()) return false;
        DefaultTrackSelector selector = (DefaultTrackSelector) mTrackSelector;
        MappingTrackSelector.MappedTrackInfo mapped = selector.getCurrentMappedTrackInfo();
        if (mapped == null) return false;
        DefaultTrackSelector.ParametersBuilder parameters = selector.buildUponParameters();
        for (int renderer = 0; renderer < mapped.getRendererCount(); renderer++) {
            if (mapped.getRendererType(renderer) == C.TRACK_TYPE_TEXT) {
                parameters.clearSelectionOverrides(renderer).setRendererDisabled(renderer, true);
            }
        }
        selector.setParameters(parameters);
        dispatchSubtitleText("");
        return true;
    }

    @Override
    public void onCues(List<Cue> cues) {
        StringBuilder text = new StringBuilder();
        for (Cue cue : cues) {
            if (cue.text == null || cue.text.length() == 0) continue;
            if (text.length() > 0) text.append('\n');
            text.append(cue.text);
        }
        dispatchSubtitleText(text.toString());
    }

    public void setRenderersFactory(RenderersFactory renderersFactory) {
        mRenderersFactory = renderersFactory;
    }

    public void setLoadControl(LoadControl loadControl) {
        mLoadControl = loadControl;
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        mMediaSource = mMediaSourceHelper.getMediaSource(path, headers);
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        //no support
    }

    @Override
    public void start() {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.setPlayWhenReady(true);
    }

    @Override
    public void pause() {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.setPlayWhenReady(false);
    }

    @Override
    public void stop() {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.stop();
    }

    @Override
    public void prepareAsync() {
        if (mInternalPlayer == null)
            return;
        if (mMediaSource == null) return;
        if (mSpeedPlaybackParameters != null) {
            mInternalPlayer.setPlaybackParameters(mSpeedPlaybackParameters);
        }
        mIsPreparing = true;
        mInternalPlayer.setMediaSource(mMediaSource);
        mInternalPlayer.prepare();
    }

    @Override
    public void reset() {
        clearSubtitleTextListener();
        if (mTrackSelector instanceof DefaultTrackSelector) {
            DefaultTrackSelector selector = (DefaultTrackSelector) mTrackSelector;
            DefaultTrackSelector.ParametersBuilder parameters = selector.buildUponParameters().clearSelectionOverrides();
            MappingTrackSelector.MappedTrackInfo mapped = selector.getCurrentMappedTrackInfo();
            if (mapped != null) {
                for (int i = 0; i < mapped.getRendererCount(); i++) parameters.setRendererDisabled(i, false);
            }
            selector.setParameters(parameters);
        }
        if (mInternalPlayer != null) {
            mInternalPlayer.stop();
            mInternalPlayer.clearMediaItems();
            mInternalPlayer.setVideoSurface(null);
            mIsPreparing = false;
        }
    }

    @Override
    public boolean isPlaying() {
        if (mInternalPlayer == null)
            return false;
        int state = mInternalPlayer.getPlaybackState();
        switch (state) {
            case Player.STATE_BUFFERING:
            case Player.STATE_READY:
                return mInternalPlayer.getPlayWhenReady();
            case Player.STATE_IDLE:
            case Player.STATE_ENDED:
            default:
                return false;
        }
    }

    @Override
    public void seekTo(long time) {
        if (mInternalPlayer == null)
            return;
        mInternalPlayer.seekTo(time);
    }

    @Override
    public void release() {
        clearSubtitleTextListener();
        if (mInternalPlayer != null) {
            mInternalPlayer.removeListener(this);
            mInternalPlayer.release();
            mInternalPlayer = null;
        }

        mIsPreparing = false;
        mSpeedPlaybackParameters = null;
    }

    @Override
    public long getCurrentPosition() {
        if (mInternalPlayer == null)
            return 0;
        return mInternalPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        if (mInternalPlayer == null)
            return 0;
        return mInternalPlayer.getDuration();
    }

    @Override
    public int getBufferedPercentage() {
        return mInternalPlayer == null ? 0 : mInternalPlayer.getBufferedPercentage();
    }

    @Override
    public void setSurface(Surface surface) {
        if (mInternalPlayer != null) {
            mInternalPlayer.setVideoSurface(surface);
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder == null)
            setSurface(null);
        else
            setSurface(holder.getSurface());
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mInternalPlayer != null)
            mInternalPlayer.setVolume((leftVolume + rightVolume) / 2);
    }

    @Override
    public void setLooping(boolean isLooping) {
        if (mInternalPlayer != null)
            mInternalPlayer.setRepeatMode(isLooping ? Player.REPEAT_MODE_ALL : Player.REPEAT_MODE_OFF);
    }

    @Override
    public void setOptions() {
        //准备好就开始播放
        mInternalPlayer.setPlayWhenReady(true);
    }

    @Override
    public void setSpeed(float speed) {
        PlaybackParameters playbackParameters = new PlaybackParameters(speed);
        mSpeedPlaybackParameters = playbackParameters;
        if (mInternalPlayer != null) {
            mInternalPlayer.setPlaybackParameters(playbackParameters);
        }
    }

    @Override
    public float getSpeed() {
        if (mSpeedPlaybackParameters != null) {
            return mSpeedPlaybackParameters.speed;
        }
        return 1f;
    }

    @Override
    public long getTcpSpeed() {
        // no support
        return 0;
    }

    @Override
    public void onPlaybackStateChanged(int playbackState) {
        if (mPlayerEventListener == null) return;
        if (mIsPreparing) {
            if (playbackState == Player.STATE_READY) {
                mPlayerEventListener.onPrepared();
                mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                mIsPreparing = false;
            }
            return;
        }
        switch (playbackState) {
            case Player.STATE_BUFFERING:
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, getBufferedPercentage());
                break;
            case Player.STATE_READY:
                mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_END, getBufferedPercentage());
                break;
            case Player.STATE_ENDED:
                mPlayerEventListener.onCompletion();
                break;
        }
    }

    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onError();
        }
    }

    @Override
    public void onVideoSizeChanged(VideoSize videoSize) {
        if (mPlayerEventListener != null) {
            mPlayerEventListener.onVideoSizeChanged(videoSize.width, videoSize.height);
            if (videoSize.unappliedRotationDegrees > 0) {
                mPlayerEventListener.onInfo(MEDIA_INFO_VIDEO_ROTATION_CHANGED, videoSize.unappliedRotationDegrees);
            }
        }
    }
}
