(ns metabase.driver.datasette
    (:require [clj-http.client :as client]
              [clojure.tools.logging :as log]
              [metabase.query-processor.store :as qp.store]
              [metabase.driver :as driver]))
  
  (driver/register! :datasette)
  
  (defmethod driver/display-name :datasette [_]
    "Datasette")
  
; TODO: Ensure we can connect to the datasette endpoint and that's valid
(defmethod driver/can-connect? :datasette [_ _]
    true)

;;; ------------------------------------------- describe-database -----------------------------------

(defn make-request
  "Make the HTTP request to the Datasette endpoint and return the response.
      If the response is 200 with a 'Continue wait' error message, try again."
  [resource query database]
  (let [url        (str resource)]
      (loop []
      (log/info "Request:" url query)
      (let [resp (client/request {:method            :get
                                  :url               url
                                  :query-params      {"query" query}
                                  :accept            :json
                                  :as                :json
                                  :throw-exceptions  false})
              status-code           (:status resp)
              body                  (:body resp)]
          (case status-code
          200 (if (= (:error body) "Continue wait") ; check does the response contains "Continue wait" message or not.
                (do
                  (Thread/sleep 2000) ; If it contains, wait 2 sec,
                  (recur)) ; then retry the query.
                resp)
          400 (throw (Exception. (format "Error occured: %s" body)))
          403 (throw (Exception. "Authorization error. Check your auth token!"))
          500 (throw (Exception. (format "Internal server error: %s" body)))
          :else (throw (Exception. (format "Unknown error. Body: %s" body))))))))

(defn- get-endpoint-data
  "Get a datasette definition from an endpoint URL."
  [{{:keys [datasette_endpoint]} :details, :as database}]
  (let [resp   (make-request datasette_endpoint nil database)
        body   (:body resp)]
    body))

; Read the datasette endpoint and pull out the table and schema
(defmethod driver/describe-database :datasette [_ database]
  (let [datasette-def (get-endpoint-data database)]
  {:tables #{{:name (:table datasette-def)
              :schema nil
              :description (:human_description_en datasette-def)}}}))

;;; ----------------------------- describe-table -------------------------------
(defmethod driver/describe-table :datasette
  [_ database table]
  {:name   (:name table)
   :schema (:schema table)
   :fields (set (for [field (:columns (get-endpoint-data database))]
                  {:name field
                   :base-type :type/Text
                   :database-type "some.Random.String"}))}) ; Is database-type the raw type from the DB?

;;; --------------------------------- query execution ------------------------------------------------------------
(defmethod driver/mbql->native :datasette [_ query]
  (log/info "MBQL:" query))
