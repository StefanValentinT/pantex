package org.example;

import java.util.ArrayList;
import java.util.List;

class Tokenizer {
    sealed interface Token
            permits TextToken,
                    StarToken,
                    ParagraphBreakToken,
                    HeadingToken,
                    NotesToken {
    }

    record TextToken(String text) implements Token {
    }

    record StarToken() implements Token {
    }

    record ParagraphBreakToken() implements Token {
    }

    record HeadingToken(int level) implements Token {
    }

    record NotesToken(List<App.Note> notes) implements Token{}
    
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
