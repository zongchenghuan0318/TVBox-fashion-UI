# 播放进度续播失效 — 审查结论与修复规格

日期：2026-10-24　范围：`app` 模块播放页续播链路（仅 Java，不改布局/资源）

> 本文已按最终落地的实现（提交 `2d65bed`）更新，首轮方案仅作失效记录保留。

## 1. 现象

视频播放到中途退出，再次打开同一影片同一集，仍从 0 开始播放，没有回到上次位置。

## 2. 首轮方案与失效原因

首轮（提交 `6fad8de`）仍然依赖 `VideoView` 的 `ProgressManager`：保存稳定 key（`resume|v2|...`），读取走 `startPlay()` → `getSavedProgress()` → `onPrepared()` → `seekTo()`。真机复测仍不续播，运行时取证结论：

- **保存侧**：数据库 `cache` 表确实出现 `resume|v2|非凡|99339|ffm3u8|0` 行，但值是 7.7 秒（7736ms）；长时间播放后退出并未刷新成真实位置。
- **读取侧**：`VideoView.release()`（停止、切集、重试都会调用）会把 `mCurrentPosition` 归零；`onPause`/`onStop` 里当 `getCurrentPosition()` 返回 0 时又被 `position <= 0` 守卫跳过，于是旧值一直残留。
- **写入点互相覆盖**：`VideoView.release()` 内部还会用旧 key 再写一次 `saveProgress()`，与播放页的写入叠加，进度不可控。

结论：继续修补 `ProgressManager` 链路收益低，改为由播放页显式接管进度的读写与定位。

## 3. 最终实现（v3 显式续播）

### 3.1 进度 key

`PlayActivity.buildEpisodeProgressKey()`：

```
resume|v3|<source>|<vodId>|<playFlag>|<episodeIdentity>
```

- `source` 优先用 Bundle 传入的 `mSourceKeyStr`，为空才退回 `mVodInfo.sourceKey`；
- `episodeIdentity` 取该集 `VodSeries.url`，为空退回集名 `VodSeries.name`；
- 集信息不可用时退回 v2 的 `buildStableProgressKey()`（`resume|v2|source|id|flag|index`）。

按“集地址”而不是集序号记录，切换集数、线路或源顺序变化都不会串进度。

### 3.2 保存

- `saveCurrentProgress()`：位置有效（`> 0` 且不接近片尾）时写 `CacheManager.save(MD5(key), position)`。
- 定时：`onResume()` 起每 5 秒保存一次（`mProgressSaveRunnable`），`onPause()` / `onDestroy()` 移除，后台被回收最多丢 5 秒。
- 时机：`onPause()`、`onStop()`，以及 `playVideo()` 中 `mVideoView.release()` 之前各保存一次。
- 播完：`STATE_PLAYBACK_COMPLETED` 时把当前集 key 写 0，播完的集不残留进度。

### 3.3 读取与定位

- 起播前 `mVideoView.setProgressKey(null)` + `setProgressManager(null)`：进度完全由播放页掌握，避免播放器内部再写。
- `prepareResumePosition()` 读出 `mResumeKey` / `mResumePosition`；读不到时回退到 `playerCfg` 的 `st`（片头跳过秒数）。
- 播放状态进入 `STATE_PREPARED`（已就绪、可 seek）时执行 `mVideoView.seekTo(mResumePosition)`，定位后清零，避免重复定位。

### 3.4 不改动范围

`VideoView`、`VodController`、`DetailActivity`、`RoomDataManger` 均不改；不调整布局、资源、进度条 UI。

## 4. 搜索排序同步修正

`SearchActivity` 搜索结果每批返回后统一 `Collections.sort` 再整表刷新。原先“前缀相同就增量追加”的优化会让后返回的高优先级源落在列表末尾，排序失真。

## 5. 验证

1. 编译：`:app:assembleDebug`（产物固定 `Z影视.apk`）。
2. 真机主流程：播放 30 秒以上 → 返回 → 重新进入同一集，期望从约 30 秒处继续。
3. 后台路径：播放中切到后台 10 秒以上再回来 / 重进，期望续播。
4. 换集 / 换线路：各自独立记录，不串集。
5. 播完一集：自动下一集从头播放，已播完的集不残留进度。
6. 日志：`adb logcat | findstr resume` 可看到 `resume save key=... position=...`、`resume read ...`、`resume seek ...`。

## 6. 风险

- 每 5 秒一次主线程 Room 写（`allowMainThreadQueries`），写入量很小。
- 旧的 v2 / URL 型进度行不会清理，但不再参与读取，不影响正确性。
