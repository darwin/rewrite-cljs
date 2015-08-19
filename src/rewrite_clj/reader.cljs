(ns rewrite-clj.reader
  (:refer-clojure :exclude [peek next])
  (:require [cljs.extended.reader :as r]
            [goog.string :as gstring]
            [rewrite-clj.node.protocols :as nd]))




;; TODO: try to get goog.string.format up and running !
(defn throw-reader
  "Throw reader exception, including line/column."
  [^not-native reader fmt & data]
  (let [c (r/get-column-number reader)
        l (r/get-line-number reader)]
    (throw
      (js/Error.
        (str data fmt
             " [at line " l ", column " c "]")))))


(def js-boundaries
  #js [\" \: \; \' \@ \^ \` \~
      \( \) \[ \] \{ \} \\ nil])


(defn boundary?
  [c]
  "Check whether a given char is a token boundary."
  (< -1 (.indexOf js-boundaries c)))


(defn ^boolean whitespace?
  [c]
  (r/whitespace? c))


(defn ^boolean linebreak?
  [c]
  (r/linebreak? c))

(defn ^boolean space?
  [c]
  (r/space? c))

(defn ^boolean whitespace-or-boundary?
  [c]
  (or (whitespace? c) (boundary? c)))

(def buf (gstring/StringBuffer. ""))

(defn read-while
  "Read while the chars fulfill the given condition. Ignores
  the unmatching char."
  ([^not-native reader p?]
   (read-while reader p? (not (p? nil))))

  ([^not-native reader p? eof?]
    (.clear buf)
    (loop []
      (if-let [c (r/read-char reader)]
        (if (p? c)
          (do
            (.append buf c)
            (recur))
          (do
            (r/unread reader c)
            (.toString buf)))
        (if eof?
          (.toString buf)
          (throw-reader reader "Unexpected EOF."))))))

(defn read-until
  "Read until a char fulfills the given condition. Ignores the
   matching char."
  [^not-native reader p?]
  (read-while
    reader
    (complement p?)
    (p? nil)))

(defn read-include-linebreak
  "Read until linebreak and include it."
  [^not-native reader]
  (str
    (read-until
      reader
      #(or (nil? %) (linebreak? %)))
    (r/read-char reader)))

(defn read-until-linebreak
  "Read until linebreak and DO NOT include it."
  [^not-native reader]
  (read-until
    reader
    #(or (nil? %) (linebreak? %))))

(defn string->edn
  "Convert string to EDN value."
  [s]
  (r/read-string s))

(defn ignore
  "Ignore the next character."
  [^not-native reader]
  (r/read-char reader)
  nil)


(defn next
  "Read next char."
  [^not-native reader]
  (r/read-char reader))

(defn peek
  "Peek next char."
  [^not-native reader]
  (r/peek-char reader))




(defn read-with-meta
  "Use the given function to read value, then attach row/col metadata."
  [^not-native reader read-fn]
  (let [row (r/get-line-number reader)
        col (r/get-column-number reader)
        ^not-native entry (read-fn reader)]
    (when entry
      (let [end-row (r/get-line-number reader)
            end-col (r/get-column-number reader)
            end-col (if (= 0 end-col)
                      (+ col (.-length (nd/string entry)))
                      end-col)] ; TODO: Figure out why numbers are sometimes whacky
        (if (= 0 col) ; why oh why
          entry
          (-with-meta
            entry
            {:row row
             :col col
             :end-row end-row
             :end-col end-col}))))))

(defn read-repeatedly
  "Call the given function on the given reader until it returns
   a non-truthy value."
  [^not-native reader read-fn]
  (->> (repeatedly #(read-fn reader))
       (take-while identity)
       (doall)))


(defn read-n
  "Call the given function on the given reader until `n` values matching `p?` have been
   collected."
  [^not-native reader node-tag read-fn p? n]
  {:pre [(pos? n)]}
  (loop [c 0
         vs []]
    (if (< c n)
      (if-let [v (read-fn reader)]
        (recur
          (if (p? v) (inc c) c)
          (conj vs v))
        (throw-reader
          reader
          "%s node expects %d value%s."
          node-tag
          n
          (if (= n 1) "" "s")))
      vs)))

(defn string-reader
  "Create reader for strings."
  [s]
  (r/indexing-push-back-reader s))




;; (let [form-rdr (r/indexing-push-back-reader "(+ 1 1)")]
;;   (read-include-linebreak form-rdr))

