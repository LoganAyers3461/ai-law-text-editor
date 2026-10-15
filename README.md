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

The application requires Java 11 or newer. Use **AI > Login to ChatGPT...**
to authorize in your browser. The desktop app receives an authorization code
through a temporary loopback listener, then stores refresh credentials in
`~/.config/ai-law-text-editor/credentials.properties` with owner-only
permissions on POSIX systems. Use **AI > Sign out of ChatGPT** to remove the
saved tokens. The app sends document requests to the Responses API with
`store:false`, streaming enabled, and web search. The user's plan and account
must permit this integration; ChatGPT sign-in does not expose ChatGPT
conversations or memory to the app. The open-source token-sharing flow also
requires the app to be eligible/registered with OpenAI.

Optionally set `OPENAI_MODEL` in `.env` or the process environment to select
the Responses model (default `gpt-4.1-mini`). No API key is required. Launch:

```sh
cp .env.example .env
java LegalTextEditorBasic
```

Document and context contents are sent to OpenAI when analysis is requested.
