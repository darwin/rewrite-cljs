(ns rewrite-clj.parser.token
  (:require [rewrite-clj.node :as node]
            [rewrite-clj.reader :as r]))

(defn- read-to-boundary
  [reader & [allowed]]
  (let [allowed? (set allowed)]
    (r/read-until
      reader
      #(and (not (allowed? %))
            (r/whitespace-or-boundary? %)))))


(defn- read-to-char-boundary
  [reader]
  (let [c (r/next reader)]
    (.concat c
         (if (not= c \\)
           (read-to-boundary reader)
           ""))))

(def symbol-suffix-chars
   [\' \:])

(defn- symbol-node
  "Symbols allow for certain boundary characters that have
   to be handled explicitly."
  [reader value value-string]
  (let [suffix (read-to-boundary
                 reader
                 symbol-suffix-chars)]
    (if (empty? suffix)
      (node/token-node value value-string)
      (let [s (.concat value-string suffix)]
        (node/token-node
          (r/string->edn s)
          s)))))

(defn parse-token
  "Parse a single token."
  [reader]
  (let [first-char (r/next reader)
        s (->> (if (= first-char \\)
                 (read-to-char-boundary reader)
                 (read-to-boundary reader))
               (.concat first-char))
        v (r/string->edn s)]
    (if (symbol? v)
      (symbol-node reader v s)
      (node/token-node v s))))
