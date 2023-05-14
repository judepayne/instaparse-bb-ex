(ns instaparse.docs)


(def parser
  "Takes a string specification of a context-free grammar,
   or a URI for a text file containing such a specification,
   or a map of parser combinators and returns a parser for that grammar.

   Optional keyword arguments:
   :input-format :ebnf
   or
   :input-format :abnf

   :output-format :enlive
   or
   :output-format :hiccup

   :start :keyword (where :keyword is name of starting production rule)

   :string-ci true (treat all string literals as case insensitive)

   :auto-whitespace (:standard or :comma)
   or
   :auto-whitespace custom-whitespace-parser

   Clj only:
   :no-slurp true (disables use of slurp to auto-detect whether
                   input is a URI.  When using this option, input
                   must be a grammar string or grammar map.  Useful
                   for platforms where slurp is slow or not available.)")

;; the defprotocol macro doesn't accept vars as docstrings so the docstrings
;; for parse and parses have to be inline.

(def defparser
  "Takes a string specification of a context-free grammar,
  or a string URI for a text file containing such a specification,
  or a map/vector of parser combinators, and sets a variable to a parser for that grammar.

  String specifications are processed at macro-time, not runtime, so this is an
  appealing alternative to (def _ (parser \"...\")) for ClojureScript users.")

(def set-default-output-format!
  "Changes the default output format.  Input should be :hiccup or :enlive")

(def failure?
  "Tests whether a parse result is a failure.")

(def get-failure
  "Extracts failure object from failed parse result.")

(def transform
  "Takes a transform map and a parse tree (or seq of parse-trees).
   A transform map is a mapping from tags to
   functions that take a node's contents and return
   a replacement for the node, i.e.,
   {:node-tag (fn [child1 child2 ...] node-replacement),
    :another-node-tag (fn [child1 child2 ...] node-replacement)}")

(def span
  "Takes a subtree of the parse tree and returns a [start-index end-index] pair
   indicating the span of text parsed by this subtree.
   start-index is inclusive and end-index is exclusive, as is customary
   with substrings.
   Returns nil if no span metadata is attached.")

(def add-line-and-column-info-to-metadata
  "Given a string `text` and a `parse-tree` for text, return parse tree
   with its metadata annotated with line and column info. The info can
   then be found in the metadata map under the keywords:

  :instaparse.gll/start-line, :instaparse.gll/start-column,
  :instaparse.gll/end-line, :instaparse.gll/end-column

  The start is inclusive, the end is exclusive. Lines and columns are 1-based.")

(def visualize
  "Creates a graphviz visualization of the parse tree.
   Optional keyword arguments:
   :output-file output-file (will save the tree image to output-file)
   :options options (options passed along to rhizome)

   Important: This will only work if graphviz is installed on your system.")
