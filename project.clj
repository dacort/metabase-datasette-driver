(defproject metabase/datasette-driver "0.0.1-SNAPSHOT"
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