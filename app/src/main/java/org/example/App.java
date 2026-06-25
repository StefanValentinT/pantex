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

    private static Font headingFont(
            int level,
            Font h1,
            Font h2,
            Font h3,
            Font h4,
            Font h5,
            Font h6) {

        return switch (level) {
            case 1 -> h1;
            case 2 -> h2;
            case 3 -> h3;
            case 4 -> h4;
            case 5 -> h5;
            default -> h6;
        };
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

            PdfWriter.getInstance(
                    document,
                    new FileOutputStream(outputPdfPath));

            document.open();

            Font normalFont =
                    new Font(
                            Font.TIMES_ROMAN,
                            12,
                            Font.NORMAL);

            Font boldFont =
                    new Font(
                            Font.TIMES_ROMAN,
                            12,
                            Font.BOLD);

            Font h1 =
                    new Font(
                            Font.TIMES_ROMAN,
                            24,
                            Font.BOLD);

            Font h2 =
                    new Font(
                            Font.TIMES_ROMAN,
                            20,
                            Font.BOLD);

            Font h3 =
                    new Font(
                            Font.TIMES_ROMAN,
                            18,
                            Font.BOLD);

            Font h4 =
                    new Font(
                            Font.TIMES_ROMAN,
                            16,
                            Font.BOLD);

            Font h5 =
                    new Font(
                            Font.TIMES_ROMAN,
                            14,
                            Font.BOLD);

            Font h6 =
                    new Font(
                            Font.TIMES_ROMAN,
                            13,
                            Font.BOLD);

            for (BlockNode block : ast.blocks()) {

                switch (block) {

                    case ParagraphNode p -> {

                        Paragraph paragraph =
                                new Paragraph();

                        paragraph.setSpacingAfter(12f);

                        for (InlineNode inline : p.children()) {

                            switch (inline) {

                                case NormalTextNode(String text) ->
                                        paragraph.add(
                                                new Chunk(
                                                        text,
                                                        normalFont));

                                case BoldTextNode(String text) ->
                                        paragraph.add(
                                                new Chunk(
                                                        text,
                                                        boldFont));
                            }
                        }

                        document.add(paragraph);
                    }

                    case HeadingNode h -> {

                        Paragraph heading =
                                new Paragraph();

                        heading.setSpacingBefore(10f);
                        heading.setSpacingAfter(8f);

                        Font headingFont =
                                headingFont(
                                        h.level(),
                                        h1,
                                        h2,
                                        h3,
                                        h4,
                                        h5,
                                        h6);

                        for (InlineNode inline : h.children()) {

                            String text =
                                    switch (inline) {
                                        case NormalTextNode(String t) -> t;
                                        case BoldTextNode(String t) -> t;
                                    };

                            heading.add(
                                    new Chunk(
                                            text,
                                            headingFont));
                        }

                        document.add(heading);
                    }
                }
            }

            System.out.println(
                    "PDF created successfully at: "
                            + outputPdfPath);

        }
        catch (DocumentException | IOException e) {

            System.err.println(
                    "An error occurred: "
                            + e.getMessage());

            e.printStackTrace();
        }
        finally {

            if (document.isOpen()) {
                document.close();
            }
        }
    }
}
