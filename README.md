# Text Swag Studio

Open this directory in Android Studio, sync Gradle, select the **app** configuration, choose a device, and press **Run**. The project uses Android SDK 36 and the Gradle wrapper included in this folder. Set the SDK path in Android Studio if your installation is elsewhere.

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

- Kotlin source lives in `app/src/main/kotlin/com/textswag/studio`. The app uses View Binding for its XML layouts.
- The manifest, XML layouts, strings and styles live in `app/src/main` and `app/src/main/res`.
- `app/src/main/assets` contains the 1,625 files extracted from the reference APK.

The editor supports photo import, collages, image and color backgrounds, editable text with bundled fonts, movable photo and sticker layers, and 1080 × 1080 PNG export. Android 10 and newer save to **Pictures/Text Swag**. Earlier devices show a document save picker.

**Feature → Text Style** opens the full-screen Edit Text view with the reference APK's bundled quote categories. **Copy** fills the input, **Clear** empties it, and **Done** adds the text and opens the style panel. Double-tap a styled text layer to edit it again.

The style panel has 136 local presets and follows the reference APK's Type strip, variants 1–5, dice, Invert, and Line spacing controls. The previews and canvas use the same rendered text and settings. The preset catalog uses the bundled reference fonts and decorations. The Kotlin renderer recreates the style families, so individual word layout and seeded variants can differ from the compiled original.

**Emoji** and **Hand Draw** appear in the third row of **Feature**. The Emoji panel uses the bundled category list and images. Hand Draw has line, dashed and neon brushes, an eraser, color, width, and stroke undo/redo. Its result is added as a layer.

**Normal Text** creates an editable text layer and opens a 16-tool grid based on the decoded reference layout. Use the toolbar pencil or double-tap the layer to edit its text. The font picker follows the bundled font catalog and supports importing a TTF or OTF from the device. **Decorators** opens the original Borders, Lines, and Typography categories.

**Curve** opens an inline panel with live circular text, a signed value and a reset button. Curving a text layer disables Palette and Color Word until the curve is reset. The existing canvas uses the reference APK's four original selection icons for delete, horizontal flip, bring to front, and drag-to-scale/rotate.

**Sticker** uses the APK's original catalog order and loads the free packs from its public image endpoint. The Bundled tab works offline. Premium packs remain locked. **Hand Draw** includes the original image brush assets, separate dash width and spacing, neon blur, and the drawing layer's Edit, Opacity, and Clone tools.

Device checks covered Normal Text entry and quote insertion, text color and the font grid, the three Decorators categories and Lines insertion, a Cat Face sticker loaded from the original free pack, and Hand Draw image brushes, dash and neon settings, and reopening a drawing layer. The reference app's points prompt blocked direct Normal Text runtime comparison, so the normal text grid was based on its decoded XML.

**Layer** has Auto select, visibility, lock, and a drag handle. The editor toolbar provides undo and redo for layer edits. **Background** opens Replace, Overlay, Adjust, Crop zoom, Rotate, Flip, Black & White, and Fit tools. **Done** saves the PNG and opens the saved-image preview with Edit, Done, and Share. Background perspective, frames, project reopening, and advanced text and image effects from the reference are outside this version.

On the connected Android 13 phone, the debug build was checked through Feature expansion, emoji insertion, layer Auto select, visibility and lock, layer undo/redo, Hand Draw with neon brush and stroke undo/redo, bundled background replacement, rotation and Black & White, and the Save preview with a 1080 × 1080 PNG.

This native app recreates the reference Home, Choose Background, and Editor screens in Kotlin and XML using the APK's logo, font, icons, and colors. It has a separate app ID, `com.textswag.studio`. The original paid features, AI and stock-image services, template system, and other screens are outside this implementation.
