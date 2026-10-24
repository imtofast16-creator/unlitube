const features = [
  {
    tag: "Only on UnliTube",
    title: "Triple Engine playback",
    body: "On-device playback first, with the official embed player as fallback when a video has no direct stream. No video ever passes through UnliTube servers.",
  },
  {
    tag: "Custom player",
    title: "Ultra quality, zero jank",
    body: "Up to 4K and 8K adaptive, HDR, per side double tap to seek, speed control, quality picker, and a clean custom control bar.",
  },
  {
    tag: "Only on UnliTube",
    title: "Distraction Shield",
    body: "Hide Shorts, hide comments, hide related videos, or go search only. Your feed, your rules, one toggle each.",
  },
  {
    tag: "Only on UnliTube",
    title: "Focus Mode",
    body: "Timestamped notes on any video. Tap a note, jump to that exact second, and share your notes as text.",
  },
  {
    tag: "Only on UnliTube",
    title: "ClipForge",
    body: "Mark a start and end on any video, save the clip, and share a link that opens at that exact moment.",
  },
  {
    tag: "Full YouTube",
    title: "Subs, likes, comments, Live, Shorts",
    body: "Home, search, channel pages, comments, playlists, history and Shorts folders, all in guest mode with everything stored on your phone.",
  },
  {
    tag: "Performance",
    title: "Built for low end phones",
    body: "Auto Lite Mode on 3GB RAM phones, APK around 21MB, and a quiet charcoal UI that keeps thumbnails front and center.",
  },
  {
    tag: "Standard",
    title: "Background, PiP, downloads",
    body: "Background audio, floating mini player, picture in picture, video downloads with offline playback, SponsorBlock, AMOLED black theme.",
  },
];

export default function Home() {
  return (
    <main className="wrap">
      <nav className="nav">
        <span className="logo-badge">&#9654;</span>
        <span className="word">UnliTube</span>
        <span className="spacer" />
        <a className="link" href="/install">Install guide</a>
        <a className="link" href="/changelog">Changelog</a>
        <a className="link" href="/faq">FAQ</a>
      </nav>

      <section className="hero">
        <h1>
          Unli watching, <span className="grad">no interruptions.</span>
        </h1>
        <p>
          A fast, beautiful Android client for YouTube with a custom ultra
          quality player, real downloads, background play, and tools neither
          YouTube nor Pure Tuber has. Free, and it runs great on low end phones.
        </p>
        <div className="cta-row">
          <a className="btn btn-primary" href="/install">Download the APK</a>
          <a className="btn btn-ghost" href="#features">See the features</a>
        </div>
        <p className="note">Android only. No Play Store needed, install in under a minute.</p>
      </section>

      <section className="grid" id="features">
        {features.map((f) => (
          <article className="card" key={f.title}>
            <span className="tag">{f.tag}</span>
            <h3>{f.title}</h3>
            <p>{f.body}</p>
          </article>
        ))}
      </section>

      <section className="section">
        <h2>Honest about the name</h2>
        <p>
          Unli means unlimited watching, no ad interruptions and no forced
          stops. It does not mean unlimited mobile data, your internet plan is
          still yours. UnliTube is not affiliated with YouTube or Google.
        </p>
      </section>

      <footer className="footer">
        UnliTube - built for viewers, not advertisers.
      </footer>
    </main>
  );
}
