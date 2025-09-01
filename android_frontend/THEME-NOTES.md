This app uses AppCompat (Theme.AppCompat.Light.NoActionBar). There are no MaterialComponents themes or widgets.
If you see 'style/Theme.MaterialComponents not found' during resource linking:
- Ensure no resource files define a MaterialComponents parent.
- Only values/themes.xml and values/styles.xml should define themes; both use AppCompat.
