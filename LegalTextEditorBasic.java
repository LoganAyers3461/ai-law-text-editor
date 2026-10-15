import java.awt.*;
import java.awt.event.*;
import java.awt.print.PrinterException;
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

public class LegalTextEditorBasic extends JFrame {
    private static final int BOLD_STYLE = 1, ITALIC_STYLE = 2, UNDERLINE_STYLE = 3;
    private final JTextPane documentEditor = new JTextPane();
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
    private File currentFile;

    public LegalTextEditorBasic() {
        setTitle("Legal Text Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
        fileMenu.addSeparator();
        fileMenu.add(menuItem("Print...", e -> printDocument()));
        fileMenu.addSeparator();
        fileMenu.add(menuItem("Exit", e -> dispose()));

        editMenu.add(menuItem("Undo", e -> undo()));
        editMenu.add(menuItem("Cut", e -> documentEditor.cut()));
        editMenu.add(menuItem("Copy", e -> documentEditor.copy()));
        editMenu.add(menuItem("Paste", e -> documentEditor.paste()));
        editMenu.add(menuItem("Select All", e -> documentEditor.selectAll()));

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
            public void insertUpdate(DocumentEvent e) { statusLabel.setText("Editing"); }
            public void removeUpdate(DocumentEvent e) { statusLabel.setText("Editing"); }
            public void changedUpdate(DocumentEvent e) { statusLabel.setText("Editing"); }
        });

        bindShortcut("B", KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK,
                () -> toggleStyle(BOLD_STYLE));
        bindShortcut("I", KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK,
                () -> toggleStyle(ITALIC_STYLE));
        bindShortcut("U", KeyEvent.VK_U, InputEvent.CTRL_DOWN_MASK,
                () -> toggleStyle(UNDERLINE_STYLE));
        bindShortcut("P", KeyEvent.VK_P, InputEvent.CTRL_DOWN_MASK, this::printDocument);
        bindShortcut("S", KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK,
                () -> saveDocument(false));
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

    private void newDocument() {
        if (chatRequestInProgress) return;
        documentEditor.setText("");
        currentFile = null;
        contextDocuments.clear();
        chatHistory.clear();
        jurisdiction = "";
        situationNotes = "";
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
        if (chatRequestInProgress) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Text and Rich Text files (*.txt, *.md, *.rtf)", "txt", "md", "rtf"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File selectedFile = chooser.getSelectedFile();
        try (InputStream input = new BufferedInputStream(new FileInputStream(selectedFile))) {
            if (hasExtension(selectedFile, "rtf")) {
                documentEditor.setText("");
                new RTFEditorKit().read(input, documentEditor.getDocument(), 0);
            } else {
                documentEditor.setText(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            currentFile = selectedFile;
            loadSession();
            statusLabel.setText("Opened: " + currentFile.getName());
        } catch (IOException | BadLocationException ex) {
            showError("Could not open file", ex);
        }
    }

    private void saveDocument(boolean saveAs) {
        if (currentFile == null || saveAs) {
            JFileChooser chooser = new JFileChooser();
            javax.swing.filechooser.FileNameExtensionFilter rtfFilter =
                    new javax.swing.filechooser.FileNameExtensionFilter("Rich Text Format (*.rtf)", "rtf");
            chooser.addChoosableFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Plain Text (*.txt)", "txt"));
            chooser.addChoosableFileFilter(rtfFilter);
            chooser.setFileFilter(rtfFilter);
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            currentFile = chooser.getSelectedFile();
            if (chooser.getFileFilter() == rtfFilter && !hasExtension(currentFile, "rtf")) {
                currentFile = new File(currentFile.getParentFile(), currentFile.getName() + ".rtf");
            }
        }
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(currentFile))) {
            if (hasExtension(currentFile, "rtf")) {
                new RTFEditorKit().write(output, documentEditor.getDocument(), 0,
                        documentEditor.getDocument().getLength());
            } else {
                output.write(documentEditor.getText().getBytes(StandardCharsets.UTF_8));
            }
            saveSession();
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

    private void undo() {
        // Minimal undo support is provided by Swing's text component actions when available.
        Action action = documentEditor.getActionMap().get("undo");
        if (action != null) action.actionPerformed(
                new ActionEvent(documentEditor, ActionEvent.ACTION_PERFORMED, "undo"));
        else statusLabel.setText("Undo is not available");
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
