# UnliTube: Deep Build Plan
LOCKED 2026-10-24 by Joel: Name UnliTube. Package ID app.unlitube.android. Vercel slug target unlitube.vercel.app. Tagline: "Unli watching, no interruptions." Accent: violet to coral gradient (plan recommendation, adopted as default). Working folder: unlitube.
Date planned: 2026-10-24. Owner directive: Vercel site plus sideloaded Android APK, no Play Store, no App Store, super polished UI, smooth on low end phones, features beyond YouTube and Pure Tuber.

## 0. Reality check, checked 2026-10-24
- This VM has Node 24 and npm, 90GB free disk, but no Java, no Gradle, no Kotlin, no Flutter, and no Android SDK installed.
- Fix: primary build route is GitHub Actions CI. GitHub runners ship with Java and the Android SDK, build the signed APK there, and attach it to a GitHub Release. Backup route is installing Android command line tools in this VM later if CI is blocked.
- Final proof rule: an APK is not called working until Joel installs it on his real phone and plays real videos. Emulator passes are not final proof.
- Distribution risk kept plain: a client that removes ads and unlocks background play cannot go on Play Store. Sideload only, personal download site only.

## 1. Product promise
YouTube, rebuilt the way a phone user actually wants it: fast on a cheap phone, no clutter, no forced Shorts loop, real downloads, real background audio, and tools for learning and music that neither YouTube nor Pure Tuber ships.

One line pitch, pending name: "Your YouTube, without the noise."

## 2. System shape, 3 parts
1. Android APK (the product)
   - Native Kotlin, single activity, Jetpack Compose UI, Media3 ExoPlayer.
   - Runs the extractor on the phone, so requests use the user home or mobile IP. No video ever passes through Vercel.
2. Vercel website (the storefront)
   - Next.js site: home, features, screenshots, install guide, changelog, FAQ, QR code to the download.
   - Serves version.json for in app update checks. Download button points to the latest GitHub Release APK.
3. GitHub repo plus Releases (the factory)
   - Source, GitHub Actions build and sign pipeline, release notes, rollback to any older APK.

## 3. Triple Engine playback, the core unique idea
No single engine, because every YouTube client dies when its one engine breaks.
- Engine A, on device extractor: NewPipeExtractor style InnerTube calls from the phone. Primary. Gives search, home, trending, channel, comments, related, and direct stream URLs.
- Engine B, Piped API fallback: if Engine A fails on a video, the app silently tries public Piped instances for the same video ID and plays that stream.
- Engine C, official embed fallback: if A and B both fail, the watch page falls back to the official embedded player so the video still plays, with a small badge saying Fallback Mode.
- The UI never changes between engines. The user just sees the video play.

## 4. Feature plan

### 4A. Parity, must match Pure Tuber in v1
- Ad free playback in the app player, SponsorBlock segment skip, Return YouTube Dislike data where available
- Background audio play with screen off, picture in picture, floating mini player
- Downloads: video 144p to 1080p and audio only, pause and resume, download queue
- Search with filters and suggestions, trending, channel pages, playlists, comments and replies
- Local subscriptions without a Google account, local playlists, history, watch later
- Quality picker, playback speed 0.25x to 3x, subtitles with size and style controls, sleep timer
- Dark and true black AMOLED themes
- Full YouTube surface, per Joel directive 2026-10-24: Home feed, Subscriptions feed, Shorts feed and viewer, Live streams and live premieres, channel pages with Videos, Shorts, Live, Playlists, Community tabs, video likes and dislikes, channel subscribe and bell, comments, replies, comment likes, playlists (YouTube and local), history, search filters (video, channel, playlist, Shorts, Live). Detail in Section 4E.

### 4B. Unique, not in YouTube and not in Pure Tuber, v1 ships 1 to 5
1. Triple Engine playback, above. App survives extractor breakage instead of dying for days.
2. Distraction Shield: user toggles that hide Shorts, hide comments, hide related videos, hide home feed entirely (search only mode). Per toggle, instant.
3. Focus Mode for study videos: timestamped notes tied to the video, auto generated chapters from captions, tap a note to jump back to that second, export notes as text.
4. ClipForge: pick any start and end inside a video, save or share just that clip, video or audio, no full download needed.
5. Custom Feeds: group local subscriptions into folders like Study, Music, Gaming, News. Each folder is its own clean feed.
6. Smart Downloads, v1.5: auto download new videos from chosen channels on WiFi only, with a storage cap that auto cleans oldest first, audio only option for podcast channels.
7. Gesture Studio, v1.5: user assigns gestures, left and right double tap seconds, swipe actions for brightness, volume, or seek, long press for 2x.
8. Per channel presets, v1.5: remember speed, quality, and subtitle choice per channel.
9. Loudness Normalizer plus Karaoke Captions, v2: even volume across videos, big stage captions that follow the words for music.
10. Watch Stats and Breaks, v2: private on device dashboard, time today, top channels, gentle break reminder at a user set limit. No data leaves the phone.
11. Privacy Vault, v2: incognito sessions that save nothing, optional PIN for history and downloads, one tap clear everything.
12. Phone to Web Continue, v2: QR pair the APK with the Vercel site, continue watching and playlists sync through your own list, still no Google account.

