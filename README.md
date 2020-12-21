<img src="https://www.artipie.com/logo.svg" width="64px" height="64px"/>

[![EO principles respected here](https://www.elegantobjects.org/badge.svg)](https://www.elegantobjects.org)
[![DevOps By Rultor.com](http://www.rultor.com/b/artipie/debian-adapter)](http://www.rultor.com/p/artipie/debian-adapter)
[![We recommend IntelliJ IDEA](https://www.elegantobjects.org/intellij-idea.svg)](https://www.jetbrains.com/idea/)

[![Javadoc](http://www.javadoc.io/badge/com.artipie/debian-adapter.svg)](http://www.javadoc.io/doc/com.artipie/debian-adapter)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](https://github.com/com.artipie/debian-adapter/blob/master/LICENSE.txt)
[![codecov](https://codecov.io/gh/artipie/debian-adapter/branch/master/graph/badge.svg)](https://codecov.io/gh/artipie/debian-adapter)
[![Hits-of-Code](https://hitsofcode.com/github/artipie/debian-adapter)](https://hitsofcode.com/view/github/artipie/debian-adapter)
[![Maven Central](https://img.shields.io/maven-central/v/com.artipie/debian-adapter.svg)](https://maven-badges.herokuapp.com/maven-central/com.artipie/debian-adapter)
[![PDD status](http://www.0pdd.com/svg?name=artipie/debian-adapter)](http://www.0pdd.com/p?name=artipie/debian-adapter)

This Java library turns your binary storage
(files, S3 objects, anything) into a Debian repository.
You may add it to your binary storage and it will become
a fully-functionable Debian repository, which
[`apt`](https://en.wikipedia.org/wiki/APT_(software)) 
will perfectly understand.

## How to contribute

Fork repository, make changes, send us a pull request. We will review
your changes and apply them to the `master` branch shortly, provided
they don't violate our quality standards. To avoid frustration, before
sending us your pull request please run full Maven build:

```
$ mvn clean install -Pqulice
```

To avoid build errors use Maven 3.2+.