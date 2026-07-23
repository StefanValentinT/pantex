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

	public sealed interface MusicElement permits Note, ClefElement, TimeSignatureElement, BarLineElement {}

	public record Note(String name, int hoehe, int wert, String vorzeichen) implements MusicElement {}
	public record ClefElement(String type) implements MusicElement {}
	public record TimeSignatureElement(int beats, int beatType) implements MusicElement {}
	public record BarLineElement(String type) implements MusicElement {}

	sealed interface ASTNode {}
	record DocNode(List<BlockNode> blocks) implements ASTNode {}
	sealed interface BlockNode extends ASTNode permits ParagraphNode, HeadingNode {}
	record ParagraphNode(List<InlineNode> children) implements BlockNode {}
	record HeadingNode(int level, List<InlineNode> children) implements BlockNode {}
	sealed interface InlineNode extends ASTNode permits NormalTextNode, BoldTextNode, NotesNode {}
	record NormalTextNode(String text) implements InlineNode {}
	record NotesNode(List<MusicElement> elements) implements InlineNode {}
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
					currentInlines.add(new NotesNode(m.elements()));
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
						renderMusicStaff(notesNode.elements(), document);
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
			case NotesNode(List<MusicElement> elements) -> {
				for (MusicElement elem : elements) {
					if (elem instanceof Note note) {
						String symbol = switch (note.wert()) {
							case 1 -> "\uD834\uDD5D";
							case 2 -> "\uD834\uDD5E";
							case 4 -> "\u2669";
							case 8 -> "\u266A";
							case 6, 16 -> "\uD834\uDD61";
							default -> "\u2669";
						};
						String acc = note.vorzeichen().equals("#") ? "\u266F" : (note.vorzeichen().equals("b") ? "\u266D" : "");
						container.add(new Chunk(" " + acc + note.name().toUpperCase() + symbol + " ", currentFont));
					} else if (elem instanceof BarLineElement bar) {
						container.add(new Chunk(" " + bar.type() + " ", currentFont));
					} else if (elem instanceof TimeSignatureElement ts) {
						container.add(new Chunk(" " + ts.beats() + "/" + ts.beatType() + " ", currentFont));
					} else if (elem instanceof ClefElement) {
						container.add(new Chunk(" 🎼 ", currentFont));
					}
				}
			}
		}
	}

	private static void renderMusicStaff(List<MusicElement> elements, Document document) throws DocumentException {
		if (elements == null || elements.isEmpty()) return;

		PdfPTable staffTable = new PdfPTable(1);
		staffTable.setWidthPercentage(100);
		staffTable.setSpacingBefore(18f);
		staffTable.setSpacingAfter(18f);

		PdfPCell cell = new PdfPCell();
		cell.setMinimumHeight(110f);
		cell.setCellEvent(new MusicStaffRenderer(elements));
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
		private final List<MusicElement> elements;

		public MusicStaffRenderer(List<MusicElement> elements) {
			this.elements = elements;
		}

		private int calculateStaffStep(String name, int octave) {
			int baseStep = switch (name.toLowerCase()) {
				case "c" -> -6;
				case "d" -> -5;
				case "e" -> -4;
				case "f" -> -3;
				case "g" -> -2;
				case "a" -> -1;
				case "b", "h" -> 0;
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

			cb.setLineWidth(1.2f);
			cb.setRGBColorStroke(0, 0, 0);
			cb.moveTo(xStart, yCenter - 2 * lineSpacing);
			cb.lineTo(xStart, yCenter + 2 * lineSpacing);
			cb.stroke();

			int numNotes = 0;
			float fixedWidthUsed = 0f;

			for (MusicElement elem : elements) {
				if (elem instanceof Note) {
					numNotes++;
				} else if (elem instanceof ClefElement) {
					fixedWidthUsed += 30f;
				} else if (elem instanceof TimeSignatureElement) {
					fixedWidthUsed += 25f;
				} else if (elem instanceof BarLineElement bar) {
					if (bar.type().equals("||")) fixedWidthUsed += 22f;
					else fixedWidthUsed += 18f;
				}
			}

			float availableWidth = (xEnd - 20f) - (xStart + 15f) - fixedWidthUsed;
			float noteSpacing = numNotes > 0 ? Math.max(25f, availableWidth / numNotes) : 35f;

			float currentX = xStart + 15f;

			for (MusicElement elem : elements) {
				if (elem instanceof ClefElement) {
					drawClef(cb, currentX + 10f, yCenter, lineSpacing);
					currentX += 30f;
				} else if (elem instanceof TimeSignatureElement ts) {
					drawTimeSignature(cb, ts.beats(), ts.beatType(), currentX + 12f, yCenter, lineSpacing);
					currentX += 25f;
				} else if (elem instanceof BarLineElement bar) {
					if (bar.type().equals("||")) {
						drawDoubleBarLine(cb, currentX + 10f, yCenter, lineSpacing);
						currentX += 22f;
					} else {
						drawSingleBarLine(cb, currentX + 8f, yCenter, lineSpacing);
						currentX += 18f;
					}
				} else if (elem instanceof Note note) {
					float xNote = currentX + (noteSpacing / 2f);
					int staffStep = calculateStaffStep(note.name(), note.hoehe());
					float yNote = yCenter + (staffStep * halfSpacing);

					if (staffStep <= -6) {
						cb.setLineWidth(1.0f);
						cb.setRGBColorStroke(0, 0, 0);
						for (int h = -6; h >= staffStep; h -= 2) {
							float yLedger = yCenter + (h * halfSpacing);
							cb.moveTo(xNote - 8f, yLedger);
							cb.lineTo(xNote + 8f, yLedger);
						}
						cb.stroke();
					} else if (staffStep >= 6) {
						cb.setLineWidth(1.0f);
						cb.setRGBColorStroke(0, 0, 0);
						for (int h = 6; h <= staffStep; h += 2) {
							float yLedger = yCenter + (h * halfSpacing);
							cb.moveTo(xNote - 8f, yLedger);
							cb.lineTo(xNote + 8f, yLedger);
						}
						cb.stroke();
					}

					if (note.vorzeichen() != null && !note.vorzeichen().isBlank()) {
						drawAccidental(cb, note.vorzeichen(), xNote - 11f, yNote, lineSpacing);
					}

					boolean filled = (note.wert() == 4 || note.wert() == 8 || note.wert() == 16 || note.wert() == 6);
					boolean hasStem = (note.wert() != 1);

					drawNotehead(cb, xNote, yNote, filled, lineSpacing);

					if (hasStem) {
						boolean stemDown = (staffStep >= 0);
						float stemLength = 3.3f * lineSpacing;
						
						float stemX = stemDown ? (xNote - 4.5f) : (xNote + 4.5f);
						float stemYEnd = stemDown ? (yNote - stemLength) : (yNote + stemLength);

						cb.setLineWidth(1.0f);
						cb.setRGBColorStroke(0, 0, 0);
						cb.moveTo(stemX, yNote);
						cb.lineTo(stemX, stemYEnd);
						cb.stroke();

						if (note.wert() == 8) {
							drawFlag(cb, stemX, stemYEnd, stemDown, 1, lineSpacing);
						} else if (note.wert() == 16 || note.wert() == 6) {
							drawFlag(cb, stemX, stemYEnd, stemDown, 2, lineSpacing);
						}
					}

					currentX += noteSpacing;
				}
			}

			cb.restoreState();
		}

		private void drawSingleBarLine(PdfContentByte cb, float x, float yCenter, float lineSpacing) {
			cb.saveState();
			cb.setLineWidth(1.2f);
			cb.setRGBColorStroke(0, 0, 0);
			cb.moveTo(x, yCenter - 2 * lineSpacing);
			cb.lineTo(x, yCenter + 2 * lineSpacing);
			cb.stroke();
			cb.restoreState();
		}

		private void drawDoubleBarLine(PdfContentByte cb, float x, float yCenter, float lineSpacing) {
			cb.saveState();
			cb.setRGBColorStroke(0, 0, 0);

			cb.setLineWidth(1.0f);
			cb.moveTo(x - 2f, yCenter - 2 * lineSpacing);
			cb.lineTo(x - 2f, yCenter + 2 * lineSpacing);
			cb.stroke();

			cb.setLineWidth(2.8f);
			cb.moveTo(x + 2f, yCenter - 2 * lineSpacing);
			cb.lineTo(x + 2f, yCenter + 2 * lineSpacing);
			cb.stroke();
			
			cb.restoreState();
		}

		private void drawTimeSignature(PdfContentByte cb, int beats, int beatType, float x, float yCenter, float lineSpacing) {
			try {
				BaseFont bf = BaseFont.createFont(BaseFont.TIMES_ITALIC, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
				cb.saveState();

				cb.beginText();
				cb.setFontAndSize(bf, 16f);
				cb.setRGBColorFill(0, 0, 0);

				cb.showTextAligned(PdfContentByte.ALIGN_CENTER, String.valueOf(beats), x, yCenter + 0.4f * lineSpacing, 0);
				cb.showTextAligned(PdfContentByte.ALIGN_CENTER, String.valueOf(beatType), x, yCenter - 1.6f * lineSpacing, 0);
				cb.endText();

				cb.restoreState();
			} catch (Exception e) {
				System.err.println("Fehler beim Zeichnen der Taktangabe: " + e.getMessage());
			}
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
				cb.stroke();
			}
			cb.restoreState();
		}

		private void drawFlag(PdfContentByte cb, float stemX, float stemYEnd, boolean stemDown, int count, float lineSpacing) {
			cb.saveState();
			cb.setRGBColorFill(0, 0, 0);

			float dir = stemDown ? -1f : 1f;

			for (int i = 0; i < count; i++) {
				float yOffset = i * (-dir * 5.5f);
				float startY = stemYEnd + yOffset;

				cb.moveTo(stemX, startY);

				float topCp1x = stemX + 4.5f;
				float topCp1y = startY - (dir * 2.0f);
				float topCp2x = stemX + 7.5f;
				float topCp2y = startY - (dir * 7.0f);
				float topMidX = stemX + 6.0f;
				float topMidY = startY - (dir * 11.0f);

				cb.curveTo(topCp1x, topCp1y, topCp2x, topCp2y, topMidX, topMidY);

				float topCp3x = stemX + 4.8f;
				float topCp3y = startY - (dir * 13.5f);
				float topCp4x = stemX + 4.2f;
				float topCp4y = startY - (dir * 15.0f);
				float tipX    = stemX + 4.5f;
				float tipY    = startY - (dir * 16.0f);

				cb.curveTo(topCp3x, topCp3y, topCp4x, topCp4y, tipX, tipY);

				float botCp1x = stemX + 2.8f;
				float botCp1y = startY - (dir * 14.5f);
				float botCp2x = stemX + 3.2f;
				float botCp2y = startY - (dir * 12.8f);
				float botMidX = stemX + 4.2f; 
				float botMidY = startY - (dir * 10.5f);

				cb.curveTo(botCp1x, botCp1y, botCp2x, botCp2y, botMidX, botMidY);

				float botCp3x = stemX + 5.5f;
				float botCp3y = startY - (dir * 6.5f);
				float botCp4x = stemX + 2.5f;
				float botCp4y = startY - (dir * 2.0f);
				float endY    = startY - (dir * 1.8f);

				cb.curveTo(botCp3x, botCp3y, botCp4x, botCp4y, stemX, endY);

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
			} else if (type.equals("§") || type.equalsIgnoreCase("n")) {
				cb.setLineWidth(1.0f);
				cb.moveTo(x - 1.5f, y - 6f); cb.lineTo(x - 1.5f, y + 4f);
				cb.moveTo(x + 1.5f, y - 4f); cb.lineTo(x + 1.5f, y + 6f);
				cb.moveTo(x - 1.5f, y + 2f); cb.lineTo(x + 1.5f, y + 4f);
				cb.moveTo(x - 1.5f, y - 4f); cb.lineTo(x + 1.5f, y - 2f);
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