### 4C. Two account modes, this replaces the old no login rule per Joel directive 2026-10-24
Reading is public. Writing is not. Real likes, real subscribe, real comment posting, and real live chat posting only exist on YouTube if the request carries a signed in Google session. Any app that claims otherwise is faking the count locally. So the app ships two clear modes:

- Guest Mode, default, no sign in: everything public works. Browse, search, watch, Shorts, Live, read comments and live chat, local subscribe (saved only in the app), local playlists, local liked videos list, downloads, history. Zero account risk.
- Signed In Mode, optional, user chooses it: sign in inside the app through the official Google sign in web view. The session stays only in the app private storage on that phone, never sent to Vercel or any server of ours. With it the app calls the authenticated InnerTube actions: real subscribe and unsubscribe with bell state, real like and dislike, post comments and replies, like comments, post in live chat, and a Subscriptions feed synced from the real account. The account screen shows exactly which mode is active, a sign out that wipes the session, and a warning in plain language that using a third party client can be flagged by Google, so a secondary account is the safer choice.
- Mode rule: no action is ever faked. If the user is in Guest Mode and taps Like on YouTube, the sheet explains it will be saved to the local Liked list, or offers Sign In to like on YouTube. Honest UI, no fake counts.
- Still not in v1: no video uploading, no channel management, no iOS app (Apple blocks this install model).

### 4D. Custom video player deep spec, per Joel directive 2026-10-24
Our own player, not the stock YouTube player and not a web view. Built on Media3 ExoPlayer with a fully custom Compose control layer, designed to beat Pure Tuber on smoothness, quality, and control.
- Ultra quality: adaptive DASH playback up to the highest format the video offers (4K and 8K where available), VP9 and AV1 hardware decoding when the phone supports it, HDR passthrough on HDR screens, and a quality menu that shows real labels (1080p60, 720p, Audio HQ) plus file size estimate per quality. Quality lock, per video, per channel, or global. Auto quality uses network speed plus a buffer health check, never drops mid scene if the buffer is healthy.
- Smoothness engine: triple buffered playback, 3 second pre roll buffer before first frame, next video stream prefetch while watching (the next queue or related video resolves in the background so tap to next feels instant), seek preview thumbnails on the progress bar, instant resume from the exact frame, frame accurate seek on long press of the progress bar.
- Zero jank rule: player controls animate at 60fps with hardware accelerated layers only, no blur or shadow effects inside the fullscreen player, controls fade in 180ms and out in 220ms, and the player works fully in Lite Mode on 3GB RAM phones.
- Controls, all custom: double tap left or right to seek with animated seconds (custom amounts per side), single tap to toggle controls, swipe left half for brightness, right half for volume with on screen level bars, swipe up or down on the right edge for playback speed scrub, pinch to zoom and crop to fill, drag down to drop into the floating mini player, long press anywhere for temporary 2x speed with a release to normal feel.
- Playback tools: speed 0.25x to 3x in 0.05 steps with pitch correction, AB loop for a section, sleep timer with fade out and shake to extend, audio only lock screen mode with big album style art, background play that survives app swipe in recents via a foreground media service, picture in picture that keeps custom controls, Chromecast style queue handoff is v2.
- Smart layer inside the player: SponsorBlock auto skip with a small undo toast, chapters rail under the progress bar (tap a chapter name to jump), caption stage with font, size, background and position controls plus karaoke follow mode in v2, loudness normalize toggle, stats for nerds overlay (codec, dropped frames, buffer, network) hidden behind a long press on the quality button.
- Live and Shorts players are separate custom surfaces, not the same player reused: Live gets low latency mode, DVR rewind where YouTube allows it, and a chat overlay toggle. Shorts gets a vertical pager with preloaded next and previous, zero black flash between shorts, hold to pause, and a speed and loop control Pure Tuber does not offer.
- Player test gate: cold open to first frame under 1.5 seconds on the floor device on WiFi, seek under 300ms, quality switch without audio restart, zero dropped audio in background for 30 minutes. Any regression blocks release.

