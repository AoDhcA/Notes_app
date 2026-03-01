# NotesApp — Block-Based Note Editor with DOCX Export

**NotesApp** is an Android application for creating structured notes with formatting support, tags, and export to Microsoft Word (.docx) files. The app uses a block-based content architecture: each element (text, table) is a separate block, providing flexibility in editing and reliable data preservation.

## Features

* ✏️ **Block Editor**:
  * **Text blocks** — support formatting: changing text size (via HTML tags), creating numbered and bulleted lists.
  * **Table blocks** — insert tables directly into text; when inserting, the text after the cursor is automatically moved to a new block, and when deleting, it correctly merges with the adjacent block.
* 🏷️ **Flexible Tag System**:
  * Each note can be assigned multiple tags.
  * Two types of tag search:
    1. **Search bar** — enter tags separated by commas; searches simultaneously by file name and tags.
    2. **Single-tag filter** — select a tag from the list, display all notes with that tag.
* 📄 **DOCX Export**:
  * Notes are saved in `.docx` format using the **Apache POI** library.
  * Export preserves the full block structure and formatting.
* 💾 **Local Storage**:
  * Metadata (tags, relationships) stored in **SQLite**.
  * Note files saved in the app's internal storage.
* 📱 **Minimum Android Version** — 8.0 (API level 26).

## Technologies & Libraries

* **Language**: Java
* **IDE**: Android Studio
* **Document format**: Apache POI (for .docx manipulation)
* **Database**: SQLite (built-in)
* **UI**: standard Android widgets (EditText, RecyclerView, etc.)

## Requirements

* Device running Android 8.0 (Oreo) or higher.
* To build from source: Android Studio 4.0+.

## Installation & Build

1. Clone the repository:
 
    git clone https://github.com/yourusername/NotesApp.git

2. Open the project in Android Studio:

    `File -> Open` → select the `NotesApp` folder.

3. Wait for Gradle sync to finish and install dependencies (Apache POI will be automatically added via `build.gradle`).

4. Run the app on an emulator or a real device.

## Usage

# Main Screen

* Displays the list of existing notes.

* "+" button to create a new note.

* Search bar and tag filter button.

# Note Editor

* A note consists of blocks. By default, one text block is created.

* **Add a text** block: tap "Add text" button (or similar in the UI).

* **Insert a table**: tap "Insert table". The current text block will split: the part before the cursor remains, the part after the cursor moves to a new block, and the table is inserted between them.

* **Text formatting**: use toolbar buttons to choose text size (HTML tags) or create lists.

* **Delete a table**: when a table is deleted, its content disappears, and adjacent text blocks are automatically merged (text after the table is appended to the previous block).

# Tag Management

* When saving a note, you can enter tags separated by commas in a dedicated field.

* On the main screen, search is available:

    * **Search by string**: enter one or more tags separated by commas — notes whose title or tags contain any of the entered words will be shown.

    * **Single-tag filter**: select a tag from the dropdown list — all notes with that tag will be displayed.

## Project Structure

    NotesApp/
    ├── app/
    │   ├── src/
    │   │   └── main/
    │   │       ├── java/com/example/notes/
    │   │       │   ├── MainActivity.java          # Main screen with list of notes
    │   │       │   ├── TextEditorActivity.java    # Note editing screen
    │   │       │   ├── DatabaseHelper.java        # SQLite operations (tags, metadata)
    │   │       │   ├── DocxAdapter.java           # Convert blocks to .docx
    │   │       │   ├── SearchAdapter.java         # Adapter for search by string
    │   │       │   ├── SearchByTagAdapter.java    # Adapter for single‑tag filter
    │   │       │   ├── SearchDialog.java          # Tag selection dialog
    │   │       │   ├── DocxFile.java              # Note model (metadata + path)
    │   │       │   ├── DocxBlockExporter.java     # Export blocks to .docx
    │   │       │   ├── DocxBlockImporter.java     # Import .docx into blocks
    │   │       │   ├── BlockManager.java          # Block management in editor
    │   │       │   ├── ContentBlock.java          # Base block class
    │   │       │   ├── TextBlock.java             # Text block
    │   │       │   ├── TableBlock.java            # Table block
    │   │       │   ├── TagEditText.java           # Custom tag input field
    │   │       │   └── BlockFocusListener.java    # Block focus listener
    │   │       └── res/                           # Resources (layout, drawable, etc.)
    │   └── build.gradle                           # Dependencies (Apache POI, etc.)
    └── README.md                                   # This file

## Screenshots
(Add screenshots of the main screen, editor, and search dialog here so users can immediately see the interface.)

## Roadmap

* Image support in blocks.

* Themes.

## License

This project is distributed under the Apache 2.0 License. ee the [LICENSE](LICENSE) file for details.

## Author

**Sergey Fomkin**

GitHub: [@AoDhcA](https://github.com/AoDhcA) 

Email:
