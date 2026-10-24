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
          <li>1.0.0, first complete build: everything below, plus mini player, background playback and picture in picture, persisted settings with AMOLED theme and Lite Mode, a local Room library (subscriptions, history with resume, likes, Watch Later, playlists, subscription feed), downloads with offline playback, SponsorBlock skipping with Undo, an in-app update checker, Distraction Shield, Focus Mode timestamped notes with share-as-text, ClipForge timestamp clip links, and the premium UI redesign (hero card, compact rows, floating pill nav, floating mini player, rounded watch player with chip actions). Debug APK only, the signed public release still needs the signing keystore.</li>
          <li>0.9.0, UI polish: premium redesign, quiet charcoal with violet as the only accent, hero card, compact rows, floating pill nav and mini player, rounded watch player with chip actions.</li>
          <li>0.8.0, slice 7: SponsorBlock skipping with Undo, in-app update checker, Distraction Shield switches.</li>
          <li>0.7.0, slice 6: downloads to the app private files dir with offline playback and a Library downloads section.</li>
          <li>0.6.0, slice 5: local Room library, subscriptions, history with resume, likes, Watch Later, playlists.</li>
          <li>0.5.0, slice 4: persisted settings, AMOLED theme, Lite Mode, quality cap.</li>
          <li>0.4.0, slice 3: mini player, background playback service, picture in picture.</li>
          <li>0.3.0, Phase 1 slice 2: channel pages (banner header, avatar, subscriber line, expandable description, full video list) reached from any video card or the watch page, and real comments on the watch page with author avatars, like counts, pinned markers, expandable replies, and load-more pagination.</li>
          <li>0.2.0, Phase 1: live home feed powered by on-device InnerTube calls, real search with load-more pagination, watch page with adaptive quality playback (merged video and audio streams), quality picker, playback speed menu, descriptions, share, and an Up next rail. Embed fallback player when a video has no direct stream.</li>
          <li>0.1.0, Phase 0: project scaffold, brand, design system, custom player shell, CI build pipeline, and this website. The live feed arrives in Phase 1.</li>
        </ul>
      </section>
    </main>
  );
}
