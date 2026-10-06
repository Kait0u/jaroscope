package pl.kaitou_dev.jaroscope.logging;

/** Controls ANSI color selection for stderr log output. */
public enum ColorMode {
  /** Detect whether stderr is an interactive terminal. */
  AUTO,

  /** Always emit ANSI colors. */
  ALWAYS,

  /** Never emit ANSI colors. */
  NEVER
}
