import { NextResponse } from "next/server";

// Update check endpoint for the UnliTube app.
// Bump these values on every public release and set apkUrl to the
// GitHub Release asset URL. sha256 must match the file on the release.
export async function GET() {
  return NextResponse.json({
    app: "UnliTube",
    versionName: "0.1.0",
    versionCode: 1,
    apkUrl: "",
    sha256: "",
    notes: "Phase 0 scaffold build. Live feed arrives in Phase 1.",
    mandatory: false,
  });
}
