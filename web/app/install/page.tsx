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
          <li>On your Android phone, tap Download the APK on the home page.</li>
          <li>When Android asks, allow install from your browser (Settings, Install unknown apps, allow for your browser). This is normal for any app installed outside the Play Store.</li>
          <li>Open the downloaded file and tap Install.</li>
          <li>If Play Protect shows a notice, it is a standard warning for apps outside the Play Store. Check the SHA256 checksum below against the app page to confirm the file is the real UnliTube release.</li>
          <li>Open UnliTube and watch. No account needed, Guest Mode works right away.</li>
        </ol>
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
