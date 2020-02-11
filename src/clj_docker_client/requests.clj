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
  (:require [clojure.string :as s]
            [jsonista.core :as json])
  (:import (java.io InputStream)
           (java.util.regex Pattern)
           (okhttp3 HttpUrl
                    HttpUrl$Builder
                    MediaType
                    OkHttpClient
                    OkHttpClient$Builder
                    Request$Builder
                    RequestBody)
           (okhttp3.internal Util)
           (okio BufferedSink
                 Okio)
           (clj_docker_client.socket UnixDomainSocketFactory)))

(defn unix-socket-client-builder
  "Constructs a client builder for a UNIX socket.

  Also returns the underlying socket for direct access."
  [^String path]
  (let [socket-factory (UnixDomainSocketFactory. path)
        builder        (.socketFactory (OkHttpClient$Builder.)
                                       socket-factory)]
    {:socket  (.getSocket socket-factory)
     :builder builder}))

(defn stream->req-body
  "Converts an InputStream to OkHttp RequestBody."
  [^InputStream stream]
  (proxy [RequestBody] []
    (contentType []
      (MediaType/get "application/octet-stream"))
    (contentLength []
      (.available stream))
    (writeTo [^BufferedSink sink]
      (let [source (Okio/source stream)]
        (try (.writeAll sink source)
             (finally (Util/closeQuietly source)))))))

(defn add-params
  "Helper fn for adding parameters to OkHttp Builders."
  [builder params location]
  (loop [obj  builder
         args params]
    (if (empty? args)
      obj
      (let [[param value] (first args)
            request       (case location
                            :header (.addHeader ^Request$Builder obj
                                                (name param)
                                                (str value))
                            :query  (.addQueryParameter ^HttpUrl$Builder obj
                                                        (name param)
                                                        (str value)))]
        (recur request (dissoc args param))))))

(defn interpolate-path
  "Replaces all occurrences of {k1}, {k2} ... with the value map provided.

  Example:
  given a/path/{id}/on/{not-this}/root/{id} and {:id hello}
  results in: a/path/hello/{not-this}/root/hello."
  [path value-map]
  (let [[param value] (first value-map)]
    (if (nil? param)
      path
      (recur
       (s/replace path
                  (re-pattern (format "\\{([%s].*?)\\}"
                                      (-> param name Pattern/quote)))
                  value)
       (dissoc value-map param)))))

(defn build-request
  "Builds a Request object for OkHttp."
  [{:keys [method ^String url query header path body]}]
  (let [formatted-url (interpolate-path url path)
        url-builder   (add-params (.newBuilder (HttpUrl/parse formatted-url))
                                  query
                                  :query)
        req-builder   (-> (Request$Builder.)
                          (.url (.build ^HttpUrl$Builder url-builder))
                          (add-params header :header))
        req-body      (cond
                        (nil? body)
                        (RequestBody/create nil "")

                        (map? body)
                        (RequestBody/create (json/write-value-as-string body)
                                            (MediaType/get "application/json; charset=utf-8"))

                        (instance? InputStream body)
                        (stream->req-body body))
        req           (case method
                        :post      (.post ^Request$Builder req-builder req-body)
                        :put       (.put ^Request$Builder req-builder req-body)
                        :head      (.head ^Request$Builder req-builder)
                        :delete    (.delete ^Request$Builder req-builder)
                        (nil :get) req-builder
                        (throw (IllegalArgumentException.
                                (format "Unsupported request method: '%s'."
                                        (name method)))))]
    (.build ^Request$Builder req)))

(defn fetch
  "Performs the request.

  If :as is passed as :stream, returns an InputStream.
  If passed as :socket, returns the underlying UNIX socket for direct I/O."
  [{:keys [conn as] :as args}]
  (let [client   ^OkHttpClient (:client conn)
        request  (build-request (update args
                                        :url
                                        #(format "http://localhost%s" %)))
        response (-> client
                     (.newCall request)
                     .execute
                     .body)]
    (case as
      :socket (:socket conn)
      :stream (.byteStream response)
      (.string response))))

(comment
  (unix-socket-client-builder "/var/run/docker.sock")

  (stream->req-body (java.io.FileInputStream. (java.io.File. "README.md")))

  (Pattern/quote "\\{([a-id].*?)\\}")

  (interpolate-path "a/{id}/path/to/{something-else}/and/{xid}/{not-this}"
                    {:id             "a-id"
                     :xid            "b-id"
                     :something-else "stuff"}))
