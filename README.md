# What is lein-tar

    How far could you take a jar if the jar was in a tar? Possibly as
    far as Myanmar. Or maybe Zanzibar, if you had a car.

Package your project up as a tarball!

Formerly known as lein-release.


## Installation

### With Leiningen 2

Add [lein-tar "2.0.0"] to your project's `:plugins`.

### With Leiningen 1

Add [lein-tar "1.1.2"] to your project's `:dev-dependencies`.


## Usage

    $ lein tar

Creates myproject-1.0.0.tar including everything in `pkg/` along with
the default dependencies plus the jar of your project. If you are
building from Hudson, your tarball will contain a build.clj file that
shows which build produced it.

## Uberjar

Add `:tar {:uberjar true}` to your project.clj.

## Output format

Use option `:format` to specify the desired output format: either a `tar` (default) or a `tgz` (tar + gzip). Example: `:tar {:format :tgz}`.

## Known Issues

Due to absurd limitations in the Java file API, Unix permissions
inside the tarball are an approximation[1]. Executable files will be
given permissions of 0755, while other files will be 0644.

[1] - to phrase it charitably.

## License

Licensed under the EPL; the same license as Clojure.
