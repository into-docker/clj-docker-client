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
  (:require [byte-streams :as bs]
            [clj-docker-client.utils :as u])
  (:import (java.nio.file Paths)
           (java.io File)
           (com.spotify.docker.client DefaultDockerClient
                                      DockerClient
                                      DockerClient$ListImagesParam
                                      DockerClient$BuildParam
                                      DockerClient$ListContainersParam
                                      DockerClient$LogsParam
                                      LogMessage
                                      DockerClient$ListNetworksParam)
           (com.spotify.docker.client.messages ContainerCreation
                                               RegistryAuth
                                               NetworkConfig)
           (org.apache.commons.compress.archivers.tar TarArchiveInputStream)))

;; TODO: Add more connection options
(defn connect
  "Connects to the local/remote Docker daemon with default settings.

  Returns the connection.

  Supply the URI as a string with protocol for a remote connection."
  ([]
   (.build (DefaultDockerClient/fromEnv)))
  ([^String uri]
   (-> (DefaultDockerClient/builder)
       (.uri uri)
       (.build))))

(defn disconnect
  "Closes the connection to the Docker server."
  [^DockerClient connection]
  (.close connection))

(defn ping
  "Healthiness check for the connection to the Docker server.

  Returns OK if everything is fine."
  [^DockerClient connection]
  (.ping connection))

(defn register
  "Builds login info for a Docker registry."
  [^String username ^String password]
  (-> (RegistryAuth/builder)
      (.username username)
      (.password password)
      (.build)))

(defn info
  "Fetches system wide info about the connected Docker server."
  [^DockerClient connection]
  (u/spotify-obj->Map (.info connection)))

;; Images

(defn pull
  "Pulls an image by *name*.

  The *name* is represented by <repo>:<tag>."
  [^DockerClient connection ^String name]
  (do (.pull connection name)
      name))

(defn build
  "Builds an image from a provided directory, repo and optional tag.

  Returns the id of the built image.

  Assumes a Dockerfile to be present in that directory."
  ([^DockerClient connection ^String path ^String repo]
   (build connection path repo "latest"))
  ([^DockerClient connection ^String path ^String repo ^String tag]
   (let [build-path (Paths/get path (into-array String []))]
     (.build
       connection
       build-path
       (format "%s:%s" repo tag)
       (into-array DockerClient$BuildParam [])))))

(defn push
  "Pushes an image by *name*.

  Returns the name of the pushed image.

  The *name* is represented by <repo>:<tag>."
  [^DockerClient connection ^String name ^RegistryAuth auth]
  (do (.push connection name auth)
      name))

(defn image-rm
  "Deletes an image by *name* or id.

  Returns the name of the removed image.

  If forced? is set removal is forced.
  If no-prune? is set untagged parents aren't removed.

  The *name* is represented by <repo>:<tag>."
  ([^DockerClient connection ^String name]
   (image-rm connection name false false))
  ([^DockerClient connection ^String name force? no-prune?]
   (do (.removeImage connection name force? no-prune?)
       name)))

(defn image-ls
  "Lists all available images."
  [^DockerClient connection]
  (->> (.listImages
         connection
         (into-array DockerClient$ListImagesParam
                     [(DockerClient$ListImagesParam/allImages)]))
       (mapv u/spotify-obj->Map)))

(defn commit-container
  "Creates an image from the changes of a container by name or id.

  Takes the repo, tag of the image and the new entry point command.
  Returns the id of the new image."
  [^DockerClient connection ^String id ^String repo ^String tag ^String command]
  (-> connection
      (.commitContainer id
                        repo
                        tag
                        (u/config-of (-> connection
                                         (.inspectContainer id)
                                         (.config)
                                         (.image))
                                     (u/sh-tokenize! command))
                        nil
                        nil)
      (.id)
      (u/format-id)))

;; Containers