### 4E. Full YouTube feature matrix, per Joel directive 2026-10-24
Every row ships or is honestly marked by mode.
- Home feed: Guest yes (public feed), Signed In yes (personalized feed)
- Search and filters (videos, channels, playlists, Shorts, Live, upload date, duration): yes both modes
- Subscriptions: Guest local only (own list and feed built in app), Signed In real YouTube subscriptions synced, subscribe, unsubscribe, bell levels
- Likes and dislikes: Guest local liked list, Signed In real like, dislike, and remove rating, with Return YouTube Dislike estimate shown
- Comments: Guest read top and newest, replies, comment likes count, Signed In plus post, reply, edit own, delete own, like comments, heart and pin shown where YouTube exposes them
- Live: watch live and premieres both modes, live chat read both modes, live chat post Signed In only, chat replay on ended streams where available
- Shorts: full vertical feed, like (mode rule applies), comments read, share, save, channel jump, remix audio info shown where exposed
- Channels: Videos, Shorts, Live, Playlists, Community posts read, About, subscribe button with live subscriber count
- Playlists: YouTube public playlists play both modes, local playlists both modes, Watch Later local in Guest, real Watch Later Signed In
- Notifications: local new video checks for local subscriptions in Guest (battery friendly, user interval), real account notifications view Signed In
- Sharing: Android share sheet, copy link, timestamp link, QR for a video, ClipForge clip share
- Not offered: uploading, Studio analytics, memberships and payments, Super Chat sending (shown in chat, not sendable)

## 5. UI and design system, million dollar standard
- Brand direction, pending name: deep charcoal base #0B0B10, one electric accent (violet to coral gradient used only on play actions, progress, and the logo, never as wall paint), clean white text, muted gray secondary text. Deliberately not YouTube red, so it reads as its own product.
- Type: Plus Jakarta Sans for headings, Inter for body. Large confident titles, small quiet metadata.
- Layout: 8pt spacing grid, 16px screen padding, 12 to 16px card radius, thumbnails always 16:9 with duration and quality badges, bottom nav with 4 tabs only (Home, Search, Library, You) plus a center downloads shortcut in Library.
- Screens: Splash (under 600ms), Onboarding (3 swipes, skip always visible), Home with feed folders, Search with recent and trending chips, Watch (player, actions row, chapters, comments, related), Channel, Shorts viewer (only if Shield allows Shorts), Library (history, playlists, subscriptions, downloads), Focus view, Clip editor, Stats, Settings in grouped sections, Update prompt sheet.
- Motion: every animation 150 to 250ms, shared element thumbnail to player transition, skeleton shimmer lists while loading, no animation longer than 300ms, no bounce physics. Animations auto reduce on low end mode.
- Player UX: double tap sides to seek with ripple and seconds label, swipe up for next, pinch to zoom, drag brightness left side and volume right side, mini player that survives navigation, queue drawer from the player.
- Responsive: phone portrait first, landscape player fills screen, tablet gets 2 column grids and a wider watch layout, foldable safe insets respected.
- Accessibility: all touch targets 48dp minimum, captions styling, contrast checked, talkback labels on player controls.

## 6. Performance budget, low end is a hard gate
Target device floor: 3GB RAM, Android 10, entry level chipset.
- APK size under 28MB, cold start to first feed under 1.2 seconds on the floor device, scroll holds 60fps with no visible jank on thumbnails.
- Lite Mode auto detection: if RAM is 3GB or less, or Android Go, the app disables blur, gradient animation, and shimmer, lowers thumbnail resolution, and reduces prefetch. Lite Mode is also a manual toggle.
- Images through Coil with exact size downsampling and memory plus disk cache caps. Lists paginate at 20, prefetch only 5 ahead in Lite Mode, 10 otherwise.
- Single activity Compose, no heavy libraries, R8 shrink and obfuscate release builds, baseline profiles for startup and scroll.
- Player starts at an adaptive quality based on network, never defaulting to 1080p on mobile data.
- Every release must pass: install on the floor emulator profile, play 10 videos across engines, download 1 video and 1 audio, background play 5 minutes. Any fail blocks the release.

## 7. Data and privacy
- Everything local: Room database for subscriptions, playlists, history, notes, downloads. Nothing about viewing goes to Vercel.
- Vercel only sees website visits and update checks (app version and Android version, nothing personal).
- No ads SDK, no analytics SDK in v1. Crash reports only if Joel approves later.

