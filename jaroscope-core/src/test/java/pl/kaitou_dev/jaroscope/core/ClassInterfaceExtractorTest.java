package pl.kaitou_dev.jaroscope.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies declared class-interface and annotation extraction from the fixture JAR. */
class ClassInterfaceExtractorTest {
  private static final String GREETER_CLASS = "example.Greeter";
  private static final String MARKER_ANNOTATION = "example.Marker";

  /** Locates the committed multi-release fixture used by extraction tests. */
  private static Path fixture() throws URISyntaxException {
    return Path.of(
        ClassInterfaceExtractorTest.class.getResource("/fixtures/multi-release.jar").toURI());
  }

  /** Confirms that the selected Java release determines the extracted class metadata. */
  @Test
  void extractsSelectedVersionAndDeclaredAnnotations() throws Exception {
    ClassInterface classInterface =
        new ClassInterfaceExtractor().extract(fixture(), GREETER_CLASS, 21);

    assertEquals(GREETER_CLASS, classInterface.binaryName());
    assertEquals(ClassKind.CLASS, classInterface.kind());
    assertEquals(1, classInterface.annotations().size());
    assertEquals(MARKER_ANNOTATION, classInterface.annotations().get(0).typeName());
    ClassAnnotationValue classValue =
        (ClassAnnotationValue) classInterface.annotations().get(0).values().get("type");
    assertEquals("java.lang.Integer", classValue.typeName());
    assertEquals(1, classInterface.fields().size());
    assertEquals(1, classInterface.constructors().size());
    assertEquals(1, classInterface.methods().size());
    assertTrue(
        classInterface.methods().get(0).annotations().stream()
            .anyMatch(annotation -> annotation.typeName().equals(MARKER_ANNOTATION)));
  }

  /** Confirms that private and package-private members are excluded from the default interface. */
  @Test
  void extractsOnlyPublicAndProtectedMembers() throws Exception {
    ClassInterface classInterface =
        new ClassInterfaceExtractor().extract(fixture(), GREETER_CLASS, 21);

    assertTrue(classInterface.fields().stream().noneMatch(field -> field.name().equals("secret")));
  }
}
