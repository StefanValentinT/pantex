package org.example;

import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPCellEvent;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.BaseFont;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class App {

    private static final Font normalFont = new Font(Font.TIMES_ROMAN, 12, Font.NORMAL);
    private static final Font boldFont = new Font(Font.TIMES_ROMAN, 12, Font.BOLD);
    private static final Font h1 = new Font(Font.TIMES_ROMAN, 24, Font.BOLD);
    private static final Font h2 = new Font(Font.TIMES_ROMAN, 20, Font.BOLD);
    private static final Font h3 = new Font(Font.TIMES_ROMAN, 18, Font.BOLD);
    private static final Font h4 = new Font(Font.TIMES_ROMAN, 16, Font.BOLD);
    private static final Font h5 = new Font(Font.TIMES_ROMAN, 14, Font.BOLD);
    private static final Font h6 = new Font(Font.TIMES_ROMAN, 13, Font.BOLD);

    private static BaseFont textBaseFont;
    static {
        try {
            textBaseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (Exception e) {
            System.err.println("Failed to load native BaseFont: " + e.getMessage());
        }
    }

    sealed interface ASTNode {}
    record DocNode(List<BlockNode> blocks) implements ASTNode {}
    sealed interface BlockNode extends ASTNode permits ParagraphNode, HeadingNode {}
    record ParagraphNode(List<InlineNode> children) implements BlockNode {}
    record HeadingNode(int level, List<InlineNode> children) implements BlockNode {}
    sealed interface InlineNode extends ASTNode permits NormalTextNode, BoldTextNode, NotesNode {}
    record NormalTextNode(String text) implements InlineNode {}
    record NotesNode(List<Note> notes) implements InlineNode {}
    record Note(String name, int hoehe, int wert, String vorzeichen){}
    record BoldTextNode(String text) implements InlineNode {}

    private static DocNode parse(List<Tokenizer.Token> tokens) {
        List<BlockNode> blocks = new ArrayList<>();
        List<InlineNode> currentInlines = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        boolean bold = false;
        boolean headingMode = false;
        int headingLevel = 1;

        for (Tokenizer.Token token : tokens) {
            switch (token) {
                case Tokenizer.StarToken ignored -> {
                    if (text.length() > 0) {
                        String value = text.toString();
                        currentInlines.add(bold ? new BoldTextNode(value) : new NormalTextNode(value));
                        text.setLength(0);
                    }
                    bold = !bold;
                }
                case Tokenizer.MusicStartToken m -> {
                    if (text.length() > 0) {
                        String value = text.toString();
                        currentInlines.add(bold ? new BoldTextNode(value) : new NormalTextNode(value));
                        text.setLength(0);
                    }
                    currentInlines.add(new NotesNode(m.notes()));
                }
                case Tokenizer.TextToken t -> text.append(t.text());
                case Tokenizer.HeadingToken h -> {
                    if (text.length() > 0) {
                        String value = text.toString();
                        currentInlines.add(bold ? new BoldTextNode(value) : new NormalTextNode(value));
                        text.setLength(0);
                    }
                    if (!currentInlines.isEmpty()) {
                        blocks.add(new ParagraphNode(new ArrayList<>(currentInlines)));
                        currentInlines.clear();
                    }
                    headingMode = true;
                    headingLevel = h.level();
                }
                case Tokenizer.ParagraphBreakToken ignored -> {
                    if (text.length() > 0) {
                        String value = text.toString();
                        currentInlines.add(bold ? new BoldTextNode(value) : new NormalTextNode(value));
                        text.setLength(0);
                    }
                    if (!currentInlines.isEmpty()) {
                        if (headingMode) {
                            blocks.add(new HeadingNode(headingLevel, new ArrayList<>(currentInlines)));
                        } else {
                            blocks.add(new ParagraphNode(new ArrayList<>(currentInlines)));
                        }
                        currentInlines.clear();
                    }
                    headingMode = false;
                }
            }
        }

        if (text.length() > 0) {
            String value = text.toString();
            currentInlines.add(bold ? new BoldTextNode(value) : new NormalTextNode(value));
        }

        if (!currentInlines.isEmpty()) {
            if (headingMode) {
                blocks.add(new HeadingNode(headingLevel, currentInlines));
            } else {
                blocks.add(new ParagraphNode(currentInlines));
            }
        }

        return new DocNode(blocks);
    }

    public static void main(String[] args) {
        String inputFilePath = args.length > 0 ? args[0] : "test.txt";
        String outputPdfPath = "output.pdf";
        Document document = new Document();

        try {
            String inputContent = Files.readString(Paths.get(inputFilePath));
            List<Tokenizer.Token> tokens = Tokenizer.tokenize(inputContent);
            DocNode ast = parse(tokens);

            PdfWriter.getInstance(document, new FileOutputStream(outputPdfPath));
            document.open();

            for (BlockNode block : ast.blocks()) {
                emitBlock(block, document);
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

    private static void emitBlock(BlockNode block, Document document) throws DocumentException {
        switch (block) {
            case ParagraphNode p -> {
                Paragraph paragraph = new Paragraph();
                paragraph.setSpacingAfter(12f);

                for (InlineNode inline : p.children()) {
                    if (inline instanceof NotesNode notesNode) {
                        if (!paragraph.isEmpty()) {
                            document.add(paragraph);
                            paragraph = new Paragraph();
                            paragraph.setSpacingAfter(12f);
                        }
                        renderMusicStaff(notesNode.notes(), document);
                    } else {
                        emitInline(inline, paragraph, normalFont);
                    }
                }
                if (!paragraph.isEmpty()) {
                    document.add(paragraph);
                }
            }
            case HeadingNode h -> {
                Paragraph heading = new Paragraph();
                heading.setSpacingBefore(10f);
                heading.setSpacingAfter(8f);
                Font headingFont = headingFont(h.level(), h1, h2, h3, h4, h5, h6);

                for (InlineNode inline : h.children()) {
                    emitInline(inline, heading, headingFont);
                }
                document.add(heading);
            }
        }
    }

    private static void emitInline(InlineNode inline, Paragraph container, Font currentFont) {
        switch (inline) {
            case NormalTextNode(String text) -> container.add(new Chunk(text, currentFont));
            case BoldTextNode(String text) -> {
                Font appliedBold = (currentFont == normalFont) ? boldFont : currentFont;
                container.add(new Chunk(text, appliedBold));
            }
            case NotesNode(List<Note> notes) -> {
                for (Note note : notes) {
                    String symbol = switch (note.wert()) {
                        case 1 -> "\uD834\uDD5D";
                        case 2 -> "\uD834\uDD5E";
                        case 4 -> "\u2669";
                        case 8 -> "\u266A";
                        case 6 -> "\uD834\uDD61";
                        default -> "\u2669";
                    };
                    String acc = note.vorzeichen().equals("#") ? "\u266F" : (note.vorzeichen().equals("b") ? "\u266D" : "");
                    container.add(new Chunk(" " + acc + note.name().toUpperCase() + symbol + " ", currentFont));
                }
            }
        }
    }

    private static void renderMusicStaff(List<Note> notes, Document document) throws DocumentException {
        if (notes == null || notes.isEmpty()) return;

        PdfPTable staffTable = new PdfPTable(1);
        staffTable.setWidthPercentage(100);
        staffTable.setSpacingBefore(18f);
        staffTable.setSpacingAfter(18f);

        PdfPCell cell = new PdfPCell();
        cell.setMinimumHeight(110f);
        cell.setCellEvent(new MusicStaffRenderer(notes));
        cell.setBorder(PdfPCell.NO_BORDER);
        
        staffTable.addCell(cell);
        document.add(staffTable);
    }

    private static Font headingFont(int level, Font... fonts) {
        if (level >= 1 && level <= fonts.length) {
            return fonts[level - 1];
        }
        return fonts[fonts.length - 1];
    }

    private static class MusicStaffRenderer implements PdfPCellEvent {
        private final List<Note> notes;

        public MusicStaffRenderer(List<Note> notes) {
            this.notes = notes;
        }

        private int calculateStaffStep(String name, int octave) {
            int baseStep = switch (name.toLowerCase()) {
                case "c" -> -6;
                case "d" -> -5;
                case "e" -> -4;
                case "f" -> -3;
                case "g" -> -2;
                case "a" -> -1;
                case "b" -> 0;
                default -> 0;
            };
            
            int octaveDiff = octave - 4; 
            return baseStep + (octaveDiff * 7);
        }

        @Override
        public void cellLayout(PdfPCell cell, org.openpdf.text.Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.LINECANVAS];

            float xStart = position.getLeft() + 20f;
            float xEnd = position.getRight() - 20f;
            float yCenter = position.getBottom() + (position.getHeight() / 2f) - 5f;

            float lineSpacing = 10f; 
            float halfSpacing = lineSpacing / 2f;

            cb.saveState();

            cb.setLineWidth(0.8f);
            cb.setRGBColorStroke(80, 80, 80);
            for (int i = -2; i <= 2; i++) {
                float yLine = yCenter + (i * lineSpacing);
                cb.moveTo(xStart, yLine);
                cb.lineTo(xEnd, yLine);
            }
            cb.stroke();

            cb.setLineWidth(1.5f);
            cb.moveTo(xStart, yCenter - 2 * lineSpacing);
            cb.lineTo(xStart, yCenter + 2 * lineSpacing);
            cb.moveTo(xEnd, yCenter - 2 * lineSpacing);
            cb.lineTo(xEnd, yCenter + 2 * lineSpacing);
            cb.stroke();

            drawClef(cb, xStart + 15f, yCenter, lineSpacing);

            float startOfNotesX = xStart + 55f;
            float noteSpacing = (xEnd - startOfNotesX - 30f) / (notes.size());
            float currentX = startOfNotesX;

            for (Note note : notes) {
                int staffStep = calculateStaffStep(note.name(), note.hoehe());
                float yNote = yCenter + (staffStep * halfSpacing);

                if (staffStep <= -6) {
                    cb.setLineWidth(1.0f);
                    cb.setRGBColorStroke(0, 0, 0);
                    for (int h = -6; h >= staffStep; h -= 2) {
                        float yLedger = yCenter + (h * halfSpacing);
                        cb.moveTo(currentX - 8f, yLedger);
                        cb.lineTo(currentX + 8f, yLedger);
                    }
                    cb.stroke();
                } else if (staffStep >= 6) {
                    cb.setLineWidth(1.0f);
                    cb.setRGBColorStroke(0, 0, 0);
                    for (int h = 6; h <= staffStep; h += 2) {
                        float yLedger = yCenter + (h * halfSpacing);
                        cb.moveTo(currentX - 8f, yLedger);
                        cb.lineTo(currentX + 8f, yLedger);
                    }
                    cb.stroke();
                }

                if (note.vorzeichen() != null && !note.vorzeichen().isBlank() && !note.vorzeichen().equals("§")) {
                    drawAccidental(cb, note.vorzeichen(), currentX - 11f, yNote, lineSpacing);
                }

                boolean filled = (note.wert() == 4 || note.wert() == 8 || note.wert() == 6);
                boolean hasStem = (note.wert() != 1);

                drawNotehead(cb, currentX, yNote, filled, lineSpacing);

                if (hasStem) {
                    boolean stemDown = (staffStep >= 0);
                    float stemLength = 3.3f * lineSpacing;
                    
                    float stemX = stemDown ? (currentX - 4.5f) : (currentX + 4.5f);
                    float stemYEnd = stemDown ? (yNote - stemLength) : (yNote + stemLength);

                    cb.setLineWidth(1.0f);
                    cb.setRGBColorStroke(0, 0, 0);
                    cb.moveTo(stemX, yNote);
                    cb.lineTo(stemX, stemYEnd);
                    cb.stroke();

                    if (note.wert() == 8) {
                        drawFlag(cb, stemX, stemYEnd, stemDown, 1, lineSpacing);
                    } else if (note.wert() == 6) {
                        drawFlag(cb, stemX, stemYEnd, stemDown, 2, lineSpacing);
                    }
                }

                currentX += noteSpacing;
            }

            cb.restoreState();
        }

        private void drawNotehead(PdfContentByte cb, float x, float y, boolean filled, float lineSpacing) {
            cb.saveState();
            cb.setRGBColorFill(0, 0, 0);
            cb.setRGBColorStroke(0, 0, 0);

            float cos = 0.9397f;
            float sin = -0.3420f;
            cb.concatCTM(cos, sin, -sin, cos, x, y);

            float rx = 1.15f * (lineSpacing / 2f);
            float ry = 0.72f * (lineSpacing / 2f);

            cb.ellipse(-rx, -ry, rx, ry);

            if (filled) {
                cb.fill();
            } else {
                cb.setLineWidth(1.3f);
                cb.stroke();
                cb.setLineWidth(0.8f);
                cb.moveTo(-rx + 1.5f, -ry + 1f);
                cb.lineTo(rx - 1.5f, ry - 1f);
                cb.stroke();
            }
            cb.restoreState();
        }

        private void drawFlag(PdfContentByte cb, float stemX, float stemYEnd, boolean stemDown, int count, float lineSpacing) {
            cb.saveState();
            cb.setRGBColorFill(0, 0, 0);
            cb.setRGBColorStroke(0, 0, 0);
            cb.setLineWidth(0.5f);

            float direction = stemDown ? 1f : -1f;

            for (int i = 0; i < count; i++) {
                float yOffset = i * (direction * 4.0f);
                float startY = stemYEnd + yOffset;

                cb.moveTo(stemX, startY);
                if (!stemDown) {
                    cb.curveTo(stemX + 3.0f, startY - 2.0f, stemX + 6.0f, startY - 7.0f, stemX + 5.0f, startY - 14.0f);
                    cb.curveTo(stemX + 4.5f, startY - 9.0f, stemX + 2.5f, startY - 4.0f, stemX, startY - 1.5f);
                } else {
                    cb.curveTo(stemX + 3.0f, startY + 2.0f, stemX + 6.0f, startY + 7.0f, stemX + 5.0f, startY + 14.0f);
                    cb.curveTo(stemX + 4.5f, startY + 9.0f, stemX + 2.5f, startY + 4.0f, stemX, startY + 1.5f);
                }
                cb.fill();
            }
            cb.restoreState();
        }

        private void drawAccidental(PdfContentByte cb, String type, float x, float y, float lineSpacing) {
            cb.saveState();
            cb.setRGBColorStroke(0, 0, 0);
            cb.setRGBColorFill(0, 0, 0);

            if (type.equals("#")) {
                cb.setLineWidth(0.8f);
                cb.moveTo(x - 1.8f, y - 7f); cb.lineTo(x - 1.2f, y + 7f);
                cb.moveTo(x + 1.2f, y - 7f); cb.lineTo(x + 1.8f, y + 7f);
                cb.stroke();

                cb.setLineWidth(1.8f);
                cb.moveTo(x - 4f, y - 3f); cb.lineTo(x + 4f, y - 1f);
                cb.moveTo(x - 4f, y + 1f); cb.lineTo(x + 4f, y + 3f);
                cb.stroke();
            } else if (type.equalsIgnoreCase("b")) {
                cb.setLineWidth(1.1f);
                cb.moveTo(x - 2f, y - 5f); cb.lineTo(x - 2f, y + 8f);
                cb.stroke();
                
                cb.moveTo(x - 2f, y - 5f);
                cb.curveTo(x + 2.5f, y - 5f, x + 3.5f, y - 1f, x - 2f, y + 2.5f);
                cb.stroke();
            }
            cb.restoreState();
        }

        private void drawClef(PdfContentByte cb, float x, float y, float lineSpacing) {
            cb.saveState();
            cb.setLineWidth(1.3f);
            cb.setRGBColorStroke(0, 0, 0);
            cb.setRGBColorFill(0, 0, 0);

            cb.moveTo(x, y - 2.2f * lineSpacing);
            cb.lineTo(x, y + 2.0f * lineSpacing);
            cb.stroke();

            cb.circle(x - 1.5f, y - 2.2f * lineSpacing, 1.8f);
            cb.fill();

            cb.moveTo(x, y + 2.0f * lineSpacing);
            cb.curveTo(x + 8f, y + 1.2f * lineSpacing, x + 5f, y + 0.3f * lineSpacing, x - 3f, y - 0.2f * lineSpacing);
            cb.curveTo(x - 11f, y - 0.8f * lineSpacing, x - 8f, y - 1.8f * lineSpacing, x, y - 1.8f * lineSpacing);
            cb.curveTo(x + 7f, y - 1.8f * lineSpacing, x + 6f, y - 0.4f * lineSpacing, x - 1f, y - 0.9f * lineSpacing);
            cb.curveTo(x - 4f, y - 1.1f * lineSpacing, x - 2f, y - 1.5f * lineSpacing, x, y - 1.2f * lineSpacing);
            
            cb.stroke();
            cb.restoreState();
        }
    }
}
