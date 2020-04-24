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
  (:require [clj-docker-client.requests :as requests]
            [clojure.test :refer :all])
  (:import (java.io InputStream)
           (java.net Socket)
           (java.util UUID)))

;; ## Helper

(defn- -connect
  "Connect for test purposes, with reasonable timeouts."
  []
  (requests/connect* {:uri "unix:///var/run/docker.sock"
                      :timeouts {:connect-timeout 10
                                 :read-timeout    2000
                                 :write-timeout   2000}}))

(defn- -fetch
  "Create a fresh connection and fetch."
  [request]
  (-> request
      (assoc :conn (-connect))
      (requests/fetch)))

;; ## Tests

(deftest ^:docker t-connect*
  (testing "connection to the docker socket"
    (let [conn (is (requests/connect* {:uri "unix:///var/run/docker.sock"}))]
      (is (= "OK" (requests/fetch {:conn conn, :url "/_ping"})))))
  (testing "connection to the docker socket with timeouts"
    (let [conn (is (-connect))]
      (is (= "OK" (requests/fetch {:conn conn, :url "/_ping"})))))
  (testing "connection with unsupported protocol"
    (is (thrown? IllegalArgumentException
                (requests/connect* {:uri "ssl://unknown-protocol"}) ))))

(deftest ^:docker t-fetch
  (testing "fetch as string"
    (is (= "OK" (-fetch {:url "/_ping"}))))

  (testing "fetch as stream"
    (with-open [^InputStream in (-fetch {:url "/_ping", :as :stream})]
      (is (= "OK" (slurp in)))))

  (testing "fetch as socket"
    (with-open [^Socket in (-fetch {:url "/_ping", :as :socket})]
      ;; Response has already been read at this point by the underlying
      ;; client so it won't be available on the input stream.
      (is (.getInputStream in))))

  (testing "fetch with method"
    (doseq [method [:head :get :post :put :delete]]
      (testing (str "- " (.toUpperCase (name method)))
        (is (-fetch {:method method, :url "/_ping"})))))

  (testing "fetch with headers"
    (is (= "OK"
           (-fetch {:url "/_ping", :header {:x-test-header "testing"}}))))

  (testing "fetch with query"
    (is (= "[]\n"
           (-fetch {:url "/containers/json"
                    :query {:filters (format "{\"label\":{\"%s\":true}}"
                                             (UUID/randomUUID))}}))))

  (testing "parameter interpolation"
    (is (= "OK"
           (-fetch {:url  "/{endpoint}"
                    :path {:endpoint "_ping"}}))))

  (testing "does not throw by default"
    (is (= "{\"message\":\"page not found\"}\n"
           (-fetch {:url "/unknown"}))))

  (testing "throws on error if `throw-exception?` is set"
    (is (thrown? RuntimeException
                 (-fetch {:url "/unknown", :throw-exception? true}))))

  (testing "throws when given invalid method"
    (is (thrown? IllegalArgumentException
                 (-fetch {:method :unknown, :url "/_ping"})))))
