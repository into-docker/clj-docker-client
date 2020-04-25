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

(ns clj-docker-client.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clj-docker-client.core :refer :all]))

(def latest-version "v1.40")

(deftest docker-connect-map
  (testing "deprecated connect returns map"
    (is (map? (connect {:uri "unix:///var/run/docker.sock"})))))

(deftest fetch-categories
  (testing "listing all available categories in latest version"
    (is (coll? (categories))))

  (testing "listing all available categories in fixed version"
    (is (coll? (categories latest-version)))))

(deftest client-construction
  (testing "making a container client of the latest version"
    (is (map? (client {:category :containers
                       :conn     (connect {:uri "unix:///var/run/docker.sock"})}))))

  (testing "making a container client of a specific version"
    (is (map? (client {:category    :containers
                       :conn        (connect {:uri "unix:///var/run/docker.sock"})
                       :api-version latest-version})))))

(deftest fetching-ops
  (testing "fetching ops for the latest container client"
    (let [client (client {:category :containers
                          :conn     (connect {:uri "unix:///var/run/docker.sock"})})
          result (ops client)]
      (is (every? keyword? result)))))

(deftest fetching-docs
  (testing "fetching docs for the latest ContainerList op"
    (let [client (client {:category :containers
                          :conn (connect {:uri "unix:///var/run/docker.sock"})})
          result (doc client :ContainerList)]
      (is (and (contains? result :doc)
               (contains? result :params))))))

(defn pull-image
  [name]
  (let [images (client {:category :images
                        :conn     (connect {:uri "unix:///var/run/docker.sock"})})]
    (invoke images {:op     :ImageCreate
                    :params {:fromImage name}})))

(defn delete-image
  [name]
  (let [images (client {:category :images
                        :conn     (connect {:uri "unix:///var/run/docker.sock"})})]
    (invoke images {:op     :ImageDelete
                    :params {:name  name
                             :force true}})))

(defn delete-container
  [id]
  (let [containers (client {:category :containers
                            :conn     (connect {:uri "unix:///var/run/docker.sock"})})]
    (invoke containers {:op     :ContainerDelete
                        :params {:id    id
                                 :force true}})))

(deftest invoke-ops
  (let [conn (connect {:uri "unix:///var/run/docker.sock"})]
    (testing "invoke an op with no params"
      (let [pinger (client {:category :_ping
                            :conn     conn})]
        (is (= "OK"
               (invoke pinger {:op :SystemPing})))))

    (testing "invoke with exception"
      (let [containers (client {:category :containers
                                :conn     conn})]
        (is (thrown? RuntimeException
                     (invoke containers {:op               :ContainerInspect
                                         :params           {:id "nein"}
                                         :throw-exception? true})))))

    (testing "invoke an op with non-stream params"
      (let [containers (client {:category :containers
                                :conn     conn})
            image      "busybox:musl"
            cname      "conny"]
        (pull-image image)
        (is (contains? (invoke containers {:op     :ContainerCreate
                                           :params {:name cname
                                                    :body {:Image image
                                                           :Cmd   "ls"}}})
                       :Id))
        (delete-container cname)
        (delete-image image)))

    (testing "invoke an op with stream params"
      (let [containers (client {:category :containers
                                :conn     conn})
            image      "busybox:musl"
            cname      "conny"]
        (pull-image image)
        (invoke containers {:op     :ContainerCreate
                            :params {:name cname
                                     :body {:Image image
                                            :Cmd   "ls"}}})
        (is (= ""
               (invoke containers {:op     :PutContainerArchive
                                   :params {:id          cname
                                            :path        "/root"
                                            :inputStream (-> "test/clj_docker_client/test.tar.gz"
                                                             io/file
                                                             io/input-stream)}})))
        (delete-container cname)
        (delete-image image)))))
