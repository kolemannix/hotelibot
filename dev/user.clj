(ns user
  (:require [clj-http.client :as client]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [clojure.tools.namespace.repl :refer (refresh)]
            [com.stuartsierra.component :as component]
            [hotelibot.routes :as routes]
            [hotelibot.bot :as bot]
            [hotelibot.slack-api :as slack]
            [hotelibot.kart :as kart]
            [hotelibot.system-instance :as system-instance]
            ring.adapter.jetty))

;;; Development-time components

(defrecord JettyServer [app port server]
  component/Lifecycle
  (start [this]
    (let [server (ring.adapter.jetty/run-jetty
                  app
                  {:port port
                   :join? false})]
      (prn :STARTED "jetty" :port port :server server)
      (assoc this :server server)))
  (stop [this]
    (.stop (:server this))
    (prn :STOPPED "jetty" :port port)
    this))

(defn jetty-server
  "Returns a Lifecycle wrapper around embedded Jetty for development."
  [port]
  (map->JettyServer {:app #'routes/app
                     :port port}))

(defn dev-system
  "Returns a complete system in :dev mode for development at the REPL.
  Options are key-value pairs from:

      :port        Web server port, default is 3000"
  [& {:keys [port]
      :or {port 3000}
      :as options}]
  (log/info "dev-system" :port port)
  (component/system-map :jetty (jetty-server port)
                        :kart (kart/kart-bot)
                        :options (or options {})))

;;; Development system lifecycle

(defn init
  "Initializes the development system."
  [& options]
  (alter-var-root #'system-instance/system (constantly (apply dev-system options))))

(defn system
  "Returns the system instance"
  []
 system-instance/system)

;; If desired change this to a vector of options that will get passed
;; to dev-system on (reset)
(def default-options [])

(defn go
  "Launches the development system. Ensure it is initialized first."
  [& options]
  (let [options (or options default-options)]
    (when-not system-instance/system (apply init options)))
  (alter-var-root #'system-instance/system component/start)
  (set! *print-length* 20)
  :started)

(defn stop
  "Shuts down the development system and destroy all its state."
  []
  (when system-instance/system
    (component/stop system-instance/system)
    (alter-var-root #'system-instance/system (constantly nil)))
  :stopped)

(defn reset
  "Stops the currently-running system, reload any code that has changed,
  and restart the system."
  []
  (stop)
  (refresh :after 'user/go))

(defn inspect-game []
  (deref (:game (:kart system-instance/system))))

(defn inspect-kart []
  (:kart system-instance/system))

(defn inspect-app []
  (:app (:jetty system-instance/system)))
