(ns hotelibot.slack-api
  "Functions for working with the Slack API."
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.data.json :as json])
  (:import [com.google.common.cache CacheBuilder CacheLoader]
           [com.google.common.util.concurrent ListenableFutureTask]))

(def outgoing-webhook-token
  (or (System/getProperty "SLACK_API_TOKEN")
      (str/trim (slurp "slack-api-token.txt"))))

(def incoming-webhook-url
  (str/trim (slurp "incoming-webhook-url.txt")))

(defn api-url
  "Returns the appropriate URL for the given method"
  [method token]
  (format "https://slack.com/api/%s?token=%s" method token))

(defn users-list
  "Returns the list of all users as a sequence of maps. Caches the
  result for up to ten minutes."
  []
  ;; TODO: Add caching
  (let [resp (client/post (api-url "users.list" outgoing-webhook-token)
                          {:as :json})]
    (when-not (-> resp :body :ok)
      (throw (ex-info "Slack API call to users.list failed"
                      {:reason ::slack-api-call-failure
                       :response resp
                       :token outgoing-webhook-token
                       :method "users.list"})))
    (-> resp :body :members)))

(defn push-message [& {:keys [username icon message]}]
  (client/post incoming-webhook-url {:body (json/write-str {:text message
                                                            :username username 
                                                            :icon_emoji icon})}))
