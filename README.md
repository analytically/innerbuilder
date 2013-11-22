innerbuilder
============

Intellij plugin that generates an inner builder class as described in Effective Java.

Follow [@analytically](http://twitter.com/analytically).

```java
public class YourTypicalBean {
  private final String foo;
  private String bar;
  private int foobar;

  private YourTypicalBean(Builder builder) {
    foo = builder.foo;
    bar = builder.bar;
    foobar = builder.foobar;
  }


  public static final class Builder {
    private final String foo;
    private String bar;
    private int foobar;

    public Builder(String foo) {
      this.foo = foo;
    }

    public Builder bar(String bar) {
      this.bar = bar;
      return this;
    }

    public Builder foobar(int foobar) {
      this.foobar = foobar;
      return this;
    }

    public YourTypicalBean build() {
      return new YourTypicalBean(this);
    }
  }
}
```