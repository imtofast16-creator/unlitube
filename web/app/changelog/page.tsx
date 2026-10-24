export default function Changelog() {
  return (
    <main className="wrap">
      <nav className="nav">
        <span className="logo-badge">&#9654;</span>
        <span className="word">UnliTube</span>
        <span className="spacer" />
        <a className="link" href="/">Home</a>
      </nav>
      <section className="section">
        <h2>Changelog</h2>
        <ul>
          <li>0.2.0, Phase 1: live home feed powered by on-device InnerTube calls, real search with load-more pagination, watch page with adaptive quality playback (merged video and audio streams), quality picker, playback speed menu, descriptions, share, and an Up next rail. Embed fallback player when a video has no direct stream.</li>
          <li>0.1.0, Phase 0: project scaffold, brand, design system, custom player shell, CI build pipeline, and this website. The live feed arrives in Phase 1.</li>
        </ul>
      </section>
    </main>
  );
}
