#!/usr/bin/env bb

(require '[instaparse.core :as insta])

;; test parsing
(def as-and-bs
  (insta/parser
   "S = AB*
    AB = A B
    A = 'a'+
    B = 'b'+"))

(assert (= [:S [:AB [:A "a" "a" "a" "a" "a"] [:B "b" "b" "b"]] [:AB [:A "a" "a" "a" "a"] [:B "b" "b"]]] (insta/parse as-and-bs "aaaaabbbaaaabb")))

  ;; test parsing with optional arguments
(assert (= [:A "a" "a" "a"] (insta/parse as-and-bs "aaa" :start :A)))

;; parses
(assert (= (seq [[:S [:AB [:A "a"] [:B "b"]]]]) (insta/parses as-and-bs "ab")))

  ;; Test the IFn interface of the var
(assert (= [:S [:AB [:A "a" "a" "a" "a" "a"] [:B "b" "b" "b"]] [:AB [:A "a" "a" "a" "a"] [:B "b" "b"]]] (as-and-bs "aaaaabbbaaaabb")))

;; Test defparser macro
(insta/defparser p "S = AB*
    AB = A B
    A = 'a'+
    B = 'b'+")

(assert (= [:S [:AB [:A "a"] [:B "b"]]] (insta/parse p "ab")))

  ;; parses
(assert (= (seq [[:S [:AB [:A "a"] [:B "b"]]]]) (insta/parses p "ab")))

  ;; Test the IFn interface of the var
(assert (= [:S [:AB [:A "a"] [:B "b"]]] (p "ab")))

;; Test failures -------------------------------------------
  ;; `parse` failures
(def failure (insta/parse as-and-bs "xaaaaabbbaaaabb"))

(assert (insta/failure? failure) "should be true")

(assert (= (insta/get-failure failure)
           {:index 0,
            :line 1,
            :text "xaaaaabbbaaaabb",
            :reason [{:tag :string, :expecting "a"}],
            :column 1}))

  ;; `parses` failure (which are carried in metadata)
(def failure (insta/parses as-and-bs "xaaaaabbbaaaabb"))

(assert (insta/failure? failure) "should be true")

(assert (= (insta/get-failure failure)
           {:index 0,
            :line 1,
            :text "xaaaaabbbaaaabb",
            :reason [{:tag :string, :expecting "a"}],
            :column 1}))

;; Test :hiccup and :enlive output formats -------------------
(def commit-msg-grammar
  "A PEG grammar to validate and parse conventional commit messages."
  (str
   "<S>            =       (HEADER <EMPTY-LINE> FOOTER GIT-REPORT? <NEWLINE>*)
                            / ( HEADER <EMPTY-LINE> BODY (<EMPTY-LINE> FOOTER)? GIT-REPORT? <NEWLINE>*)

                            / (HEADER <EMPTY-LINE> BODY GIT-REPORT? <NEWLINE>*)
                            / (HEADER GIT-REPORT? <NEWLINE>*);"
   "<HEADER>       =       TYPE (<'('>SCOPE<')'>)? <':'> <SPACE> SUBJECT;"
   "TYPE           =       'feat' | 'fix' | 'refactor' | 'perf' | 'style' | 'test' | 'docs' | 'build' | 'ops' | 'chore';"
   "SCOPE          =       #'[a-zA-Z0-9]+';"
   "SUBJECT        =       TEXT ISSUE-REF? TEXT? !'.';"
   "BODY           =       (!PRE-FOOTER PARAGRAPH) / (!PRE-FOOTER PARAGRAPH (<EMPTY-LINE> PARAGRAPH)*);"
   "PARAGRAPH      =       (ISSUE-REF / TEXT / (NEWLINE !NEWLINE))+;"
   "PRE-FOOTER     =       NEWLINE+ FOOTER;"
   "FOOTER         =       FOOTER-ELEMENT (<NEWLINE> FOOTER-ELEMENT)*;"
   "FOOTER-ELEMENT =       FOOTER-TOKEN <':'> <WHITESPACE> FOOTER-VALUE;"
   "FOOTER-TOKEN   =       ('BREAKING CHANGE' (<'('>SCOPE<')'>)?) / #'[a-zA-Z\\-^\\#]+';"
   "FOOTER-VALUE   =       (ISSUE-REF / TEXT)+;"
   "GIT-REPORT     =       (<EMPTY-LINE> / <NEWLINE>) COMMENT*;"
   "COMMENT        =       <'#'> #'[^\\n]*' <NEWLINE?> ;"
   "ISSUE-REF      =       <'#'> ISSUE-ID;"
   "ISSUE-ID       =       #'([A-Z]+\\-)?[0-9]+';"
   "TEXT           =       #'[^\\n\\#]+';"
   "SPACE          =       ' ';"
   "WHITESPACE     =       #'\\s';"
   "NEWLINE        =       <'\n'>;"
   "EMPTY-LINE     =       <'\n\n'>;"))

(def commit-msg-parser (insta/parser commit-msg-grammar))

(assert (= '([:TYPE "feat"] [:SUBJECT [:TEXT "adding a new awesome feature"]])
           (insta/parse commit-msg-parser "feat: adding a new awesome feature")))

(def commit-msg-parser-enlive (insta/parser commit-msg-grammar :output-format :enlive))

  ;; test nested AutoFlattenSeqs are handled by serialize
(assert (= '({:tag :TYPE, :content ("feat")}
             {:tag :SUBJECT,
              :content ({:tag :TEXT, :content ("adding a new awesome feature")})})
           (insta/parse commit-msg-parser-enlive "feat: adding a new awesome feature")))

(assert (= '(({:tag :TYPE, :content ("feat")}
              {:tag :SUBJECT,
               :content
               ({:tag :TEXT, :content ("adding a new awesome feature")})}))
           (insta/parses commit-msg-parser-enlive "feat: adding a new awesome feature")))

  ;; test that set-default-output-format! works
(assert (= :enlive (insta/set-default-output-format! :enlive)))

(def commit-msg-parser (insta/parser commit-msg-grammar :output-format :enlive))

(assert (= :hiccup (insta/set-default-output-format! :hiccup)))

(assert (= (insta/parse commit-msg-parser-enlive "feat: adding a new awesome feature")
           (insta/parse commit-msg-parser "feat: adding a new awesome feature")))

;; spans -----------------------------------------------------
(assert (= (insta/span (insta/parse as-and-bs "aaaabbbbaaaabbbbab")) [0 18]))

;; line and column information
(def multiline-text "This is line 1\nThis is line 2")

(def words-and-numbers
  (insta/parser
    "sentence = token (<whitespace> token)*
     <token> = word | number
     whitespace = #'\\s+'
     word = #'[a-zA-Z]+'
     number = #'[0-9]+'"))

(def parse-result (insta/parse words-and-numbers multiline-text))

(assert (= (meta (insta/add-line-and-column-info-to-metadata multiline-text parse-result))
           {:instaparse.gll/start-index 0,
            :instaparse.gll/end-index 29,
            :instaparse.gll/start-line 1,
            :instaparse.gll/start-column 1,
            :instaparse.gll/end-line 2,
            :instaparse.gll/end-column 15}))

;; transform -------------------------------------------------
(def words-and-numbers-one-character-at-a-time
  (insta/parser
    "sentence = token (<whitespace> token)*
     <token> = word | number
     whitespace = #'\\s+'
     word = letter+
     number = digit+
     <letter> = #'[a-zA-Z]'
     <digit> = #'[0-9]'"))

(def parse-result (words-and-numbers-one-character-at-a-time "abc 123 def"))

(assert (= [:sentence "abc" 123 "def"]
         (insta/transform
          {:word str,
           :number (comp clojure.edn/read-string str)}
          (words-and-numbers-one-character-at-a-time "abc 123 def"))))

(when-not (= "executable" (System/getProperty "org.graalvm.nativeimage.kind"))
  (shutdown-agents)
  (System/exit 0))
