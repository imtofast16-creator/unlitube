import { NextResponse } from "next/server";

// Update check endpoint for the UnliTube app.
// Bump these values on every public release and set apkUrl to the
// GitHub Release asset URL. sha256 must match the file on the release.
export async function GET() {
  return NextResponse.json({
    app: "UnliTube",
    versionName: "0.2.0",
    versionCode: 2,
    apkUrl: "",
    sha256: "",
    notes: "Phase 1: live home feed, real search with pagination, watch page with adaptive quality playback, speed control, and Up next. Signed release APK arrives once the signing keystore is set up.",
    mandatory: false,
  });
}
