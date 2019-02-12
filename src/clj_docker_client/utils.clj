;   This file is part of clj-docker-client.
;
;   clj-docker-client is free software: you can redistribute it and/or modify
;   it under the terms of the GNU Lesser General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   clj-docker-client is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU Lesser General Public License for more details.
;
;   You should have received a copy of the GNU Lesser General Public License
;   along with clj-docker-client. If not, see <http://www.gnu.org/licenses/>.

(ns clj-docker-client.utils
  (:import (com.spotify.docker.client.messages HostConfig
                                               ContainerConfig
                                               PortBinding)
           (java.util List
                      Set)))

(def id-length 12)

(defn format-id
  "Return docker SHA256 ids in the standard length"
  [^String id]
  (-> id
      (clojure.string/split #":")
      (last)
      (subs 0 id-length)))

;; TODO: Optimize as mentioned in:
;; https://www.reddit.com/r/Clojure/comments/8zurv4/critical_code_review_and_feedback/
(defn sh-tokenize!
  "Tokenizes a shell command given as a string into the command and its args.
  Either returns a list of tokens or throws an IllegalStateException.

  Sample input: sh -c 'while sleep 1; do echo \\\"${RANDOM}\\\"; done'
  Output: [sh, -c, while sleep 1; do echo \"${RANDOM}\"; done]"
  [^String command]
  (let [[escaped?
         current-arg
         args
         state] (loop [cmd         command
                       escaped?    false
                       state       :no-token
                       current-arg ""
                       args        []]
                  (if (or (nil? cmd)
                          (zero? (count cmd)))
                    [escaped? current-arg args state]
                    (let [char ^Character (first cmd)]
                      (if escaped?
                        (recur (rest cmd) false state (str current-arg char) args)
                        (case state
                          :single-quote (if (= char \')
                                          (recur (rest cmd) escaped? :normal current-arg args)
                                          (recur (rest cmd) escaped? state (str current-arg char) args))
                          :double-quote (case char
                                          \" (recur (rest cmd) escaped? :normal current-arg args)
                                          \\ (let [next (second cmd)]
                                               (if (or (= next \")
                                                       (= next \\))
                                                 (recur (drop 2 cmd) escaped? state (str current-arg next) args)
                                                 (recur (drop 2 cmd) escaped? state (str current-arg char next) args)))
                                          (recur (rest cmd) escaped? state (str current-arg char) args))
                          (:no-token :normal) (case char
                                                \\ (recur (rest cmd) true :normal current-arg args)
                                                \' (recur (rest cmd) escaped? :single-quote current-arg args)
                                                \" (recur (rest cmd) escaped? :double-quote current-arg args)
                                                (if-not (Character/isWhitespace char)
                                                  (recur (rest cmd) escaped? :normal (str current-arg char) args)
                                                  (if (= state :normal)
                                                    (recur (rest cmd) escaped? :no-token "" (conj args current-arg))
                                                    (recur (rest cmd) escaped? state current-arg args))))
                          (throw (IllegalStateException.
                                   (format "Invalid shell command: %s, unexpected token %s found." command state))))))))]
    (if escaped?
      (conj args (str current-arg \\))
      (if (not= state :no-token)
        (conj args current-arg)
        args))))

(defn port-binding-default-public
  [bind]
  (if (clojure.string/includes? bind ":")
    (let [[host port] (clojure.string/split bind #":")]
      (PortBinding/of ^String host ^String port))
    (PortBinding/of "0.0.0.0" ^String bind)))

(defn filter-host
  [s]
  (if (clojure.string/includes? s ":")
    (second (clojure.string/split s #":"))
    s))

(defn port-configs-from
  [port-mapping]
  (let [host-ports      (->> port-mapping
                             (keys)
                             (map str)
                             (map #(vector % [(port-binding-default-public %)]))
                             (reduce (fn [acc [k v]]
                                       (assoc acc (filter-host k) v)) {}))
        container-ports (->> port-mapping
                             (vals)
                             (map str)
                             (into #{}))]
    {:host-config   (if (empty? host-ports)
                      (.build (HostConfig/builder))
                      (-> (HostConfig/builder)
                          (.portBindings host-ports)
                          (.build)))
     :exposed-ports container-ports}))

(defn config-of
  ([^String image]
   (config-of image [] [] {}))
  ([^String image ^List cmd]
   (config-of image cmd [] {}))
  ([^String image ^List cmd ^List env-vars]
   (config-of image cmd env-vars {}))
  ([^String image ^List cmd ^List env-vars port-bindings]
   (let [port-config (port-configs-from port-bindings)]
     (-> (ContainerConfig/builder)
         (.hostConfig (:host-config port-config))
         (.env env-vars)
         (.image image)
         (.exposedPorts ^Set (:exposed-ports port-config))
         (.cmd cmd)
         (.build)))))
