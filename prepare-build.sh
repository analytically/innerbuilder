#!/usr/bin/env bash

mkdir -p target/dependency/intellij-idea
curl -L https://download.jetbrains.com/idea/ideaIC-2021.2.3.tar.gz | tar xz --strip-components=1 -C target/dependency/intellij-idea
