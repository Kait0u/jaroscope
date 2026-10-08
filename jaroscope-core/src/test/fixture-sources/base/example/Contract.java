package example;

public interface Contract {
  String CONTRACT_NAME = "contract";

  default String contractGreeting() {
    return CONTRACT_NAME;
  }

  static String staticHelper() {
    return CONTRACT_NAME;
  }
}