## 8. Build phases
- Phase 0, setup: lock name and package ID (cannot change after first public APK), create repo, keystore generated and backed up by Joel, GitHub Actions pipeline builds a hello world signed APK, Vercel site skeleton live with a placeholder download.
- Phase 1, core: Engine A browsing and playback, search, watch page, channel, comments read, mini player, background audio, PiP, themes, Lite Mode.
- Phase 2, parity plus unique 1 to 5: downloads, SponsorBlock, local subscriptions and Custom Feeds, Distraction Shield, Focus Mode, ClipForge, update checker.
- Phase 3, polish gate: full UI pass against the design system, motion timing pass, floor device performance test, 10 video proof run, bug sweep.
- Phase 4, launch: Vercel site complete with screenshots from the real APK, install guide with Play Protect explainer, QR code, changelog, release v1.0.0 signed APK on GitHub Releases.
- Phase 5, v1.5 and v2 features in the order listed in 4B.

## 9. Website plan, Vercel
- Pages: Home (hero, phone mockup with real screenshots, feature grid, unique features section, download CTA), Install (step by step with screenshots, unknown apps permission, Play Protect note), Changelog, FAQ, Privacy (plain language, no data collected).
- Tech: Next.js on Vercel Hobby like Joel other sites, Tailwind, static first for speed, version.json route generated from the latest release data.
- Download safety: SHA256 checksum of the APK printed on the download page so users can verify the file is the real one.

## 10. Name, checked 2026-10-24
FreeTube, Joel pick if available: NOT available, checked 2026-10-24. FreeTube is a famous open source YouTube client for Windows, Mac and Linux at freetubeapp.io, current release 0.25.3 Beta, with the exact same feature pitch (no ads, local subscriptions, SponsorBlock, distraction free settings). There is also an unofficial FreeTube Android fork and unrelated FreeTube apps already on Google Play using package com.riju.freetube with 110k installs. Using the name would make the app look like a copy or a fake of their project, search results would go to them, and it risks a trademark complaint. Do not use FreeTube, and do not use LibreTube either (also a famous Android YouTube client).
If the Free feeling is what Joel wants, the closest ownable coined options are: FreeVyn (free plus Vynlo root, keeps the free promise, invented so ownable), FreeFlow Play, and OpenVibe.
UnliTube, Joel candidate checked 2026-10-24: appears AVAILABLE at search level, unlike FreeTube. No app, APK, website, or package named UnliTube was found in web, Play Store, or APK site checks, and no unlitube.com product surfaced. Strengths: instantly understood in the Philippines (unli rice, unli data), says the benefit in one word, catchy and easy to spell. Two honest warnings: (1) Tube in the name carries the same YouTube trademark risk as Pure Tuber and LibreTube, Google can complain the name implies affiliation, and a descriptive name is hard to protect as our own trademark; (2) close neighbor names exist, UniTube (a PC video downloader) and LiTube (an Android YouTube wrapper), one letter away, so search confusion is possible. Promise rule if locked: Unli means unlimited watching and no ad interruptions in marketing, never unlimited internet or data, which the app does not provide. Final checks at Phase 0 before lock: unlitube.vercel.app slug claim, GitHub repo name, package ID app.unlitube.android availability in our own signing (package IDs are first come, ours once shipped).
Already taken by real video apps, do not use: Vidora, Lumora, Nuvio, Velora, Playora. All found live on Play Store or the web in checks today.
Fresh coined candidates, final trademark and domain check still needed before lock:
1. Vynlo (top pick): invented from video plus flow. Short, premium, ownable, easy to say, no Tube in the name so lower trademark risk. Tagline: "Watch, without the noise."
2. Vibeo: video plus vibe. Friendliest and most memorable. Tagline: "Your vibe, your videos."
3. Zavlo: invented, sharp and tech sounding, very ownable. Tagline: "YouTube, your way."
More options if none land: Tunix, Wavlo, Playvyn, Lunio, Vuno, Koda Play, Mirlo.
Package ID follows the winner, example for the top pick: app.vynlo.android. Vercel slug follows the winner, example: vynlo.vercel.app.

## 11. Decisions
1. DONE 2026-10-24: Name locked by Joel, UnliTube. Package ID app.unlitube.android, tagline "Unli watching, no interruptions."
2. Accent color: violet to coral adopted as default from the plan recommendation. Joel can switch it any time before launch, it is a theme token, not a rebuild.
3. Signed In Mode is in the plan (Section 4C). Default stays Guest Mode, sign in is the user choice inside the app.
4. Remaining Phase 0 items: claim unlitube.vercel.app slug, create GitHub repo, generate and back up the signing keystore (Joel holds the backup).
