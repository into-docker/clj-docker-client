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
  (:require [clj-docker-client.utils :as u]
            [clj-docker-client.formatters :as f])
  (:import (java.nio.file Paths)
           (com.spotify.docker.client DefaultDockerClient
                                      DockerClient
                                      DockerClient$ListImagesParam
                                      DockerClient$BuildParam
                                      DockerClient$ListContainersParam
                                      DockerClient$LogsParam)
           (com.spotify.docker.client.messages ContainerConfig
                                               HostConfig
                                               ContainerCreation
                                               RegistryAuth)
           (java.util List)))

(defn connect
  "Connects to the local Docker daemon with default settings.

  Returns the connection."
  []
  (.build (DefaultDockerClient/fromEnv)))

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
  (let [data (.info connection)]
    {:arch                 (.architecture data)
     :cluster-store        (.clusterStore data)
     :cgroup-driver        (.cgroupDriver data)
     :containers           (.containers data)
     :running-containers   (.containersRunning data)
     :paused-containers    (.containersPaused data)
     :cpu-cfs-period?      (.cpuCfsPeriod data)
     :cpu-cfs-quota?       (.cpuCfsQuota data)
     :debug?               (.debug data)
     :docker-root-dir      (.dockerRootDir data)
     :storage-driver       (.storageDriver data)
     :driver-status        (.driverStatus data)
     :experimental-build?  (.experimentalBuild data)
     :http-proxy           (.httpProxy data)
     :https-proxy          (.httpsProxy data)
     :id                   (.id data)
     :ipv4-forwarding?     (.ipv4Forwarding data)
     :images               (.images data)
     :index-server-address (.indexServerAddress data)
     :init-path            (.initPath data)
     :init-sha1            (.initSha1 data)
     :kernel-memory        (.kernelMemory data)
     :kernel-version       (.kernelVersion data)
     :labels               (.labels data)
     :mem-total            (.memTotal data)
     :mem-limit            (.memoryLimit data)
     :cpus                 (.cpus data)
     :even-listeners       (.eventsListener data)
     :file-descriptors     (.fileDescriptors data)
     :go-routines          (.goroutines data)
     :name                 (.name data)
     :no-proxy             (.noProxy data)
     :oom-kill-disabled?   (.oomKillDisable data)
     :operating-system     (.operatingSystem data)
     :os-type              (.osType data)
     :plugins              {:networks (.networks (.plugins data))
                            :volumes  (.volumes (.plugins data))}
     :registry-config      (f/format-registry-config (.registryConfig data))
     :server-version       (.serverVersion data)
     :swap-limit?          (.swapLimit data)
     :swarm-info           (f/format-swarm-info (.swarm data))
     :system-status        (.systemStatus data)
     :system-time          (.systemTime data)}))

(defn- config-of
  [^String image ^List cmd ^List env-vars]
  (-> (ContainerConfig/builder)
      (.hostConfig (.build (HostConfig/builder)))
      (.env env-vars)
      (.image image)
      (.cmd cmd)
      (.build)))

;; Images

(defn pull
  "Pulls an image by *name*.

  The *name* is represented by <repo>:<tag>."
  [^DockerClient connection ^String name]
  (do (.pull connection name)
      name))

(defn build
  "Builds an image from a provided directory, repo and optional tag.

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

  The *name* is represented by <repo>:<tag>."
  [^DockerClient connection ^String name ^RegistryAuth auth]
  (do (.push connection name auth)
      name))

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
       (mapv f/format-image)))

(defn commit-container
  "Creates an image from the changes of a container by name or id.

  Takes the repo, tag of the image and the new entry point command.
  Returns the id of the new image."
  [^DockerClient connection ^String id ^String repo ^String tag ^String command]
  (-> connection
      (.commitContainer id
                        repo
                        tag
                        (config-of (-> connection
                                       (.inspectContainer id)
                                       (.config)
                                       (.image))
                                   (u/sh-tokenize! command)
                                   [])
                        nil
                        nil)
      (.id)
      (u/format-id)))

;; Containers

(defn create
  "Creates a container.

  Takes the image, entry point command and environment vars as a map
  and returns the id of the created container."
  [^DockerClient connection image cmd env-vars]
  (let [config   (config-of image
                            (u/sh-tokenize! cmd)
                            (f/format-env-vars env-vars))
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
        (mapv f/format-container-ps))))

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

(defn container-state
  "Returns the current state of a created container by name or id."
  [^DockerClient connection name]
  (-> connection
      (.inspectContainer name)
      (.state)
      (f/format-state)))

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
