# Type & Frame

Open this directory in Android Studio, sync Gradle, select the **app** configuration, choose a device, and press **Run**. The project uses Android SDK 36 and the Gradle wrapper included in this folder. Set the SDK path in Android Studio if your installation is elsewhere.

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Start a design with a bundled background, a photo, a blank canvas, or a collage. **Saved exports** opens the app's image grid on Android 10 and newer. On older Android versions, it opens the system file picker so you can find images saved to a folder of your choice.

Type & Frame runs offline. It uses bundled assets and on-device processing, and its import and export pickers are limited to local files.

- Kotlin source lives in `app/src/main/kotlin/app/typeandframe/studio`. The app uses View Binding for its XML layouts.
- The manifest, XML layouts, strings and styles live in `app/src/main` and `app/src/main/res`.
- `app/src/main/assets` includes material extracted from the reference APK. Verify redistribution rights and required credits before publishing or sharing the project.

The editor supports photo import, collages, image and color backgrounds, editable text with bundled fonts, movable photo and sticker layers, and 1080 × 1080 PNG export. Android 10 and newer save to **Pictures/Type & Frame**. Earlier devices show a document save picker.

**Feature → Text Style** opens the full-screen Edit Text view with the reference APK's bundled quote categories. **Copy** fills the input, **Clear** empties it, and **Done** adds the text and opens the style panel. Double-tap a styled text layer to edit it again.

The style panel has 136 local presets and follows the reference APK's Type strip, variants 1–5, dice, Invert, and Line spacing controls. The previews and canvas use the same rendered text and settings. The preset catalog uses the bundled reference fonts and decorations. The Kotlin renderer recreates the style families, so individual word layout and seeded variants can differ from the compiled original.

**Emoji** and **Hand Draw** appear in the third row of **Feature**. The Emoji panel uses the bundled category list and images. Hand Draw has line, dashed and neon brushes, an eraser, color, width, and stroke undo/redo. Its result is added as a layer.

**Normal Text** creates an editable text layer and opens a 16-tool grid based on the decoded reference layout. Use the toolbar pencil or double-tap the layer to edit its text. The font picker follows the bundled font catalog and supports importing a TTF or OTF from the device. **Decorators** opens the original Borders, Lines, and Typography categories.

**Curve** opens an inline panel with live circular text, a signed value and a reset button. Curving a text layer disables Palette and Color Word until the curve is reset. The existing canvas uses the reference APK's four original selection icons for delete, horizontal flip, bring to front, and drag-to-scale/rotate.

**Sticker** shows the available offline packs. The **Birthday** tab contains 23 bundled birthday stickers. **Hand Draw** includes the original image brush assets, separate dash width and spacing, neon blur, and the drawing layer's Edit, Opacity, and Clone tools.

Device checks covered Normal Text entry and quote insertion, text color and the font grid, the three Decorators categories and Lines insertion, a Cat Face sticker loaded from the original free pack, and Hand Draw image brushes, dash and neon settings, and reopening a drawing layer. The reference app's points prompt blocked direct Normal Text runtime comparison, so the normal text grid was based on its decoded XML.

**Layer** has Auto select, visibility, lock, and a drag handle. The editor toolbar provides undo and redo for layer edits. **Background** opens Replace, Overlay, Adjust, Crop zoom, Rotate, Flip, Black & White, and Fit tools. **Export** saves the PNG on the device and opens a preview with Edit and Done. Background perspective, frames, project reopening, and advanced text and image effects from the reference are outside this version.

On the connected Android 13 phone, the debug build was checked through Feature expansion, emoji insertion, layer Auto select, visibility and lock, layer undo/redo, Hand Draw with neon brush and stroke undo/redo, bundled background replacement, rotation and Black & White, and the Save preview with a 1080 × 1080 PNG.

Type & Frame uses the application ID `app.typeandframe.studio`. Android installs it separately from builds with the previous application ID.

## Release signing

Keep the release keystore outside this project. Set `TYPEANDFRAME_KEYSTORE` to its absolute path and configure `TYPEANDFRAME_STORE_PASSWORD`, `TYPEANDFRAME_KEY_ALIAS`, and `TYPEANDFRAME_KEY_PASSWORD` in the build environment. Without signing credentials, Gradle produces an unsigned release. Use a new key for a first release if the previous key or its credentials were shared.
