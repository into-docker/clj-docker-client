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

(ns clj-docker-client.requests
  (:require [unixsocket-http.core :as uhttp]
            [clojure.string :as s]
            [jsonista.core :as json])
  (:import [java.util.regex Pattern]))

;; ## Helpers

(defn panic!
  "Helper for erroring out for wrong args."
  [^String message]
  (throw (IllegalArgumentException. message)))

(defn interpolate-path
  "Replaces all occurrences of {k1}, {k2} ... with the value map provided.

  Example:
  given a/path/{id}/on/{not-this}/root/{id} and {:id hello}
  results in: a/path/hello/{not-this}/root/hello."
  [path value-map]
  (let [[param value] (first value-map)]
    (if (nil? param)
      path
      (recur (s/replace path
                        (re-pattern (format "\\{([%s].*?)\\}"
                                            (-> param
                                                name
                                                Pattern/quote)))
                        value)
             (dissoc value-map param)))))

(defn- maybe-serialize-body
  "If the body is a map, convert it to JSON and attach the correct headers."
  [{:keys [body]
    :as   request}]
  (if (map? body)
    (-> request
        (assoc-in [:headers "content-type"] "application/json")
        (update :body json/write-value-as-string))
    request))

;; ## Connect

(defn connect*
  "Connects to the provided :uri in the connection options.

  Optionally takes connect, read, write and call timeout in ms.
  All are set to 0 by default, which is no timeout.

  Returns the connection.

  The url must be fully qualified with the protocol.
  eg. unix:///var/run/docker.sock or https://my.docker.host:6375"
  [{:keys [uri timeouts builder-fn]}]
  (when (nil? uri)
    (panic! ":uri is required"))
  (uhttp/client uri
                {:builder-fn         (if builder-fn
                                       builder-fn
                                       identity)
                 :connect-timeout-ms (:connect-timeout timeouts)
                 :read-timeout-ms    (:read-timeout timeouts)
                 :write-timeout-ms   (:write-timeout timeouts)
                 :call-timeout-ms    (:call-timeout timeouts)
                 :mode               :recreate}))

;; ## Fetch

(defn- build-request
  "Builds a Request object for unixsocket-http."
  [{:keys [conn method ^String url query header path body as throw-exception?]
    :or   {method :get}}]
  (-> {:client           conn
       :method           method
       :url              (interpolate-path url path)
       :headers          header
       :query-params     query
       :body             body
       :as               (or as :string)
       :throw-exceptions throw-exception?}
      maybe-serialize-body))

(defn fetch
  "Performs the request.

  If :as is passed as :stream, returns an InputStream.
  If passed as :socket, returns the underlying UNIX socket for direct I/O."
  [request]
  (-> (build-request request)
      uhttp/request
      :body))
