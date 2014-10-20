# What is lein-tar

    How far could you take a jar if the jar was in a tar? Possibly as
    far as Myanmar. Or maybe Zanzibar, if you had a car.

Package your project up as a tarball!

Formerly known as lein-release.

## Installation

### With Leiningen 2

Add [lein-tar "3.3.0"] to your project's `:plugins`.

### With Leiningen 1

Add [lein-tar "1.1.2"] to your project's `:dev-dependencies`.

## Usage

    $ lein tar

Creates myproject-1.0.0.tar including your project's jar, its
dependencies, and everything in `pkg/`. A build.clj file may also be
added, containing git and/or jenkins details about the build that
generated your tar.

## Advanced Usage

The out of the box behavior is customizable via options in project.clj
and also via the command line.

### Options in project.clj

lein-tar checks project.clj for a :tar entry, and looks there for a
map like the following. Of course, the entire map and all its keys are
optional, none of this is required.

    :tar {:uberjar true
          :format :tar-gz
          :output-dir "foobar"
          :leading-path "bazquux"}

  - `:uberjar` allows you to specify whether you'd prefer to have your
    project's uberjar in the tar, rather than the project jar and
    dependency jars, the default is false
  - `:format` allows you to build a .tar.gz (`:tar-gz`) or .tgz
    (`:tgz`) file, if you wish, as opposed to just a .tar (the default)
  - `:output-dir` determines where the tar will be generated, by
    default it's the project's target directory (that is, the
    `:target-path` of the project)
  - `:leading-path` specifies the first path component of the files in
    the tar, by default it's "{project-name}-{project-version}", but
    you can override it here

### Command line options

lein-tar also accepts option command line arguments.

  - `--name`/`-n` by default, the generated file name will be
    something like myproject-1.0.0.tar (or whatever extension
    `:format` uses). To change that, the `-n` or `--name` argument is
    supported. The following will create a tar file called
    `custom-name.tar`:

        $ lein tar --name custom-name

## Known Issues

Due to absurd limitations in the Java file API, Unix permissions
inside the tarball are an approximation[1]. Executable files will be
given permissions of 0755, while other files will be 0644.

[1] - to phrase it charitably.

## License

Licensed under the EPL; the same license as Clojure.
