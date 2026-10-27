package io.github.yagipass.verbatime.jmc.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.yagipass.verbatime.format.TraceBuilder;
import io.github.yagipass.verbatime.format.Vbtm;
import io.github.yagipass.verbatime.jmc.index.TestTraces;
import io.github.yagipass.verbatime.jmc.index.TraceIndexer;
import io.github.yagipass.verbatime.jmc.index.TraceSnapshot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ExportNamesTest {

  @Test
  void collidingShortNamesAreNumberedInFirstUseOrder() throws IOException {
    ExportNames names = new ExportNames(trace());
    assertEquals(
        "X.m", names.displayName(2), "the first id met keeps the bare name, whatever its id");
    assertEquals("X.m#2", names.displayName(1), "an overload met later is numbered");
    assertEquals(
        "X.m#3", names.displayName(4), "a same-named class in another package is numbered too");
    assertEquals("X.other", names.displayName(3));
    assertEquals("X.m", names.displayName(2), "stable on repeated lookups");
    assertEquals(4, names.registeredCount());
    assertEquals(2, names.registeredIdAt(0));
    assertEquals(1, names.registeredIdAt(1));
    assertEquals(4, names.registeredIdAt(2));
    assertEquals(3, names.registeredIdAt(3));
    assertEquals(
        "pkg.a.X.m(I)V", names.fullName(2), "the methods section resolves a name to its signature");
    assertEquals("pkg.a.X.m()V", names.fullName(1), "…and a numbered name to the other overload");
  }

  @Test
  void unknownIdsAreNamedLikeTheViewerAndCanBeLookedUp() throws IOException {
    ExportNames names = new ExportNames(trace());
    assertEquals("<unknown#9>", names.displayName(9));
    assertEquals("<unknown#9>", names.fullName(9));
    assertEquals(
        "<unknown#70000>",
        names.displayName(70_000),
        "ids beyond the registered range grow the table");
    assertArrayEquals("<unknown#9>".getBytes(StandardCharsets.UTF_8), names.utf8(9));
  }

  @Test
  void shortNameMatchesTheViewerPageRule() {
    assertEquals("Cls.m", ExportNames.shortName("pkg.Cls.m(I)V"));
    assertEquals("Cls.m", ExportNames.shortName("Cls.m"));
    assertEquals("m", ExportNames.shortName("m()V"));
    assertEquals("<no enter>", ExportNames.shortName("<no enter>"));
  }

  @Test
  void controlCharactersInANameAreShownAsEscapesLikeVbtmShowsThem() {
    assertEquals(
        "w\\x1b]0;title\\x07\\x1b[2K\\x09x\\x0a\\x0d\\x9b\\u202e1\\u2028\\u2066",
        ExportNames.sanitize(
            "w\u001b]0;title\u0007\u001b[2K\tx\n\r\u009b"
                + (char) 0x202E
                + "1"
                + (char) 0x2028
                + (char) 0x2066),
        "each character is shown, so names that differ only in them stay apart");
    assertEquals("pkg.Cls.m(I)V", ExportNames.sanitize("pkg.Cls.m(I)V"));
    assertEquals("例.処理()V", ExportNames.sanitize("例.処理()V"), "other text is left as it is");
  }

  @Test
  void noControlCharacterInARecordedNameReachesTheExport(@TempDir Path dir) throws IOException {
    TraceBuilder w = TestTraces.writer();
    w.thread(7, "w\u001b]0;title\u0007\u001b[2K\tx" + (char) 0x202E + "1");
    w.clazz(1, "pkg.Ro\u009bot", "r\u0001un()V");
    TraceBuilder.Payload p = new TraceBuilder.Payload(100);
    p.enter(100, 1).exit(200_100);
    w.chunk(7, 100, p.bytes(), true);
    w.gc(120, 10, Vbtm.GC_ACTION_MINOR, "Copy\u001b[1A", "Allocation" + (char) 0x2028 + "Failure");
    w.end();
    TraceSnapshot d = TestTraces.index(w);
    Path out = dir.resolve("s1.txt");
    SessionExporter.export(d, d.sessions.get(0), 0, out, TraceIndexer.ProgressListener.NONE);

    String text = Files.readString(out);
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      int type = Character.getType(c);
      if (c != '\n'
          && (type == Character.CONTROL
              || type == Character.FORMAT
              || type == Character.LINE_SEPARATOR
              || type == Character.PARAGRAPH_SEPARATOR)) {
        fail(
            String.format(
                "U+%04X at %d would reach a terminal or an agent reading the export as it is:%n%s",
                (int) c, i, text));
      }
    }
    assertTrue(
        text.contains("thread: w\\x1b]0;title\\x07\\x1b[2K\\x09x\\u202e1"), "the thread: " + text);
    assertTrue(text.contains("Ro\\x9bot.r\\x01un = pkg.Ro\\x9bot.r\\x01un()V"), "methods: " + text);
    assertTrue(text.contains("Copy\\x1b[1A: Allocation\\u2028Failure"), "gc pauses: " + text);
  }

  private static TraceSnapshot trace() throws IOException {
    TraceBuilder w = TestTraces.writer();
    w.clazz(1, "pkg.a.X", "m()V", "m(I)V", "other()V");
    w.clazz(4, "pkg.b.X", "m()V");
    return TestTraces.index(w);
  }
}
