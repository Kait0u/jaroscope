package pl.kaitou_dev.jaroscope.core;

/** JVM declaration kinds represented by a class interface. */
public enum ClassKind {
  /** A regular concrete or abstract class. */
  CLASS,

  /** An interface declaration. */
  INTERFACE,

  /** An enum declaration. */
  ENUM,

  /** An annotation declaration. */
  ANNOTATION,

  /** A record declaration. */
  RECORD
}
