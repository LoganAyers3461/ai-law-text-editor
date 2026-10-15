import java.awt.*;
import java.awt.event.*;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.*;
import javax.swing.text.rtf.RTFEditorKit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Destination;

public class LegalTextEditorBasic extends JFrame {
    private static final int BOLD_STYLE = 1, ITALIC_STYLE = 2, UNDERLINE_STYLE = 3;
    private final JTextPane documentEditor = new JTextPane();
    private boolean loadingDocument;
    private final UndoManager undoManager = new UndoManager();
    private final UndoableEditListener undoListener = (UndoableEditEvent event) -> {
        if (!loadingDocument) undoManager.addEdit(event.getEdit());
    };
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel caseLabel = new JLabel("Case: No case selected");
    private final JEditorPane aiPanel = new JEditorPane();
    private final JTextArea chatInput = new JTextArea(3, 20);
    private final List<String> contextDocuments = new ArrayList<>();
    private final List<String> chatHistory = new ArrayList<>();
    private final OpenAIClient aiClient = new OpenAIClient();
    private final JButton uploadButton = new JButton("Upload Case Material");
    private final JButton analyzeButton = new JButton("AI Analyze");
    private final JButton sendButton = new JButton("Send");
    private String jurisdiction = "";
    private String situationNotes = "";
    private boolean chatRequestInProgress;
    private boolean documentDirty;
    private String lastSearch = "";
    private File currentFile;

