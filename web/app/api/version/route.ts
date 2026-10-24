import { NextResponse } from "next/server";

// Update check endpoint for the UnliTube app.
// Bump these values on every public release and set apkUrl to the
// GitHub Release asset URL. sha256 must match the file on the release.
export async function GET() {
  return NextResponse.json({
    app: "UnliTube",
    versionName: "0.3.0",
    versionCode: 3,
    apkUrl: "",
    sha256: "",
    notes: "Phase 1 slice 2: channel pages with video lists, plus real comments with replies and load-more on the watch page. Signed release APK arrives once the signing keystore is set up.",
    mandatory: false,
  });
}
