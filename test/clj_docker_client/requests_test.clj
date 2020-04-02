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

(ns clj-docker-client.requests-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [clojure.java.io :as io]
            [clj-docker-client.requests :refer :all])
  (:import (java.io InputStream)
           (okhttp3 HttpUrl
                    HttpUrl$Builder
                    OkHttpClient$Builder
                    Request
                    RequestBody
                    Request$Builder)))

(deftest docker-connection
  (testing "successful connection to the socket"
    (is (instance? clj_docker_client.socket.TunnelingUnixSocket
                   (:socket (connect* {:uri "unix:///var/run/docker.sock"}))))
    (is (instance? okhttp3.OkHttpClient
                   (:client (connect* {:uri "unix:///var/run/docker.sock"})))))

  (testing "connection with usupported protocol"
    (is (thrown? IllegalArgumentException
                 (connect* {:uri "http://this-does-not-work"}))))

  (testing "connection with set timeouts"
    (let [{:keys [client]} (connect* {:uri      "unix:///var/run/docker.sock"
                                      :timeouts {:connect-timeout 10
                                                 :read-timeout    2000
                                                 :write-timeout   3000}})]
      (is (and (= 10 (.connectTimeoutMillis client))
               (= 2000 (.readTimeoutMillis client))
               (= 3000 (.writeTimeoutMillis client))
               (= 0 (.callTimeoutMillis client)))))))

(deftest unix-sockets
  (testing "creating a unix socket client builder"
    (let [conn (unix-socket-client-builder "unix:///var/run/docker.sock")]
      (is (and (contains? conn :socket)
               (contains? conn :builder)
               (instance? java.net.Socket (:socket conn))
               (instance? OkHttpClient$Builder (:builder conn)))))))

(deftest OkHttp-helpers
  (testing "changing an InputStream to a RequestBody"
    (is (instance? RequestBody (stream->req-body (-> "README.md"
                                                     io/file
                                                     io/input-stream)))))

  (testing "path param interpolation"
    (is (= "/containers/a-id/of/{not-this}/path/at/a-id"
           (interpolate-path "/containers/{id}/of/{not-this}/path/at/a-id"
                             {:id "a-id"})))))

(deftest building-requests
  (testing "build a request with header and query params"
    (let [url-builder (add-params (.newBuilder (HttpUrl/parse "http://aurl"))
                                  {:all true}
                                  :query)
          builder     (-> (Request$Builder.)
                          (.url (.build ^HttpUrl$Builder url-builder))
                          (add-params {:X-Header1 "header"} :header))]
      (is (instance? Request (.build builder)))))

  (testing "build a get request without body"
    (let [req (build-request {:method :get
                              :url    "http://aurl"})]
      (is (= "GET" (.method req)))))

  (testing "build a head request without body"
    (let [req (build-request {:method :head
                              :url    "http://aurl"})]
      (is (= "HEAD" (.method req)))))

  (testing "build a delete request without body"
    (let [req (build-request {:method :delete
                              :url    "http://aurl"})]
      (is (= "DELETE" (.method req)))))

  (testing "build a get request with path params"
    (let [req (build-request {:url  "http://aurl/{a-id}/this"
                              :path {:a-id "a-id"}})]
      (is (= "GET" (.method req)))
      (is (= "http://aurl/a-id/this" (-> req .url str)))))

  (testing "build a request with an unsupported method"
    (is (thrown? IllegalArgumentException
                 (build-request {:method :crap
                                 :url    "http://aurl"}))))

  (testing "build a post request without body"
    (let [req (build-request {:method :post
                              :url    "http://aurl"})]
      (is (= "POST" (.method req)))))

  (testing "build a put request without body"
    (let [req (build-request {:method :put
                              :url    "http://aurl"})]
      (is (= "PUT" (.method req)))))

  (testing "build a post request with a map body"
    (let [req       (build-request {:method :post
                                    :url    "http://aurl"
                                    :body   {:key "value"}})]
      (is (= "application/json; charset=utf-8"
             (-> req .body .contentType str)))))

  (testing "build a post request with a stream body"
    (let [req       (build-request {:method :post
                                    :url    "http://aurl"
                                    :body   (-> "README.md"
                                                io/file
                                                io/input-stream)})]
      (is (= "application/octet-stream"
             (-> req .body .contentType str)))))

  (testing "build a request with query and header params"
    (let [req (build-request {:url    "https://aurl"
                              :query  {:q1 "v1" :q2 "v2"}
                              :header {:h1 "hv1" :h2 "hv2"}})]
      (is (= #{"h1" "h2"}
             (-> req .headers .names)))
      (is (= #{"q1" "q2"}
             (-> req .url .queryParameterNames))))))

(deftest fetching-stuff
  (testing "normal response"
    (is (= "OK" (fetch {:conn (connect* {:uri "unix:///var/run/docker.sock"})
                        :url  "/_ping"}))))

  (testing "streaming response"
    (is (instance? InputStream
                   (fetch {:conn (connect* {:uri "unix:///var/run/docker.sock"})
                           :url  "/_ping"
                           :as   :stream}))))

  (testing "exposing socket"
    (let [socket (fetch {:conn (connect* {:uri "unix:///var/run/docker.sock"})
                         :url  "/_ping"
                         :as   :socket})]
      (is (instance? java.net.Socket socket))
      (.close socket)))

  (testing "wrong request throws"
    (is (thrown? RuntimeException
                 (fetch {:conn             (connect* {:uri "unix:///var/run/docker.sock"})
                         :url              "/ping"
                         :throw-exception? true})))))
