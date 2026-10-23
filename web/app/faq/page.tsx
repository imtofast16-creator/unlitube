const faqs = [
  {
    q: "Is UnliTube free?",
    a: "Yes. The app is free, with no account required to watch.",
  },
  {
    q: "Why is it not on the Play Store?",
    a: "Apps that change how YouTube ads and Premium features work are not allowed on the Play Store, so UnliTube is installed directly from this site, like Pure Tuber and NewPipe.",
  },
  {
    q: "Does UnliTube host videos?",
    a: "No. The app on your phone talks directly to YouTube, exactly like a browser. This site only hosts the download, the install guide, and the update check.",
  },
  {
    q: "Do I need to sign in?",
    a: "No. Guest Mode covers watching, Shorts, Live, reading comments, local subscriptions and downloads. Signing in is optional and only needed for real YouTube likes, account subscriptions and posting comments.",
  },
  {
    q: "Will it run on my phone?",
    a: "UnliTube targets phones with 3GB of RAM and Android 10 or newer, with an automatic Lite Mode on lower end devices.",
  },
];

export default function Faq() {
  return (
    <main className="wrap">
      <nav className="nav">
        <span className="logo-badge">&#9654;</span>
        <span className="word">UnliTube</span>
        <span className="spacer" />
        <a className="link" href="/">Home</a>
      </nav>
      <section className="section">
        <h2>FAQ</h2>
        {faqs.map((f) => (
          <div key={f.q} style={{ marginBottom: 18 }}>
            <p style={{ color: "var(--text)", fontWeight: 700 }}>{f.q}</p>
            <p>{f.a}</p>
          </div>
        ))}
      </section>
    </main>
  );
}
