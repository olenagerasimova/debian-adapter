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

## Debian repository structure

Debian repository [has](https://www.debian.org/doc/manuals/repository-howto/repository-howto#id442666) 
the following structure:
```
(repository root) 
| 
+-dists
  | 
  |-my_repo
  | |-main
  | | |-binary-*
  | | +-source 
  | |-contrib
  | | |-binary-*
  | | +-source 
  | +-non-free
  |   |-binary-*
  |   +-source
  |
  |-testing 
  | ...
  |
  +-unstable 
    | ...
    |
```
`main` contains free package, `non-free` - non-free ones and `contrib` contains free packages which 
depend on non-free ones. `testing` and `unstable` have the same structure as `main` or `testing`.  

`*` in `binary-*` stands for the architecture, currently Debian [supports](https://wiki.debian.org/SupportedArchitectures) 
more than 20 of them. Each `binary-*` directory contains a `Packages.gz` index file. Index files contain 
paths to the individual packages, so packages can be located anywhere in the repository.

## Packages index file

Packages index files are called [Binary Packages Indices](https://wiki.debian.org/DebianRepository/Format#A.22Packages.22_Indices) 
and contains information about packages in paragraphs, each paragraph has the format defined by 
[Debian Policy](https://www.debian.org/doc/debian-policy/#s-binarycontrolfiles) 
and several other fields such as `Filename`, `Size` and checksums, here is an example:

```text
Package: aglfn
Version: 1.7-3
Architecture: all
Maintainer: Debian Fonts Task Force <pkg-fonts-devel@lists.alioth.debian.org>
Installed-Size: 138
Filename: main/aglfn_1.7-3_all.deb
Size: 29238
MD5sum: cee972bb5e9f9151239e146743a40c9c
SHA1: d404261883ae7bd5a3e35abca99b1256ae070ed5
SHA256: 421a5c6432cb7f9c398aa5c89676884e421ad0fde6bf13d0cadee178bf0daf7c
Section: fonts
Priority: extra
Homepage: http://sourceforge.net/adobe/aglfn/
Description: Adobe Glyph List For New Fonts
 AGL (Adobe Glyph List) maps glyph names to Unicode values ...
```
 
## How to contribute

Fork repository, make changes, send us a pull request. We will review
your changes and apply them to the `master` branch shortly, provided
they don't violate our quality standards. To avoid frustration, before
sending us your pull request please run full Maven build:

```
$ mvn clean install -Pqulice
```

To avoid build errors use Maven 3.2+.