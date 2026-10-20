# ZYingshi (Z影视)

An Android aggregated video client for TV boxes, TVs and landscape phones, built on top of the open-source [TVBoxOSC](https://github.com/CatVodTVOfficial/TVBoxOSC) project (CatVodTVOfficial).

This project ships the client only: **all video resources and third-party APIs come from the Internet. The app stores and distributes no video content.**

> The app and all video APIs are completely free. For issues, contact the developer 宗成焕.

---

## Introduction

ZYingshi is a multi-source aggregated video client. It does not provide sources itself; instead it aggregates the collection/parsing sites listed in a config file into one place: a single search queries every enabled site at once, results are ordered by the priority you configure, and playback is handled by the built-in player.

Compared with the stock TVBoxOSC, this project reworks the home, category, search, favorites/history and player pages, focusing on the issues that hurt real usage the most — blank home pages, empty category pages, jumbled search order and unsuitable picture scaling.

- Default config URL: `https://tv.xn--yhqu5zs87a.top`
- Package name: `com.studio.osc`
- Android 5.0+ (minSdk 21 / targetSdk 28)
- Current version: `v1.0.13` (versionCode 13)

## Features

### Home
- Default data source is pinned to **Douban Recommendations** and placed first in the source list
- **Four-layer fallback** so the home page is never blank: Douban direct → local cache → current source recommendations → automatic switch to another available source
- Ships built-in mainstream collection sites (Feifan, Liangzi, Baofeng, Jisu) placed at the front of the site list, and prefers collection sites for the home source, avoiding unstable cloud-drive sites
- Cards with a full-screen backdrop poster; the backdrop switches in sync with the cards
- Cards are laid out on an **arc**: the selected card sits at the apex (tallest / brightest / largest, and grows a bit more when focused) while its neighbours sink, dim and tilt outward; the focused card always stays centered and card transitions use a fixed 350 ms eased curve
- Card sizes are tightened so **more cards fit on screen**; after 6 s idle it slides to the next card, wrapping from the last card back to the first

### Categories
- Sidebar categories are fixed to: Home, Movie, TV Series, Variety, Anime, Documentary (other categories hidden)
- Category matching prefers **leaf categories** of each site, avoiding empty pages caused by parent categories returning zero results
- Tapping a card jumps straight to the aggregated search page by title

### Search
- One search queries all enabled sites, and **the check order is the display order**; every search screen follows it strictly
- Search source management supports per-site checkboxes and Select All / Deselect All, **defaulting to all selected**
- Default priority: `文采┃秒播` > `非凡┃影视` > the rest (a user-defined order takes precedence)
- **15 s timeout guard**: when it fires the search is wrapped up — results already returned stay visible, and only a completely empty search shows the "search timed out" notice
- With no source checked it does not spin: it tells you to check at least one source instead
- Search history can be cleared in one tap; the section collapses when empty

### Favorites & History
- Both are grid views with a per-item delete mode
- Clearing everything requires a **confirmation dialog**

### Playback
- Default picture scale is **crop** (center-crop to fill the screen); switchable to Default / 16:9 / 4:3 / Fill / Original
- The progress bar has its own row with distinct played / buffered / track colors and clear drag and focus feedback
- The program info, clock and bottom notice auto-hide after 5 s of inactivity and reappear on any key or touch; the "buffering…" notice is the exception — it disappears the moment buffering finishes. Any notice that carries a retry button stays on screen
- Episodes panel and gesture seeking are available straight from the player
- **20 s start timeout**: if playback has not started in time the app retries on its own, and if it still fails it shows "video playback error" with a retry entry point

### Settings
- Config URL validation: it is saved only after validation passes, the dialog is locked while validating, failures show the exact reason, and both total and searchable site counts are displayed
- Home data source, search source management, player engine / render / scale, DNS and search layout (list / grid) settings
- Settings are grouped as: config & sources (config file / default site), home & search (home recommendations / search display / search source management), playback (player / IJK decode method) and other (share with friends / about)

## Source structure

A Gradle multi-module project (`rootProject.name = "MoviePlayer"`) with two modules: `app` (the application) and `player` (the playback core). The Chinese [README.md](./README.md) documents every package in detail; the essentials:

```
tvbox_source/
├── app/            application module (applicationId com.studio.osc)
│   ├── libs/       thunder.jar, uiSupport-release.aar (closed-source UI lib)
│   └── src/main/   AndroidManifest.xml, assets (tvbox.jar, js/, parse.html),
│                   python/ (Chaquopy spiders), java/com/github/{tvbox/osc,
│                   catvod, com/fongmi/quickjs, okhttp3/dnsoverhttps}, res/
├── player/         IJK kernel (tv/danmaku/ijk) + video player framework
│                   (xyz/doikki/videoplayer), armeabi-v7a only
├── gradle/         wrapper and China mirror config
├── settings.gradle include ':app' ':player'
├── site.json       built-in site config sample
└── README.md / README.en.md / 使用指南.md / LICENSE
```

Key packages under `com.github.tvbox.osc`: `api` (config parsing and site management), `bean` (data models), `cache` (Room + local cache), `server` (LAN HTTP service and push-control), `receiver` (broadcast entry points), `player` (player kernel adapters), `ui.activity` (Home / Play / Search / Detail / Setting / Collect / History / Push …), `ui.fragment`, `ui.adapter`, `ui.dialog`, `ui.tv` (TV widgets incl. the home card view), `util`.

Tech stack: Java 8, `compileSdk 30` / `buildTools 30.0.3`, `minSdk 21` / `targetSdk 28`, armeabi-v7a, multiDex; Room 2.3.0, OkGo 3.0.4 / OkHttp 3.12.1, Glide 3.7.0, BaseRecyclerViewAdapterHelper, TV-RecyclerView, LoadSir; QuickJS 2.4.5 and Chaquopy Python 3.8 for script spiders; NanoHTTPD for the built-in server.

## Docs

| Document | Content |
| --- | --- |
| [README.en.md](./README.en.md) | This file: overview, features, source structure, build |
| [README.md](./README.md) | Chinese version, more detailed (adds per-package tables and the full changelog) |
| [使用指南](./使用指南.md) | Chinese user manual: install, first launch, remote keys, home / category / search / detail / playback / favorites, every setting explained, FAQ |

## Changelog

> See the repository commits page for the full history. Recent highlights:

### 2026-10-20
- Docs: README gained the source-structure, package and tech-stack sections; the user guide gained the version number and the search-timeout / slow-start FAQ entries
- Playback: the "buffering…" notice hides as soon as buffering ends, no longer waiting out the full 5 s
- Home: card sizes moved to `home_card_*` size tokens (with a `values-sw600dp` override), the focused card grows further, idle carousel back to 6 s
- Stability: 15 s timeout on the search chain, 20 s start timeout with request de-duplication on playback

### 2026-10-17
- Home backdrop promoted from the ViewPager fragments to an Activity-level layer, so category pages no longer leak the previous page's backdrop; sidebar glass restyle
- Home cards: ring 3D carousel → virtual infinite loop → back to a plain list loop (last card wraps to the first), arc depth reworked and cards shrunk so more fit on screen
- Player: episode panel and gesture seeking added

### 2026-10-16
- Home: Douban Recommendations pinned first, four-layer fallback so it is never blank, backdrop poster USM sharpening and `l`-grade covers, arc layout / 350 ms transitions / centered selection
- Search: all sources checked by default with a built-in priority (`文采┃秒播` > `非凡┃影视` > the rest), check order = display order
- Player: notices auto-hide after 5 s, ad notice moved into the control bar, progress bar redrawn as its own row
- Settings: grouped layout rebuild; the config-URL dialog is locked until validation finishes
- Initial commit: repackaged TVBoxOSC baseline

## Build

```bash
# debug
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk

# release
./gradlew :app:assembleRelease
```

Dependency downloads are configured with China-based mirrors (Maven → Aliyun / Tencent, Gradle distribution → Tencent mirror).
Note: Chaquopy Android wheels are only served from `chaquo.com/pypi-7.0`, so the `pip{}` block in `build.gradle` must not set an `indexUrl` mirror, otherwise ABI wheel resolution fails.

## Disclaimer

- This project is for learning and technical research only. All videos, images and APIs come from third parties on the Internet; this app does not provide, store or distribute any video resource.
- The app and all video APIs are completely free. Do not use them commercially, and do not pay for this app in any app store.
- Any consequences of using this app are borne by the user; the developer assumes no responsibility.
- If anything infringes your rights, please contact us for removal.

## Contact

- QQ group: 879811030
- Developer: 宗成焕
