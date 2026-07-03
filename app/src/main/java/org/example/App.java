package org.example;

import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class App {

    private static final Font normalFont = new Font(Font.TIMES_ROMAN, 12, Font.NORMAL);
    private static final Font boldFont = new Font(Font.TIMES_ROMAN, 12, Font.BOLD);
    private static final Font musicFont =
        new Font(Font.TIMES_ROMAN, 12, Font.NORMAL);
    private static final Font h1 = new Font(Font.TIMES_ROMAN, 24, Font.BOLD);
    private static final Font h2 = new Font(Font.TIMES_ROMAN, 20, Font.BOLD);
    private static final Font h3 = new Font(Font.TIMES_ROMAN, 18, Font.BOLD);
    private static final Font h4 = new Font(Font.TIMES_ROMAN, 16, Font.BOLD);
    private static final Font h5 = new Font(Font.TIMES_ROMAN, 14, Font.BOLD);
    private static final Font h6 = new Font(Font.TIMES_ROMAN, 13, Font.BOLD);

    sealed interface ASTNode {
    }

    record DocNode(List<BlockNode> blocks) implements ASTNode {
    }

    sealed interface BlockNode extends ASTNode
            permits ParagraphNode,
                    HeadingNode {
    }

    record ParagraphNode(List<InlineNode> children)
            implements BlockNode {
    }

    record HeadingNode(
            int level,
            List<InlineNode> children)
            implements BlockNode {
    }

    sealed interface InlineNode extends ASTNode
            permits NormalTextNode,
                    BoldTextNode, NotesNode {
    }

    record NormalTextNode(String text)
            implements InlineNode {
    }
	
	record NotesNode(List<Note> notes)
            implements InlineNode {
    }

	record Note(String name, int hoehe, int wert, String vorzeichen){}


    record BoldTextNode(String text)
            implements InlineNode {
    }


    private static DocNode parse(List<Tokenizer.Token> tokens) {

        List<BlockNode> blocks = new ArrayList<>();

        List<InlineNode> currentInlines =
                new ArrayList<>();

        StringBuilder text =
                new StringBuilder();

        boolean bold = false;

        boolean headingMode = false;
        int headingLevel = 1;

        for (Tokenizer.Token token : tokens) {

            switch (token) {

                case Tokenizer.StarToken ignored -> {

                    if (text.length() > 0) {

                        String value = text.toString();

                        currentInlines.add(
                                bold
                                        ? new BoldTextNode(value)
                                        : new NormalTextNode(value));

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

                case Tokenizer.TextToken t ->
                        text.append(t.text());

                case Tokenizer.HeadingToken h -> {

                    if (text.length() > 0) {

                        String value = text.toString();

                        currentInlines.add(
                                bold
                                        ? new BoldTextNode(value)
                                        : new NormalTextNode(value));

                        text.setLength(0);
                    }

                    if (!currentInlines.isEmpty()) {

                        blocks.add(
                                new ParagraphNode(
                                        new ArrayList<>(currentInlines)));

                        currentInlines.clear();
                    }

                    headingMode = true;
                    headingLevel = h.level();
                }

                case Tokenizer.ParagraphBreakToken ignored -> {

                    if (text.length() > 0) {

                        String value = text.toString();

                        currentInlines.add(
                                bold
                                        ? new BoldTextNode(value)
                                        : new NormalTextNode(value));

                        text.setLength(0);
                    }

                    if (!currentInlines.isEmpty()) {

                        if (headingMode) {
                            blocks.add(
                                    new HeadingNode(
                                            headingLevel,
                                            new ArrayList<>(currentInlines)));
                        }
                        else {
                            blocks.add(
                                    new ParagraphNode(
                                            new ArrayList<>(currentInlines)));
                        }

                        currentInlines.clear();
                    }

                    headingMode = false;
                }
            }
        }

        if (text.length() > 0) {

            String value = text.toString();

            currentInlines.add(
                    bold
                            ? new BoldTextNode(value)
                            : new NormalTextNode(value));
        }

        if (!currentInlines.isEmpty()) {

            if (headingMode) {
                blocks.add(
                        new HeadingNode(
                                headingLevel,
                                currentInlines));
            }
            else {
                blocks.add(
                        new ParagraphNode(
                                currentInlines));
            }
        }

        return new DocNode(blocks);
    }

    public static void main(String[] args) {

        String inputFilePath =
                args.length > 0
                        ? args[0]
                        : "test.txt";

        String outputPdfPath = "output.pdf";

        Document document = new Document();

        try {

            String inputContent =
                    Files.readString(
                            Paths.get(inputFilePath));

            List<Tokenizer.Token> tokens =
                    Tokenizer.tokenize(inputContent);

            DocNode ast =
                    parse(tokens);

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
            case NormalTextNode(String text) -> 
                container.add(new Chunk(text, currentFont));

            case BoldTextNode(String text) -> {
                Font appliedBold = (currentFont == normalFont) ? boldFont : currentFont;
                container.add(new Chunk(text, appliedBold));
            }

            case NotesNode(List<Note> notes) -> {
                for (Note note : notes) {
                    container.add(new Chunk(note.name() + note.hoehe() + " ", currentFont));
                }
            }

        }
    }

    private static void renderMusicStaff(List<Note> notes, Document document) throws DocumentException {
        if (notes == null || notes.isEmpty()) return;

        int staffRows = 5; 
        int totalColumns = notes.size() + 1;

        PdfPTable staffTable = new PdfPTable(totalColumns);
        staffTable.setWidthPercentage(100);
        staffTable.setSpacingBefore(8f);
        staffTable.setSpacingAfter(8f);

        for (int row = 0; row < staffRows; row++) {
            PdfPCell clefCell = new PdfPCell(new Phrase(row == 2 ? "𝄞" : "", musicFont));
            styleStaffCell(clefCell, row, staffRows);
            staffTable.addCell(clefCell);

            for (Note note : notes) {
                boolean isNoteInRow = (staffRows - 1 - (note.hoehe() % staffRows)) == row;
                
                String noteSymbol = "";
                if (isNoteInRow) {
                    String accidental = (note.vorzeichen() != null && !note.vorzeichen().isBlank()) ? note.vorzeichen() : "";
                    noteSymbol = accidental + "𝅝 (" + note.name() + ")";
                }

                PdfPCell noteCell = new PdfPCell(new Phrase(noteSymbol, musicFont));
                styleStaffCell(noteCell, row, staffRows);
                staffTable.addCell(noteCell);
            }
        }
        document.add(staffTable);
    }

    private static void styleStaffCell(PdfPCell cell, int currentRow, int totalRows) {
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingTop(4f);
        cell.setPaddingBottom(4f);
        cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);

        cell.setBorderWidthTop(0.5f);
        
        if (currentRow == totalRows - 1) {
            cell.setBorderWidthBottom(0.5f);
        }
    }

    private static Font headingFont(int level, Font... fonts) {
        if (level >= 1 && level <= fonts.length) {
            return fonts[level - 1];
        }
        return fonts[fonts.length - 1];
    }
}
