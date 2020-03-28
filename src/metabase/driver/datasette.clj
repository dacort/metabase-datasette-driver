(ns metabase.driver.datasette
    (:require [metabase.driver :as driver]))
  
  (driver/register! :datasette)
  
  (defmethod driver/display-name :datasette [_]
    "Datasette")
  