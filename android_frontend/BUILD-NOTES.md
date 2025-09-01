Build notes:
- This app uses AppCompat only; no Material dependencies.
- Environment variables are read at runtime via System.getenv(): AI_BASE_URL (required), AI_API_KEY (optional).
- Adaptive launcher icon is defined in mipmap-anydpi-v26/ic_launcher.xml, with a legacy drawable reference in the manifest as fallback.
- If aapt reports parseDebugLocalResources errors, check app/build/intermediates/packaged_res/debug/packageDebugResources/values/values.xml and confirm there are no duplicate <item> names across files.
