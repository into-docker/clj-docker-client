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
  (:require [clj-docker-client.utils :as u])
  (:import (java.io File)
           (java.util.concurrent TimeUnit)
           (com.github.dockerjava.core DefaultDockerClientConfig
                                       DockerClientBuilder)
           (com.github.dockerjava.api DockerClient)
           (com.github.dockerjava.jaxrs JerseyDockerCmdExecFactory)
           (com.github.dockerjava.core.command PullImageResultCallback
                                               BuildImageResultCallback
                                               PushImageResultCallback
                                               LogContainerResultCallback
                                               WaitContainerResultCallback)
           (com.github.dockerjava.api.model Frame
                                            Statistics)
           (com.github.dockerjava.core.async ResultCallbackTemplate)
           (com.github.dockerjava.api.command CreateContainerCmd)))

;; TODO: Add more connection options
(defn connect
  "Connects to the local/remote Docker daemon with default settings.

  Returns the connection.

  Supply the URI as a string with protocol for a remote connection."
  ([]
   (connect nil))
  ([^String uri]
   (let [config       (DefaultDockerClientConfig/createDefaultConfigBuilder)
         config       (if (nil? uri)
                        config
                        (.withDockerHost config uri))
         exec-factory (-> (JerseyDockerCmdExecFactory.)
                          (.withConnectTimeout (int 1000))
                          (.withMaxTotalConnections (int 100))
                          (.withMaxPerRouteConnections (int 10)))]
     (-> (DockerClientBuilder/getInstance config)
         (.withDockerCmdExecFactory exec-factory)
         (.build)))))

(defn disconnect
  "Closes the connection to the Docker server."
  [^DockerClient connection]
  (.close connection))

(defn ping
  "Healthiness check for the connection to the Docker server.

  Returns OK if everything is fine."
  [^DockerClient connection]
  (when (nil? (.exec (.pingCmd connection)))
    "OK"))

(defn info
  "Fetches system wide info about the connected Docker server."
  [^DockerClient connection]
  (u/->Map (.exec (.infoCmd connection))))

;; Images

(defn pull
  "Pulls an image by *name*.

  The *name* is represented by <repo>:<tag>."
  [^DockerClient connection ^String name]
  (-> (.pullImageCmd connection name)
      (.exec (PullImageResultCallback.))
      (.awaitCompletion))
  name)

(defn build
  "Builds an image from a provided directory, repo and optional tag.

  *WORKS ONLY IF DAEMON IS RUNNING LOCALLY.*

  Returns the id of the built image.

  Assumes a Dockerfile to be present in that directory."
  ([^DockerClient connection ^String path ^String repo]
   (build connection path repo "latest"))
  ([^DockerClient connection ^String path ^String repo ^String tag]
   (-> (.buildImageCmd connection (File. path))
       (.withTags #{(format "%s:%s" repo tag)})
       (.exec (BuildImageResultCallback.))
       (.awaitImageId))))

(defn push
  "Pushes an image by *name*.

  Returns the name of the pushed image.

  The *name* is represented by <repo>:<tag>."
  [^DockerClient connection ^String name]
  (-> (.pushImageCmd connection name)
      (.exec (PushImageResultCallback.))
      (.awaitCompletion))
  name)

(defn image-rm
  "Deletes an image by *name* or id.

  Returns the name of the removed image.

  If forced? is set removal is forced.
  If no-prune? is set untagged parents aren't removed.

  The *name* is represented by <repo>:<tag>."
  ([^DockerClient connection ^String name]
   (image-rm connection name false false))
  ([^DockerClient connection ^String name force? no-prune?]
   (-> (.removeImageCmd connection name)
       (.withForce force?)
       (.withNoPrune no-prune?)
       (.exec))
   name))

(defn image-ls
  "Lists all available images."
  [^DockerClient connection]
  (->> (.listImagesCmd connection)
       (.exec)
       (mapv u/->Map)))

(defn commit-container
  "Creates an image from the changes of a container by name or id.

  Takes the repo, tag of the image and the new entry point command.
  Returns the id of the new image."
  [^DockerClient connection ^String id ^String repo ^String tag ^String command]
  (-> (.commitCmd connection id)
      (.withRepository repo)
      (.withTag tag)
      (.withCmd (into-array String (u/sh-tokenize! command)))
      (.exec)
      (u/format-id)))

;; Containers

(defn create
  "Creates a container.

  Takes the following as params:
  - image
  - entry point command
  - env vars, optional
  - host->container port mapping, optional
  - working dir, optional
  - user, optional

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
   (let [creation ^CreateContainerCmd (-> (.createContainerCmd connection image)
                                          (.withCmd (into-array String (u/sh-tokenize! cmd)))
                                          (.withEnv (u/format-env-vars env-vars))
                                          (.withHostConfig (u/port-configs-from exposed-ports)))
         creation (if (some? working-dir)
                    (.withWorkingDir creation working-dir)
                    creation)
         creation (if (some? user)
                    (.withUser creation user)
                    creation)]
     (-> creation
         (.exec)
         (u/->Map)
         (:Id)
         (u/format-id)))))

