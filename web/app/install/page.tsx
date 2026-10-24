export default function Install() {
  return (
    <main className="wrap">
      <nav className="nav">
        <span className="logo-badge">&#9654;</span>
        <span className="word">UnliTube</span>
        <span className="spacer" />
        <a className="link" href="/">Home</a>
      </nav>
      <section className="section">
        <h2>Install UnliTube in under a minute</h2>
        <ol>
          <li>On your Android phone, tap Download the APK on the home page. It downloads UnliTube-1.0.0-debug.apk straight from the GitHub Release, no login needed.</li>
          <li>When Android asks, allow install from your browser (Settings, Install unknown apps, allow for your browser). This is normal for any app installed outside the Play Store.</li>
          <li>Open the downloaded file and tap Install.</li>
          <li>If Play Protect shows a notice, it is a standard warning for apps outside the Play Store. This is a debug build, not yet signed with a release key, so Play Protect may warn more strongly than a store app.</li>
          <li>Open UnliTube and watch. No account needed, Guest Mode works right away.</li>
        </ol>
        <p>
          SHA256 of UnliTube-1.0.0-debug.apk:
          d611362e9eacd05d6ee44b619516b27753cb14675e5d7ffcdd0e9a2e60d464e9
        </p>
        <p>
          Updates work the same way, and your settings, subscriptions and
          downloads carry over, because every release is signed with the same
          UnliTube key. The app also checks /api/version on this site and tells
          you when a newer version is ready.
        </p>
      </section>
    </main>
  );
}
