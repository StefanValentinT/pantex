package org.example;

import java.util.ArrayList;
import java.util.List;

class Tokenizer {
    sealed interface Token
            permits TextToken,
                    StarToken,
                    ParagraphBreakToken,
                    HeadingToken,
                    MusicStartToken {
    }

    record TextToken(String text) implements Token {
    }

    record StarToken() implements Token {
    }

    record ParagraphBreakToken() implements Token {
    }

    record HeadingToken(int level) implements Token {
    }

    record MusicStartToken(List<App.Note> notes) implements Token{}
    
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
                        tokens.add(
                                new TextToken(
                                        textBuffer.toString()));
                        textBuffer.setLength(0);
                    }

                    tokens.add(
                            new HeadingToken(level));

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

                i += 7;

                List<App.Note> notes = new ArrayList<>();

                while (i < input.length() && !input.startsWith("</music>", i)) {
                    if (input.charAt(i) == ' ' || input.charAt(i) == '\n' || input.charAt(i) == '\r') {
                        i++;
                        continue;
                    }

                    
                    if (i + 3 >= input.length()) {
                        throw new RuntimeException("Incomplete note definition near end of music block.");
                    }

                    char name = input.charAt(i);
                    i++;

                    int hoehe = Character.getNumericValue(input.charAt(i));
                    i++;

                    int wert = Character.getNumericValue(input.charAt(i));
                    i++;

                    char vorzeichen = input.charAt(i);
                    i++;

                    if (vorzeichen != 'b' && vorzeichen != '#' && vorzeichen != '§') {
                        throw new RuntimeException("Das ist keine valide Note. Ungültiges Vorzeichen: " + vorzeichen);
                    }
                    
                    if (name < 'a' || name > 'g') {
                        throw new RuntimeException("Ungültiger Notenname: " + name);
                    }

                    notes.add(new App.Note(String.valueOf(name), hoehe, wert, String.valueOf(vorzeichen)));
                }

                if (input.startsWith("</music>", i)) {
                    i += 8;
                } else {
                    throw new RuntimeException("Missing closing </music> tag.");
                }

                tokens.add(new MusicStartToken(notes));
                continue; 
            }
            if (input.startsWith("\r\n\r\n", i)) {

                if (textBuffer.length() > 0) {
                    tokens.add(
                            new TextToken(
                                    textBuffer.toString()));
                    textBuffer.setLength(0);
                }

                tokens.add(
                        new ParagraphBreakToken());

                i += 4;
            }
            else if (input.startsWith("\n\n", i)) {

                if (textBuffer.length() > 0) {
                    tokens.add(
                            new TextToken(
                                    textBuffer.toString()));
                    textBuffer.setLength(0);
                }

                tokens.add(
                        new ParagraphBreakToken());

                i += 2;
            }
            else if (input.charAt(i) == '*') {

                if (textBuffer.length() > 0) {
                    tokens.add(
                            new TextToken(
                                    textBuffer.toString()));
                    textBuffer.setLength(0);
                }

                tokens.add(
                        new StarToken());

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
                }
                else {
                    textBuffer.append(c);
                }

                i++;
            }
        }

        if (textBuffer.length() > 0) {
            tokens.add(
                    new TextToken(
                            textBuffer.toString()));
        }

        return tokens;
    }
}
