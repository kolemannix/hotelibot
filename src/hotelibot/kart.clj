(ns hotelibot.kart
  (:require [clojure.java.io :as io]
            [com.stuartsierra.component :as component]
            [hotelibot.system-instance :as system-instance]
            [hotelibot.slack-api :as slack]
            [hotelibot.kart.game :as game]))

;; TODO: make these a bunch of REGEXES
(def start-game-commands (set (clojure.string/split-lines (slurp (io/resource "start-cmd.txt")))))

(def accept-game-commands #{"in" ":kart:" ":banana:" ":bananas:" ":blueshell:"
                            ":bomb:" ":bullet:" ":cloud:" ":fat:" ":goldenshower:"
                            ":greenshell:" ":lightningbolt:" ":mushroom:" ":pow:"
                            ":redbox:" ":redshell:" ":squid:" ":star:" ":triplepack:"})



;; Needs to depend on DB and Incoming web hook components
(defrecord KartBot [game db]
  component/Lifecycle
  (start [this]
    (prn ";; Starting Kart Component")
    this)
  (stop [this]
    (prn ";; Stopping Kart Component")
    (assoc this :game (atom nil))))

(defn kart-bot []
  (map->KartBot {:game (atom nil)}))

(defn- from-bot? [{{user :user_id} :params}]
  (= "USLACKBOT" user))

(defn- handle-accept [game sender]
  (let [new-game  (game/add-player-to-game game sender)]
    (if (= (count (:players game)) 3)
      (game/begin-race new-game)
      new-game)))

(defn- seeking-handler
  "Defines what actions the bot will take while seeking a game"
  [game sender content]
  (let [new-game (if (contains? accept-game-commands content)
                   (handle-accept game sender)
                   game)]
    {:game new-game
     :message (game/to-message new-game)}))

(defn- in-progress-handler
  [game sender content]
  {:game game
   :message (game/time-remaining game)})

(defn- parse-user [message]
  (slack/user-with-id (clojure.string/join (rest (re-find #"@\w+" message)))))

(defn- awaiting-results-handler
  [game sender content]
  (prn "content" content)
  (let [new-game  (if-let [winner (if (= "me" content)
                                    sender
                                    (parse-user content))]
                    (game/set-winner game winner)
                    game)]
    {:game new-game
     :message (game/to-message new-game)}))

(defn- completed-handler
  [game sender content]
  {:game game
   :message (game/to-message game)})

(defn- idle-handler
  [game sender content]
  (when (contains? start-game-commands content)
    (let [new-game (game/new-game [sender])]
      {:game new-game
       :message (game/to-message new-game)})))

(defn- handle* [kart sender content]
  (let [handler (if-let [game (deref (:game kart))]
                  (case (:status game)
                    :seeking seeking-handler
                    :racing in-progress-handler
                    :awaiting-results awaiting-results-handler
                    :completed completed-handler)
                  idle-handler)]
    (let [{:keys [game message]} (handler @(:game kart) sender content)]
      (reset! (:game kart) game)
      message)))

(defn handle [kart request]
  (prn (:params request))
  (when-not (from-bot? request)
    (let [{:keys [user_name text]} (:params request)]
      (handle* kart user_name text))))

