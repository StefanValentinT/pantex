package org.example;


import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

public class App {

	public static void main(String[] args) {
		String inputFilePath = (args.length > 0) ? args[0] : "test.txt";
		String outputPdfPath = "output.pdf";

		Document document = new Document();

		try {
			PdfWriter.getInstance(document, new FileOutputStream(outputPdfPath));
			document.open();
			Font textFont = new Font(Font.TIMES_ROMAN, 12, Font.BOLD);

			try (BufferedReader br = new BufferedReader(new FileReader(inputFilePath))) {
				String line;
				while ((line = br.readLine()) != null) {
					Paragraph paragraph = new Paragraph(line, textFont);
					paragraph.setSpacingAfter(6f);
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
}