(ns metabase.driver.datasette
  (:require [clj-http.client :as client]
            [clojure.tools.logging :as log]
            [metabase.query-processor.store :as qp.store]
            [metabase.driver :as driver]
            [metabase.query-processor.interface :as qp.i]
            [metabase.mbql.util :as mbql.u]
            [metabase.query-processor.util :as qputil]
            [metabase.driver.sql.util.unprepare :as unprepare]
            [metabase.query-processor.context :as context]
            [metabase.query-processor.reducible :as qp.reducible]))
  
(driver/register! :datasette, :parent :sqlite)
  
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
                                  :query-params      query
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

(defn- get-database-json
  "Retrieve the database definition from the Datasette endpoint."
  [{{:keys [datasette_endpoint]} :details, :as database}]
  (let [db_url  (format "%s.json" datasette_endpoint)
        resp    (make-request db_url nil database)]
    (:body resp)))

(defn- get-table-json
  "Retrieve the table definition from the Datasette endpoint."
  [{{:keys [datasette_endpoint]} :details, :as database}
   table]
  (let [db_url  (format "%s/%s.json" datasette_endpoint (:name table))
        resp    (make-request db_url nil database)]
    (:body resp))
  )

; Read the datasette endpoint and pull out the table name
(defmethod driver/describe-database :datasette [_ database]
  (let [datasette-def (get-database-json database)]
  {:tables (set (for [table-def (:tables datasette-def)] {:name (:name table-def), :schema nil}))}))

;;; ----------------------------- describe-table -------------------------------
(defmethod driver/describe-table :datasette
  [_ database table]
  (let [table-def     (get-table-json database table)
        res           (merge {:name   (:name table)
                              :schema (:schema table)
                              :fields (set (for [field (:columns table-def)]
                                             {:name field
                                              :base-type :type/Text
                                              :database-type "some.Random.String"}))} ; Is database-type the raw type from the DB?
                             (when-let [val (if-not (clojure.string/blank? (:description table-def)) (:description table-def))]
                               {:description val}))]
    res)
  )

(defn create-record [data]
  (let [res (merge {:username (data :username)
                    :first-name (get-in data [:user-info :name :first])
                    :last-name (get-in data [:user-info :name :last])}
                   (when-let [gender (get-in data [:user-info :sex])]
                     {:gender gender}))]
    res))

;;; --------------------------------- query execution ------------------------------------------------------------

; We need to override the execution here to send the SQL query to the Datasette endpoint
(defmethod driver/execute-reducible-query :datasette
  [driver
   {{sql :query, params :params} :native
    query-type                   :type
    :as                          outer-query}
   context
   respond]
  (let [sql     (str "-- "
                     (qputil/query->remark outer-query) "\n"
                     (unprepare/unprepare driver (cons sql params)))
        endpoint (:datasette_endpoint (:details (qp.store/database)))]
    (log/info sql endpoint)
    (let [response (:body (make-request (format "%s.json" endpoint), {:sql sql}, nil))]
      (respond {:cols (for [key (:columns response)] {:name key})}
               (lazy-cat (:rows response))))))
