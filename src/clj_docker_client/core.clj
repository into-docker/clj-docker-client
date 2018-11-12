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
                                      DockerClient$BuildParam)
           (com.spotify.docker.client.messages Image)))

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

(defn format-image
  [^Image image]
  {:id        (u/format-id (.id image))
   :repo-tags (.repoTags image)
   :created   (.created image)
   :size      (.size image)})

(defn pull
  "Pulls an image by *name*.

  The *name* is represented by <repo>:<tag>."
  [^DockerClient connection ^String name]
  (.pull connection name))

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
         (into-array DockerClient$ListImagesParam [(DockerClient$ListImagesParam/allImages)]))
       (map format-image)))
