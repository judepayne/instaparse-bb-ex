# instaparse-bb-ex

A babashka wrapper for [Instaparse](https://github.com/Engelberg/instaparse).

This is an experiment with a babashka library wrapping an 'internal' pod (the details of which are hidden from the user).

## Installation

Add this library to your `bb.edn` in the `:deps` map:

``` clojure
io.github.babashka/instaparse-bb-ex {:git/sha "<latest-sha>"}
```

## API

Most of the core instaparse api is exposed:

### pod.babashka.instaparse

- `parser`
- `defparser`
- `parse`
- `parses`
- `failure?`
- `get-failure`
- `set-default-output-format!`
- `transform`
- `span`
- `add-line-and-column-info-to-metadata`

## Differences with instaparse

1. The `visualize` function is not currently exposed due to [this issue](https://github.com/oracle/graal/issues/4124) with graal native image on macs.
2. [Tracing](https://github.com/Engelberg/instaparse/blob/master/docs/Tracing.md) currently doesn't work as it involves dynamic class loading which is not allowed in native images. More to come on this!

## Example

``` clojure
(require '[instaparse.core :as insta])

(def as-and-bs
  (insta/parser
   "S = AB*
    AB = A B
    A = 'a'+
    B = 'b'+"))

(prn (as-and-bs "aaaaabbbaaaabb"))

(def failure (insta/parse as-and-bs "xaaaaabbbaaaabb"))

(prn failure)

(prn :failure? (insta/failure? failure))
```

## Build

Run `script/compile`. This requires `GRAALVM_HOME` to be set.

## Test

Run tests by running  `bb test/test_pod.bb` and `bb test/test_client.bb`

## License

Copyright © 2023 

Distributed under the EPL 1.0 license, same as Instaparse and Clojure. See LICENSE.