(defn create
  "Creates a container.

  Takes the image, entry point command, env vars and host->container port mapping.

  Returns the id of the created container."
  ([connection image]
   (create connection image "" {} {} nil nil))
  ([connection image cmd]
   (create connection image cmd {} {} nil nil))
  ([connection image cmd env-vars]
   (create connection image cmd env-vars {} nil nil))
  ([connection image cmd env-vars exposed-ports]
   (create connection image cmd env-vars exposed-ports nil nil))
  ([connection image cmd env-vars exposed-ports working-dir]
   (create connection image cmd env-vars exposed-ports working-dir nil))
  ([^DockerClient connection image cmd env-vars exposed-ports working-dir user]
   (let [config   (u/config-of image
                               (u/sh-tokenize! cmd)
                               (u/format-env-vars env-vars)
                               exposed-ports
                               working-dir
                               user)
         creation ^ContainerCreation (.createContainer connection config)]
     (u/format-id (.id creation)))))

(defn ps
  "Lists all containers.

  Lists all running containers by default, all can be listed by passing a true param to *all?*"
  ([^DockerClient connection] (ps connection false))
  ([^DockerClient connection all?]
   (->> (.listContainers
          connection
          (into-array DockerClient$ListContainersParam
                      [(DockerClient$ListContainersParam/allContainers all?)]))
        (mapv u/spotify-obj->Map))))

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
  "Returns a lazy seq of logs split by lines from a container by name or id."
  [^DockerClient connection name]
  (->> (.logs connection
              name
              (into-array DockerClient$LogsParam
                          [(DockerClient$LogsParam/stdout)
                           (DockerClient$LogsParam/stderr)]))
       (iterator-seq)
       (map #(.content ^LogMessage %))
       (bs/to-input-stream)
       (clojure.java.io/reader)
       (line-seq)))

(defn container-state
  "Returns the current state of a created container by name or id."
  [^DockerClient connection name]
  (-> connection
      (.inspectContainer name)
      (.state)
      (u/spotify-obj->Map)))

(defn wait-container
  "Waits for the exit of a container by id or name."
  [^DockerClient connection name]
  (.statusCode (.waitContainer connection name)))

(defn run
  "Runs a container with a specified image, command, env vars and host->container port mappings.

  Returns the container id.

  Runs synchronously by default, i.e. waits for the container exit.
  If detached? flag is true, executes asynchronously."
  ([^DockerClient connection image command env-vars exposed-ports]
   (run connection image command env-vars exposed-ports false))
  ([^DockerClient connection image command env-vars exposed-ports detached?]
   (let [id (->> (create connection image command env-vars exposed-ports)
                 (start connection))]
     (if (not detached?)
       (do (wait-container connection id)
           id)
       id))))

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

(defn cp
  "Copies the contents of the host-path to the container by id/name
  to the container-path inside the container"
  [^DockerClient connection ^String id ^String host-path ^String container-path]
  (do (.copyToContainer connection
                        (.toPath (File. host-path))
                        id
                        container-path)
      id))

(defn stream-path
  "Returns a tar stream of a given path in a container by name."
  [^DockerClient connection ^String id ^String path]
  (TarArchiveInputStream.
    (.archiveContainer connection id path)))

(defn inspect
  "Inspects a container by name or id."
  [^DockerClient connection ^String container]
  (u/spotify-obj->Map (.inspectContainer connection container)))

;; Networks

(defn network-create
  "Creates a new docker network with a unique name."
  ([^DockerClient connection name]
   (network-create connection name true true))
  ([^DockerClient connection name check-duplicate?]
   (network-create connection name check-duplicate? true))
  ([^DockerClient connection name check-duplicate? attachable?]
   (let [config (-> (NetworkConfig/builder)
                    (.checkDuplicate check-duplicate?)
                    (.attachable attachable?)
                    (.name name)
                    (.build))]
     (do (.createNetwork connection config)
         name))))

(defn network-rm
  "Removes a network by name."
  [^DockerClient connection name]
  (do (.removeNetwork connection name)
      name))

(defn network-ls
  "Lists all networks."
  [^DockerClient connection]
  (->> (.listNetworks connection
                      (into-array DockerClient$ListNetworksParam []))
       (map u/spotify-obj->Map)))

(defn network-connect
  "Connects a container to a network.

  Takes the name/id of the container and the name of the network."
  [^DockerClient connection ^String network ^String container]
  (do (.connectToNetwork connection container network)
      container))

(defn network-disconnect
  "Disconnects a container to a network.

  Takes the name/id of the container and the name of the network."
  [^DockerClient connection ^String network ^String container]
  (do (.disconnectFromNetwork connection container network)
      container))
