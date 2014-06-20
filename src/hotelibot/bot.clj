(ns hotelibot.bot
  "Functions that implement the logic for the hotelibot"
  (:require [clojail.core :as clojail]
            [clojail.testers]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [hotelibot.slack-api :as slack]))

(def slack-api-token
  (or (System/getProperty "SLACK_API_TOKEN")
      (str/trim (slurp "slack-api-token.txt"))))

(defn unknown-command-handler
  "Returns an error message indicating that the command was not recognized"
  [message command args]
  (format "Sorry, I don't know what to do with '%s %s'" command args))

(defn parse-users
  "Returns a sequence of user IDs from message text."
  [text]
  (->> (re-seq #"\<@(U[^\>|]+)\|?([^\>]+)?\>" text)
       (map second)))

(defn skype-call-handler
  "Returns a link that will start a Skype call with the named users."
  [message command args linker]
  (let [message-users   (into #{} (parse-users args))
        all-users       (slack/users-list slack-api-token)
        user-map        (zipmap (map :id all-users) all-users)
        skype-handles   (zipmap message-users
                                (map (fn [u]
                                       (-> u user-map :skype))
                                     message-users))
        present-handles (remove nil? (vals skype-handles))
        absent-handles  (->> skype-handles
                             (filter (fn [[k v]] (nil? v)))
                             keys)]
    (format "Start your call by <%s|clicking here>%s"
            (linker :skype present-handles)
            (if-not (seq absent-handles)
              ""
              (format "\nI wasn't able to find Skype handles for these people: %s.\nMaybe you should bug them to update their Slack profile."
                     (->> absent-handles
                          (map #(format "<@%s>" %))
                          (str/join "," )))))))

(defn say-handler
  "Just echoes back what was said."
  [message command args linker]
  args)

(let [sb (clojail/sandbox clojail.testers/secure-tester)]
  (defn eval-handler
    "Evaluates the passed form in a Clojail sandbox"
    [message command args linker]
    (let [w (java.io.StringWriter.)
          [val output] (binding [*out* w]
                         (let [result (-> args read-string eval pr-str)]
                           [result (str w)]))]
      (str "```"
           val
           (when-not (str/blank? output) (str "\n;; => " output))
           "```"))))

(def command-handlers
  {"skype" skype-call-handler
   "say"   say-handler
   "eval"  eval-handler})

(defn handle
  "Main entry point for handling messages to the hotelibot."
  [message linker]
  (let [{:keys [user_id text trigger_word]} message
        trigger-len (count trigger_word)
        has-colon? (= ":" (subs text trigger-len (inc trigger-len)))
        phrase (-> text
                   (subs (if has-colon?
                           (inc trigger-len)
                           trigger-len))
                   str/trim)
        [command args] (str/split phrase #"\s" 2)
        handler (get command-handlers command unknown-command-handler)]
    (try
      (handler message command args linker)
      (catch Throwable t
        (log/error t "Error in handler")
        (format "Error handling command %s with arguments %s: %s"
                command args (.getMessage t))))))