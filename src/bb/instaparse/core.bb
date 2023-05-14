(ns instaparse.core
  (:require [babashka.pods :as pods]
            [instaparse.transform :as tran]
            [instaparse.docs :as docs]))

;; /tmp/bb --nrepl-server until bb 1.3.180 released
;; C-c C-x c j  (cider-connect-clj)

(pods/load-pod "./pod-babashka-instaparse")

(require '[pod.babashka.instaparse :as insta])


;; Public functions

(defprotocol Parser
  (parse [parser text & {:as options}]
    "Use parser to parse the text.  Returns first parse tree found
  that completely parses the text.  If no parse tree is possible, returns
  a Failure object.

  Optional keyword arguments:
  :start :keyword  (where :keyword is name of starting production rule)
  :partial true    (parses that don't consume the whole string are okay)
  :total true      (if parse fails, embed failure node in tree)
  :unhide <:tags or :content or :all> (for this parse, disable hiding)
  :optimize :memory   (when possible, employ strategy to use less memory)

  Clj only:
  :trace true      (print diagnostic trace while parsing)")
  
  (parses [parser text & {:as options}]
    "Use parser to parse the text.  Returns lazy seq of all parse trees
  that completely parse the text.  If no parse tree is possible, returns
  () with a Failure object attached as metadata.

  Optional keyword arguments:
  :start :keyword  (where :keyword is name of starting production rule)
  :partial true    (parses that don't consume the whole string are okay)
  :total true      (if parse fails, embed failure node in tree)
  :unhide <:tags or :content or :all> (for this parse, disable hiding)"))

(defn ^{:doc docs/parser}
  parser [grammar-specification & {:as options}]
  (let [p (insta/parser grammar-specification options)]
    (reify
      clojure.lang.IFn
      (invoke [_ text] (insta/parse p text))
      (invoke [_ text & {:as opts}] (insta/parse p text opts))
      (applyTo [_ args] (apply insta/parse p args))
      Parser
      (parse [_ text & {:as opts}] (insta/parse p text opts))
      (parses [_ text & {:as opts}] (insta/parses p text opts)))))

(defmacro ^{:doc docs/defparser} defparser
  [name grammar & {:as opts}]
  (if (string? grammar)
    (let [p (apply parser grammar opts)]
      `(def ~name ~p))
    `(def ~name (parser ~grammar ~@opts))))

(defn ^{:doc docs/set-default-output-format!}
  set-default-output-format! [type] (insta/set-default-output-format! type))

(defn failure? [x]
  (boolean (or (:pod.babashka.instaparse/failure x)
               (:pod.babashka.instaparse/failure (meta x)))))

(defn get-failure [x]
  (cond
    (:pod.babashka.instaparse/failure x)
    (dissoc x :pod.babashka.instaparse/failure)

    (:pod.babashka.instaparse/failure (meta x))
    (dissoc (meta x) :pod.babashka.instaparse/failure)

    :else nil))

(defn ^{:doc docs/transform} transform
  [transform-map parse-tree]
  (cond
    (string? parse-tree)  parse-tree
    (and (map? parse-tree) (:tag parse-tree))  (tran/enlive-transform transform-map parse-tree)
    (and (vector? parse-tree) (keyword? (first parse-tree)))  (tran/hiccup-transform transform-map parse-tree)
    (sequential? parse-tree)  (tran/map-preserving-meta (partial transform transform-map) parse-tree)
    (failure? parse-tree)  parse-tree
    :else
    (tran/throw-illegal-argument-exception
     "Invalid parse-tree, not recognized as either enlive or hiccup format.")))

(defn ^{:doc docs/span} span [result] (insta/span result))

(defn ^{:doc docs/add-line-and-column-info-to-metadata}
  add-line-and-column-info-to-metadata [text parse-tree]
  (insta/add-line-and-column-info-to-metadata text parse-tree))

;; disabled. See https://github.com/oracle/graal/issues/4124
#_(defn ^{:doc docs/visualize} visualize [tree & {output-file :output-file options :options :as opts}]
    (apply insta/visualize tree opts))
