(ns hotelibot.kart.game
  (:import [java.util TimerTask Timer]))

;; Game component

(def ^:private game-status #{:seeking :racing :finished})

;; After 'time' reaches 0,
; :racing -> :finished (persist game)
; :finished -> (stop game) (clear state, await initialization)

(def ^:const avg-time 15)

(defrecord Game [players status winner time])

(defn new-game
  ([]
     (map->Game {:players []
                 :status :seeking}))
  ([players status winner]
     (map->Game {:players players 
                 :status status})))

(defn add-player-to-game [game player]
  (update-in game [:players] conj player))

(defn- do-after [time callback]
  (let [task (proxy [TimerTask] []
               (run [] (callback)))]
    (. (new Timer) (schedule task (long 1000)))))

(defn finish-race [game]
  (assoc game :status :finished))


(defn begin-race [game]
  (-> game
      (assoc :status :racing)
      (do-after avg-time #(finish-race game))
      (assoc :time avg-time)))

(def statuses {:seeking "Seeking..."
               :racing "Racing!"
               :finished "Game Completed"})

(defn to-message
  "Turns the game record into a message for posting in the #kart channel"
  [{:keys [players status winner time] :as game}]
  (if-not game (prn "no game!"))
  (case status
    :seeking (format "Current Status: %s (%d/%d)\nPlayers: %s" (statuses status) (count players) 4 (str players))
    :racing (format "Current Status: %s (~%d minutes remaining)" (statuses status) time)
    :finished (format "Current Status: %s, Congratulations %s!") (statuses status) winner))
