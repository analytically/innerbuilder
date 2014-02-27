innerbuilder [![Build Status](https://travis-ci.org/analytically/innerbuilder.png)](https://travis-ci.org/analytically/innerbuilder)
============

[IntelliJ IDEA](http://www.jetbrains.com/idea/) plugin that adds a 'Builder' action to the Generate menu (Alt+Insert)
which generates an inner builder class as described in Effective Java. Works with IntelliJ IDEA 12.x and 13.x.

Follow [@analytically](http://twitter.com/analytically) for updates. 3/2/2014, 1000 installs! 14/2/2014, 1500 installs!

![screenshot](screenshot.png)

```java
public class YourTypicalBean {
  private final String foo;
  private String bar, baz;
  private int qux;

  private YourTypicalBean(Builder builder) {
    foo = builder.foo;
    bar = builder.bar;
    baz = builder.baz;
    setQux(builder.qux);
  }

  public void setQux(int qux) {
    this.qux = qux;
  }

  public static final class Builder {
    private final String foo;
    private String bar;
    private String baz;
    private int qux;

    public Builder(String foo) {
      this.foo = foo;
    }

    public Builder bar(String bar) {
      this.bar = bar;
      return this;
    }

    public Builder baz(String baz) {
      this.baz = baz;
      return this;
    }

    public Builder qux(int qux) {
      this.qux = qux;
      return this;
    }

    public YourTypicalBean build() {
      return new YourTypicalBean(this);
    }
  }
}
```

### Installation

In IntelliJ IDEA 12.x or later, go to `File` > `Settings` > `Plugins`. Click the `Browse repositories` button, in
the search field, type `innerbuilder`. It should show up in the plugin list. Right-click it and select `Download and Install`.

#### Manual installation

Download the plugin jar `innerbuilder.jar` and select "Install Plugin From Disk" in IntelliJ's plugin preferences.

### Usage

Use `Shift+Alt+B` or `Alt+Insert` and select `Builder...`. Choose the fields to be included and press `OK`. When generating a
builder when a builder already exists, the plugin will try to update it. It will add missing fields and builder methods, but
never remove any fields or methods.

### Rate

If you enjoy this plugin, please rate it on it's [plugins.jetbrains.com page](http://plugins.jetbrains.com/plugin/7354).

### Building

Run `mvn package`. It will download IntelliJ IDEA Community Edition to unpack jars and use them to compile the plugin.

### License

Licensed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0).

Copyright 2013-2014 [Mathias Bogaert](mailto:mathias.bogaert@gmail.com).
