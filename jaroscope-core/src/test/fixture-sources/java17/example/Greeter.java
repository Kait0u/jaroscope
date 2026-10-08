package example;

@Marker(name = "java17", type = Integer.class)
public class Greeter extends BaseGreeter implements Contract {
  private String secret;

  @Marker(name = "field17")
  public String value;

  @Marker(name = "constructor17")
  public Greeter() {}

  @Marker(name = "method17")
  public String greeting() {
    return "java17";
  }
}
