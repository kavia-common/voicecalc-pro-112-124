Troubleshooting parseDebugLocalResources:
- Ensure all values/*.xml files are well-formed and contain only valid resource types.
- Avoid duplicate resource names across different files.
- Adaptive icon: keep only one ic_launcher.xml under mipmap-anydpi-v26; use a legacy drawable icon in the manifest if needed.
- If errors persist, inspect app/build/intermediates/packaged_res/debug/packageDebugResources/values/values.xml for the exact offending line.
