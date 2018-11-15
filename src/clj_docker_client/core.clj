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

(ns clj-docker-client.core
  (:require [clj-docker-client.utils :as u])
  (:import (java.nio.file Paths)
           (com.spotify.docker.client DefaultDockerClient
                                      DockerClient
                                      DockerClient$ListImagesParam
                                      DockerClient$BuildParam
                                      DockerClient$ListContainersParam
                                      DockerClient$LogsParam)
           (com.spotify.docker.client.messages Image
                                               Container
                                               Container$PortMapping
                                               ContainerConfig
                                               HostConfig
                                               ContainerCreation)
           (java.util List)))

(defn connect
  "Connects to the local Docker daemon with default settings.

  Returns the connection."
  []
  (.build (DefaultDockerClient/fromEnv)))

(defn ping
  "Healthiness check for the connection to the Docker server.

  Returns OK if everything is fine."
  [^DockerClient connection]
  (.ping connection))

;; Images

(defn- format-image
  [^Image image]
  {:id        (u/format-id (.id image))
   :repo-tags (.repoTags image)
   :created   (.created image)
   :size      (.size image)})

(defn pull
  "Pulls an image by *name*.

  The *name* is represented by <repo>:<tag>."
  [^DockerClient connection ^String name]
  (do (.pull connection name)
      name))

(defn build
  "Builds an image from a provided directory.

  Assumes a Dockerfile to be present in that directory."
  [^DockerClient connection ^String path]
  (let [build-path (Paths/get path (into-array String []))]
    (.build
      connection
      build-path
      (into-array DockerClient$BuildParam []))))

(defn push
  "Pushes an image by *name* or id.

  The *name* is represented by <repo>:<tag>."
  [^DockerClient connection ^String name]
  (.push connection name))

(defn image-rm
  "Deletes an image by *name* or id.

  The *name* is represented by <repo>:<tag>."
  ([^DockerClient connection ^String name]
   (.removeImage connection name))
  ([^DockerClient connection ^String name force? no-prune?]
   (.removeImage connection name force? no-prune?)))

(defn image-ls
  "Lists all available images."
  [^DockerClient connection]
  (->> (.listImages
         connection
         (into-array DockerClient$ListImagesParam
                     [(DockerClient$ListImagesParam/allImages)]))
       (mapv format-image)))

;; Containers

(defn- format-port-mapping
  [^Container$PortMapping port-mapping]
  {:public  (.publicPort port-mapping)
   :private (.privatePort port-mapping)
   :type    (keyword (.type port-mapping))
   :ip      (.ip port-mapping)})

(defn- format-container-ps
  [^Container container]
  {:id      (u/format-id (.id container))
   :names   (mapv #(clojure.string/replace % #"/" "")
                  (.names container))
   :image   (.image container)
   :command (.command container)
   :state   (keyword (.state container))
   :status  (.status container)
   :ports   (mapv format-port-mapping (.ports container))})

(defn- config-of
  [^String image ^List cmd ^List env-vars]
  (-> (ContainerConfig/builder)
      (.hostConfig (.build (HostConfig/builder)))
      (.env env-vars)
      (.image image)
      (.cmd cmd)
      (.build)))

(defn- format-env-vars
  [env-vars]
  (map #(format "%s=%s" (name (first %)) (last %))
       env-vars))

(defn create
  "Creates a container.

  Takes the image, entry point command and environment vars as a map
  and returns the id of the created container."
  [^DockerClient connection image cmd env-vars]
  (let [config   (config-of image
                            (u/sh-tokenize! cmd)
                            (format-env-vars env-vars))
        creation ^ContainerCreation (.createContainer connection config)]
    (u/format-id (.id creation))))

(defn ps
  "Lists all containers.

  Lists all running containers by default, all can be listed by passing a true param to *all?*"
  ([^DockerClient connection] (ps connection false))
  ([^DockerClient connection all?]
   (->> (.listContainers
          connection
          (into-array DockerClient$ListContainersParam
                      [(DockerClient$ListContainersParam/allContainers all?)]))
        (mapv format-container-ps))))

(defn start
  "Starts a created container asynchronously by name or id.

  Returns the name or id."
  [^DockerClient connection name]
  (do (.startContainer connection name)
      name))

(defn stop
  "Stops a container with SIGTERM by name or id.

  Waits for timeout secs or value of timeout before killing.
  Returns the name or id."
  ([^DockerClient connection name] (stop connection name 30))
  ([^DockerClient connection name timeout]
   (do (.stopContainer connection name timeout)
       name)))

(defn kill
  "Kills container with SIGKILL by name or id.

  Assumes the container to be running.
  Returns the name or id."
  [^DockerClient connection name]
  (do (.killContainer connection name)
      name))

(defn restart
  "Restarts a container with by name or id.

  Waits for timeout secs or value of timeout before killing.
  Returns the name or id."
  ([^DockerClient connection name] (restart connection name 30))
  ([^DockerClient connection name timeout]
   (do (.restartContainer connection name timeout)
       name)))

(defn pause
  "Pauses a container by name or id.

  Returns the name or id."
  [^DockerClient connection name]
  (do (.pauseContainer connection name)
      name))

(defn un-pause
  "Un-pauses a container by name or id.

  Returns the name or id."
  [^DockerClient connection name]
  (do (.unpauseContainer connection name)
      name))

(defn logs
  "Returns a line-seq of logs from a container by name or id."
  [^DockerClient connection name]
  (-> (.logs connection
             name
             (into-array DockerClient$LogsParam
                         [(DockerClient$LogsParam/stdout)
                          (DockerClient$LogsParam/stderr)]))
      (.readFully)
      (clojure.string/split-lines)))

(defn rm
  "Removes a container by name or id.

  Pass true to force kill a running container and remove it.
  Returns the name or id."
  ([^DockerClient connection name] (rm connection name false))
  ([^DockerClient connection name force?]
   (do (when force?
         (kill connection name))
       (.removeContainer connection name)
       name)))
