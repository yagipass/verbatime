package io.github.yagipass.verbatime.cli;

final class StrictJson {

  private final String s;

  private int i;

  private StrictJson(String s) {
    this.s = s;
  }

  static void requireObject(String line) {
    StrictJson p = new StrictJson(line);
    p.object();
    if (p.i != line.length()) {
      throw p.fail("text after the object");
    }
  }

  private void object() {
    expect('{');
    if (peek() == '}') {
      i++;
      return;
    }
    while (true) {
      string();
      expect(':');
      value();
      char c = next();
      if (c == '}') {
        return;
      }
      if (c != ',') {
        throw fail("expected , or }");
      }
    }
  }

  private void array() {
    expect('[');
    if (peek() == ']') {
      i++;
      return;
    }
    while (true) {
      value();
      char c = next();
      if (c == ']') {
        return;
      }
      if (c != ',') {
        throw fail("expected , or ]");
      }
    }
  }

  private void value() {
    switch (peek()) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> string();
      case 't' -> literal("true");
      case 'f' -> literal("false");
      case 'n' -> literal("null");
      default -> number();
    }
  }

  private void string() {
    expect('"');
    while (true) {
      char c = next();
      if (c == '"') {
        return;
      }
      if (c < 0x20) {
        throw fail("unescaped control character");
      }
      if (c == '\\') {
        char e = next();
        if (e == 'u') {
          for (int k = 0; k < 4; k++) {
            if (Character.digit(next(), 16) < 0) {
              throw fail("bad \\u escape");
            }
          }
        } else if ("\"\\/bfnrt".indexOf(e) < 0) {
          throw fail("bad escape");
        }
      }
    }
  }

  private void number() {
    if (peek() == '-') {
      i++;
    }
    if (peek() == '0') {
      i++;
    } else {
      digits();
    }
    if (peek() == '.') {
      i++;
      digits();
    }
    if (peek() == 'e' || peek() == 'E') {
      i++;
      if (peek() == '+' || peek() == '-') {
        i++;
      }
      digits();
    }
  }

  private void digits() {
    int start = i;
    while (i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
      i++;
    }
    if (i == start) {
      throw fail("expected a digit");
    }
  }

  private void literal(String word) {
    if (!s.startsWith(word, i)) {
      throw fail("expected " + word);
    }
    i += word.length();
  }

  private void expect(char c) {
    if (next() != c) {
      throw fail("expected " + c);
    }
  }

  private char peek() {
    return i < s.length() ? s.charAt(i) : '\0';
  }

  private char next() {
    if (i >= s.length()) {
      throw fail("unexpected end");
    }
    return s.charAt(i++);
  }

  private AssertionError fail(String why) {
    return new AssertionError("not a JSON object (" + why + " at " + i + "): " + s);
  }
}
