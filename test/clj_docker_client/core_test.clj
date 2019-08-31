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
            [clj-docker-client.core :refer :all]
            [clj-docker-client.utils :as u])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)
           (java.io File InputStream)
           (com.github.dockerjava.api DockerClient)))

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

(defn correct-id?
  [id]
  (let [sha-pattern #"\b[0-9a-f]{5,40}\b"]
    (not (nil? (re-matches sha-pattern id)))))

(defn correct-ip?
  [id]
  (let [ip-pattern #"^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$"]
    (not (nil? (re-matches ip-pattern id)))))

(deftest test-docker-system
  (with-open [conn (connect)]
    (testing "Test ping to a Docker server"
      (is (= "OK" (ping conn))))
    (testing "Docker system info"
      (is (some? (info conn))))))

(deftest test-images
  (with-open [conn (connect)]
    (testing "Pulling an image by name"
      (is (= img (pull conn img))))
    (testing "Building an image from a Dockerfile"
      (let [id (build conn (temp-docker-dir) "test")]
        (is (correct-id? id))))
    (testing "Removing an image"
      (let [id (build conn (temp-docker-dir) "test")]
        (is (correct-id? (image-rm conn id)))))
    (testing "Listing all images"
      (let [id (build conn (temp-docker-dir) "test")]
        (is (not (empty? (->> (image-ls conn)
                              (filter #(= (u/format-id (:Id %)) id))))))
        (image-rm conn id)))
    (testing "Container commit"
      (let [id     (create conn img "echo hello" {} {})
            img-id (commit-container conn id "test/test" "latest" "echo hi")]
        (is (correct-id? img-id))
        (is (not (empty? (->> (image-ls conn)
                              (filter #(= (u/format-id (:Id %))))))))
        (image-rm conn img-id)
        (rm conn id)))
    (image-rm conn img)))

(deftest test-containers
  (with-open [conn ^DockerClient (connect)]
    (pull conn img)
    (testing "Creating a container"
      (let [container-id (create conn img "echo hello" {:k "v"} {})]
        (is (correct-id? container-id))
        (rm conn container-id)))
    (testing "Creating a container with a map"
      (let [container-id (create {:connection    conn
                                  :image         img
                                  :command       "echo hello"
                                  :env           {:k "v"}
                                  :exposed-ports {}
                                  :network-mode  "host"})]
        (is (correct-id? container-id))
        (rm conn container-id)))
    (testing "Container lifecycle"
      (let [image "redis:alpine"
            _     (pull conn image)
            id    (create conn image "redis-server" {} {6379 6379})
            id    (start conn id)
            info  (first (filter #(= id (u/format-id (:Id %))) (ps conn true)))]
        (is (correct-id? id))
        (is (= "running" (:State info)))
        (is (= [{:PublicPort  6379
                 :PrivatePort 6379
                 :Type        "tcp"
                 :IP          "0.0.0.0"}] (:Ports info)))
        (kill conn id)
        (rm conn id)
        (image-rm conn image)))
    (testing "Listing the created container"
      (let [id   (create conn img "echo hello" {:k "v"} {})
            info (first (filter #(= id (u/format-id (:Id %))) (ps conn true)))]
        (is (= "echo hello" (:Command info)))
        (is (= "created" (:State info)))
        (is (= img (:Image info)))
        (rm conn id)))
    (testing "Logging from a container"
      (let [id     (create conn img "sh -c 'for i in `seq 1 5`; do echo $i; done'" {} {})
            _      (start conn id)
            _      (wait-container conn id)
            output (logs conn id)]
        (is (= ["1" "2" "3" "4" "5"] output))
        (rm conn id)))
    (testing "Live logging from a container"
      (let [id (create conn img "sh -c 'for i in `seq 1 5`; do echo $i; done'" {} {})
            _  (start conn id)
            _  (logs-live conn id #(is (contains? #{"1" "2" "3" "4" "5"} %)))]
        (rm conn id)))
    (testing "Stats from a container"
      (let [id     (create conn img "sh -c 'for i in `seq 1 5`; do echo $i; done'" {} {})
            _      (start conn id)
            _      (wait-container conn id)
            output (stats conn id)]
        (is (map? output))
        (rm conn id)))
    (testing "Creation with working dir and user"
      (let [id     (create conn img "sh -c 'pwd && whoami'" {} {} "/tmp" "root")
            _      (start conn id)
            _      (wait-container conn id)
            output (logs conn id)]
        (is (= ["/tmp" "root"] output)
            (rm conn id))))
    (testing "Fetching container state"
      (let [id    (create conn img "echo hello" {} {})
            _     (start conn id)
            _     (wait-container conn id)
            state (container-state conn id)]
        (is (= "exited" (:Status state)))
        (is (zero? (:ExitCode state)))
        (rm conn id)))
    (testing "Running from an image"
      (let [id (run conn img "echo hello" {} {})]
        (is (correct-id? id))
        (rm conn id)))
    (testing "Copying to a container"
      (let [id     (run conn img "echo hello" {} {})
            ret-id (cp conn id (System/getProperty "user.dir") "/tmp")]
        (is (= id ret-id))
        (rm conn id)))
    (testing "Returning a tar stream of a path"
      (let [id     (run conn img "touch /tmp/test.txt && echo 'hello' > /tmp/test.txt" {} {})
            stream (stream-path conn id "/tmp/test.txt")]
        (is (instance? InputStream stream))
        (rm conn id)))
    (testing "Inspecting a container"
      (let [container-id (create conn img "echo ok" {} {})
            _            (start conn container-id)
            inspect-res  (inspect conn container-id)
            ip           (-> inspect-res :NetworkSettings :IPAddress)
            _            (stop conn container-id)
            _            (rm conn container-id)]
        (is (not (nil? ip)))
        (is (correct-ip? ip))))
    (testing "Port binding with different IP"
      (let [image "redis:alpine"
            _     (pull conn image)
            id    (create conn image "redis-server" {} {"127.0.0.1:6379" "6379"
                                                        "6378"           "6379"})
            id    (start conn id)
            info  (first (filter #(= id (u/format-id (:Id %))) (ps conn true)))]
        (is (correct-id? id))
        (is (= "running" (:State info)))
        (is (= [{:IP          "0.0.0.0"
                 :PrivatePort 6379
                 :PublicPort  6378
                 :Type        "tcp"}
                {:IP          "127.0.0.1"
                 :PrivatePort 6379
                 :PublicPort  6379
                 :Type        "tcp"}] (:Ports info)))
        (kill conn id)
        (rm conn id)
        (image-rm conn image)))
    (testing "Removing a container"
      (let [id (create conn img "echo hello" {:k "v"} {})
            _  (rm conn id)]
        (is (empty? (filter #(= id (u/format-id (:Id %))) (ps conn true))))))
    (image-rm conn img)))

(deftest test-networks
  (with-open [conn ^DockerClient (connect)]
    (pull conn img)
    (testing "Creating a network"
      (is (= "sky-net" (network-create conn "sky-net"))))
    (testing "Listing networks"
      (is (not (empty? (->> (network-ls conn)
                            (filter #(= (:Name %) "sky-net")))))))
    (let [id (create conn img "echo hello" {} {})]
      (testing "Connecting a container"
        (is (correct-id? (network-connect conn "sky-net" id))))
      (testing "Disconnecting a container"
        (is (correct-id? (network-disconnect conn "sky-net" id))))
      (rm conn id))
    (testing "Removing a network"
      (is (= "sky-net" (network-rm conn "sky-net"))))
    (image-rm conn img)))
