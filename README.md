<div align="center">

# Z影视

面向电视大屏的多源影视聚合客户端

[![Android](https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/)
[![Version](https://img.shields.io/badge/version-1.7.7-6c5ce7)](https://github.com/zongchenghuan0318/TVBox-fashion-UI/releases/tag/v1.7.7)
[![Architecture](https://img.shields.io/badge/ABI-armeabi--v7a-111111)](#运行环境)

</div>

<p align="center">
  <img width="2358" height="1080" alt="Z影视首页" src="https://github.com/user-attachments/assets/85c6f696-fd7e-4018-9085-115b589f4139" />
</p>

<p align="center">
  <img width="2358" height="1080" alt="Z影视播放界面" src="https://github.com/user-attachments/assets/f810db62-0641-4fca-aa2d-0028b98c831c" />
</p>
<img width="2358" height="1080" alt="ae314aad5b1a6eb8d1dc98f1389e5acb" src="https://github.com/user-attachments/assets/32b9a6b1-3517-4362-b111-5f40565bcb2e" />
<img width="2358" height="1080" alt="9efe4530bc7622a8c3bd8562867118c7" src="https://github.com/user-attachments/assets/eb81db0c-a3ff-4955-8500-ffaa6bff79cd" />
<img width="2358" height="1080" alt="6150f75c63cdcfa5586b0059e0376609" src="https://github.com/user-attachments/assets/4c2a7c36-f6a8-4281-bbd0-8a65c2272d7f" />
<img width="2358" height="1080" alt="75c336fe4d24d721044f024138aa058a" src="https://github.com/user-attachments/assets/7112dcb9-7e72-44d3-a3ec-0ee6918f51f6" />
<img width="2358" height="1080" alt="f80ad8647789d3673996f417acc26897" src="https://github.com/user-attachments/assets/623cfbdc-1b5e-460b-a6c8-2796de96d11e" />
<img width="2358" height="1080" alt="79149b8b68f3cffda720353cdba14fbe" src="https://github.com/user-attachments/assets/6bf5c11f-e13c-42e2-b9b2-d34aa70cb40e" />

Z影视是一款以电视盒子和智能电视为主要使用场景的 Android 影视聚合客户端，也支持手机横屏使用。它不内置或提供影片内容，而是读取用户配置的影视源，将搜索、详情、播放、收藏和历史整合到一个适合遥控器操作的界面中。

> 软件与影片接口完全免费，请勿轻信任何广告。
> 影片、图片和接口来自互联网第三方；本项目不存储、不上传、不传播任何影视资源。

首次使用建议先阅读 **[《Z影视使用指南》](./使用指南.md)**。

## 当前版本

| 项目 | 信息 |
| --- | --- |
| 应用名称 | Z影视 |
| 包名 | `com.studio.osc` |
| 当前版本 | `v1.7.7` |
| versionCode | `206` |
| 最低系统 | Android 5.0（API 21） |
| 主要设备 | Android TV、电视盒子、智能电视 |
| 其他设备 | 手机、平板横屏使用 |
| CPU 架构 | `armeabi-v7a` |
| 最新发布 | [GitHub Release v1.7.7](https://github.com/zongchenghuan0318/TVBox-fashion-UI/releases/tag/v1.7.7) |

## 下载与安装

### GitHub Release

前往 [Releases 页面](https://github.com/zongchenghuan0318/TVBox-fashion-UI/releases) 下载最新的 `Z.apk`。每个正式版本同时提供：

- `Z.apk`：正式签名安装包。
- `update.json`：应用内更新使用的版本清单。
- `SHA256SUMS.txt`：APK 的 SHA-256 校验值。

当前版本直接下载：[Z.apk v1.7.7](https://github.com/zongchenghuan0318/TVBox-fashion-UI/releases/download/v1.7.7/Z.apk)

### 电视盒子或智能电视

1. 将 APK 复制到 U 盘，或通过局域网传到电视。
2. 在电视文件管理器中打开 `Z.apk`。
3. 如果系统阻止安装未知来源应用，在系统安全设置中允许文件管理器安装应用。
4. 安装完成后打开 Z影视。

### 手机安装

直接打开 APK 安装即可。应用主要按横屏电视界面设计，手机上建议开启自动旋转或手动横屏。

### ADB 安装

适合开发调试或已连接 USB 调试设备的情况：

```powershell
adb devices
adb install -r -d Z.apk
```

应用包名为 `com.studio.osc`。正式包必须使用项目认可的发布签名，不能使用任意调试签名覆盖已安装的正式版本。

## 核心功能

### 1. 启动等待与配置初始化

启动后会显示玻璃黑风格的等待页，不会把配置源初始化过程和普通加载进度直接暴露给用户。启动遮罩会同时等待：

- 配置源初始化完成；
- 首页首张海报成功加载，或完成占位兜底。

两个条件都满足后才进入首页。配置源加载失败时，应用会按照配置容灾策略尝试其他地址或使用本地缓存。

### 2. 首页推荐

- 默认推荐数据源为豆瓣推荐。
- 首页背景海报跟随当前选中的卡片切换。
- 约 6 秒无操作后自动切换下一张卡片。
- 遥控器按键、焦点移动和手动拖动会打断自动轮播，并在操作结束后重新计时。
- 豆瓣请求失败时，会依次使用本地缓存、当前数据源推荐和其他可用数据源进行兜底。
- 选中首页卡片后，可进入对应影片的搜索结果并选择可用线路。

### 3. 九种首页卡片样式

在 **设置 → 首页卡片样式** 中可以切换：

| 样式 | 视觉特点 |
| --- | --- |
| 星轨环游 | 卡片沿弧线环绕移动，新用户默认样式 |
| 垂直升降 | 卡片上下层叠移动，保留相邻卡片露边 |
| 折扇选片 | 卡片以扇形展开并围绕焦点切换 |
| 桌面抽牌 | 卡片像桌面牌组一样错位叠放 |
| 光闸切片 | 海报以分片切换动画过渡 |
| 胶片走带 | 卡片像胶片一样横向经过 |
| 3D 海报转盘 | 卡片使用横向透视和旋转效果 |
| 穿过海报 | 选中卡片时产生穿透式视觉变化 |
| 平直横移 | 更克制的横向平移样式 |

所有首页卡片样式共用首尾循环机制：

- 真实影片列表不会被复制修改。
- 适配器使用虚拟位置映射到真实影片位置。
- 遥控器、自动轮播和手动拖动均可跨过首尾继续切换。
- 手动拖动到虚拟边界后，会自动回到中间等价位置，避免卡片停在列表尽头。
- 少量影片同屏重复的特殊处理目前未单独限制。

项目还提供了一个不影响 Android 设置的交互式选型页：[card-motion-showcase.html](./design/card-motion-showcase.html)。可以先在浏览器中体验动效，再决定是否应用到客户端。

### 4. 四种首页布局

在 **设置 → 首页布局** 中可以切换：

- **经典侧栏**：左侧固定分类栏，适合传统电视导航。
- **Apple TV 顶栏**：分类移动到顶部，内容区域横向铺开。
- **动态抽屉**：默认隐藏侧栏，需要时从左侧滑出。
- **极简沉浸**：全屏画报式布局，顶部导航按需显示。

动态抽屉布局支持：

- 内容区最左侧按遥控器左键唤起侧栏；
- 遥控器菜单键唤起侧栏；
- 唤起后自动把焦点放到当前首页分类；
- 侧栏打开时按右键进入当前分类内容；
- 再次按菜单键重新聚焦当前分类。

Apple TV 和极简沉浸布局已取消通过屏幕上半区左右滑动切换分类，避免与卡片滑动产生冲突；顶部导航和遥控器导航仍可正常切换分类。

### 5. 分类浏览

分类页包含电影、电视剧、综艺、动漫和纪录片等入口，具体分类由配置源提供。分类结果按照网格展示，选中影片后进入搜索或详情流程。

「直播」是独立入口，直播源需要在设置中配置。

### 6. 多源搜索

- 一次搜索可同时查询所有已勾选的影视源。
- 结果按照搜索源优先级排列。
- 每个源独立返回结果，部分源失败不会阻塞已经返回的结果。
- 搜索最多等待约 15 秒。
- 超时后保留已获取结果，并提供重试未响应来源的入口。
- 没有结果时会提示检查网络、配置和搜索源状态。

### 7. 详情页

详情页展示影片海报、名称、简介、类型、年份、主演、导演、评分和可播放剧集等信息，具体字段取决于数据源。

当前详情页背景使用高清海报背景：

- 使用更高分辨率加载背景；
- 已移除过强的高斯模糊；
- 保留暗色遮罩，保证详情文字可读；
- 背景随影片详情切换。

### 8. 播放

- 播放器支持项目内置播放器和 IJK 相关播放能力。
- 确定键暂停或播放。
- 左右键快进、快退，默认步长约 10 秒。
- 上键打开多集影片的选集面板。
- 下键显示控制栏。
- 遥控器长按确定键可进入临时 2 倍速。
- 播放控制栏会在一段时间无操作后自动隐藏。
- 支持自动播放下一集，并提供倒计时取消。
- 支持视频内嵌字幕、站点字幕和本地字幕文件。
- 支持多音轨选择和语言偏好匹配。
- 播放进度按来源、影片、线路和剧集保存，退出时也会保存。
- 起播超时后显示重试入口，不会无限自动重试。
- 支持画面比例、倍速、渲染方式等播放设置。

### 9. 收藏与历史

- 收藏和播放历史以网格展示。
- 支持删除模式。
- 清空操作需要二次确认。
- 播放历史可用于继续播放。

### 10. 设置与配置容灾

设置页主要包括：

- 配置接口地址；
- 配置地址库和收藏地址；
- 搜索源选择与优先级；
- 直播源；
- 首页布局；
- 首页卡片样式；
- 播放器和解码器；
- 渲染方式和画面比例；
- 字幕、音轨和跳过片头片尾；
- 安全 DNS；
- 长辈模式；
- 局域网网页；
- 检查更新。

配置地址保存前会先下载并解析，校验通过后才写入本地。当前地址不可用时，会依次尝试：

1. 当前配置地址；
2. 配置库中的地址；
3. 内置备用配置；
4. 默认配置地址；
5. 本地缓存。

直播源支持多行 M3U 或 TVBox JSON 地址，多源合并时会去重。局域网网页服务默认使用 `9978` 端口，可用于扫码打开配置页面或向电视推送内容。

## 遥控器操作

| 按键 | 首页 | 分类 / 搜索 | 播放页 |
| --- | --- | --- | --- |
| 上 / 下 | 切换导航区域或页面 | 移动焦点 | 选集、控制栏 |
| 左 / 右 | 切换卡片或导航项 | 移动焦点 | 快退 / 快进 |
| 确定 | 打开当前项目 | 确认选择 | 播放 / 暂停 |
| 菜单 | 动态抽屉唤起侧栏 | 打开相关操作 | 显示播放操作 |
| 返回 | 返回上一级 | 返回上一级 | 退出控制层或返回 |

不同布局会对左右和上下键做适配。例如动态抽屉布局在内容区最左侧按左键可唤起侧栏，菜单键也可以直接唤起侧栏；极简沉浸布局按上键或菜单键可显示顶部导航。

## 常见问题

### 首页为空或一直加载

先检查网络，再进入设置检查配置地址是否可访问、是否能解析出站点。如果豆瓣推荐受到网络限流，应用会进入短暂冷却并使用缓存或其他推荐数据。

### 搜索没有结果

确认已经勾选搜索源，并检查源的优先级和可用性。多个源同时失败时，通常与配置地址、网络、DNS 或源站限制有关。

### 播放失败

先尝试其他线路或其他来源，再检查播放器和解码器设置。不同源的播放协议、编码和防盗链策略可能不同。

### 遥控器无法进入动态抽屉侧栏

确认当前首页布局是「动态抽屉」，然后按菜单键。也可以在内容区最左侧按左键。侧栏打开后，焦点应自动落到当前分类。

### 更新失败

检查网络和系统安装权限。应用更新会校验包名、版本号、发布签名和 SHA-256；如果 APK 被替换或损坏，更新会被拒绝。

## 项目结构

```text
tvbox_source/
├─ app/                         # Android 主应用
│  └─ src/main/
│     ├─ java/                  # Activity、Fragment、Adapter、播放器入口
│     ├─ res/                   # 布局、图片、尺寸和主题资源
│     └─ assets/                # 应用内置资源
├─ player/                      # 播放器相关模块
├─ design/                      # 独立设计实验和卡片动效选型页
├─ docs/releases/               # 每个正式版本的发布说明
├─ release/                     # 应用内更新清单
├─ tools/                       # 构建、发布和测试脚本
├─ 使用指南.md                  # 面向普通用户的详细操作指南
├─ README.md                    # 项目总览
└─ README.en.md                 # 英文项目说明
```

## 开发环境

建议使用以下环境：

| 工具 | 要求 |
| --- | --- |
| Android Studio / Gradle | 使用项目现有 Gradle Wrapper |
| JDK | JDK 11 |
| Android SDK | compileSdk 30，Build Tools 30.0.3 |
| Node.js | 仅在检查 `design/` HTML 时需要 |
| ADB | 用于真机安装和调试 |

项目当前 Gradle 版本较旧，JDK 21 可能触发 `Unsupported class file major version 65` 或 Java 模块访问错误。构建 Android 项目前请优先设置 JDK 11：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-11.0.16.101-hotspot'
```

### 获取源码

```powershell
git clone https://github.com/zongchenghuan0318/TVBox-fashion-UI.git
Set-Location TVBox-fashion-UI
```

### Debug 构建

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-11.0.16.101-hotspot'
.\gradlew.bat :app:assembleDebug
```

APK 输出在：

```text
app\build\outputs\apk\debug\Z影视.apk
```

### 常用验证

```powershell
.\gradlew.bat :app:compileDebugJavaWithJavac
git diff --check
adb install -r app\build\outputs\apk\debug\Z影视.apk
```

### 正式发布

正式发布前需要：

1. 修改 `app/build.gradle` 中的 `versionCode` 和 `versionName`；
2. 新建对应的 `docs/releases/<version>.md`；
3. 确认工作区干净；
4. 确认正式签名配置有效；
5. 确认 `GITHUB_TOKEN` 或 Git Credential Manager 已配置；
6. 使用 JDK 11 执行发布脚本。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-11.0.16.101-hotspot'
.\tools\publish-github.ps1
```

发布脚本会自动完成：

- Release APK 构建；
- 包名、版本号和签名校验；
- 生成 `Z.apk`、`update.json` 和 `SHA256SUMS.txt`；
- 更新仓库内的 `release/update.json`；
- 推送 `main` 分支；
- 创建版本标签和 GitHub Release；
- 上传 Release 附件；
- 校验远程分支、标签和附件。

不要把 `release-signing.properties`、密钥文件、访问令牌或其他凭据提交到仓库。

## 技术实现概览

- Android 应用包名：`com.studio.osc`。
- 首页由 `HomeActivity`、`FirstFragment` 和 `HomeCardRecyclerView` 协作完成。
- 首页卡片样式集中在 `HomeCardMotion`，真实数据与虚拟循环位置由 `HomeHotVodAdapter` 管理。
- 首页卡片使用虚拟位置块实现首尾循环，不复制真实影片对象。
- 动态抽屉、Apple TV 顶栏、极简沉浸等布局由 `HomeActivity` 统一处理。
- 搜索任务支持超时、取消和分批结果排序。
- 图片加载使用 Glide，数据持久化使用 Room，轻量设置使用 Hawk。
- 播放器位于 `player` 模块，并由播放页控制器统一处理遥控器、触摸、字幕、音轨和续播。
- 局域网网页服务使用 NanoHTTPD，默认端口为 `9978`。
- 应用更新清单包含版本号、下载地址、文件大小和 SHA-256，安装前会校验包名、版本和签名。

## 版本记录

### v1.7.0

- 动态抽屉布局支持遥控器菜单键唤起侧栏。
- 唤起后自动聚焦当前首页分类。
- 侧栏已打开时再次按菜单键，会重新聚焦当前分类。

### v1.7.2

- 修复应用内远程更新在安装包校验失败后重复切换下载线路的问题。
- 包名、版本号或签名不匹配时立即显示明确错误，不再下载完成后反复回到 0%。
- 网络、文件大小或 SHA-256 校验异常仍会自动尝试备用下载线路。

### v1.7.3

- 发布远程更新验证版本，功能代码保持不变。
- 用于验证版本检查、下载、SHA-256 校验和正式签名覆盖安装流程。

### v1.6.9

- 详情页背景改为无模糊高清显示。
- 修复首页卡片手动拖动到循环边界后无法继续切换的问题。
- Apple TV 和极简沉浸布局取消上半屏左右滑动切换分类。
- 首页卡片样式支持首尾相接循环切换。
- 将星轨环游设为新用户默认卡片样式。
- 优化启动等待页、垂直升降叠卡和首页卡片展示效果。
- 增加交互式卡片动效选型页。

### v1.6.8 及更早版本

此前版本持续完善启动等待页、首页卡片动画、详情背景、播放器控制、配置容灾、搜索超时和更新校验。完整变更可查看 [`docs/releases/`](./docs/releases/) 和 Git 提交历史。

## 免责声明

- 本项目仅用于学习和技术研究。
- 所有影片、图片、播放接口和配置内容均来自互联网第三方。
- 本软件不提供、不存储、不上传、不传播任何影视资源。
- 使用者应自行确认所使用内容符合当地法律法规和相关服务条款。
- 因配置第三方接口、播放第三方内容或使用本项目产生的任何后果，由使用者自行承担。
- 如果相关内容涉及侵权，请联系对应内容提供方或项目维护者处理。

## 交流

- QQ：2020997318
- 使用问题请先阅读 [《Z影视使用指南》](./使用指南.md)。

## 赞助

项目维护和测试需要持续投入。如果项目对你有帮助，可以在项目页面支持开发工作。

<p align="center">
  <img width="720" alt="赞助二维码" src="https://github.com/user-attachments/assets/7945bc38-8b5f-486d-91c3-e85836f8c9c0" />
</p>
