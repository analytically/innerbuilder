innerbuilder [![Build Status](https://travis-ci.org/analytically/innerbuilder.png)](https://travis-ci.org/analytically/innerbuilder)
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
    setFoobar(builder.foobar);
  }

  private void setFoobar(int foobar) {
    this.foobar = foobar;
  }

  public static final class Builder {
    private final String foo;
    private String bar;
    private int foobar;

    public Builder(String foo) {
      this.foo = foo;
    }

    public Builder(YourTypicalBean copy) {
      foo = copy.foo;
      bar = copy.bar;
      foobar = copy.foobar;
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

In IntelliJ IDEA, go to `File` > `Settings` > `Plugins`. Click the `Browse repositories` button, in the search field, type `innerbuilder`.
It should show up in the plugins list. Right-click it and select `Download and Install`.

#### Manual installation

Copy `innerbuilder.jar` to your `~/.IntelliJIdea12/config/plugins` directory.

### Usage

Use `Shift+Alt+B` or `Alt+Insert` and select `Builder`. Choose the fields to be included and press `OK`.

### Rate

If you enjoy this plugin, please rate it on it's [plugins.jetbrains.com page](http://plugins.jetbrains.com/plugin/7354).

### Building

Run `mvn package`. It will download IntelliJ IDEA Community Edition to unpack jars and use them to compile the plugin.

### License

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Copyright 2013 [Mathias Bogaert](mailto:mathias.bogaert@gmail.com).