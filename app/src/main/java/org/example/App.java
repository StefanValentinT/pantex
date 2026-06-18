package org.example;

import org.openpdf.text.Chunk;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class App {

	sealed interface Node permits NormalTextNode, BoldTextNode {}
	
	record NormalTextNode(String text) implements Node {}
	record BoldTextNode(String text) implements Node {}

	public static void main(String[] args) {
		String inputFilePath = (args.length > 0) ? args[0] : "test.txt";
		String outputPdfPath = "output.pdf";

		Document document = new Document();

		try {
			PdfWriter.getInstance(document, new FileOutputStream(outputPdfPath));
			document.open();

			Font normalFont = new Font(Font.TIMES_ROMAN, 12, Font.NORMAL);
			Font boldFont = new Font(Font.TIMES_ROMAN, 12, Font.BOLD);

			try (BufferedReader br = new BufferedReader(new FileReader(inputFilePath))) {
				String line;
				while ((line = br.readLine()) != null) {
					List<Node> ast = parseLineToAST(line);
					
					Paragraph paragraph = new Paragraph();
					paragraph.setSpacingAfter(6f);

					for (Node node : ast) {
						switch (node) {
							case BoldTextNode(String text) -> 
								paragraph.add(new Chunk(text, boldFont));
							case NormalTextNode(String text) -> 
								paragraph.add(new Chunk(text, normalFont));
						}
					}
					
					document.add(paragraph);
				}
			}

			System.out.println("PDF created at: " + outputPdfPath);

		} catch (DocumentException | IOException e) {
			System.err.println("An error occurred: " + e.getMessage());
			e.printStackTrace();
		} finally {
			if (document.isOpen()) {
				document.close();
			}
		}
	}

	private static List<Node> parseLineToAST(String line) {
		List<Node> nodes = new ArrayList<>();
		int lastPos = 0;
		boolean isBold = false;

		while (true) {
			int nextPos = line.indexOf("*", lastPos);
			
			if (nextPos == -1) {
				String remaining = line.substring(lastPos);
				if (!remaining.isEmpty()) {
					nodes.add(isBold ? new BoldTextNode(remaining) : new NormalTextNode(remaining));
				}
				break;
			}

			String part = line.substring(lastPos, nextPos);
			if (!part.isEmpty()) {
				nodes.add(isBold ? new BoldTextNode(part) : new NormalTextNode(part));
			}

			isBold = !isBold;
			lastPos = nextPos + 2;
		}

		return nodes;
	}
}
