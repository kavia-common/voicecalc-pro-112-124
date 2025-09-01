Resources cleanup notes:
- Consolidated AppCompat themes in res/values/themes.xml.
- Removed MaterialComponents dependencies and resources.
- Removed duplicate styles (KeypadButton, KeypadButtonAccent) from styles.xml; single source now in themes.xml.
- Toolbar uses VoiceCalcToolbarStyle (AppCompat) and android:title for compatibility.
