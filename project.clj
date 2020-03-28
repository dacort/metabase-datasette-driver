(defproject metabase/athena-driver "0.3.1-athena-jdbc-2.0.7"
  :min-lein-version "2.5.0"

  :aliases
  {"test"       ["with-profile" "+unit_tests" "test"]}

  :profiles
  {:provided
   {:dependencies
    [[org.clojure/clojure "1.10.0"]
     [metabase-core "1.0.0-SNAPSHOT"]]}

   :uberjar
   {:auto-clean    true
    :aot           :all
    :omit-source   true
    :javac-options ["-target" "1.8", "-source" "1.8"]
    :target-path   "target/%s"
    :uberjar-name  "datasette.metabase-driver.jar"}

   :unit_tests
   {:test-paths     ^:replace ["test_unit"]}})