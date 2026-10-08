package example;

@Marker(name = "base", type = String.class)
public class Greeter extends BaseGreeter implements Contract {
  private String secret;

  @Marker(name = "field")
  public String value;

  @Marker(name = "constructor")
  public Greeter() {}

  @Marker(name = "method")
  public String greeting() {
    return "base";
  }
}
