(ns build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn" :aliases [:native]}))
(def uber-file (format "pod-babashka-instaparse.jar"))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["src/clj" "resources"]
               :target-dir class-dir})
  (b/compile-clj {:basis basis
                  :src-dirs ["src/clj"]
                  :class-dir class-dir
                  :ns-compile '[pod.babashka.instaparse]})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'pod.babashka.instaparse}))
