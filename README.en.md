<img width="2358" height="1080" alt="3a333afb64c43b7d1875991f8ceb6252" src="https://github.com/user-attachments/assets/85c6f696-fd7e-4018-9085-115b589f4139" />
<img width="2358" height="1080" alt="fb49b4a6c40a7d93a88ed2e6d3576992" src="https://github.com/user-attachments/assets/f810db62-0641-4fca-aa2d-0028b98c831c" />
<img width="2358" height="1080" alt="7707b998cc63466a2fe7e06b92029893" src="https://github.com/user-attachments/assets/8b7c8e99-ab26-4fca-ae25-24bb6a4a99d9" />
<img width="2358" height="1080" alt="9aa364a29bd910b53affa1123cb48d89" src="https://github.com/user-attachments/assets/750c239d-13a9-4dad-a699-66edbb69014f" />
<img width="2358" height="1080" alt="133a8cc6bbccfc5c0dc8ebd7da8060c9" src="https://github.com/user-attachments/assets/0bda9125-1813-4af9-bf33-d7e083d4d6da" />

# Z影视 (ZYingshi)

Z影视 is a video aggregation client designed for TV screens. It suits TV boxes and smart TVs, and also works on landscape phones. It is fully usable with a TV remote.

It does not provide any video itself. It brings together the video sources you configure: search once, every checked source is queried together, results are ordered by your source priority, and you can play what you pick.

> The app and the video interfaces are free. Do not trust any advertisements.
> Video resources come from third parties on the Internet. This software does not store or distribute any video content.

For first-time setup, see the **[Z影视 User Guide](./使用指南.md)** (Chinese).

| Item | Details |
| --- | --- |
| Current version | v1.5.3 |
| Requirements | Android 5.0 or later |
| Devices | TV boxes and smart TVs (primary); phones and tablets in landscape |
| CPU architecture | armeabi-v7a only |
| App updates | Check manually in Settings, or enable automatic check at launch |

---

## Features

### Home
- The default data source is Douban recommendations, listed first.
- Recommendation cards are arranged in an arc. The selected card sits at the top and is largest; cards on either side step down and dim.
- The full-screen background poster follows the selected card.
- After 6 seconds with no input, the home screen advances to the next card. Any key press or focus change stops the timer and restarts it.
- Home has several fallbacks: if the Douban request fails, the app uses a local cache, then the current source's recommendations, then another available source.
- Selecting a card searches its title directly.

### Categories
- The sidebar categories are fixed: Movies, TV Series, Variety, Anime, Documentaries.
- Live TV has its own entry. Live sources are configured in Settings.
- Selecting a card searches its title.

### Search
- One search queries every checked source at once.
- Results follow the order of the checked sources, so higher-priority sources appear first.
- Everything is checked by default. The default priority is `文采┃秒播`, then `非凡┃影视`, then the others. Once you reorder the list, your order is used instead.
- Search waits up to 15 seconds. If some sources have not responded, a short notice appears and a Retry button is shown. Results found so far stay on screen.

### Favorites and history
- Both are shown as grids and support a delete mode.
- Clearing either list asks for confirmation first.

### Playback
- **Aspect ratio** cycles through Default → 16:9 → 4:3 → Fill → Original → Crop. The default is Default.
- **Speed** cycles through 0.5 → 1.0 → 1.5 → 2.0 → 3.0 → 0.5.
- **Remote**: OK plays or pauses. Left and right seek about 10 seconds, and holding them speeds up. Up opens the episode list when there is more than one episode. Down shows the control bar.
- **Long-press speed-up**: holding OK during playback runs at 2×. On touch screens, holding the picture gives 2×, dragging right gives 3×, and swiping down locks the speed.
- **Lock**: press any direction key to show the lock icon, then press OK to unlock.
- **Auto-hide**: the control bar hides after about 4 seconds and the bottom bar after about 6 seconds (about 10 seconds with Senior Mode).
- **Auto next episode**: an 8-second countdown appears after an episode ends. You can cancel it or play the next episode immediately.
- **Subtitles and audio**: embedded subtitles, source subtitles and local subtitle files (SRT, VTT, ASS, SSA). The audio track is matched by your language preference.
- **Resume**: the position is saved every 5 seconds and when you leave. The same episode resumes from the saved point. A finished episode clears its position.
- **Start timeout**: if no picture appears within 20 seconds, the app shows "播放超时，请检查线路或网络" with a retry button. It does not retry automatically.

### Settings
- **Config address**: the address is verified before it is saved. Failures show the reason, plus the total site count and the searchable site count.
- **Config library and saved addresses**: choose from saved addresses.
- **Automatic fallback**: if the current address cannot be reached, the app tries the config library, built-in backup addresses and the default address in turn. If all fail, it uses the cached config.
- **Live sources**: enter one M3U or TVBox JSON source per line. Sources are merged.
- **Playback options**: player, IJK decoder, render mode, aspect ratio, subtitles and audio, skip intro/outro, step length, secure DNS.
- **Senior Mode**: larger text and longer control-bar display.
- **LAN web page**: scan a QR code to open the current config on a phone, or push content to the TV (default port 9978).
- **Check for updates**: manual, or automatic at launch.

---

## Recent updates

