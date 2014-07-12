(ns hotelibot.kart
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [hotelibot.system-instance :as system-instance]
            [hotelibot.kart.game :as game]))

;; TODO: make these a bunch of REGEXES
(def start-game-commands (set (clojure.string/split-lines (slurp (io/resource "start-cmd.txt")))))

(def accept-game-commands #{"in" ":kart:" ":banana:" ":bananas:" ":blueshell:"
                            ":bomb:" ":bullet:" ":cloud:" ":fat:" ":goldenshower:"
                            ":greenshell:" ":lightningbolt:" ":mushroom:" ":pow:"
                            ":redbox:" ":redshell:" ":squid:" ":star:" ":triplepack:"})



;; Needs to depend on DB and Incoming web hook components
(defrecord KartBot [game db push-service]
  component/Lifecycle
  (start [this]
    (prn ";; Starting Kart Component")
    this)
  (stop [this]
    (prn ";; Stopping Kart Component")
    (assoc this :game (atom nil))
    ))

(defn kart-bot []
  (map->KartBot {:game (atom nil)}))

(defn- from-bot? [{{user :user_id} :params}]
  (= "USLACKBOT" user))

(defn- seeking-handler
  "Defines what actions the bot will take while seeking a game"
  [game sender message]
  (if (contains? accept-game-commands message)
    (let [new-game  (game/add-player-to-game game sender)]
      (if (= (count (:players game)) 3)
        (game/begin-race game)
        new-game))
    game))

(defn- in-progress-handler
  [game sender message]
  (when (= "time?" message)
    )
  )

(defn- completed-handler
  [game sender message]
  )

(defn- idle-handler
  [game sender message]
  (when (contains? start-game-commands message)
    (game/new-game)))

(defn- handle* [kart sender message]
  (let [handler (if-let [game @(:game kart)]
                  (case (:status game)
                    :seeking seeking-handler
                    :racing in-progress-handler
                    :completed completed-handler)
                  idle-handler)]
    (game/to-message (swap! (:game kart) handler sender message))))


(defn handle [kart request]
  (when-not (from-bot? request)
    (let [{:keys [user_name text]} (:params request)
          {:keys [_ message]}  (handle* kart user_name text)]
      message)))

