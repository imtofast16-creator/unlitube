import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "UnliTube - Unli watching, no interruptions",
  description:
    "UnliTube is a fast, polished Android YouTube client: custom player, background play, downloads, and tools YouTube and Pure Tuber do not have. Free APK, no Play Store needed.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
