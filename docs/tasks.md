# 任务清单 — 播放进度续播修复（最终实现）

对应规格：`docs/spec.md`

| 状态 | # | 任务 | 文件 / 函数 | 验收 |
|---|---|---|---|---|
| [x] | 1 | 进度 key 改为 v3：源 + 影片 + 线路 + 集地址 | `PlayActivity.buildEpisodeProgressKey()` | 同剧不同集 / 不同线路互不影响 |
| [x] | 2 | 定时保存 + 退出保存 + 起播前保存 | `onResume()` 的 `mProgressSaveRunnable`、`onPause()`、`onStop()`、`saveCurrentProgress()` | 每 5 秒落盘，后台回收最多丢 5 秒 |
| [x] | 3 | 关闭播放器内部进度，就绪后显式 seek | `setProgressManager(null)`、`setProgressKey(null)`、`prepareResumePosition()`、`STATE_PREPARED` | 起播定位到上次位置 |
| [x] | 4 | 播完清理当前集进度 | `STATE_PLAYBACK_COMPLETED` → `saveProgressValue(mResumeKey, 0)` | 播完的集不残留进度 |
| [x] | 5 | 搜索结果每批重排 | `SearchActivity` 结果回调 | 后返回的高优先级源不会被追加到末尾 |
| [x] | 6 | 编译并推送 | `.\gradlew.bat :app:assembleDebug` | 通过，已随 `2d65bed` 推送 |
| [ ] | 7 | 真机验证 | 主流程 + 后台路径 + 换集 / 换线路 | 待用户实测确认 |

执行顺序：1 → 2 → 3 → 4 → 5 → 6 → 7。第 7 步需要用户在设备上实际播放验证。
