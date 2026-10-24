# 播放进度续播失效 — 审查结论与修复规格

日期：2026-10-24　范围：`app` 模块播放页续播链路（仅 Java，不改布局/资源）

## 1. 现象

视频播放到中途退出，再次打开同一影片同一集，从 0 开始播放，没有回到上次位置。

## 2. 现有链路（审查结论：链路本身完整）

| 阶段 | 位置 | 行为 |
|---|---|---|
| 保存 A | `PlayActivity.onStop()` → `saveCurrentProgress()` | `CacheManager.save(MD5(buildStableProgressKey()), position)` |
| 保存 B | `PlayActivity.onDestroy()` → `VideoView.release()` → `saveProgress()` | `ProgressManager.saveProgress(mProgressKey, mCurrentPosition)` |
| 读取 | `playVideo()` → `setProgressKey(buildStableProgressKey())` → `mVideoView.start()` → `VideoView.startPlay()` → `getSavedProgress()` | 查 `CacheManager`，`onPrepared()` 里 `seekTo(mCurrentPosition)` |
| 集数/线路 | `DetailActivity` 读 `RoomDataManger.getVodInfo(sourceKey, vodId)` | 恢复 `playIndex / playFlag / playerCfg` 后 `jumpToPlay()` |

`VideoView`、`VodController` 均无“启动时把进度重置为 0”的逻辑，`VodController.setProgress()` 只刷新 UI、不 seek，可排除播放器控件把恢复位置覆盖掉。

## 3. 问题点（按可能性排序）

**P1（主因怀疑）稳定 key 的 source 段来源不可靠**
`PlayActivity.buildStableProgressKey()` 使用 `mVodInfo.sourceKey`，而该字段是 `DetailActivity` 从详情接口返回的 `mVideo.sourceKey` 拷贝来的（`DetailActivity.java:189`），部分入口（换源搜索、聚合搜索进详情）可能为空或与 Bundle 里的 `mSourceKeyStr` 不一致。同一部片在不同入口会算出不同的 key，导致“保存用 A key、读取用 B key”，永远不命中。

**P2 升级前的旧进度不可达**
旧实现 key 取 `mPlayBean.progressKeyStr`，它来自播放接口的 `proKey`（`UiHelper.java:239`，默认 null）。多数站点不返回 `proKey`，此时旧实现直接拿播放 URL 当 key。新实现只在 `proKey` 非空时做一次兜底，URL 型旧进度彻底读不到，用户观感就是“以前能续播，现在不能”。

**P3 保存时机过少**
只在 `onStop()` 与 `release()` 保存。播放器/盒子类设备后台进程回收激进，某些退出路径只走到 `onPause()`，位置就丢了。

**P4 键值拼接无分隔符**
`sourceKey + id + playFlag + playIndex` 直接相加，字段为空时易与别的影片撞 key（次要，但与 P1 一起改）。

**P5 环境因素**
本轮排查执行过 `adb uninstall`，Room 数据库（历史记录 + 缓存进度）被整体清空。重装后旧片确实没有任何可恢复数据，需要重新播放一次才会有进度。

## 4. 修复方案（决策完成）

### 4.1 `PlayActivity.buildStableProgressKey()` 重写（P1/P4）
- source 段优先用 Bundle 的 `mSourceKeyStr`，为空才退回 `mVodInfo.sourceKey`。
- 加固定前缀与 `|` 分隔：`"resume|v2|" + source + "|" + id + "|" + playFlag + "|" + playIndex`。
- `mVodInfo == null` 时保持现有兜底（返回 `mPlayBean.progressKeyStr`）。

### 4.2 新增播放 URL 兜底 key（P2）
- 新增字段 `private String mCurrentPlayUrl;`，在 `playVideo(String url, ...)` 拿到最终 `playUrl` 后赋值。
- 新增 `private String buildLegacyProgressKey()`：`mPlayBean.progressKeyStr` 非空则返回它；否则返回 `mCurrentPlayUrl`；都为空返回 null。

### 4.3 `getSavedProgress()` 三级读取 + 命中日志（P2）
按顺序读 `CacheManager`：
1. `MD5(buildStableProgressKey())`
2. `MD5(buildLegacyProgressKey())`
3. `MD5(mCurrentPlayUrl)`

任一处读到 `Number` 且 `> 0` 即返回；都没有才返回 `st * 1000`（片头跳过）。保留 `Number` 判断，避免 ClassCastException；`mVodPlayerCfgJsonObject` 增加 null 守卫。

### 4.4 保存时机与双写（P3/P2）
- `onPause()` 中在 `mVideoView.pause()` 之前调用 `saveCurrentProgress()`，`onStop()` 保留调用。
- `saveCurrentProgress()` 在同一位置同时写入稳定 key、legacy key（非空时）与 `mCurrentPlayUrl`（非空时），三者都写，保证任何入口都能读到。

### 4.5 不改动范围
`VideoView`、`VodController`、`DetailActivity`、`RoomDataManger` 均不改；不调整布局、资源、进度条 UI。

## 5. 验证

1. 编译：`.\gradlew.bat clean assembleDebug`（产物名固定 `Z影视.apk`）。
2. 安装：`adb install -r`（设备弹窗需点“允许”）。
3. 真机主流程：播放任意影片 30 秒以上 → 返回 → 从首页“继续观看”进入同一集，期望自动定位到约 30 秒。
4. 换入口复测：从详情页换源/聚合搜索进入同一集，期望同样续播（覆盖 P1）。
5. 日志确认：`adb logcat -s PlayActivity` 能看到稳定 key、legacy key 与实际读到的毫秒值。
6. 回归：切集、播完自动下一集、`et` 片尾跳过仍正常；播完的剧集不应残留进度。

## 6. 风险

- 双写会增加 2～3 条 Room 写操作，均在主线程 `allowMainThreadQueries` 下，写入量极小。
- 旧 key 残留行不会清理，不影响读取优先级（稳定 key 永远先查）。

## 7. 待用户确认

本规格仅覆盖“续播失效”的修复；确认后再改代码并编译安装。
