(ns pod.babashka.instaparse
  (:refer-clojure :exclude [read-string read])
  (:require [bencode.core :as bencode]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [cognitect.transit :as transit]
            [instaparse.core :as insta]
            [instaparse.gll :as gll])
  (:import [java.io PushbackInputStream])
  (:gen-class))

(set! *warn-on-reflection* true)

(def stdin (PushbackInputStream. System/in))
(def stdout System/out)

(def debug? true)

(defn debug [& strs]
  (when debug?
    (binding [*out* (io/writer System/err)]
      (apply prn strs))))

(defn write
  ([v] (write stdout v))
  ([stream v]
   ;; (debug :writing v)
   (bencode/write-bencode stream v)
   (flush)))

(defn read-string [^"[B" v]
  (String. v))

(defn read [stream]
  (bencode/read-bencode stream))

(def parsers
  (atom {}))

(defn parser [grammar & opts]
  (let [parser (apply insta/parser grammar opts)
        id (gensym)]
    (swap! parsers assoc id parser)
    {::id id}))

(defn mark-failure [result]
  (cond
    ;; `parses` failures are set in metadata
    (and (insta/failure? result) (= result '())) (with-meta result (assoc (meta result) ::failure true))
    (insta/failure? result) (assoc result ::failure true)
    :else result))

(defn parse [ref & opts]
  (let [id (::id ref)
        p (get @parsers id)]
    (-> (apply insta/parse p opts)
        mark-failure)))

(defn parses [ref & opts]
  (let [id (::id ref)
        p (get @parsers id)]
    (-> (apply insta/parses p opts)
        mark-failure)))

(def lookup*
  {'pod.babashka.instaparse
   {'set-default-output-format! insta/set-default-output-format!
    'parser parser
    'parse parse
    'parses parses
    'add-line-and-column-info-to-metadata insta/add-line-and-column-info-to-metadata
    'span insta/span
    #_#_'visualize insta/visualize ;; disabled as javax.awt not supported for OS X in graal.
                                   ;; See: https://github.com/oracle/graal/issues/4124
                                   ;; ps. I develop on a mac!
    }})

(defn lookup [var]
  (let [var-ns (symbol (namespace var))
        var-name (symbol (name var))]
    (get-in lookup* [var-ns var-name])))

(def describe-map
  (walk/postwalk
   (fn [v]
     (if (ident? v) (name v)
         v))
   {:format :transit+json
    :namespaces [{:name "pod.babashka.instaparse"
                  :vars [{"name" "set-default-output-format!"}
                         {"name" "parser"}
                         {"name" "parse"}
                         {"name" "parses"}
                         {"name" "span" "arg-meta" "true"}
                         {"name" "add-line-and-column-info-to-metadata" "arg-meta" "true"}
                         #_{"name" "visualize"} ;; disabled. see above
                         ]}]}))

(def out (java.io.StringWriter.))

(defn read-transit [^String v]
  (transit/read
   (transit/reader
    (java.io.ByteArrayInputStream. (.getBytes v "utf-8"))
    :json)))

;;clojure.walk/prewalk & friends strip metadata.
;;instaparse parse result contains important metadata, so we need a metadata preserving version of prewalk.
(defn walk-with-meta
  "Like clojure.walk/walk but preserves metadata."
  [inner outer form]
  (cond
    (list? form) (outer (with-meta (apply list (map inner form)) (meta form)))
    (instance? clojure.lang.IMapEntry form)
    (outer (clojure.lang.MapEntry/create (inner (key form)) (inner (val form))))
    (seq? form) (outer (with-meta (doall (map inner form)) (meta form)))
    (instance? clojure.lang.IRecord form)
    (outer (with-meta (reduce (fn [r x] (conj r (inner x))) form form) (meta form)))
    (coll? form) (outer (with-meta (into (empty form) (map inner form)) (meta form)))
    :else (outer form)))

(defn prewalk-with-meta
  [f form]
  (walk-with-meta (partial prewalk-with-meta f) identity (f form)))

(defn serialize- [x]
  (cond
    (instance? instaparse.auto_flatten_seq.AutoFlattenSeq x) (with-meta (seq x) (meta x))
    (meta x) (with-meta x (meta x))
    :else x))

(defn serialize [x]
  (prewalk-with-meta serialize- x))

(defn write-transit [v]
  (let [baos (java.io.ByteArrayOutputStream.)]
    (transit/write (transit/writer baos :json {:transform transit/write-meta}) v)
    (.flush baos)
    (.toString baos "utf-8")))

(defn -main [& _args]
  (loop []
    (let [message (try (read stdin)
                       (catch java.io.EOFException _
                         ::EOF))]
      (when-not (identical? ::EOF message)
        (let [op (get message "op")
              op (read-string op)
              op (keyword op)
              id (some-> (get message "id")
                         read-string)
              id (or id "unknown")]
          (case op
            :describe (do (write stdout describe-map)
                          (recur))
            :invoke (do (try
                          (let [var (-> (get message "var")
                                        read-string
                                        symbol)
                                args (get message "args")
                                args (read-string args)
                                args (read-transit args)]
                            (if-let [f (lookup var)]
                              (let [v (apply f args)
                                    ;; _ (debug :value v :type (type v))
                                    value (write-transit (serialize v))
                                    reply {"value" value
                                           "id" id
                                           "status" ["done"]}]
                                (write stdout reply))
                              (throw (ex-info (str "Var not found: " var) {}))))
                          (catch Throwable e
                            (debug e)
                            (let [reply {"ex-message" (ex-message e)
                                         "ex-data" (write-transit
                                                    (assoc (ex-data e)
                                                           :type (str (class e))))
                                         "id" id
                                         "status" ["done" "error"]}]
                              (write stdout reply))))
                        (recur))
            :shutdown (System/exit 0)
            (do
              (let [reply {"ex-message" "Unknown op"
                           "ex-data" (pr-str {:op op})
                           "id" id
                           "status" ["done" "error"]}]
                (write stdout reply))
              (recur))))))))
