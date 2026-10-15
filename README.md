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

Basic editing now includes working undo/redo (`Ctrl+Z`/`Ctrl+Y`), find/find
next (`Ctrl+F`/`F3`), replace (`Ctrl+H`), and a prompt before discarding
unsaved document edits. DOCX files can be opened and saved with bold, italic,
underline, font family/size, and paragraph alignment preserved. Advanced
Word features such as tables, images, tracked changes, and page headers are
not currently preserved by DOCX import/export. PDF export uses the operating
system's print dialog and requires a PDF-capable print service; the document's
rich formatting is rendered by Swing for printing. RTF remains the preferred
editable interchange format for the formatting currently supported by the
editor.

The application requires Java 11 or newer. Use **AI > Login to ChatGPT...**
to authorize in your browser. The desktop app receives an authorization code
through a temporary loopback listener, then stores refresh credentials in
`~/.config/ai-law-text-editor/credentials.properties` with owner-only
permissions on POSIX systems. Use **AI > Sign out of ChatGPT** to remove the
saved tokens. The app sends document requests to the Responses API with
`store:false`, streaming enabled, and web search. ChatGPT sign-in does not
expose ChatGPT conversations or memory to the app.

**Free ChatGPT accounts are not documented as eligible for ChatGPT plan
inference through this flow.** OpenAI's current [quickstart](https://developers.openai.com/siwc/quickstart)
specifies eligible Plus and Pro users for plan-backed requests. Identity
sign-in alone does not provide inference access. The integration also depends
on OpenAI enabling the open-source token-sharing flow for the app and the
account/workspace granting `chatgpt.tokens.use.direct`. Do not assume a
successful browser login means AI requests are enabled.

The app retrieves the signed-in account's model catalog from [`/v1/models`](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference) and
uses the first model OpenAI marks for display. This catalog endpoint is
documented for delegated ChatGPT credentials; model listing does not guarantee
that every model or tool will work for every request. Set `OPENAI_MODEL` in
`.env` or the process environment to require a specific model slug. If it is
not in the account's visible model catalog, the app reports that and does not
silently substitute another model. No API key is required. Launch:

```sh
cp .env.example .env
java LegalTextEditorBasic
```

Document and context contents are sent to OpenAI when analysis is requested.
ChatGPT plan usage is subject to account, app, workspace, region, model, and
usage limits. A limit or eligibility error stops the request; the app does
not switch to API billing or retry through another account. A real sign-in
and completed Responses request still need to be checked with an eligible
account; credential-free model-selection and error-handling checks are run
with `java -cp <classes-directory> ChatGPTIntegrationTests` after compilation.
