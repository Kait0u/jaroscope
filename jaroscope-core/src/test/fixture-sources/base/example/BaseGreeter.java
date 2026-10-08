package example;

@Marker(name = "parent")
@InheritedTag("parent-tag")
public class BaseGreeter {
  protected String inheritedValue;

  public String inheritedGreeting() {
    return "parent";
  }
}
