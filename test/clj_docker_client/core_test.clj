;   This file is part of clj-docker-client.
;
;   clj-docker-client is free software: you can redistribute it and/or modify
;   it under the terms of the GNU General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   clj-docker-client is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU General Public License for more details.
;
;   You should have received a copy of the GNU General Public License
;   along with clj-docker-client. If not, see <http://www.gnu.org/licenses/>.

(ns clj-docker-client.core-test
  (:require [clojure.test :refer :all]
            [clj-docker-client.core :refer :all])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (java.io File)
           (com.spotify.docker.client.messages RemovedImage)
           (com.spotify.docker.client DockerClient)))

(def sha-pattern #"\b[0-9a-f]{5,40}\b")

(def img "busybox:musl")

(defn temp-docker-dir
  []
  (let [content     (format "FROM %s\nRUN touch test.txt\n" img)
        tmp-dir     (str
                      (.toAbsolutePath
                        (Files/createTempDirectory
                          "docker-test"
                          (into-array FileAttribute []))))
        docker-file (str tmp-dir
                         File/separator
                         "Dockerfile")
        _           (spit docker-file content)]
    tmp-dir))

(deftest test-connection
  (with-open [conn (connect)]
    (testing "Test ping to a Docker server"
      (is (= "OK" (ping conn))))))

(deftest test-images
  (with-open [conn (connect)]
    (testing "Pulling an image by name"
      (is (= img (pull conn img))))
    (testing "Building an image from a Dockerfile"
      (let [id (build conn (temp-docker-dir) "test")]
        (is (not (nil? (re-matches sha-pattern id))))
        (image-rm conn id)))
    (testing "Removing an image"
      (let [id (build conn (temp-docker-dir) "test")]
        (is (instance? RemovedImage (first (image-rm conn id))))))
    (testing "Listing all images"
      (let [id (build conn (temp-docker-dir) "test")]
        (is (not (empty? (->> (image-ls conn)
                              (filter #(= (:id %) id))))))
        (image-rm conn id)
        (image-rm conn img)))))

(deftest test-containers
  (with-open [conn ^DockerClient (connect)]
    (pull conn img)
    (testing "Creating a container"
      (let [container-id (create conn img "echo hello" {:k "v"})]
        (is (not (nil? (re-matches sha-pattern container-id))))
        (rm conn container-id)))
    (testing "Container lifecycle"
      (let [image "redis:alpine"
            _     (pull conn image)
            id    (create conn image "redis-server" {})
            id    (start conn id)
            info  (first (filter #(= id (:id %)) (ps conn true)))]
        (is (not (nil? (re-matches sha-pattern id))))
        (is (= :running (:state info)))
        (kill conn id)
        (rm conn id)
        (image-rm conn image)))
    (testing "Listing the created container"
      (let [id   (create conn img "echo hello" {:k "v"})
            info (first (filter #(= id (:id %)) (ps conn true)))]
        (is (= "echo hello" (:command info)))
        (is (= :created (:state info)))
        (is (= img (:image info)))
        (rm conn id)))
    (testing "Logging from a container"
      (let [id     (create conn img "sh -c 'for i in `seq 1 5`; do echo $i; done'" {})
            _      (start conn id)
            _      (.waitContainer conn id)
            output (logs conn id)]
        (is (= ["1" "2" "3" "4" "5"] output))
        (rm conn id)))
    (testing "Fetching container state"
      (let [id    (create conn img "echo hello" {})
            _     (start conn id)
            _     (.waitContainer conn id)
            state (container-state conn id)]
        (is (= :exited (:status state)))
        (is (zero? (:exit-code state)))
        (rm conn id)))
    (testing "Removing a container"
      (let [id (create conn img "echo hello" {:k "v"})
            _  (rm conn id)]
        (is (empty? (filter #(= id (:id %)) (ps conn true))))))
    (image-rm conn img)))
