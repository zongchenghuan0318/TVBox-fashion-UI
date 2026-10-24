# 任务清单 — 播放进度续播修复

对应规格：`docs/spec.md`

| 状态 | # | 任务 | 文件 / 函数 | 验收 |
|---|---|---|---|---|
| [x] | 1 | 重写稳定 key：`mSourceKeyStr` 优先、`resume\|v2\|...` 分段 | `PlayActivity.buildStableProgressKey()` | key 在两处入口一致，含 `\|` 分隔与前缀 |
| [x] | 2 | 记录最终播放 URL，新增 legacy URL key | `PlayActivity.mCurrentPlayUrl`（`playVideo` 内赋值）、`buildLegacyProgressKey()` | 旧版本 URL 型进度可被读到 |
| [x] | 3 | `getSavedProgress()` 三级读取 + null 守卫 + 命中日志 | `PlayActivity.addProcessMng()` 内匿名 `ProgressManager` | 稳定 key → proKey → playUrl 依次回退，`Number` 判断保留 |
| [x] | 4 | 保存时机与双写 | `PlayActivity.onPause()`、`saveCurrentProgress()` | 暂停/离屏都会写；稳定 key + legacy key + playUrl 同时写入 |
| [x] | 5 | 编译 | `.\gradlew.bat clean assembleDebug` | 产物 `Z影视.apk` 生成、无编译错误（2026-10-24 15:40 已通过） |
| [ ] | 6 | 安装与真机验证 | `adb install -r`；主流程 + 换入口流程 | 已安装并正常启动（15:40:29）；**续播效果待真机播放验证** |

执行顺序：1 → 2 → 3 → 4 → 5 → 6。第 5、6 步需用户授权设备安装操作。
