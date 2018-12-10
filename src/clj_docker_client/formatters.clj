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

(ns clj-docker-client.formatters
  (:require [clj-docker-client.utils :as u])
  (:import (com.spotify.docker.client.messages Info$IndexConfig
                                               Info$RegistryConfig
                                               Image
                                               Container$PortMapping
                                               Container
                                               ContainerState
                                               Info
                                               Network)
           (com.spotify.docker.client.messages.swarm SwarmInfo
                                                     SwarmCluster
                                                     SwarmSpec
                                                     RaftConfig
                                                     CaConfig
                                                     ExternalCa
                                                     Driver
                                                     RemoteManager)))

(defn format-image
  [^Image image]
  {:id        (u/format-id (.id image))
   :repo-tags (.repoTags image)
   :created   (.created image)
   :size      (.size image)})

(defn format-port-mapping
  [^Container$PortMapping port-mapping]
  {:public  (.publicPort port-mapping)
   :private (.privatePort port-mapping)
   :type    (keyword (.type port-mapping))
   :ip      (.ip port-mapping)})

(defn format-container-ps
  [^Container container]
  {:id      (u/format-id (.id container))
   :names   (mapv #(clojure.string/replace % #"/" "")
                  (.names container))
   :image   (.image container)
   :command (.command container)
   :state   (keyword (.state container))
   :status  (.status container)
   :ports   (mapv format-port-mapping (.ports container))})

(defn format-env-vars
  [env-vars]
  (map #(format "%s=%s" (name (first %)) (last %))
       env-vars))

(defn format-state
  [^ContainerState state]
  {:status      (keyword (.status state))
   :running?    (.running state)
   :paused      (.paused state)
   :restarting? (.restarting state)
   :pid         (.pid state)
   :exit-code   (.exitCode state)
   :started-at  (.startedAt state)
   :finished-at (.finishedAt state)
   :error       (.error state)
   :oom-killed? (.oomKilled state)})

(defn format-index-config
  [^Info$IndexConfig config]
  {:name      (.name config)
   :mirrors   (.mirrors config)
   :secure?   (.secure config)
   :official? (.official config)})

(defn format-registry-config
  [^Info$RegistryConfig config]
  (let [index-configs ^Info$IndexConfig (.indexConfigs config)]
    {:index-configs           (into {}
                                    (for [[k v] index-configs]
                                      [k (format-index-config v)]))
     :insecure-registry-cidrs (.insecureRegistryCidrs config)}))

(defn format-raft-config
  [^RaftConfig config]
  {:snapshot-interval              (.snapshotInterval config)
   :keep-old-snapshots             (.keepOldSnapshots config)
   :log-entries-for-slow-followers (.logEntriesForSlowFollowers config)
   :election-tick                  (.electionTick config)
   :heartbeat-tick                 (.heartbeatTick config)})

(defn format-external-ca
  [^ExternalCa ca]
  {:protocol (.protocol ca)
   :url      (.url ca)
   :options  (.options ca)})

(defn format-ca-config
  [^CaConfig config]
  {:node-cert-expiry (.nodeCertExpiry config)
   :external-cas     (map format-external-ca
                          (.externalCas config))})

(defn format-driver
  [^Driver driver]
  (when (not (nil? driver))
    {:name    (.name driver)
     :options (.options driver)}))

(defn format-swarm-spec
  [^SwarmSpec spec]
  {:name              (.name spec)
   :label             (.labels spec)
   :orchestration     {:task-history-retention-limit
                       (.taskHistoryRetentionLimit
                         (.orchestration spec))}
   :raft              (format-raft-config (.raft spec))
   :dispatcher        {:heartbeat-period
                       (.heartbeatPeriod
                         (.dispatcher spec))}
   :ca-config         (format-ca-config (.caConfig spec))
   :encryption-config {:auto-lock-managers
                       (.autoLockManagers
                         (.encryptionConfig spec))}
   :task-defaults     {:log-driver (format-driver
                                     (.logDriver (.taskDefaults spec)))}})

(defn format-swarm-cluster
  [^SwarmCluster cluster]
  (when (not (nil? cluster))
    {:id         (.id cluster)
     :version    {:index (.index (.version cluster))}
     :created_at (.createdAt cluster)
     :updated-at (.updatedAt cluster)
     :spec       (format-swarm-spec (.swarmSpec cluster))}))

(defn format-remote-manager
  [^RemoteManager manager]
  {:addr    (.addr manager)
   :node-id (.nodeId manager)})

(defn format-swarm-info
  [^SwarmInfo info]
  {:cluster            (format-swarm-cluster (.cluster info))
   :control-available? (.controlAvailable info)
   :error              (.error info)
   :local-node-state   (.localNodeState info)
   :node-addr          (.nodeAddr info)
   :node-id            (.nodeId info)
   :nodes              (.nodes info)
   :managers           (.managers info)
   :remote-managers    (map format-remote-manager (.remoteManagers info))})

(defn format-info
  [^Info info]
  {:arch                 (.architecture info)
   :cluster-store        (.clusterStore info)
   :cgroup-driver        (.cgroupDriver info)
   :containers           (.containers info)
   :running-containers   (.containersRunning info)
   :paused-containers    (.containersPaused info)
   :cpu-cfs-period?      (.cpuCfsPeriod info)
   :cpu-cfs-quota?       (.cpuCfsQuota info)
   :debug?               (.debug info)
   :docker-root-dir      (.dockerRootDir info)
   :storage-driver       (.storageDriver info)
   :driver-status        (.driverStatus info)
   :experimental-build?  (.experimentalBuild info)
   :http-proxy           (.httpProxy info)
   :https-proxy          (.httpsProxy info)
   :id                   (.id info)
   :ipv4-forwarding?     (.ipv4Forwarding info)
   :images               (.images info)
   :index-server-address (.indexServerAddress info)
   :init-path            (.initPath info)
   :init-sha1            (.initSha1 info)
   :kernel-memory        (.kernelMemory info)
   :kernel-version       (.kernelVersion info)
   :labels               (.labels info)
   :mem-total            (.memTotal info)
   :mem-limit            (.memoryLimit info)
   :cpus                 (.cpus info)
   :even-listeners       (.eventsListener info)
   :file-descriptors     (.fileDescriptors info)
   :go-routines          (.goroutines info)
   :name                 (.name info)
   :no-proxy             (.noProxy info)
   :oom-kill-disabled?   (.oomKillDisable info)
   :operating-system     (.operatingSystem info)
   :os-type              (.osType info)
   :plugins              {:networks (.networks (.plugins info))
                          :volumes  (.volumes (.plugins info))}
   :registry-config      (format-registry-config (.registryConfig info))
   :server-version       (.serverVersion info)
   :swap-limit?          (.swapLimit info)
   :swarm-info           (format-swarm-info (.swarm info))
   :system-status        (.systemStatus info)
   :system-time          (.systemTime info)})

(defn format-network
  [^Network network]
  {:name   (.name network)
   :id     (u/format-id (.id network))
   :scope  (.scope network)
   :driver (.driver network)})