- **1.5.6**: Improved seeking and playback progress persistence, with HLS prefetch support.
- **1.5.5**: Enlarged home-screen media cards for better readability on large displays.
- **1.5.3**: Faster category switching and resume.
- **1.5.2**: Hid the category filter button.
- **1.5.1**: Fixed a crash when opening the search page.
- **1.5.0**: Upgraded the image loader and added poster preloading.
- **2026-10-24**: Playback improvements: long-press speed-up, subtitle and audio selection, skip intro/outro, more accurate resume.
- **2026-10-20**: Config fallback, config library and saved addresses, live source settings, 15-second search timeout, start-timeout notice.

---

## FAQ

- **No results on home or search**: check your network, then check that the sources in Search Source Management are checked.
- **Home spins or stays blank**: Douban may be rate-limiting your network. The app pauses direct Douban requests for about 5 minutes. Wait a few minutes before trying again.
- **Video does not play**: try another line or another source. If it still fails, change the player or the IJK decoder.

See the [User Guide](./使用指南.md) for more questions (Chinese).

---

## Technical details

### Home
- Cards are drawn by `HomeCardRecyclerView`. Arc placement, scale and opacity depend on position. Switching takes 350 ms.
- Card sizes come from the `home_card_*` dimension tokens. `values-sw600dp` provides large-screen overrides.
- The background poster is an Activity-level layer, so it does not linger after switching pages. Posters are sharpened with `SharpenTransformation`.
- The auto-scroll interval is 6000 ms (`AUTO_SCROLL_DELAY`).
- After a Douban rate-limit, the app enters a 5-minute cooldown (`FirstFragment`). The cooldown is kept in memory only, so restarting the app clears it.

### Search
- Search tasks are managed with `Future` and are cancelled when the page is destroyed or a new search starts.
- Results are sorted by the checked-source order after each batch.
- The search timeout is 15 seconds (`SearchActivity`).
- The default priority is defined in `DefaultConfig.SEARCH_SITE_PRIORITY`.

### Playback
- The player wraps IJKPlayer and ExoPlayer (`player` module). Third-party players are not offered in the player list.
- The resume key is built from source, title, line and episode URL (`PlayActivity.buildEpisodeProgressKey`). The position is saved every 5 seconds.
- The start timeout is 20 seconds (`playbackStartTimeout`). On timeout, the app shows an error with a retry option.
- Speed steps are `{0.5, 1.0, 1.5, 2.0, 3.0}` (`VodController`).
- Aspect-ratio values use the `VideoView.SCREEN_SCALE_*` constants. The default is `SCREEN_SCALE_DEFAULT` (index 0).
- Remote long-press OK starts a 2× boost after 400 ms (`BaseController.startKeySpeedBoost`), and only while playing.
- Touch speed boost is managed by `SpeedGestureState` (states: 2×, 3×, locked).
- Subtitles are parsed by `ExternalSubtitle`, with separate handling for ASS/SSA and SRT/VTT.
- Control-bar timeouts are set with `setDismissTimeout`: 4 seconds normally, 10 seconds in Senior Mode. The bottom bar hides after 6 seconds (10 seconds in Senior Mode).
- The auto-next countdown is 8 seconds (`AUTO_NEXT_COUNTDOWN_SECONDS`).

### Config sources
- Verification is done by `ApiConfig.verifyConfig`: download, parse JSON, then count sites.
- Fallback order: current address, config library, `DEFAULT_API_SAVED_CONFIGS`, default address. The read timeout during fallback is 7 seconds.
- Built-in sources (`DefaultConfig.EXTRA_SITES`) are Feiyan (非凡), Liangzi (量子), Baofeng (暴风) and Jisu (极速). They are placed at the top of the site list.
- Live source input may start with `{`, `[` or `#EXTM3U`. Entries are de-duplicated with a `LinkedHashSet`.

### Updates
- The primary update source is the Gitee release. The fallback is the GitHub release (`UpdateClient`).
- GitHub downloads try `gh-proxy.com`, `ghfast.top` and then a direct link.
- The update manifest includes a SHA-256 checksum, and the APK must match it before installation.
- Installation goes through the system installer. There is no silent install.

### Other
- Image loading uses Glide 4.14.2. Networking uses OkGo 3.0.4 and OkHttp 3.12.1.
- Storage uses Room 2.3.0 and Hawk 2.0.1.
- Script runtimes: QuickJS (`wang.harlon.quickjs` 2.4.5) and Chaquopy Python. The bundled `tvbox.jar` is loaded with `DexClassLoader`.
- The LAN service uses NanoHTTPD 2.3.1 (`RemoteServer`, default port 9978).

Source layout, packages and build instructions are in [docs/开发说明.md](./docs/开发说明.md) (Chinese).

---

## Disclaimer

- This project is for study and technical research only. All films, images and interfaces come from third parties on the Internet. This software does not provide, store or distribute any video resources.
- The software and video interfaces are completely free. Do not use them commercially, and do not pay for this software in any app store.
- You are responsible for any consequences of using this software. The developer accepts no liability.
- If you believe your rights have been infringed, please contact us to have the content removed.

## Contact

- QQ: 2020997318
- For questions, contact the developer.

---

## Support

Development takes a lot of work. If you can, consider a donation.
<img width="1080" height="1474" alt="9e5eb1a90ee0cdc01a13084787e6f4fc" src="https://github.com/user-attachments/assets/7945bc38-8b5f-486d-91c3-e85836f8c9c0" />
