<img width="2358" height="1080" alt="e9929ac87570ab76408eb78cb8c7b2cc" src="https://github.com/user-attachments/assets/628169e7-faec-4423-bdba-c91ce121d8e3" />
<img width="2358" height="1080" alt="b114f60fdcbf82ed8454cd825eece86a" src="https://github.com/user-attachments/assets/0ba983d1-4ec0-40dd-84d9-23c1592a3c39" />
<img width="2358" height="1080" alt="66de4e048c9e9c9758230af061bbf019" src="https://github.com/user-attachments/assets/57c0328d-0283-478b-95d3-83eae8b49bbc" />
<img width="2358" height="1080" alt="9aa364a29bd910b53affa1123cb48d89" src="https://github.com/user-attachments/assets/750c239d-13a9-4dad-a699-66edbb69014f" />
<img width="2358" height="1080" alt="133a8cc6bbccfc5c0dc8ebd7da8060c9" src="https://github.com/user-attachments/assets/0bda9125-1813-4af9-bf33-d7e083d4d6da" />




# Z影视

基于开源项目 [TVBoxOSC](https://github.com/CatVodTVOfficial/TVBoxOSC)（CatVodTVOfficial）二次开发与打磨的 Android 影视聚合客户端，面向电视盒子 / 电视 / 手机横屏使用。

项目只做客户端本身：**影片资源与各家接口全部来源于互联网第三方，本软件不存储、不传播任何视频内容。**

> 软件与影片接口完全免费，软件有问题请联系开发者 宗成焕

首次使用请看 **[《Z影视 使用指南》](./使用指南.md)**：安装步骤、首页与搜索、遥控器按键、播放页操作、设置项逐条说明与常见问题。

---

## 项目介绍

Z影视 是一款多源聚合的影视客户端。它自己不提供片源，而是把配置里的一批「采集源 / 解析源」聚合成一个统一的观影入口：一次搜索同时查询所有已勾选的源，按你设定的优先级顺序展示结果，再交给内置播放器播放。

相比原版 TVBoxOSC，本项目在首页、分类、搜索、收藏历史、播放器等页面上做了大量细节优化，重点解决「首页打不开」「分类页空白」「搜索顺序乱」「播放画面比例不合适」这类实际使用中最影响体验的问题。

- 默认配置地址：`https://tv.xn--yhqu5zs87a.top`
- 包名：`com.studio.osc`
- 支持 Android 5.0 及以上（minSdk 21 / targetSdk 28）
- 当前版本：`v1.1.24`（versionCode 134），支持应用内检查更新（Gitee 优先、GitHub 备用），发布方式见 [在线更新发布说明](docs/在线更新发布.md)

## 主要功能

### 首页
- 默认数据源固定为**豆瓣推荐**，并置于数据源列表第一位
- 卡片采用**弧形排布**：选中的卡片位于弧顶（最高最亮最大，获得焦点时再额外放大），两侧依次下沉、变暗并轻微外翻；切卡时卡片沿弧面滑动，聚焦动效为短促减速曲线
- 卡片整体尺寸经过收敛，**同屏可显示更多张**，末端最后一张向右滑到底后回到第一张
- 卡片 + 全屏背景海报的展示方式，背景随卡片同步切换，不滞后
- 静置 6 秒自动轮播下一张，任意按键或焦点变化立即打断并重新计时
- **四层兜底**保证首页永远有内容：豆瓣直连 → 本地缓存 → 当前数据源推荐 → 自动切换其他可用数据源
- 内置补充非凡、量子、暴风、极速等主流采集源并置于站点列表最前，首页源选择采用**采集源优先**策略，避开不稳定的网盘类源

### 分类
- 侧边栏分类固定为：首页、电影、电视剧、综艺、动漫、纪录片（隐藏其他分类）；侧边栏另有**「直播」**入口，其节目源可在设置里自定义（支持多行 M3U / TVBox JSON，多源自动合并）
- 分类匹配**按叶子分类优先**匹配源的分类，避免父分类查询返回 0 条导致分类页空白
- 分类页卡片点击直接按片名跳转聚合搜索页

### 搜索
- 一次搜索聚合全部已勾选源，**勾选顺序即展示顺序**，所有搜索界面严格按该顺序展示
- 结果分批返回时，每批都会重新按勾选顺序排序，后返回的高优先级源不会被追加到列表末尾
- 搜索有 **15 秒**超时保护：到时自动收尾，已返回的结果照常展示；一条都没有时会提示「搜索超时，请检查网络或更换搜索源」，不会一直停在转圈状态
- 搜索源管理支持逐个勾选、全选 / 全不选，**默认全选**
- 默认优先级：`文采┃秒播` > `非凡┃影视` > 其余源（用户自定义过顺序后以用户顺序为准）
- 历史搜索记录可一键清空，历史为空时自动收起该区域

### 收藏与历史
- 收藏、历史均为网格浏览，支持进入删除模式逐条删除
- 「清空」需要**弹窗二次确认**，确认后才真正删除

### 播放
- 默认画面比例为**原始**（按视频自身比例显示，点播与直播一致），可随时切换 默认 / 16:9 / 4:3 / 填充 / 裁剪
- 底部控制栏：暂停 / 播放、上一集 / 下一集、画面比例、倍速（0.5 ～ 3.0 步进 0.25）
- 进度条独立成行，已播 / 缓冲 / 轨道三段配色，拖动与聚焦均有明确反馈
- 遥控器：确定键播放暂停、左右键快进快退（带进度提示）、上下键切换上一集 / 下一集
- 播放时顶部的节目介绍、时间与底部提示在无操作 5 秒后自动收起，按任意键或触摸屏幕重新显示；「缓冲中…」提示例外，缓冲一结束立即消失
- 起播有 20 秒等待：超时仍未出画面会自动重试，仍失败则提示「视频播放出错」并给出重试入口
- 支持屏幕锁定、横竖屏切换，避免误触
- 支持**长按加速**：按住画面进入 2×，向右拖进入 3×，下滑可锁定倍速，返回键优先退出加速
- 支持**字幕与音轨**：内嵌字幕、站点返回字幕、本地字幕文件（SRT / WebVTT / ASS 基础文本）；音轨按语言偏好跨集匹配
- 控制栏提供**字幕 / 音轨**入口，并恢复片头 / 片尾跳过与快进步长设置
- **进度续播**：按「源 + 影片 + 线路 + 集地址」记录位置，播放中每 5 秒与退出时保存；起播就绪后自动定位到上次位置，播完的集不残留进度

### 设置
- 配置地址校验：校验通过后才保存，校验期间锁定不可退出，失败时显示具体原因，并同时展示站点总数与可搜索站点数
- 配置弹窗已重做为**「配置库 / 收藏地址」**：不再支持手动粘贴配置内容，改为从已保存地址中选择，点选即自动校验并切换
- **配置源容灾**：当前地址连不上时，自动按「当前地址 → 配置库 → 内置备用地址 → 默认地址」顺序试连，第一个可用的会被采用；全部失败则使用本地缓存并提示
- 分组：接口与数据源（设置配置文件 / 直播源地址 / 默认网站）、首页与搜索（首页推荐 / 搜索展示 / 搜索源管理）、播放（播放器 / IJK 解码方式 / 渲染方式 / 画面缩放 / 嗅探 Webview / 安全 DNS）、其他（分享给好友 / 关于）

## 源码结构

Gradle 多模块工程，`rootProject.name = "MoviePlayer"`，仅含 `app`（主应用）与 `player`（播放内核）两个模块。

```
tvbox_source/
├── app/                        主应用模块（applicationId com.studio.osc）
├── player/                     播放器模块：IJK 内核 + 播放器框架
├── gradle/                     Gradle Wrapper 与国内镜像配置
├── build.gradle                根构建脚本
├── settings.gradle             模块声明（include ':app' ':player'）
├── gradle.properties
├── site.json                   内置站点配置示例
├── README.md / README.en.md / 使用指南.md
└── LICENSE
```

### app 模块

```
app/
├── libs/
│   ├── thunder.jar             迅雷下载支持
│   └── uiSupport-release.aar   闭源 UI 支持库（附带更新/上报能力，见免责声明）
└── src/main/
    ├── AndroidManifest.xml     入口为 HomeActivity（MAIN + LEANBACK_LAUNCHER）
    ├── assets/
    │   ├── tvbox.jar           内置爬虫包，运行时由 DexClassLoader 动态加载
    │   ├── js/                 JS 爬虫引擎与依赖（drpy、cat.js、cheerio、crypto-js、pako 等）
    │   ├── parse.html          内置解析页
    │   └── site.json / site_muti.json / readme.json
    ├── python/                 Chaquopy Python 爬虫运行时（app.py / runner.py / trigger.py / base/spider.py）
    ├── java/com/github/
    │   ├── tvbox/osc/          本项目主体代码（详见下表）
    │   ├── catvod/             爬虫加载与执行（JarLoader、crawler、utils）
    │   ├── com/fongmi/quickjs/ QuickJS 引擎适配（bean / method / utils）
    │   └── okhttp3/dnsoverhttps/  安全 DNS（DoH）
    └── res/                    layout(54) / drawable(96) / values + values-sw600dp / xml / raw / animator
```

### com/github/tvbox/osc 包说明

| 包 | 文件数 | 职责 |
| --- | --- | --- |
| `api` | 2 | `ApiConfig`（配置解析、站点管理、默认配置地址）、`BaseLoader` |
| `base` | 3 | `App`、`BaseActivity`、`BaseLazyFragment` 等基类 |
| `bean` | 17 | 数据模型（影片、站点、搜索结果等） |
| `cache` | 8 | Room 数据库与本地缓存（收藏、历史、播放记录） |
| `callback` / `data` / `event` | 2 / 2 / 4 | 回调封装、全局数据、EventBus 事件 |
| `player` | 1+3+2+2 | 播放内核接入：`IjkMediaPlayer`、`VodController` / `LiveController`、渲染与第三方播放器（MX / Reex） |
| `receiver` | 3 | 开机自启、外部搜索与自定义 Web 广播接收 |
| `server` | 7 | 局域网 HTTP 服务与口令投屏（`RemoteServer`、`ControlManager` 等） |
| `ui.activity` | 11 | `HomeActivity`（入口）、`PlayActivity`、`SearchActivity`、`DetailActivity`、`SettingActivity`、`CollectActivity`、`HistoryActivity`、`PushActivity`、`LivePlayActivity` 等 |
| `ui.fragment` | 3 | `FirstFragment`（首页）、`GridFragment`（分类 / 收藏 / 历史网格）、`ModelSettingFragment` |
| `ui.adapter` | 25 | 各页面的 RecyclerView 适配器 |
| `ui.dialog` | 12 | 设置项、配置地址、备份、选集、快速搜索、搜索站点等对话框 |
| `ui.tv` + `ui.tv.widget` | 1 + 9 | TV 端控件：首页卡片 `HomeCardRecyclerView`、虚拟键盘、跑马灯、不滚动 ViewPager 等 |
| `util` | 12 (+js 5 / python 1 / SSL 1 / thunder 1) | `OkGoHelper`、`HawkConfig`、`FileUtils`、`AppManager`、`MD5`、`SharpenTransformation` 等 |
| `viewmodel` | 1 | `SourceViewModel`（搜索聚合） |

### player 模块

```
player/src/main/
├── java/tv/danmaku/ijk/         IJKPlayer 内核（含 media / ffmpeg / annotations）
├── java/xyz/doikki/videoplayer/ 播放器框架（exo / ijk / controller / render / util）
├── jniLibs/armeabi-v7a/        仅保留 armeabi-v7a 原生库
└── res/values/
```

### 构建技术栈

| 项 | 值 |
| --- | --- |
| 语言 / 构建 | Java 8、Gradle、`compileSdk 30` / `buildTools 30.0.3` |
| 兼容性 | `minSdk 21` / `targetSdk 28`，仅 `armeabi-v7a`，开启 multiDex |
| 数据 | Room 2.3.0、Hawk、Gson |
| 网络 | OkGo 3.0.4、OkHttp 3.12.1、DoH（dnsoverhttps） |
| UI | Glide 3.7.0、BaseRecyclerViewAdapterHelper、TV-RecyclerView、LoadSir、AutoSize |
| 脚本运行时 | QuickJS（`wang.harlon.quickjs` 2.4.5）、Chaquopy Python 3.8（含 requests / bs4 / lxml / pyquery / pycryptodome） |
| 本地服务 | NanoHTTPD 2.3.1 |

## 文档

| 文档 | 内容 |
| --- | --- |
| [README.md](./README.md) | 项目介绍、主要功能、源码结构、构建与免责声明 |
| [使用指南](./使用指南.md) | 面向用户的操作手册：安装、首次启动、遥控器按键、首页 / 分类 / 搜索 / 详情 / 播放 / 收藏历史、设置项逐条说明、常见问题 FAQ |
| [播放器增强说明](./docs/播放器增强-2026-10-24.md) | 长按加速、进度条、字幕音轨与片头片尾的实现要点与验证方式 |
| [续播修复规格](./docs/spec.md) / [任务清单](./docs/tasks.md) | 播放进度续播链路的审查结论、修复方案与验收步骤 |

## 更新记录

> 完整提交历史见仓库 commits 页面，下表按主题归纳重要变更（倒序）。

### 2026-10-24

- **播放器增强**：长按画面加速（2× / 3× / 下滑锁定）、字幕与音轨选择（内嵌 / 站点 / 本地字幕文件）、片头片尾跳过与步长设置
- **进度续播**：改由播放页显式接管进度 —— 按「源 + 影片 + 线路 + 集地址」记录，播放中每 5 秒与退出时保存，起播就绪后显式 seek 定位，播完清除该集进度
- **搜索排序**：结果每批返回后重新按搜索源勾选顺序排序，避免后返回的高优先级源落在列表末尾
- **控制栏**：进度条独立成行并重做分段配色与拖动反馈，新增字幕 / 音轨入口，按钮随状态切换
- **构建**：调试 / 正式产物统一命名为 `Z影视.apk`
- **文档**：新增播放器增强说明与续播修复规格

### 2026-10-20

- **文档**：README 增补源码结构 / 包说明 / 构建技术栈；使用指南补充版本号，FAQ 新增「搜索超时」「起播长时间缓冲」
- **播放**：缓冲结束即隐藏「缓冲中…」提示，不再等满 5 秒
- **首页**：卡片尺寸抽为 `home_card_*` 尺寸令牌（含 `values-sw600dp` 大屏覆盖），选中卡片额外放大；静置轮播回到 6 秒
- **稳定性**：搜索链路加 15 秒超时、起播加 20 秒超时并请求去重，避免长时间转圈
- **配置源容灾**：配置地址改为候选链试连（当前地址 → 配置库 → 内置备用地址 → 默认地址），首个可用者被采用，全失败回落本地缓存
- **配置库 / 收藏地址**：配置弹窗重做，去掉配置内容粘贴框，改为「配置库」「收藏地址」两个入口，点选即校验并切换
- **直播**：设置新增「直播源地址」（多行 M3U / TVBox JSON，多源去重合并），侧边栏新增「直播」入口，清空该设置即回到总配置自带的直播源
- **稳定性修复**：搜索请求统一由 `Future` 管理并可在页面销毁时停止、详情页线程池随页面关闭、EventBus 注销修正、配置库列表越界防护、仅在配置地址真正变化时才重启应用
- **加载提示**：全局 Loading 文案为「请耐心等待」

### 2026-10-17

- **首页背景**：海报层从 ViewPager 剥离为 Activity 级独立图层，切到分类页不再透出上一页残留；后台加载与轮播的活跃判断收敛
- **侧边栏**：玻璃质感改版
- **首页卡片**：环形 3D 轮播 → 虚拟无限循环 → 回调为普通列表循环（末张滑到底回到第一张），弧形深度重做并缩小卡片、同屏显示更多
- **播放页**：新增选集面板与手势加速

### 2026-10-16

- **首页**：数据源改为豆瓣推荐优先并置顶；四层兜底保证首页永不空白；背景海报补 USM 锐化、封面统一提到 `l` 档；卡片弧形排布、切换动画 350ms、选中项始终居中
- **搜索**：搜索源默认全选并内置优先级（`文采┃秒播` > `非凡┃影视` > 其余），勾选顺序即展示顺序；一个源都没勾选时不再空转
- **播放页**：提示 5 秒自动收起、广告提示并入控制栏、进度条改双行重绘（已播青色 / 缓冲 / 轨道分层）
- **设置**：分组标题与行高字号层级重做；配置地址校验期间锁定对话框，校验通过才保存
- **初始提交**：基于 TVBoxOSC 的重打包基线

## 构建

```bash
# 调试包
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/Z影视.apk（debug / release 统一命名）

# 正式包
./gradlew :app:assembleRelease
```

依赖下载已配置国内镜像（Maven → 阿里云 / 腾讯云，Gradle 发行版 → 腾讯云镜像）。
注意：Chaquopy 的 Android 原生轮子只在 `chaquo.com/pypi-7.0` 提供，`build.gradle` 的 `pip{}` 块不能配置 `indexUrl` 镜像，否则 ABI 轮子解析失败。

## 免责声明

- 本项目仅用于学习与技术研究，所有影片、图片、接口均来自互联网第三方，本软件不提供、不存储、不传播任何影视资源。
- 软件与影片接口完全免费，请勿用于任何商业用途，也不要在任何应用商店付费购买本软件。
- 因使用本软件产生的任何后果由使用者自行承担，开发者不承担任何责任。
- 如有侵权，请联系删除。

## 交流

- QQ ：2020997318
- 有问题请联系开发者：宗成焕
