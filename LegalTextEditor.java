import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class LegalTextEditor extends JFrame {

    public LegalTextEditor() {
        setTitle("Legal Text Editor");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 750);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout());

        JMenuBar menuBar = new JMenuBar();

        menuBar.add(new JMenu("File"));
        menuBar.add(new JMenu("Edit"));
        menuBar.add(new JMenu("Format"));
        menuBar.add(new JMenu("Case"));
        menuBar.add(new JMenu("AI"));
        menuBar.add(new JMenu("Help"));

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
                new String[]{"Serif", "SansSerif", "Monospaced"}
        );

        JComboBox<String> sizeBox = new JComboBox<>(
                new String[]{"12", "14", "16", "18", "20", "24", "28"}
        );
        sizeBox.setSelectedItem("16");

        JButton left = new JButton("Left");
        JButton center = new JButton("Center");
        JButton right = new JButton("Right");

        JButton upload = new JButton("Upload Case Material");
        JButton analyze = new JButton("AI Analyze");

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


        JTextPane documentEditor = new JTextPane();
        documentEditor.setFont(new Font("Serif", Font.PLAIN, 16));
        documentEditor.setMargin(new Insets(45, 55, 45, 55));
        documentEditor.setText(
                "Legal Document\n\n" +
                "Case Name: ______________________________________\n\n" +
                "Case Number: ____________________________________\n\n\n" +
                "Begin drafting your legal document here..."
        );

        JScrollPane documentScroll = new JScrollPane(documentEditor);
        documentScroll.setBorder(
                BorderFactory.createTitledBorder("Document Editor")
        );

        JTextArea aiPanel = new JTextArea();
        aiPanel.setEditable(false);
        aiPanel.setLineWrap(true);
        aiPanel.setWrapStyleWord(true);
        aiPanel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        aiPanel.setMargin(new Insets(10, 10, 10, 10));
        aiPanel.setText(
                "AI Suggestions\n\n" +
                "Relevant case law\n\n" +
                "Relevant statutes\n\n" +
                "Relevant rulings\n\n" +
                "Case analysis\n\n" +
                "Research suggestions"
        );

        JScrollPane aiScroll = new JScrollPane(aiPanel);
        aiScroll.setPreferredSize(new Dimension(330, 0));
        aiScroll.setBorder(
                BorderFactory.createTitledBorder("AI Suggestions")
        );

        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                documentScroll,
                aiScroll
        );

        splitPane.setDividerLocation(800);

        mainPanel.add(splitPane, BorderLayout.CENTER);


        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(4, 8, 4, 8));

        JLabel caseLabel = new JLabel("Case: No case selected");
        JLabel statusLabel = new JLabel("Ready");

        statusBar.add(caseLabel, BorderLayout.WEST);
        statusBar.add(statusLabel, BorderLayout.EAST);

        add(mainPanel, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LegalTextEditor editor = new LegalTextEditor();
            editor.setVisible(true);
        });
    }
}
