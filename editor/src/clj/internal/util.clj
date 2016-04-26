(ns internal.util
  "Helpful utility functions for graph"
  (:require [camel-snake-kebab :refer [->Camel_Snake_Case_String]]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as str]
            [plumbing.fnk.pfnk :as pf]
            [potemkin.namespaces :as namespaces]
            [schema.core :as s]))

(set! *warn-on-reflection* true)

(defn predicate? [x] (instance? schema.core.Predicate x))
(defn schema?    [x] (satisfies? s/Schema x))
(defn protocol?  [x] (and (map? x) (contains? x :on-interface)))

(defn var-get-recursive [var-or-value]
  (if (var? var-or-value)
    (recur (var-get var-or-value))
    var-or-value))

(defn apply-if-fn [f & args]
  (if (fn? f)
    (apply f args)
    f))

(defn removev [pred coll]
  (filterv (complement pred) coll))

(defn conjv [coll x]
  (conj (or coll []) x))

(defn filterm [pred m]
  "like filter but applys the predicate to each key value pair of the map"
  (into {} (filter pred m)))

(defn key-set
  [m]
  (set (keys m)))

(defn map-keys
  [f m]
  (reduce-kv (fn [m k v] (assoc m (f k) v)) (empty m) m))

(defn map-vals
  [f m]
  (reduce-kv (fn [m k v] (assoc m k (f v))) m m))

(defn map-first
  [f pairs]
  (mapv (fn [[a b]] [(f a) b]) pairs))

(defn map-diff
  [m1 m2 sub-fn]
  (reduce-kv
    (fn [result k v]
      (if (contains? result k)
        (let [ov (get result k)]
          (let [nv (sub-fn ov v)]
            (if (empty? nv)
              (dissoc result k)
              (assoc result k nv))))
        result))
    m1
    m2))

(defn parse-number
  "Reads a number from a string. Returns nil if not a number."
  [s]
  (when (re-find #"^-?\d+\.?\d*$" s)
    (read-string (str/replace s #"^(-?)0*(\d+\.?\d*)$" "$1$2"))))

(defn parse-int
  "Reads an integer from a string. Returns nil if not an integer."
  [s]
  (when (re-find #"^-?\d+\.?0*$" s)
    (read-string (str/replace s #"^(-?)0*(\d+)\.?0*$" "$1$2"))))

(defn push-with-size-limit
  "Given bounds, a vector, and a value, returns a new vector with value at the tail.
  The vector will contain no more than max-size elements. If the result would exceed
  this limit, elements are dropped from the front of the vector to reduce to min-size.

  Intended for use along with `clojure.core/peek` and `clojure.core/pop` to treat a
  vector as a stack with limited size."
  [min-size max-size stack value]
  (let [stack (conj stack value)
        size (count stack)]
    (if (< max-size size)
      (vec (drop (- size min-size) stack))
      stack)))

(defn replace-top
  [stack value]
  (conj (pop stack) value))

(defn apply-deltas
  [old new removed-f added-f]
  (let [removed (set/difference old new)
        added   (set/difference new old)]
    [(removed-f removed) (added-f added)]))

(defn keyword->label
  "Turn a keyword into a human readable string"
  [k]
  (-> k
    name
    ->Camel_Snake_Case_String
    (str/replace "_" " ")))

(def safe-inc (fnil inc 0))

(defn collify
  [val-or-coll]
  (if (and (coll? val-or-coll) (not (map? val-or-coll))) val-or-coll [val-or-coll]))

(defn stackify [x]
  (if (coll? x)
    (vec x)
    [x]))

(defn project
  "Like clojure.set/project, but returns a vector of tuples instead of
  a set of maps."
  [xrel ks]
  (with-meta (vec (map #(vals (select-keys % ks)) xrel)) (meta xrel)))

(defn filter-vals [p m] (select-keys m (for [[k v] m :when (p v)] k)))

(defn guard [f g]
  (fn [& args]
    (when (apply f args)
      (apply g args))))

(defn fnk-schema
  [f]
  (when f
    (pf/input-schema f)))

(defn fnk-arguments
  [f]
  (when f
    (key-set (dissoc (fnk-schema f) s/Keyword))))

(defn var?! [v] (when (var? v) v))

(defn vgr [s] (some-> s resolve var?! var-get))

(defn resolve-value-type
  [x]
  (cond
    (symbol? x)                     (resolve-value-type (resolve x))
    (var? x)                        (resolve-value-type (var-get x))
    (vector? x)                     (mapv resolve-value-type x)
    :else                           x))

(defn resolve-schema [x]
  (cond
    (symbol? x)    (resolve-schema (resolve x))
    (var? x)       (resolve-schema (var-get x))
    (predicate? x) x
    (vector? x)    (mapv resolve-schema x)
    (class? x)     x
    (protocol? x)  x
    (map? x)       x
    :else          nil))

(defn assert-form-kind [place kind-label required-kind label form]
  (assert (required-kind form)
          (str place " " label " requires a " kind-label " not a " (class form) " of " form)))

(defn pfnk?
  "True if the function has a schema. (I.e., it is a valid production function"
  [f]
  (and (fn? f) (contains? (meta f) :schema)))

(defn- quoted-var? [x] (and (seq? x) (= 'var (first x))))
(defn- pfnk-form?  [x] (seq? x))

(defn- maybe-expand-macros
  [[call & _ :as body]]
  (if (or (= "fn" (name call)) (= "fnk" (name call)))
    body
    (macroexpand-1 body)))

(defn- parse-fnk-arguments
  [f]
  (let [f (maybe-expand-macros f)]
    (loop [args  #{}
           forms (seq (second f))]
      (if-let [arg (first forms)]
        (if (= :- (first forms))
          (recur args (drop 2 forms))
          (recur (conj args (keyword (first forms))) (next forms)))
        args))))

(defn inputs-needed [x]
  (cond
    (pfnk? x)       (fnk-arguments x)
    (symbol? x)     (inputs-needed (resolve x))
    (var? x)        (inputs-needed (var-get x))
    (quoted-var? x) (inputs-needed (var-get (resolve (second x))))
    (pfnk-form? x)  (parse-fnk-arguments x)
    :else           #{}))

(defn- wrap-protocol
  [tp tp-form]
  (if (protocol? tp)
    (list `s/protocol tp-form)
    tp))

(defn- wrap-enum
  [tp tp-form]
  (if (set? tp)
    (do (println tp-form)
      (list `s/enum tp-form))
    tp))

(defn preprocess-schema
  [tp-form]
  (let [multi?    (vector? tp-form)
        schema    (if multi? (first tp-form) tp-form)
        tp        (resolve-value-type schema)
        processed (wrap-enum (wrap-protocol tp schema) schema)]
    (if multi?
      (vector processed)
      processed)))
