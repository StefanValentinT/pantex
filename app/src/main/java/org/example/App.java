package org.example;

import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class App {

    enum TokenType { TEXT, STAR, PARAGRAPH_BREAK }
    record Token(TokenType type, String value) {}

    private static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        StringBuilder textBuffer = new StringBuilder();
        int i = 0;
        int len = input.length();

        while (i < len) {
            if (input.startsWith("\n\n", i)) {
                if (textBuffer.length() > 0) {
                    tokens.add(new Token(TokenType.TEXT, textBuffer.toString()));
                    textBuffer.setLength(0);
                }
                tokens.add(new Token(TokenType.PARAGRAPH_BREAK, ""));
                i += 2;
            } else if (input.startsWith("\r\n\r\n", i)) {
                if (textBuffer.length() > 0) {
                    tokens.add(new Token(TokenType.TEXT, textBuffer.toString()));
                    textBuffer.setLength(0);
                }
                tokens.add(new Token(TokenType.PARAGRAPH_BREAK, ""));
                i += 4;
            } else if (input.charAt(i) == '*') {
                if (textBuffer.length() > 0) {
                    tokens.add(new Token(TokenType.TEXT, textBuffer.toString()));
                    textBuffer.setLength(0);
                }
                tokens.add(new Token(TokenType.STAR, "*"));
                i++;
            } else {
                char c = input.charAt(i);
                if (c == '\r') {
                    i++;
                    continue;
                }
                if (c == '\n') {
                    textBuffer.append(" ");
                } else {
                    textBuffer.append(c);
                }
                i++;
            }
        }

        if (textBuffer.length() > 0) {
            tokens.add(new Token(TokenType.TEXT, textBuffer.toString()));
        }

        return tokens;
    }

    sealed interface ASTNode {}
    
    record DocNode(List<ParagraphNode> paragraphs) implements ASTNode {}
    record ParagraphNode(List<InlineNode> children) implements ASTNode {}

    sealed interface InlineNode extends ASTNode permits NormalTextNode, BoldTextNode {}
    record NormalTextNode(String text) implements InlineNode {}
    record BoldTextNode(String text) implements InlineNode {}

    private static DocNode parse(List<Token> tokens) {
        List<ParagraphNode> paragraphs = new ArrayList<>();
        List<InlineNode> currentParagraphInlines = new ArrayList<>();
        boolean insideBold = false;
        StringBuilder inlineTextAccumulator = new StringBuilder();

        for (Token token : tokens) {
            switch (token.type()) {
                case STAR -> {
                    // Flush existing text buffer before toggling style
                    if (inlineTextAccumulator.length() > 0) {
                        String txt = inlineTextAccumulator.toString();
                        currentParagraphInlines.add(insideBold ? new BoldTextNode(txt) : new NormalTextNode(txt));
                        inlineTextAccumulator.setLength(0);
                    }
                    insideBold = !insideBold;
                }
                case TEXT -> inlineTextAccumulator.append(token.value());
                case PARAGRAPH_BREAK -> {
                    if (inlineTextAccumulator.length() > 0) {
                        String txt = inlineTextAccumulator.toString();
                        currentParagraphInlines.add(insideBold ? new BoldTextNode(txt) : new NormalTextNode(txt));
                        inlineTextAccumulator.setLength(0);
                    }
                    if (!currentParagraphInlines.isEmpty()) {
                        paragraphs.add(new ParagraphNode(new ArrayList<>(currentParagraphInlines)));
                        currentParagraphInlines.clear();
                    }
                }
            }
        }

        if (inlineTextAccumulator.length() > 0) {
            String txt = inlineTextAccumulator.toString();
            currentParagraphInlines.add(insideBold ? new BoldTextNode(txt) : new NormalTextNode(txt));
        }
        if (!currentParagraphInlines.isEmpty()) {
            paragraphs.add(new ParagraphNode(currentParagraphInlines));
        }

        return new DocNode(paragraphs);
    }

    public static void main(String[] args) {
        String inputFilePath = (args.length > 0) ? args[0] : "test.txt";
        String outputPdfPath = "output.pdf";

        Document document = new Document();

        try {
            String inputContent = Files.readString(Paths.get(inputFilePath));
            
            List<Token> tokens = tokenize(inputContent);
            DocNode ast = parse(tokens);

            PdfWriter.getInstance(document, new FileOutputStream(outputPdfPath));
            document.open();

            Font normalFont = new Font(Font.TIMES_ROMAN, 12, Font.NORMAL);
            Font boldFont = new Font(Font.TIMES_ROMAN, 12, Font.BOLD);

            for (ParagraphNode pNode : ast.paragraphs()) {
                Paragraph pdfParagraph = new Paragraph();
                pdfParagraph.setSpacingAfter(12f);

                for (InlineNode inline : pNode.children()) {
                    switch (inline) {
                        case NormalTextNode(String text) -> pdfParagraph.add(new Chunk(text, normalFont));
                        case BoldTextNode(String text)   -> pdfParagraph.add(new Chunk(text, boldFont));
                    }
                }
                document.add(pdfParagraph);
            }

            System.out.println("PDF created successfully at: " + outputPdfPath);

        } catch (DocumentException | IOException e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (document.isOpen()) {
                document.close();
            }
        }
    }
}
