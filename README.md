innerbuilder
============

[IntelliJ IDEA](http://www.jetbrains.com/idea/) plugin that adds a 'Builder' action to the Generate menu (Alt+Insert)
which generates an inner builder class as described in Effective Java.

Follow [@analytically](http://twitter.com/analytically) for updates.

![screenshot](screenshot.png)

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

    public Builder(YourTypicalBean copy) {
      this.foo = copy.foo;
      this.bar = copy.bar;
      this.foobar = copy.foobar;
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

### Installation

Copy `innerbuilder.jar` to your `~/.IntelliJIdea12/config/plugins` directory.

### Usage

Use `Shift+Alt+B` or `Alt+Insert` and select `Builder`. Choose the fields to be included and press `OK`.

### License

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Copyright 2013 [Mathias Bogaert](mailto:mathias.bogaert@gmail.com).