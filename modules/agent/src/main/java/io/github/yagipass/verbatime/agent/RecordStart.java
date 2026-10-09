package io.github.yagipass.verbatime.agent;

import java.util.Locale;

public enum RecordStart {
  ONDEMAND,
  STARTUP;

  @Override
  public String toString() {
    return name().toLowerCase(Locale.ROOT);
  }

  static RecordStart parse(String value) {
    return switch (value) {
      case "ondemand" -> ONDEMAND;
      case "startup" -> STARTUP;
      default ->
          throw new IllegalArgumentException(
              "record= needs startup or ondemand, got '" + value + "'");
    };
  }
}
