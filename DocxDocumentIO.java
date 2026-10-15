import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Small dependency-free OOXML reader/writer for the editor's basic rich-text features. */
final class DocxDocumentIO {
    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final int MAX_DOCUMENT_XML_BYTES = 50 * 1024 * 1024;

    static void write(StyledDocument document, OutputStream output) throws IOException, BadLocationException {
        String documentXml = documentXml(document);
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            entry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                    + "</Types>");
            entry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                    + "</Relationships>");
            entry(zip, "word/document.xml", documentXml);
        }
    }

    static DefaultStyledDocument read(InputStream input) throws IOException {
        byte[] xml = null;
        try (ZipInputStream zip = new ZipInputStream(input, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = zip.read(buffer)) != -1) {
                        if (bytes.size() + count > MAX_DOCUMENT_XML_BYTES)
                            throw new IOException("The DOCX document body is too large to open.");
                        bytes.write(buffer, 0, count);
                    }
                    xml = bytes.toByteArray(); break;
                }
            }
        }
        if (xml == null) throw new IOException("This DOCX file does not contain word/document.xml.");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            org.w3c.dom.Document xmlDoc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
            DefaultStyledDocument result = new DefaultStyledDocument();
            NodeList paragraphs = xmlDoc.getElementsByTagNameNS(W, "p");
            for (int i = 0; i < paragraphs.getLength(); i++) {
                org.w3c.dom.Element paragraph = (org.w3c.dom.Element) paragraphs.item(i);
                int paragraphStart = result.getLength();
                readParagraph(paragraph, result);
                int paragraphLength = result.getLength() - paragraphStart;
                SimpleAttributeSet paragraphAttributes = paragraphAttributes(paragraph);
                if (i < paragraphs.getLength() - 1) {
                    result.insertString(result.getLength(), "\n", null);
                    paragraphLength++;
                }
                if (paragraphLength > 0)
                    result.setParagraphAttributes(paragraphStart, paragraphLength, paragraphAttributes, false);
            }
            return result;
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IOException("Could not read the DOCX document body.", ex);
        }
    }

    private static void readParagraph(org.w3c.dom.Element paragraph, DefaultStyledDocument document)
            throws BadLocationException {
        NodeList runs = paragraph.getChildNodes();
        for (int i = 0; i < runs.getLength(); i++) {
            Node node = runs.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE || !"r".equals(node.getLocalName())) continue;
            org.w3c.dom.Element run = (org.w3c.dom.Element) node;
            SimpleAttributeSet attributes = runAttributes(run);
            NodeList children = run.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                if (child.getNodeType() != Node.ELEMENT_NODE) continue;
                String local = child.getLocalName();
                if ("t".equals(local)) document.insertString(document.getLength(), child.getTextContent(), attributes);
                else if ("tab".equals(local)) document.insertString(document.getLength(), "\t", attributes);
                else if ("br".equals(local) || "cr".equals(local)) document.insertString(document.getLength(), "\n", attributes);
            }
        }
    }

    private static SimpleAttributeSet paragraphAttributes(org.w3c.dom.Element paragraph) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        NodeList jc = paragraph.getElementsByTagNameNS(W, "jc");
        if (jc.getLength() > 0) {
            String alignment = attr((org.w3c.dom.Element) jc.item(0), "val");
            if ("center".equals(alignment)) StyleConstants.setAlignment(attributes, StyleConstants.ALIGN_CENTER);
            else if ("right".equals(alignment) || "end".equals(alignment)) StyleConstants.setAlignment(attributes, StyleConstants.ALIGN_RIGHT);
            else if ("both".equals(alignment) || "distribute".equals(alignment)) StyleConstants.setAlignment(attributes, StyleConstants.ALIGN_JUSTIFIED);
            else StyleConstants.setAlignment(attributes, StyleConstants.ALIGN_LEFT);
        }
        return attributes;
    }

    private static SimpleAttributeSet runAttributes(org.w3c.dom.Element run) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        NodeList bold = run.getElementsByTagNameNS(W, "b");
        NodeList italic = run.getElementsByTagNameNS(W, "i");
        NodeList underline = run.getElementsByTagNameNS(W, "u");
        if (bold.getLength() > 0) StyleConstants.setBold(attributes, !"0".equals(attr((org.w3c.dom.Element) bold.item(0), "val")));
        if (italic.getLength() > 0) StyleConstants.setItalic(attributes, !"0".equals(attr((org.w3c.dom.Element) italic.item(0), "val")));
        if (underline.getLength() > 0) StyleConstants.setUnderline(attributes, !"none".equals(attr((org.w3c.dom.Element) underline.item(0), "val")));
        NodeList fonts = run.getElementsByTagNameNS(W, "rFonts");
        if (fonts.getLength() > 0) {
            org.w3c.dom.Element font = (org.w3c.dom.Element) fonts.item(0);
            String family = attr(font, "ascii"); if (family == null) family = attr(font, "hAnsi");
            if (family != null) StyleConstants.setFontFamily(attributes, family);
        }
        NodeList sizes = run.getElementsByTagNameNS(W, "sz");
        if (sizes.getLength() > 0) {
            try { StyleConstants.setFontSize(attributes, Integer.parseInt(attr((org.w3c.dom.Element) sizes.item(0), "val")) / 2); }
            catch (NumberFormatException ignored) { }
        }
        return attributes;
    }

    private static String documentXml(StyledDocument document) throws BadLocationException {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<w:document xmlns:w=\"").append(W).append("\"><w:body>");
        int length = document.getLength(); int offset = 0;
        while (offset < length || (length == 0 && offset == 0)) {
            javax.swing.text.Element paragraph = document.getParagraphElement(offset);
            int start = Math.min(offset, length);
            int end = Math.min(paragraph.getEndOffset(), length);
            int contentEnd = end;
            while (contentEnd > start) {
                char last = document.getText(contentEnd - 1, 1).charAt(0);
                if (last != '\n' && last != '\r') break;
                contentEnd--;
            }
            xml.append("<w:p>");
            int alignment = StyleConstants.getAlignment(paragraph.getAttributes());
            String justification = alignment == StyleConstants.ALIGN_CENTER ? "center"
                    : alignment == StyleConstants.ALIGN_RIGHT ? "right"
                    : alignment == StyleConstants.ALIGN_JUSTIFIED ? "both" : "left";
            xml.append("<w:pPr><w:jc w:val=\"").append(justification).append("\"/></w:pPr>");
            int position = start;
            while (position < contentEnd) {
                javax.swing.text.Element character = document.getCharacterElement(position);
                int runEnd = Math.min(character.getEndOffset(), contentEnd);
                if (runEnd <= position) break;
                AttributeSet style = character.getAttributes();
                xml.append("<w:r>");
                appendRunStyle(xml, style);
                xml.append("<w:t xml:space=\"preserve\">")
                        .append(escapeXml(document.getText(position, runEnd - position)))
                        .append("</w:t></w:r>");
                position = runEnd;
            }
            xml.append("</w:p>");
            if (end >= length) break;
            offset = end;
        }
        xml.append("<w:sectPr><w:pgSz w:w=\"12240\" w:h=\"15840\"/><w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\" w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/></w:sectPr>")
                .append("</w:body></w:document>");
        return xml.toString();
    }

    private static void appendRunStyle(StringBuilder xml, AttributeSet style) {
        boolean bold = StyleConstants.isBold(style), italic = StyleConstants.isItalic(style), underline = StyleConstants.isUnderline(style);
        String font = StyleConstants.getFontFamily(style);
        int size = StyleConstants.getFontSize(style);
        xml.append("<w:rPr>");
        if (bold) xml.append("<w:b/>"); if (italic) xml.append("<w:i/>");
        if (underline) xml.append("<w:u w:val=\"single\"/>");
        if (font != null && !font.isBlank()) xml.append("<w:rFonts w:ascii=\"").append(escapeXml(font))
                .append("\" w:hAnsi=\"").append(escapeXml(font)).append("\"/>");
        if (size > 0) xml.append("<w:sz w:val=\"").append(size * 2).append("\"/>");
        xml.append("</w:rPr>");
    }

    private static String attr(org.w3c.dom.Element element, String name) {
        String value = element.getAttributeNS(W, name);
        if (value == null || value.isEmpty()) value = element.getAttribute("w:" + name);
        return value == null || value.isEmpty() ? null : value;
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static void entry(ZipOutputStream zip, String name, String contents) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(contents.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
