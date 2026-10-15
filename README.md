# ai-law-text-editor
Repository for Group 5 to work on CSC-2218 Text Editor project at CT State Community College

## AI analysis prototype

The **AI Analyze** action starts a chat scoped to the current document.
**Upload Case Material** and **Case > Import Case Material...** accept multiple
UTF-8 text files. Use **Case > Case Information** to set jurisdiction(s) and
situation notes. Each request includes only the current document, its attached
context, its metadata, and its own conversation history. It does not use
ChatGPT account conversations or memory. Web research is enabled, and returned
source URLs are shown as clickable references in the chat.

When the document has been saved, its chat, context file contents, and case
metadata are stored in a neighboring `<document name>.session.properties`
file and restored when the document is opened again. These sidecars are local
plain-text files (restricted to the current user on POSIX systems) and are
excluded from Git. Untitled document sessions remain in memory until the
document is saved.

The editor supports rich formatting in the current document, including font,
size, bold, italic, underline, and paragraph alignment. Save as `.rtf` to
preserve that formatting when reopening the document; `.txt` and `.md` remain
available for plain text, which does not retain formatting.

The application requires Java 11 or newer. Copy `.env.example` to `.env` in
the project directory and add your key to `OPENAI_API_KEY`. The app loads this
file at startup; process environment variables override values from `.env`.
Optionally set `OPENAI_MODEL` to choose a model; the default is
`gpt-4.1-mini`. Launch the app with the project directory as the working
directory so it can find `.env`:

```sh
cp .env.example .env
# Edit .env and set OPENAI_API_KEY
java LegalTextEditorBasic
```

The local `.env` file is excluded from Git. The key is read at startup and is
not written by the application. A desktop application making direct API
requests cannot hide a shared API key from its user or from inspection of the
running process. A deployed multi-user version should send requests through a
trusted backend that stores the key. Document and context contents are sent to
the configured OpenAI API when analysis is requested; do not submit material
unless permitted for that use.
