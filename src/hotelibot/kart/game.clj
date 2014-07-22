(ns hotelibot.kart.game
  (:require [hotelibot.slack-api :as slack]
            [hotelibot.system-instance :refer [system]])
  (:import [java.util TimerTask Timer]))

;; Game component

(def ^:private game-status #{:seeking :racing :awaiting-results :finished})

;; After 'time' reaches 0,
; :racing -> :finished (persist game)
; :finished -> (stop game) (clear state, await initialization)

(def ^:const avg-time 5000) ; 5s

(defrecord Game [players status winner time])

;; Global state entry point - use sparingly
(defn- update-game! [updated-game]
  (prn "Setting system game atom to " updated-game)
  (reset! (:game (:kart system)) updated-game))

(defn new-game
  ([]
     (map->Game {:players []
                 :status :seeking}))
  ([players]
     (map->Game {:players players 
                 :status :seeking})))

(defn add-player-to-game [game player]
  (update-in game [:players] conj player))

(defn set-winner [game winner]
  (assoc game
    :winner winner
    :status :completed))

(defn- do-after [time callback]
  (let [task (proxy [TimerTask] []
               (run [] (callback)))]
    (. (new Timer) (schedule task (long time)))))

(defn time-remaining [game]
  (str (/ (:time game) 60000) " minutes"))

(defn finish-race [game]
  (slack/push-message :username "hotelibot" :icon ":kart8:" :message "Who won?")
  (update-game! (assoc game :status :awaiting-results)))

(defn begin-race [game]
  (do-after avg-time #(finish-race game))
  (assoc game
    :status :racing
    :time avg-time))

(defn to-message
  "Turns the game record into a message for posting in the #kart channel"
  [{:keys [players status winner time] :as game}]
  (when game 
    (case status
      :seeking (format "Current Status: Seeking (%d/%d)\nPlayers: %s" (count players) 4 (str players))
      :racing (format "Current Status: Racing (~%s remaining)" (time-remaining game))
      :awaiting-results "Current Status: Awaiting Results" 
      :completed (format "Current Status: Race Completed, %s"
                         (if winner
                           (str "Congratulations " winner)
                           "No winner specified")))))
