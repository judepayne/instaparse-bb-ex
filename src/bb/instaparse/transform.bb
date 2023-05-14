(ns instaparse.transform)

(defn throw-illegal-argument-exception
  [& message]
  (let [^String text (apply str message)]
    (-> text
        IllegalArgumentException.
        throw)))

(defn map-preserving-meta [f l]
  (with-meta (map f l) (meta l)))

(defn merge-meta-
  "A variation on with-meta that merges the existing metamap into the new metamap,
rather than overwriting the metamap entirely."
  [obj metamap]
  (with-meta obj (merge metamap (meta obj))))

(defn merge-meta
  "This variation of the merge-meta in gll does nothing if obj is not
something that can have a metamap attached."
  [obj metamap]
  (if (instance? clojure.lang.IObj obj)
    (merge-meta- obj metamap)
    obj))

(defn enlive-transform
  [transform-map parse-tree]
  (let [transform (transform-map (:tag parse-tree))]
    (cond
      transform
      (merge-meta 
       (apply transform (map (partial enlive-transform transform-map)
                             (:content parse-tree)))
       (meta parse-tree))
      (:tag parse-tree)
      (assoc parse-tree :content (map (partial enlive-transform transform-map)
                                      (:content parse-tree)))
      :else
      parse-tree)))

(defn hiccup-transform
  [transform-map parse-tree]
  (if (and (sequential? parse-tree) (seq parse-tree))
    (if-let [transform (transform-map (first parse-tree))]
      (merge-meta
        (apply transform (map (partial hiccup-transform transform-map)
                              (next parse-tree)))
        (meta parse-tree))
      (with-meta 
        (into [(first parse-tree)]
              (map (partial hiccup-transform transform-map) 
                   (next parse-tree)))
        (meta parse-tree)))
    parse-tree))
