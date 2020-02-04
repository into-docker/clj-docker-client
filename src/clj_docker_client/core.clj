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

(ns clj-docker-client.core
  (:require [clojure.string :as s]
            [jsonista.core :as json]
            [clj-docker-client.requests :as req]
            [clj-docker-client.specs :as spec])
  (:import (java.time Duration)
           (java.net URI)
           (okhttp3 OkHttpClient$Builder)))

(defn- panic!
  "Helper for erroring out for wrong args."
  [^String message]
  (throw (IllegalArgumentException. message)))

(defn connect
  "Connects to the provided :uri in the connection options.

  Optionally takes connect, read, write and call timeout in ms.
  All are set to 0 by default, which is no timeout.

  Returns the connection.

  The url must be fully qualified with the protocol.
  eg. unix:///var/run/docker.sock or https://my.docker.host:6375"
  [{:keys [uri connect-timeout read-timeout write-timeout call-timeout]}]
  (when (nil? uri)
    (panic! ":uri is required"))
  (let [uri              (URI. uri)
        scheme           (.getScheme uri)
        path             (.getPath uri)
        {:keys [^OkHttpClient$Builder builder
                socket]} (case scheme
                           "unix" (req/unix-socket-client-builder path)
                           (panic! (format "Protocol '%s' not supported yet." scheme)))
        timeout-from     #(Duration/ofMillis (or % 0))
        builder+opts     (-> builder
                             (.connectTimeout (timeout-from connect-timeout))
                             (.readTimeout (timeout-from read-timeout))
                             (.writeTimeout (timeout-from write-timeout))
                             (.callTimeout (timeout-from call-timeout)))]
    {:client (.build builder+opts)
     :socket socket}))

(defn categories
  "Returns the available categories.

  Takes an optional API version for specific ones."
  ([]
   (categories nil))
  ([version]
   (->> (get (spec/fetch-spec version) "paths")
        (map #(second (s/split (key %) #"/")))
        (map keyword)
        (into #{}))))

(defn client
  "Constructs a client for a specified category.

  Returns the client.

  Examples are: :containers, :images, etc"
  [{:keys [category conn api-version] :as args}]
  (when (some nil? [category conn])
    (panic! ":category, :conn are required"))
  (assoc args
         :paths
         (:paths (spec/get-paths-of-category category api-version))))

(defn ops
  "Returns the supported ops for a client."
  [client]
  (->> client
       :paths
       (map spec/->endpoint)
       (mapcat :ops)
       (map :op)))

(defn doc
  "Returns the doc of the supplied category and operation"
  [{:keys [category api-version]} operation]
  (when (nil? category)
    (panic! ":category is required"))
  (update-in
   (select-keys (spec/request-info-of category
                                      operation
                                      api-version)
                [:doc :params])
   [:params]
   #(map (fn [param]
           (select-keys param [:name :type :description]))
         %)))

(defn invoke
  "Performs the operation with the specified client and a map of options.

  Returns the resulting map.

  Options are:
  :op specifying the operation to invoke. Required.
  :params specifying the params to be passed to the :op.
  :as specifying the result. Can be either of :stream, :socket, :data. Defaults to :data.

  If a :socket is requested, the underlying UNIX socket is returned."
  [{:keys [category conn api-version]} {:keys [op params as]}]
  (when (some nil? [category conn op])
    (panic! ":category, :conn are required in client, :op is required in operation map"))
  (let [request-info   (spec/request-info-of category op api-version)
        _              (when (nil? request-info)
                         (panic! "Invalid params for invoking op."))
        {:keys [body
                query
                header
                path]} (->> request-info
                            :params
                            (reduce (partial spec/gather-request-params
                                             params)
                                    {}))
        response       (req/fetch {:conn   conn
                                   :url    (:path request-info)
                                   :method (:method request-info)
                                   :query  query
                                   :header header
                                   :body   (-> body vals first)
                                   :path   path
                                   :as     as})
        try-json-parse #(try
                          (json/read-value %
                                           (json/object-mapper
                                            {:decode-key-fn keyword}))
                          (catch Exception _ %))]
    (case as
      (:socket :stream) response
      (try-json-parse response))))

(comment
  (require '[clojure.java.io :as io])

  (-> (URI. "unix:///var/run/docker.sock")
      .getPath)

  (connect {:uri "unix:///var/run/docker.sock"})

  (connect {:uri "https://my.docker.host:6375"})

  (req/fetch {:conn (connect {:uri "unix:///var/run/docker.sock"})
              :url  "/v1.30/_ping"})

  (req/fetch {:conn   (connect {:uri "unix:///var/run/docker.sock"})
              :url    "/containers/create"
              :method :post
              :query  {:name "conny"}
              :body   {:Image "busybox:musl"
                       :Cmd   "ls"}
              :header {:X-Header "header"}})

  (req/fetch {:conn   (connect {:uri "unix:///var/run/docker.sock"})
              :url    "/containers/cp-this/archive"
              :method :put
              :query  {:path "/root/src"}
              :body   (-> "src.tar.gz"
                          io/file
                          io/input-stream)})
  ;; PLANNED API

  (def conn (connect {:uri             "unix:///var/run/docker.sock"
                      :connect-timeout 0
                      :read-timeout    0
                      :write-timeout   0
                      :call-timeout    0}))

  (categories)

  (categories "v1.40")

  (def containers (client {:category    :containers
                           :conn        conn
                           :api-version "v1.40"}))

  (def images (client {:category :images
                       :conn     conn}))

  (ops images)

  (ops containers)

  (doc containers :ContainerAttach)

  (def sock
    (invoke containers {:op     :ContainerAttach
                        :params {:id     "conny"
                                 :stream true
                                 :stdin  true}
                        :as     :socket}))

  (io/copy "hello ohai" (.getOutputStream sock))

  (.close sock)

  (invoke containers {:op     :ContainerList
                      :params {:all true}})

  (invoke containers {:op     :ContainerCreate
                      :params {:name "conny"
                               :body {:Image "busybox:musl"
                                      :Cmd   "ls"}}})

  (invoke containers {:op     :PutContainerArchive
                      :params {:path        "/root"
                               :id          "cp-this"
                               :inputStream (-> "src.tar.gz"
                                                io/file
                                                io/input-stream)}})

  (doc containers :ContainerCreate)

  (invoke images {:op :ImageList})

  (def pinger (client {:category    :_ping
                       :conn        conn
                       :api-version "v1.25"}))

  (invoke pinger {:op :SystemPing})

  (invoke containers {:op     :ContainerLogs
                      :params {:id     "conny"
                               :stdout true
                               :follow true}
                      :as     :stream}))