(defn ps
  "Lists all containers.

  Lists all running containers by default, all can be listed by passing a true param to *all?*"
  ([^DockerClient connection] (ps connection false))
  ([^DockerClient connection all?]
   (->> (.withShowAll (.listContainersCmd connection) all?)
        (.exec)
        (mapv u/->Map))))

(defn start
  "Starts a created container asynchronously by name or id.

  Returns the name or id."
  [^DockerClient connection name]
  (-> (.startContainerCmd connection name)
      (.exec))
  name)

(defn stop
  "Stops a container with SIGTERM by name or id.

  Waits for timeout secs or value of timeout before killing.
  Returns the name or id."
  ([^DockerClient connection name]
   (stop connection name 30))
  ([^DockerClient connection name timeout]
   (-> (.stopContainerCmd connection name)
       (.withTimeout (int timeout))
       (.exec))
   name))

(defn kill
  "Kills container with SIGKILL by name or id.

  Assumes the container to be running.
  Returns the name or id."
  [^DockerClient connection name]
  (-> (.killContainerCmd connection name)
      (.exec))
  name)

(defn restart
  "Restarts a container with by name or id.

  Waits for timeout secs or value of timeout before killing.
  Returns the name or id."
  ([^DockerClient connection name]
   (restart connection name 30))
  ([^DockerClient connection name timeout]
   (-> (.restartContainerCmd connection name)
       (.withtTimeout timeout)
       (.exec))
   name))

(defn pause
  "Pauses a container by name or id.

  Returns the name or id."
  [^DockerClient connection name]
  (-> (.pauseContainerCmd connection name)
      (.exec))
  name)

(defn un-pause
  "Un-pauses a container by name or id.

  Returns the name or id."
  [^DockerClient connection name]
  (-> (.unpauseContainerCmd connection name)
      (.exec))
  name)

(defn logs
  "Returns a lazy seq of logs split by lines from a container by name or id."
  [^DockerClient connection name]
  (let [log-acc  (atom [])
        callback (proxy [LogContainerResultCallback] []
                   (onNext [^Frame frame]
                     (swap! log-acc conj (String. (.getPayload frame)))))]
    (-> (.logContainerCmd connection name)
        (.withStdOut true)
        (.withStdErr true)
        (.exec callback)
        (.awaitCompletion 1 (TimeUnit/SECONDS)))
    (->> @log-acc
         (map clojure.string/trim))))

(defn logs-live
  "Live version of logs, takes a callback to be notified of Logs as the appear.

  Attaches to the container and blocks until the attached container is finished."
  [^DockerClient connection name callback]
  (let [log-callback (proxy [LogContainerResultCallback] []
                       (onNext [^Frame frame]
                         (callback (String. (.getPayload frame)))))]
    (-> (.logContainerCmd connection name)
        (.withStdOut true)
        (.withStdErr true)
        (.withFollowStream true)
        (.exec log-callback))
    (.awaitCompletion log-callback)))