    public LegalTextEditorBasic() {
        setTitle("Legal Text Editor");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setSize(1200, 750);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenu editMenu = new JMenu("Edit");
        JMenu formatMenu = new JMenu("Format");
        JMenu caseMenu = new JMenu("Case");
        JMenu aiMenu = new JMenu("AI");
        JMenu helpMenu = new JMenu("Help");

        fileMenu.add(menuItem("New", e -> newDocument()));
        fileMenu.add(menuItem("Open...", e -> openDocument()));
        fileMenu.add(menuItem("Save", e -> saveDocument(false)));
        fileMenu.add(menuItem("Save As...", e -> saveDocument(true)));
        fileMenu.add(menuItem("Export PDF...", e -> exportPdf()));
        fileMenu.addSeparator();
        fileMenu.add(menuItem("Print...", e -> printDocument()));
        fileMenu.addSeparator();
        fileMenu.add(menuItem("Exit", e -> exitEditor()));

        editMenu.add(menuItem("Undo", e -> undo()));
        editMenu.add(menuItem("Redo", e -> redo()));
        editMenu.addSeparator();
        editMenu.add(menuItem("Cut", e -> documentEditor.cut()));
        editMenu.add(menuItem("Copy", e -> documentEditor.copy()));
        editMenu.add(menuItem("Paste", e -> documentEditor.paste()));
        editMenu.add(menuItem("Select All", e -> documentEditor.selectAll()));
        editMenu.addSeparator();
        editMenu.add(menuItem("Find...", e -> findText()));
        editMenu.add(menuItem("Find Next", e -> findNext()));
        editMenu.add(menuItem("Replace...", e -> replaceTextDialog()));

        formatMenu.add(menuItem("Bold", e -> toggleStyle(BOLD_STYLE)));
        formatMenu.add(menuItem("Italic", e -> toggleStyle(ITALIC_STYLE)));
        formatMenu.add(menuItem("Underline", e -> toggleStyle(UNDERLINE_STYLE)));

       
        caseMenu.add(menuItem("Case Information", e -> showCaseInformation()));
        caseMenu.add(menuItem("Import Case Material...", e -> addContextDocuments()));
        aiMenu.add(menuItem("AI Analyze", e -> analyzeDocument()));
        aiMenu.add(menuItem("AI Suggestions", e -> analyzeDocument()));
        aiMenu.addSeparator();
        aiMenu.add(menuItem("Login to ChatGPT...", e -> loginToChatGPT()));
        aiMenu.add(menuItem("Sign out of ChatGPT", e -> signOutOfChatGPT()));
        helpMenu.add(new JMenuItem("About"));

        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(formatMenu);
        menuBar.add(caseMenu);
        menuBar.add(aiMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        JButton bold = new JButton("B");
        JButton italic = new JButton("I");
        JButton underline = new JButton("U");
        bold.setFont(new Font("SansSerif", Font.BOLD, 14));
        italic.setFont(new Font("SansSerif", Font.ITALIC, 14));
        underline.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JComboBox<String> fontBox = new JComboBox<>(
                new String[]{"Serif", "SansSerif", "Monospaced"});
        JComboBox<String> sizeBox = new JComboBox<>(
                new String[]{"12", "14", "16", "18", "20", "24", "28"});
        sizeBox.setSelectedItem("16");

        JButton left = new JButton("Left");
        JButton center = new JButton("Center");
        JButton right = new JButton("Right");

        bold.addActionListener(e -> toggleStyle(BOLD_STYLE));
        italic.addActionListener(e -> toggleStyle(ITALIC_STYLE));
        underline.addActionListener(e -> toggleStyle(UNDERLINE_STYLE));
        fontBox.addActionListener(e -> changeFont((String) fontBox.getSelectedItem()));
        sizeBox.addActionListener(e -> changeSize((String) sizeBox.getSelectedItem()));
        left.addActionListener(e -> alignText(StyleConstants.ALIGN_LEFT));
        center.addActionListener(e -> alignText(StyleConstants.ALIGN_CENTER));
        right.addActionListener(e -> alignText(StyleConstants.ALIGN_RIGHT));
        uploadButton.addActionListener(e -> addContextDocuments());
        analyzeButton.addActionListener(e -> analyzeDocument());

      
        
        uploadButton.setFocusable(false);
        analyzeButton.setFocusable(false);

        toolbar.add(bold);
        toolbar.add(italic);
        toolbar.add(underline);
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(new JLabel("Font:"));
        toolbar.add(fontBox);
        toolbar.add(new JLabel("Size:"));
        toolbar.add(sizeBox);
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(left);
        toolbar.add(center);
        toolbar.add(right);
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(uploadButton);
        toolbar.add(analyzeButton);
        mainPanel.add(toolbar, BorderLayout.NORTH);

        documentEditor.setFont(new Font("Serif", Font.PLAIN, 16));
        documentEditor.setMargin(new Insets(45, 55, 45, 55));
        // documentEditor.setText(
        //         "Legal Document\n\n" +
        //         "Case Name: ______________________________________\n\n" +
        //         "Case Number: ____________________________________\n\n\n" +
        //         "Begin drafting your legal document here...");
        JScrollPane documentScroll = new JScrollPane(documentEditor);
        documentScroll.setBorder(BorderFactory.createTitledBorder("Document Editor"));

        aiPanel.setContentType("text/html");
        aiPanel.setEditable(false);
        aiPanel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        aiPanel.setMargin(new Insets(10, 10, 10, 10));
        aiPanel.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(event.getURL().toURI());
                } catch (Exception ex) {
                    showError("Could not open reference", ex);
                }
            }
        });
        JScrollPane aiScroll = new JScrollPane(aiPanel);
        aiScroll.setBorder(BorderFactory.createTitledBorder("Document Chat"));

        JPanel chatComposer = new JPanel(new BorderLayout(5, 5));
        chatInput.setLineWrap(true);
        chatInput.setWrapStyleWord(true);
        chatInput.setFont(new Font("SansSerif", Font.PLAIN, 14));
        chatInput.setBorder(BorderFactory.createTitledBorder("Message"));
        sendButton.addActionListener(e -> sendChatMessage());
        chatComposer.add(new JScrollPane(chatInput), BorderLayout.CENTER);
        chatComposer.add(sendButton, BorderLayout.EAST);
        JPanel aiContainer = new JPanel(new BorderLayout(5, 5));
        aiContainer.add(aiScroll, BorderLayout.CENTER);
        aiContainer.add(chatComposer, BorderLayout.SOUTH);
        aiContainer.setPreferredSize(new Dimension(360, 0));

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, documentScroll, aiContainer);
        splitPane.setDividerLocation(800);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(4, 8, 4, 8));
        statusBar.add(caseLabel, BorderLayout.WEST);
        statusBar.add(statusLabel, BorderLayout.EAST);
        add(mainPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        documentEditor.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { markDocumentChanged(); }
            public void removeUpdate(DocumentEvent e) { markDocumentChanged(); }
            public void changedUpdate(DocumentEvent e) { markDocumentChanged(); }
        });
        documentEditor.getDocument().addUndoableEditListener(undoListener);

        bindShortcut("B", KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK,
                () -> toggleStyle(BOLD_STYLE));
        bindShortcut("I", KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK,
                () -> toggleStyle(ITALIC_STYLE));
        bindShortcut("U", KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK,
                () -> toggleStyle(UNDERLINE_STYLE));
        bindShortcut("P", KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK, this::printDocument);
        bindShortcut("S", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK,
                () -> saveDocument(false));
        bindShortcut("Z", KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK, this::undo);
        bindShortcut("Y", KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK, this::redo);
        bindShortcut("F", KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK, this::findText);
        bindShortcut("H", KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK, this::replaceTextDialog);
        bindShortcut("F3", KeyEvent.VK_F3, 0, this::findNext);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { exitEditor(); }
        });
    }

    private JMenuItem menuItem(String label, ActionListener action) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(action);
        return item;
    }

    private void toggleStyle(int style) {
        StyledDocument doc = documentEditor.getStyledDocument();
        int start = documentEditor.getSelectionStart();
        int end = documentEditor.getSelectionEnd();
        if (start == end) {
            statusLabel.setText("Select text to format");
            return;
        }
        AttributeSet current = doc.getCharacterElement(start).getAttributes();
        boolean enabled = style == BOLD_STYLE ? StyleConstants.isBold(current)
                : style == ITALIC_STYLE ? StyleConstants.isItalic(current)
                : StyleConstants.isUnderline(current);
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        if (style == BOLD_STYLE) StyleConstants.setBold(attributes, !enabled);
        else if (style == ITALIC_STYLE) StyleConstants.setItalic(attributes, !enabled);
        else StyleConstants.setUnderline(attributes, !enabled);
        doc.setCharacterAttributes(start, end - start, attributes, false);
        documentEditor.requestFocusInWindow();
    }

    private void changeFont(String fontName) {
        if (fontName == null) return;
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setFontFamily(attributes, fontName);
        applyCharacterAttributes(attributes);
    }

    private void changeSize(String size) {
        if (size == null) return;
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setFontSize(attributes, Integer.parseInt(size));
        applyCharacterAttributes(attributes);
    }

    private void applyCharacterAttributes(AttributeSet attributes) {
        int start = documentEditor.getSelectionStart();
        int end = documentEditor.getSelectionEnd();
        if (start == end) {
            documentEditor.setCharacterAttributes(attributes, false);
        } else {
            documentEditor.getStyledDocument().setCharacterAttributes(
                    start, end - start, attributes, false);
        }
        documentEditor.requestFocusInWindow();
    }

    private void alignText(int alignment) {
        StyledDocument document = documentEditor.getStyledDocument();
        int start = documentEditor.getSelectionStart();
        int end = documentEditor.getSelectionEnd();
        Element firstParagraph = document.getParagraphElement(start);
        // A selection ending at the start of a paragraph does not include that paragraph.
        int lastOffset = end > start ? end - 1 : start;
        Element lastParagraph = document.getParagraphElement(lastOffset);
        int from = firstParagraph.getStartOffset();
        int to = Math.min(document.getLength(), lastParagraph.getEndOffset());
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setAlignment(attributes, alignment);
        document.setParagraphAttributes(from, Math.max(1, to - from), attributes, false);
        documentEditor.requestFocusInWindow();
    }

    private void markDocumentChanged() {
        if (loadingDocument) return;
        documentDirty = true;
        statusLabel.setText("Editing");
        setTitle("Legal Text Editor — " + (currentFile == null ? "Untitled" : currentFile.getName()) + " *");
    }

    private boolean confirmDiscardChanges() {
        if (!documentDirty) return true;
        Object[] options = {"Save", "Discard", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, "Save changes to the current document?",
                "Unsaved changes", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, options, options[0]);
        if (choice == 1) return true;
        if (choice != 0) return false;
        saveDocument(false);
        return !documentDirty;
    }

    private void exitEditor() {
        if (confirmDiscardChanges()) dispose();
    }

    private void findText() {
        String initial = lastSearch.isEmpty() ? "" : lastSearch;
        String query = JOptionPane.showInputDialog(this, "Find:", initial);
        if (query == null || query.isEmpty()) return;
        lastSearch = query;
        findNext(query);
    }

    private void findNext() {
        if (lastSearch.isEmpty()) { findText(); return; }
        findNext(lastSearch);
    }

    private void findNext(String query) {
        String text = documentEditor.getText();
        int from = documentEditor.getSelectionEnd();
        int found = findIgnoreCase(text, query, from, text.length());
        if (found < 0 && from > 0) found = findIgnoreCase(text, query, 0, Math.min(from, text.length()));
        if (found < 0) {
            statusLabel.setText("Text not found: " + query);
            return;
        }
        documentEditor.requestFocusInWindow();
        documentEditor.select(found, found + query.length());
        statusLabel.setText("Found: " + query);
    }

    private int findIgnoreCase(String text, String query, int from, int to) {
        int last = Math.min(to - query.length(), text.length() - query.length());
        for (int i = Math.max(0, from); i <= last; i++)
            if (text.regionMatches(true, i, query, 0, query.length())) return i;
        return -1;
    }

    private void replaceTextDialog() {
        JTextField find = new JTextField(lastSearch, 24);
        JTextField replace = new JTextField(24);
        JPanel form = new JPanel(new GridLayout(2, 2, 6, 6));
        form.add(new JLabel("Find:")); form.add(find);
        form.add(new JLabel("Replace with:")); form.add(replace);
        Object[] options = {"Replace Next", "Replace All", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, form, "Find and Replace",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        String query = find.getText();
        if (query.isEmpty() || choice < 0 || choice == 2) return;
        lastSearch = query;
        if (choice == 0) {
            int start = documentEditor.getSelectionStart(), end = documentEditor.getSelectionEnd();
            if (end - start == query.length()
                    && documentEditor.getText().regionMatches(true, start, query, 0, query.length())) {
                documentEditor.replaceSelection(replace.getText());
            }
            findNext(query);
        } else {
            String text = documentEditor.getText();
            List<Integer> matches = new ArrayList<>();
            for (int at = 0; at <= text.length() - query.length();) {
                int found = findIgnoreCase(text, query, at, text.length());
                if (found < 0) break;
                matches.add(found);
                at = found + query.length();
            }
            try {
                StyledDocument doc = documentEditor.getStyledDocument();
                for (int i = matches.size() - 1; i >= 0; i--) {
                    int at = matches.get(i);
                    doc.remove(at, query.length());
                    doc.insertString(at, replace.getText(), null);
                }
                statusLabel.setText("Replaced " + matches.size() + " occurrence(s)");
            } catch (BadLocationException ex) { showError("Could not replace text", ex); }
        }
    }

    private void newDocument() {
        if (chatRequestInProgress || !confirmDiscardChanges()) return;
        loadingDocument = true;
        documentEditor.setText("");
        loadingDocument = false;
        undoManager.discardAllEdits();
        documentDirty = false;
        currentFile = null;
        contextDocuments.clear();
        chatHistory.clear();
        jurisdiction = "";
        situationNotes = "";
        setTitle("Legal Text Editor — Untitled");
        renderChat();
        caseLabel.setText("Case: No case selected");
        statusLabel.setText("New document");
    }

    private void addContextDocuments() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setDialogTitle("Add context documents (plain text)");
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        int added = 0;
        for (File file : chooser.getSelectedFiles()) {
            try {
                String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                contextDocuments.add("[" + file.getName() + "]\n" + content);
                added++;
            } catch (IOException ex) {
                showError("Could not read " + file.getName(), ex);
            }
        }
        statusLabel.setText(added + " context document(s) added (" + contextDocuments.size() + " total)");
        saveSession();
    }

    private void analyzeDocument() {
        sendMessage("Analyze the current draft. Identify issues and ambiguities, and support factual or legal claims with explicit references.");
    }

    private void loginToChatGPT() {
        statusLabel.setText("Waiting for ChatGPT sign-in");
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { aiClient.signIn(); return null; }
            protected void done() {
                try { get(); statusLabel.setText("Signed in to ChatGPT");
                    JOptionPane.showMessageDialog(LegalTextEditorBasic.this,
                            "ChatGPT is connected. The document assistant can now use this account's eligible plan access.",
                            "ChatGPT connected", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) { statusLabel.setText("ChatGPT sign-in failed"); showError("Could not sign in to ChatGPT", ex); }
            }
        }.execute();
    }

    private void signOutOfChatGPT() {
        try { aiClient.signOut(); statusLabel.setText("Signed out of ChatGPT"); }
        catch (IOException ex) { showError("Could not sign out", ex); }
    }

    private void sendChatMessage() {
        String message = chatInput.getText().trim();
        if (message.isEmpty()) return;
        chatInput.setText("");
        sendMessage(message);
    }

    private void sendMessage(String message) {
        if (chatRequestInProgress) return;
        String document = documentEditor.getText();
        if (document.isBlank()) {
            JOptionPane.showMessageDialog(this, "Enter or open a document before chatting.",
                    "Document Chat", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        List<String> context = new ArrayList<>(contextDocuments);
        String history = String.join("\n\n", chatHistory);
        String requestJurisdiction = jurisdiction;
        String requestSituationNotes = situationNotes;
        chatHistory.add("You: " + message);
        renderChat();
        saveSession();
        statusLabel.setText("AI response in progress");
        setAiActionsEnabled(false);
        sendButton.setEnabled(false);
        documentEditor.setEditable(false);
        chatRequestInProgress = true;
        new SwingWorker<String, Void>() {
            protected String doInBackground() throws Exception {
                return aiClient.chat(document, context, requestJurisdiction,
                        requestSituationNotes, history, message);
            }
            protected void done() {
                setAiActionsEnabled(true);
                sendButton.setEnabled(true);
                documentEditor.setEditable(true);
                chatRequestInProgress = false;
                try {
                    chatHistory.add("Assistant: " + get());
                    renderChat();
                    saveSession();
                    statusLabel.setText("Response complete");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    chatHistory.add("Assistant: Request failed: " + cause.getMessage());
                    renderChat();
                    saveSession();
                    statusLabel.setText("AI request failed");
                }
            }
        }.execute();
    }

    private void renderChat() {
        String content;
        if (chatHistory.isEmpty()) {
            content = "This document's chat and case context are saved with the document.\n"
                    + "Set its jurisdiction in Case Information and attach relevant source files.";
        } else {
            content = String.join("\n\n", chatHistory);
        }
        String escaped = escapeHtml(content);
        java.util.regex.Matcher urls = java.util.regex.Pattern.compile("https?://[^\\s<]+", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(escaped);
        StringBuffer linked = new StringBuffer();
        while (urls.find()) {
            String url = urls.group();
            while (url.endsWith(".") || url.endsWith(",") || url.endsWith(")")) url = url.substring(0, url.length() - 1);
            urls.appendReplacement(linked, java.util.regex.Matcher.quoteReplacement(
                    "<a href=\"" + url + "\">" + url + "</a>" + urls.group().substring(url.length())));
        }
        urls.appendTail(linked);
        aiPanel.setText("<html><body style='font-family:sans-serif;font-size:10pt'>"
                + linked.toString().replace("\n", "<br>") + "</body></html>");
        aiPanel.setCaretPosition(0);
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private void setAiActionsEnabled(boolean enabled) {
        // Keep analysis available from both the toolbar and menu while requests run.
        for (Component component : getJMenuBar().getComponents()) {
            if (component instanceof JMenu && ((JMenu) component).getText().equals("AI")) {
                for (Component item : ((JMenu) component).getMenuComponents()) item.setEnabled(enabled);
            }
        }
        uploadButton.setEnabled(enabled);
        analyzeButton.setEnabled(enabled);
    }

    private void showCaseInformation() {
        JTextField jurisdictionField = new JTextField(jurisdiction, 28);
        JTextArea notesField = new JTextArea(situationNotes, 5, 28);
        notesField.setLineWrap(true);
        notesField.setWrapStyleWord(true);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.anchor = GridBagConstraints.NORTHWEST;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.gridx = 0; constraints.gridy = 0;
        form.add(new JLabel("Applicable jurisdiction(s):"), constraints);
        constraints.gridx = 1;
        form.add(jurisdictionField, constraints);
        constraints.gridx = 0; constraints.gridy = 1;
        form.add(new JLabel("Situation notes:"), constraints);
        constraints.gridx = 1; constraints.fill = GridBagConstraints.BOTH;
        form.add(new JScrollPane(notesField), constraints);
        int choice = JOptionPane.showConfirmDialog(this, form,
                "Case Information — " + (currentFile == null ? "Untitled document" : currentFile.getName()),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice == JOptionPane.OK_OPTION) {
            jurisdiction = jurisdictionField.getText().trim();
            situationNotes = notesField.getText().trim();
            caseLabel.setText(jurisdiction.isEmpty() ? "Case: No jurisdiction set" : "Jurisdiction: " + jurisdiction);
            saveSession();
        }
    }

    private void openDocument() {
        if (chatRequestInProgress || !confirmDiscardChanges()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Documents (*.txt, *.md, *.rtf, *.docx)", "txt", "md", "rtf", "docx"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File selectedFile = chooser.getSelectedFile();
        try (InputStream input = new BufferedInputStream(new FileInputStream(selectedFile))) {
            loadingDocument = true;
            if (hasExtension(selectedFile, "rtf")) {
                documentEditor.setText("");
                new RTFEditorKit().read(input, documentEditor.getDocument(), 0);
            } else if (hasExtension(selectedFile, "docx")) {
                replaceDocument(DocxDocumentIO.read(input));
            } else {
                documentEditor.setText(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            loadingDocument = false;
            undoManager.discardAllEdits();
            documentDirty = false;
            currentFile = selectedFile;
            setTitle("Legal Text Editor — " + currentFile.getName());
            loadSession();
            statusLabel.setText("Opened: " + currentFile.getName());
        } catch (IOException | BadLocationException ex) {
            loadingDocument = false;
            showError("Could not open file", ex);
        } finally {
            loadingDocument = false;
        }
    }

    private void replaceDocument(StyledDocument source) throws BadLocationException {
        StyledDocument target = documentEditor.getStyledDocument();
        if (target.getLength() > 0) target.remove(0, target.getLength());
        int offset = 0;
        while (offset < source.getLength()) {
            Element character = source.getCharacterElement(offset);
            int end = Math.min(character.getEndOffset(), source.getLength());
            if (end <= offset) break;
            target.insertString(target.getLength(), source.getText(offset, end - offset), character.getAttributes());
            offset = end;
        }
        int paragraphOffset = 0;
        while (paragraphOffset < source.getLength()) {
            Element paragraph = source.getParagraphElement(paragraphOffset);
            int end = Math.min(paragraph.getEndOffset(), source.getLength());
            if (end <= paragraphOffset) break;
            target.setParagraphAttributes(paragraphOffset, end - paragraphOffset, paragraph.getAttributes(), true);
            paragraphOffset = end;
        }
    }

    private void saveDocument(boolean saveAs) {
        if (currentFile == null || saveAs) {
            JFileChooser chooser = new JFileChooser();
            javax.swing.filechooser.FileNameExtensionFilter rtfFilter =
                    new javax.swing.filechooser.FileNameExtensionFilter("Rich Text Format (*.rtf)", "rtf");
            javax.swing.filechooser.FileNameExtensionFilter docxFilter =
                    new javax.swing.filechooser.FileNameExtensionFilter("Word Document (*.docx)", "docx");
            javax.swing.filechooser.FileNameExtensionFilter markdownFilter =
                    new javax.swing.filechooser.FileNameExtensionFilter("Markdown (*.md)", "md");
            chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Plain Text (*.txt)", "txt"));
            chooser.addChoosableFileFilter(markdownFilter);
            chooser.addChoosableFileFilter(rtfFilter);
            chooser.addChoosableFileFilter(docxFilter);
            chooser.setFileFilter(rtfFilter);
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            currentFile = chooser.getSelectedFile();
            if (chooser.getFileFilter() == rtfFilter && !hasExtension(currentFile, "rtf")) {
                currentFile = new File(currentFile.getParentFile(), currentFile.getName() + ".rtf");
            } else if (chooser.getFileFilter() == docxFilter && !hasExtension(currentFile, "docx")) {
                currentFile = new File(currentFile.getParentFile(), currentFile.getName() + ".docx");
            } else if (chooser.getFileFilter() == markdownFilter && !hasExtension(currentFile, "md")) {
                currentFile = new File(currentFile.getParentFile(), currentFile.getName() + ".md");
            } else if (chooser.getFileFilter() instanceof javax.swing.filechooser.FileNameExtensionFilter
                    && ((javax.swing.filechooser.FileNameExtensionFilter) chooser.getFileFilter()).getExtensions()[0].equals("txt")
                    && !hasExtension(currentFile, "txt")) {
                currentFile = new File(currentFile.getParentFile(), currentFile.getName() + ".txt");
            }
        }
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(currentFile))) {
            if (hasExtension(currentFile, "rtf")) {
                new RTFEditorKit().write(output, documentEditor.getDocument(), 0,
                        documentEditor.getDocument().getLength());
            } else if (hasExtension(currentFile, "docx")) {
                DocxDocumentIO.write(documentEditor.getStyledDocument(), output);
            } else {
                output.write(documentEditor.getText().getBytes(StandardCharsets.UTF_8));
            }
            saveSession();
            documentDirty = false;
            setTitle("Legal Text Editor — " + currentFile.getName());
            statusLabel.setText("Saved: " + currentFile.getName());
        } catch (IOException | BadLocationException ex) {
            showError("Could not save file", ex);
        }
    }

    private boolean hasExtension(File file, String extension) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith("." + extension);
    }

    private File sessionFile() {
        if (currentFile == null) return null;
        return currentFile.toPath().toAbsolutePath()
                .resolveSibling(currentFile.getName() + ".session.properties").toFile();
    }

    private void saveSession() {
        File file = sessionFile();
        if (file == null) return; // Untitled sessions remain in memory until the document is saved.
        Properties properties = new Properties();
        properties.setProperty("jurisdiction", jurisdiction);
        properties.setProperty("situationNotes", situationNotes);
        properties.setProperty("context.count", Integer.toString(contextDocuments.size()));
        for (int i = 0; i < contextDocuments.size(); i++)
            properties.setProperty("context." + i, contextDocuments.get(i));
        properties.setProperty("chat.count", Integer.toString(chatHistory.size()));
        for (int i = 0; i < chatHistory.size(); i++)
            properties.setProperty("chat." + i, chatHistory.get(i));
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            properties.store(writer, "Per-document AI chat and case context");
            try {
                Files.setPosixFilePermissions(file.toPath(), EnumSet.of(
                        PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } catch (UnsupportedOperationException ignored) {
                // Non-POSIX systems use their normal per-user file permissions.
            }
        } catch (IOException ex) {
            showError("Could not save document chat session", ex);
        }
    }

    private void loadSession() {
        contextDocuments.clear();
        chatHistory.clear();
        jurisdiction = "";
        situationNotes = "";
        File file = sessionFile();
        if (file != null && file.isFile()) {
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                properties.load(reader);
                jurisdiction = properties.getProperty("jurisdiction", "");
                situationNotes = properties.getProperty("situationNotes", "");
                int contextCount = Integer.parseInt(properties.getProperty("context.count", "0"));
                for (int i = 0; i < contextCount; i++)
                    contextDocuments.add(properties.getProperty("context." + i, ""));
                int chatCount = Integer.parseInt(properties.getProperty("chat.count", "0"));
                for (int i = 0; i < chatCount; i++)
                    chatHistory.add(properties.getProperty("chat." + i, ""));
            } catch (IOException | NumberFormatException ex) {
                showError("Could not restore document chat session", ex);
                contextDocuments.clear();
                chatHistory.clear();
                jurisdiction = "";
                situationNotes = "";
            }
        }
        caseLabel.setText(jurisdiction.isEmpty() ? "Case: No jurisdiction set" : "Jurisdiction: " + jurisdiction);
        renderChat();
    }

    private void printDocument() {
        try {
            boolean printed = documentEditor.print();
            statusLabel.setText(printed ? "Print complete" : "Print cancelled");
        } catch (PrinterException ex) {
            showError("Could not print document", ex);
        }
    }

    private void exportPdf() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export PDF");
        chooser.setSelectedFile(new File(currentFile == null ? "document.pdf"
                : currentFile.getName().replaceFirst("(?i)\\.[^.]+$", "") + ".pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File destination = chooser.getSelectedFile();
        if (!hasExtension(destination, "pdf")) destination = new File(destination.getParentFile(), destination.getName() + ".pdf");
        if (destination.exists() && JOptionPane.showConfirmDialog(this,
                "Replace " + destination.getName() + "?", "Confirm Export", JOptionPane.YES_NO_OPTION)
                != JOptionPane.YES_OPTION) return;
        try {
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setJobName("Export " + destination.getName());
            job.setPrintable(documentEditor.getPrintable(null, null));
            PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
            attributes.add(new Destination(destination.toURI()));
            if (!job.printDialog(attributes)) return;
            if (job.getPrintService() == null
                    || !job.getPrintService().isAttributeCategorySupported(Destination.class)) {
                throw new PrinterException("The selected print service cannot save to a file. Choose a PDF-capable printer or install a system PDF printer.");
            }
            job.print(attributes);
            statusLabel.setText("PDF exported: " + destination.getName());
        } catch (PrinterException ex) {
            showError("Could not export PDF. Choose a PDF-capable print service", ex);
        }
    }

    private void undo() {
        if (!undoManager.canUndo()) { statusLabel.setText("Nothing to undo"); return; }
        try { undoManager.undo(); }
        catch (CannotUndoException ex) { showError("Could not undo", ex); }
    }

    private void redo() {
        if (!undoManager.canRedo()) { statusLabel.setText("Nothing to redo"); return; }
        try { undoManager.redo(); }
        catch (CannotRedoException ex) { showError("Could not redo", ex); }
    }

    private void bindShortcut(String name, int keyCode, int modifiers, Runnable action) {
        documentEditor.getInputMap().put(KeyStroke.getKeyStroke(keyCode, modifiers), name);
        documentEditor.getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    private void showError(String message, Exception ex) {
        statusLabel.setText("Error");
        JOptionPane.showMessageDialog(this, message + ":\n" + ex.getMessage(),
                "Legal Text Editor", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LegalTextEditorBasic editor = new LegalTextEditorBasic();
            editor.setVisible(true);
        });
    }
}
