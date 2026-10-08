package io.github.yagipass.verbatime.fixtures;

public final class InstanceMain {

  public void main(String[] args) {
    System.out.println("root() = " + new Fixture().root() + " with " + args.length + " args");
  }
}
