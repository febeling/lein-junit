(ns lein-junit.test.core
  (:refer-clojure :exclude [read])
  (:import [org.apache.tools.ant.types FileSet]
           [org.apache.tools.ant.taskdefs.optional.junit
            BatchTest BriefJUnitResultFormatter FormatterElement JUnitTask JUnitResultFormatter
            PlainJUnitResultFormatter SummaryJUnitResultFormatter XMLJUnitResultFormatter]
           java.io.File)
  (:require [clojure.test :refer :all]
            [lancet.core :as lancet]
            [lein-junit.core :refer :all]
            [lein-junit.task-args :refer :all]
            [leiningen.core.main :refer [*exit-process?*]]
            [leiningen.core.project :refer [read]]))

(def project (read "sample/project.clj"))
(def fileset-spec ["classes" :includes "**/*Test.class"])

(deftest test-configure-batch-test
  (is (instance? BatchTest (configure-batch-test {} project (lancet/junit {}) (testcase-fileset project)))))

(deftest test-configure-classpath
  (is (configure-classpath project (lancet/junit {}))))

(deftest test-configure-jvm-args
  (configure-jvm-args project (lancet/junit {})))

(deftest test-junit-options
  (is (= (junit-options project) {:fork "on" :haltonerror "off" :haltonfailure "off"})))

(deftest test-junit-formatter-class
  (are [type expected-class]
    (is (= (junit-formatter-class type) expected-class))
    :brief BriefJUnitResultFormatter
    :plain PlainJUnitResultFormatter
    :summary SummaryJUnitResultFormatter
    :xml XMLJUnitResultFormatter
    "brief" BriefJUnitResultFormatter
    "plain" PlainJUnitResultFormatter
    "summary" SummaryJUnitResultFormatter
    "xml" XMLJUnitResultFormatter))

(deftest test-junit-formatter-element
  (are [type expected-class]
    (let [formatter-element (junit-formatter-element type false)]
      (is (isa? (class formatter-element) FormatterElement))
      (is (= (.getClassname formatter-element) (.getName (junit-formatter-class type)))))
    :brief :plain :summary :xml
    "brief" "plain" "summary" "xml"))

(deftest test-extract-task
  (let [task (extract-task project {})]
    (is (isa? (class task) JUnitTask)))
  (let [task (extract-task project {} "com.example")]
    (is (isa? (class task) JUnitTask))))

(deftest test-testcase-fileset
  (are [fileset expected]
    (is (= (sort expected)
           (sort (seq (.getIncludedFiles (.getDirectoryScanner fileset))))))
    (testcase-fileset project)
    ["com/example/SubscriptionTest.class"
     "com/other/SubscriptionTest.class"]
    (testcase-fileset project "com.example")
    ["com/example/SubscriptionTest.class"]
    (testcase-fileset project "com.example.Subscription")
    ["com/example/SubscriptionTest.class"]
    (testcase-fileset project "com.example" "com.other")
    ["com/example/SubscriptionTest.class"
     "com/other/SubscriptionTest.class"]
    (testcase-fileset project "com.another") nil))

(deftest test-junit
  ;; TODO: Clear error and failure properties.
  (try
    (binding [*exit-process?* false]
      (junit project "com.other"))
    (catch clojure.lang.ExceptionInfo e
      (is (= 0 (:exit-code (ex-data e))))))
  (try
    (binding [*exit-process?* false]
      (junit project "com.example"))
    (catch clojure.lang.ExceptionInfo e
      (is (= 1 (:exit-code (ex-data e))))))
  (try
    (binding [*exit-process?* false]
      (junit project))
    (catch clojure.lang.ExceptionInfo e
      (is (= 1 (:exit-code (ex-data e))))))
   (let [results-dir (java.io.File. (project :junit-results-dir))]
         (is true (.isDirectory results-dir))
         (is true (-> (.listFiles results-dir (reify java.io.FilenameFilter (accept [_ _ name] (.endsWith name (project :junit-formatter)))))
                      count
                      (> 0)))))

(deftest test-file-pattern
  ; file pattern that does not work
  (is (empty? (find-testcases (assoc project :junit-test-file-pattern #".*Tesd\.java"))))

  ; file pattern that does work
  (is (= 2 (count (find-testcases (assoc project :junit-test-file-pattern #".*\Subscription.*\.java")))))

  ; check the default case
  (is (= 2 (count (find-testcases project)))))

(deftest test-arg-formatter
  (is (= (keywordify-args ["a" ":b" "c"]) ["a" :b "c"])))

(deftest test-parse-task-arg-selectors
  (let [basic-args ["a" "b"]]
    (is (= (:selectors (parse-task-args {} basic-args)) basic-args)))
  (let [empty-args []]
    (is (= (:selectors (parse-task-args {} empty-args)) [])))
  (let [options-args ["a" ":junit-formatter" ":brief" "b"]]
    (is (= (:selectors (parse-task-args {} options-args)) ["a" "b"]))))

(deftest test-parse-task-arg-options
  (let [basic-args [":junit-formatter" "brief"]]
    (is (= (:options (parse-task-args {} basic-args)) {:junit-formatter "brief"})))
  (let [basic-args [":junit-formatter" "plain"]]
    (is (= (:options (parse-task-args {} basic-args)) {:junit-formatter "plain"})))
  (let [kw-args [":junit-formatter" ":brief"]]
    (is (= (:options (parse-task-args {} kw-args)) {:junit-formatter :brief})))
  (let [project {:junit-results-dir "."}
        merge-args [":junit-formatter" ":summary"]]
    (is (= (:options (parse-task-args project merge-args)) {:junit-results-dir "." :junit-formatter :summary})))
  (let [project {:junit-formatter :plain}
        override-args [":junit-formatter" "xml"]]
    (is (= (:options (parse-task-args project override-args)) {:junit-formatter "xml"}))))

(deftest test-parse-task-args
  (let [args ["test-pattern" ":junit-formatter" "brief" "test-pattern2"]]
    (is (= (parse-task-args {} args) {:options {:junit-formatter "brief"}
                                      :selectors ["test-pattern" "test-pattern2"]}))))
