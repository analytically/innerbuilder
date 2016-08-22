#!/usr/bin/env bash

mkdir -p target/dependency/intellij-idea
curl -L https://download.jetbrains.com/idea/ideaIC-2016.2.2.tar.gz | tar xz --strip-components=1 -C target/dependency/intellij-idea
