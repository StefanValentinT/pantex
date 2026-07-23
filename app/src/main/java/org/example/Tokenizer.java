package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Tokenizer {
	sealed interface Token
			permits TextToken,
					StarToken,
					ParagraphBreakToken,
					HeadingToken,
					MusicStartToken {
	}

	record TextToken(String text) implements Token {}
	record StarToken() implements Token {}
	record ParagraphBreakToken() implements Token {}
	record HeadingToken(int level) implements Token {}
	record MusicStartToken(List<App.MusicElement> elements) implements Token {}

	static List<Token> tokenize(String input) {

		List<Token> tokens = new ArrayList<>();
		StringBuilder textBuffer = new StringBuilder();

		int i = 0;

		while (i < input.length()) {

			if (i == 0 || input.charAt(i - 1) == '\n') {
				int start = i;
				int level = 0;

				while (i < input.length()
						&& input.charAt(i) == '#'
						&& level < 6) {
					level++;
					i++;
				}

				if (level > 0
						&& i < input.length()
						&& input.charAt(i) == ' ') {

					if (textBuffer.length() > 0) {
						tokens.add(new TextToken(textBuffer.toString()));
						textBuffer.setLength(0);
					}

					tokens.add(new HeadingToken(level));
					i++;
					continue;
				}

				i = start;
			}

			if (input.startsWith("<music>", i)) {
				if (textBuffer.length() > 0) {
					tokens.add(new TextToken(textBuffer.toString()));
					textBuffer.setLength(0);
				}

				int musicEnd = input.indexOf("</music>", i);
				if (musicEnd == -1) {
					throw new RuntimeException("Missing closing </music> tag.");
				}

				String musicContent = input.substring(i + 7, musicEnd);
				i = musicEnd + 8;

				List<App.MusicElement> elements = parseMusicContent(musicContent);
				tokens.add(new MusicStartToken(elements));
				continue;
			}

			if (input.startsWith("\r\n\r\n", i)) {
				if (textBuffer.length() > 0) {
					tokens.add(new TextToken(textBuffer.toString()));
					textBuffer.setLength(0);
				}
				tokens.add(new ParagraphBreakToken());
				i += 4;
			}
			else if (input.startsWith("\n\n", i)) {
				if (textBuffer.length() > 0) {
					tokens.add(new TextToken(textBuffer.toString()));
					textBuffer.setLength(0);
				}
				tokens.add(new ParagraphBreakToken());
				i += 2;
			}
			else if (input.charAt(i) == '*') {
				if (textBuffer.length() > 0) {
					tokens.add(new TextToken(textBuffer.toString()));
					textBuffer.setLength(0);
				}
				tokens.add(new StarToken());
				i++;
			}
			else {
				char c = input.charAt(i);
				if (c == '\r') {
					i++;
					continue;
				}
				if (c == '\n') {
					textBuffer.append(' ');
				} else {
					textBuffer.append(c);
				}
				i++;
			}
		}

		if (textBuffer.length() > 0) {
			tokens.add(new TextToken(textBuffer.toString()));
		}

		return tokens;
	}

	private static List<App.MusicElement> parseMusicContent(String content) {
		List<App.MusicElement> elements = new ArrayList<>();
		
		String normalized = content.replace("||", " DOUBLEBAR ").replace("|", " SINGLEBAR ");
		
		String[] parts = normalized.trim().split("\\s+");

		Pattern notePattern = Pattern.compile("^([#b§])?([a-gA-GhH])([0-9]):([1-9][0-9]*)$");
		Pattern timeSigPattern = Pattern.compile("^(\\d+)/(\\d+)$");

		for (String part : parts) {
			if (part.isEmpty()) continue;

			if (part.equals("&")) {
				elements.add(new App.ClefElement("&"));
			} else if (part.equals("DOUBLEBAR")) {
				elements.add(new App.BarLineElement("||"));
			} else if (part.equals("SINGLEBAR")) {
				elements.add(new App.BarLineElement("|"));
			} else {
				Matcher timeSigMatcher = timeSigPattern.matcher(part);
				Matcher noteMatcher = notePattern.matcher(part);

				if (timeSigMatcher.matches()) {
					int beats = Integer.parseInt(timeSigMatcher.group(1));
					int beatType = Integer.parseInt(timeSigMatcher.group(2));
					elements.add(new App.TimeSignatureElement(beats, beatType));
				} else if (noteMatcher.matches()) {
					String vorzeichen = noteMatcher.group(1) != null ? noteMatcher.group(1) : "";
					String name = noteMatcher.group(2);
					int hoehe = Integer.parseInt(noteMatcher.group(3));
					int wert = Integer.parseInt(noteMatcher.group(4));
					
					elements.add(new App.Note(name, hoehe, wert, vorzeichen));
				} else {
					throw new RuntimeException("Unbekanntes Musik-Element: " + part);
				}
			}
		}
		return elements;
	}
}