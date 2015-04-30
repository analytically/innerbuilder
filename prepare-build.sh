#!/usr/bin/env bash

mkdir -p target/dependency/intellij-idea
curl -L http://download.jetbrains.com/idea/ideaIC-14.1.2.tar.gz | tar xz --strip-components=1 -C target/dependency/intellij-idea