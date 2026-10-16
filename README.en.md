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

## Features

### Home
- Default data source is pinned to **Douban Recommendations** and placed first in the source list
- Cards with a full-screen backdrop poster; the backdrop switches in sync with the cards
- **Four-layer fallback** so the home page is never blank: Douban direct → local cache → current source recommendations → automatic switch to another available source
- Ships built-in mainstream collection sites (Feifan, Liangzi, Baofeng, Jisu) placed at the front of the site list, and prefers collection sites for the home source, avoiding unstable cloud-drive sites

### Categories
- Sidebar categories are fixed to: Home, Movie, TV Series, Variety, Anime, Documentary (other categories hidden)
- Category matching prefers **leaf categories** of each site, avoiding empty pages caused by parent categories returning zero results
- Tapping a card jumps straight to the aggregated search page by title

### Search
- One search queries all enabled sites, and **the check order is the display order**; every search screen follows it strictly
- Search source management supports per-site checkboxes and Select All / Deselect All, **defaulting to all selected**
- Default priority: `文采┃秒播` > `非凡┃影视` > the rest (a user-defined order takes precedence)
- Search history can be cleared in one tap; the section collapses when empty

### Favorites & History
- Both are grid views with a per-item delete mode
- Clearing everything requires a **confirmation dialog**

### Playback
- Default picture scale is **crop** (center-crop to fill the screen); switchable to Default / 16:9 / 4:3 / Fill / Original
- The progress bar has its own row with distinct played / buffered / track colors and clear drag and focus feedback
- The program info, clock and bottom-right notice auto-hide during playback and reappear on any key or touch

### Settings
- Config URL validation: it is saved only after validation passes, the dialog is locked while validating, failures show the exact reason, and both total and searchable site counts are displayed
- Home data source, search source management, player engine / render / scale, DNS and search layout (list / grid) settings

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