(defn container-state
  "Returns the current state of a created container by name or id."
  [^DockerClient connection name]
  (-> (.inspectContainerCmd connection name)
      (.exec)
      (u/->Map)
      (:State)))

(defn wait-container
  "Waits for the exit of a container by id or name."
  [^DockerClient connection name]
  (-> (.waitContainerCmd connection name)
      (.exec (WaitContainerResultCallback.))
      (.awaitStatusCode)))

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
   (when force?
     (kill connection name))
   (-> (.removeContainerCmd connection name)
       (.exec))
   name))

(defn cp
  "Copies the host-path recursively to the container by id/name
  to the container-path inside the container"
  [^DockerClient connection ^String id ^String host-path ^String container-path]
  (-> (.copyArchiveToContainerCmd connection id)
      (.withHostResource host-path)
      (.withRemotePath container-path)
      (.exec))
  id)

(defn stream-path
  "Returns a input stream of a given path in a container by name.

  The stream points to a tar archive of the supplied path.

  Create a TarArchiveInputStream from this to process the archived
  files."
  [^DockerClient connection ^String id ^String path]
  (-> (.copyArchiveFromContainerCmd connection id path)
      (.exec)))

(defn inspect
  "Inspects a container by name or id."
  [^DockerClient connection ^String container]
  (-> (.inspectContainerCmd connection container)
      (.exec)
      (u/->Map)))

(defn stats
  "Returns the resource stats of a created container by name or id/name.

  Samples for 1 second by default. Pass sample-duration to change"
  ([^DockerClient connection name]
   (stats connection name 1))
  ([^DockerClient connection name sample-duration]
   (let [stats-acc (atom {})
         callback  (proxy [ResultCallbackTemplate] []
                     (onNext [^Statistics stats]
                       (swap! stats-acc merge (u/->Map stats))))]
     (-> (.statsCmd connection name)
         (.exec callback)
         (.awaitCompletion sample-duration (TimeUnit/SECONDS)))
     @stats-acc)))

(defn stats-live
  "Live version of stats, takes a callback to be notified of Stats as the appear.

  Attaches to the container and blocks until the attached container is finished."
  [^DockerClient connection name callback]
  (let [stats-callback (proxy [ResultCallbackTemplate] []
                         (onNext [^Statistics stats]
                           (callback (u/->Map stats))))]
    (-> (.statsCmd connection name)
        (.exec stats-callback))
    (.awaitCompletion stats-callback)))

;; Networks

(defn network-create
  "Creates a new docker network with a unique name."
  ([^DockerClient connection name]
   (network-create connection name true true))
  ([^DockerClient connection name check-duplicate?]
   (network-create connection name check-duplicate? true))
  ([^DockerClient connection name check-duplicate? attachable?]
   (-> (.createNetworkCmd connection)
       (.withName name)
       (.withCheckDuplicate check-duplicate?)
       (.withAttachable attachable?)
       (.exec))
   name))

(defn network-rm
  "Removes a network by name."
  [^DockerClient connection name]
  (-> (.removeNetworkCmd connection name)
      (.exec))
  name)

(defn network-ls
  "Lists all networks."
  [^DockerClient connection]
  (->> (.listNetworksCmd connection)
       (.exec)
       (map u/->Map)))

(defn network-connect
  "Connects a container to a network.

  Takes the name/id of the container and the name of the network."
  [^DockerClient connection ^String network ^String container]
  (-> (.connectToNetworkCmd connection)
      (.withContainerId container)
      (.withNetworkId network)
      (.exec))
  container)

(defn network-disconnect
  "Disconnects a container to a network.

  Takes the name/id of the container and the name of the network."
  [^DockerClient connection ^String network ^String container]
  (-> (.disconnectFromNetworkCmd connection)
      (.withContainerId container)
      (.withNetworkId network)
      (.exec))
  container)
