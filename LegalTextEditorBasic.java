import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.print.PrinterException;
import java.io.*;

public class LegalTextEditorBasic extends JFrame {
    private static final int BOLD_STYLE = 1, ITALIC_STYLE = 2, UNDERLINE_STYLE = 3;
    private final JTextPane documentEditor = new JTextPane();
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel caseLabel = new JLabel("Case: No case selected");
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

        // Kept as UI placeholders. AI behavior is intentionally not implemented.
        caseMenu.add(new JMenuItem("Case Information"));
        caseMenu.add(new JMenuItem("Import Case Material"));
        aiMenu.add(new JMenuItem("AI Analyze"));
        aiMenu.add(new JMenuItem("AI Suggestions"));
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
        JButton upload = new JButton("Upload Case Material");
        JButton analyze = new JButton("AI Analyze");

        bold.addActionListener(e -> toggleStyle(BOLD_STYLE));
        italic.addActionListener(e -> toggleStyle(ITALIC_STYLE));
        underline.addActionListener(e -> toggleStyle(UNDERLINE_STYLE));
        fontBox.addActionListener(e -> changeFont((String) fontBox.getSelectedItem()));
        sizeBox.addActionListener(e -> changeSize((String) sizeBox.getSelectedItem()));
        left.addActionListener(e -> alignText(StyleConstants.ALIGN_LEFT));
        center.addActionListener(e -> alignText(StyleConstants.ALIGN_CENTER));
        right.addActionListener(e -> alignText(StyleConstants.ALIGN_RIGHT));

        // AI/case controls remain visible and deliberately do nothing.
        upload.setFocusable(false);
        analyze.setFocusable(false);

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
        toolbar.add(upload);
        toolbar.add(analyze);
        mainPanel.add(toolbar, BorderLayout.NORTH);

        documentEditor.setFont(new Font("Serif", Font.PLAIN, 16));
        documentEditor.setMargin(new Insets(45, 55, 45, 55));
        documentEditor.setText(
                "Legal Document\n\n" +
                "Case Name: ______________________________________\n\n" +
                "Case Number: ____________________________________\n\n\n" +
                "Begin drafting your legal document here...");
        JScrollPane documentScroll = new JScrollPane(documentEditor);
        documentScroll.setBorder(BorderFactory.createTitledBorder("Document Editor"));

        JTextArea aiPanel = new JTextArea();
        aiPanel.setEditable(false);
        aiPanel.setLineWrap(true);
        aiPanel.setWrapStyleWord(true);
        aiPanel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        aiPanel.setMargin(new Insets(10, 10, 10, 10));
        aiPanel.setText("AI Suggestions\n\nRelevant case law\n\nRelevant statutes\n\n" +
                "Relevant rulings\n\nCase analysis\n\nResearch suggestions");
        JScrollPane aiScroll = new JScrollPane(aiPanel);
        aiScroll.setPreferredSize(new Dimension(330, 0));
        aiScroll.setBorder(BorderFactory.createTitledBorder("AI Suggestions"));

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, documentScroll, aiScroll);
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
        int start = documentEditor.getSelectionStart();
        int end = documentEditor.getSelectionEnd();
        int length = documentEditor.getDocument().getLength();
        int from = start == end ? 0 : start;
        int to = start == end ? length : end;
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        StyleConstants.setAlignment(attributes, alignment);
        documentEditor.getStyledDocument().setParagraphAttributes(
                from, Math.max(1, to - from), attributes, false);
        documentEditor.requestFocusInWindow();
    }

    private void newDocument() {
        documentEditor.setText("");
        currentFile = null;
        caseLabel.setText("Case: No case selected");
        statusLabel.setText("New document");
    }

    private void openDocument() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        currentFile = chooser.getSelectedFile();
        try (BufferedReader reader = new BufferedReader(new FileReader(currentFile))) {
            documentEditor.setText("");
            String line;
            while ((line = reader.readLine()) != null) {
                documentEditor.getDocument().insertString(
                        documentEditor.getDocument().getLength(), line + "\n", null);
            }
            statusLabel.setText("Opened: " + currentFile.getName());
        } catch (IOException | BadLocationException ex) {
            showError("Could not open file", ex);
        }
    }

    private void saveDocument(boolean saveAs) {
        if (currentFile == null || saveAs) {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            currentFile = chooser.getSelectedFile();
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(currentFile))) {
            writer.write(documentEditor.getText());
            statusLabel.setText("Saved: " + currentFile.getName());
        } catch (IOException ex) {
            showError("Could not save file", ex);
        }
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
