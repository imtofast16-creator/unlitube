import { NextResponse } from "next/server";

// Update check endpoint for the UnliTube app.
// Bump these values on every public release and set apkUrl to the
// GitHub Release asset URL. sha256 must match the file on the release.
export async function GET() {
  return NextResponse.json({
    app: "UnliTube",
    versionName: "1.0.0",
    versionCode: 11,
    apkUrl: "https://github.com/imtofast16-creator/unlitube/releases/download/v1.0.0/UnliTube-1.0.0-debug.apk",
    sha256: "d611362e9eacd05d6ee44b619516b27753cb14675e5d7ffcdd0e9a2e60d464e9",
    notes: "1.0.0 first complete build: live feed, search, watch with custom player, channel pages, comments, mini player, background play, PiP, local library, downloads with offline playback, SponsorBlock, update checker, Distraction Shield, Focus notes and ClipForge, plus the premium UI redesign. Debug APK, the signed public release still needs the signing keystore.",
    mandatory: false,
  });
}
