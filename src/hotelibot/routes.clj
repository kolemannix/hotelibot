(ns hotelibot.routes
  (:require [clojure.tools.logging :as log]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.json :refer [wrap-json-body]]
            [ring.util.response :refer :all]))

(defn hotelibot
  [request]
  (response "Hello"))

(defn four-oh-four [request]
  (-> (response "Page not found")
      (status 404)))

(def routes-by-uri
  {"/hotelibot" hotelibot})

(defn handler [request]
  (log/trace "handler called" :request request)
  (let [h (case [(:request-method request) (:uri request)]
            [:post "/hotelibot"] hotelibot
            four-oh-four)]
    (h request)))

(def app
  (-> handler
      wrap-keyword-params
      (wrap-json-body {:keywords? true})
      wrap-params))
