## clj-docker-client [![Build Status](https://travis-ci.org/lispyclouds/clj-docker-client.svg?branch=master)](https://travis-ci.org/lispyclouds/clj-docker-client)

[![License: LGPL v3](https://img.shields.io/badge/license-LGPL%20v3-blue.svg?style=flat-square)](http://www.gnu.org/licenses/lgpl-3.0)
[![Clojars Project](https://img.shields.io/clojars/v/lispyclouds/clj-docker-client.svg?style=flat-square)](https://clojars.org/lispyclouds/clj-docker-client)

[![cljdoc badge](https://cljdoc.org/badge/lispyclouds/clj-docker-client)](https://cljdoc.org/d/lispyclouds/clj-docker-client/CURRENT)
[![Dependencies Status](https://versions.deps.co/lispyclouds/clj-docker-client/status.png)](https://versions.deps.co/lispyclouds/clj-docker-client)
[![Downloads](https://versions.deps.co/lispyclouds/clj-docker-client/downloads.svg)](https://versions.deps.co/lispyclouds/clj-docker-client)

An idiomatic Clojure Docker client based on the excellent JVM [client](https://github.com/spotify/docker-client) by Spotify.

### Why not use the Spotify lib directly?
The Spotify lib though being excellent, has Java style vararg method calls,
non-standard variable passing and undocumented behaviour. This eases out these
things to make an idiomatic, clojure friendly API to Docker.

This is a work in progress and aims to be fully compliant and up to date with
the Docker API changes.

**Please raise issues here for any new feature requests!**

### Installation
Leiningen/Boot
```clojure
[lispyclouds/clj-docker-client "0.1.11"]
```

Clojure CLI/deps.edn
```clojure
{lispyclouds/clj-docker-client {:mvn/version "0.1.11"}}
```

Gradle
```groovy
compile 'lispyclouds:clj-docker-client:0.1.11'
```

Maven
```xml
<dependency>
  <groupId>lispyclouds</groupId>
  <artifactId>clj-docker-client</artifactId>
  <version>0.1.11</version>
</dependency>
```

### Build Requirements
- Leiningen 2.8+
- JDK 1.8+

### Running tests locally
- Install [leiningen](https://leiningen.org/)
- Install [Docker](https://www.docker.com/)
- `lein test` to run all unit tests. (needs Docker and working internet)

Auto generated code docs can be found [here](https://cljdoc.org/d/lispyclouds/clj-docker-client/CURRENT)

### Usage

```clojure
(require '[clj-docker-client.core :as docker])
```

#### Creating a connection to the local Docker daemon
```clojure
(def conn (docker/connect))
```

#### Creating a connection to a remote Docker daemon
```clojure
(def conn (docker/connect "http://192.168.33.10:2375"))
```

#### Closing the connection to the Docker daemon
```clojure
(docker/disconnect conn)
```

Connections can be used in the `(with-open)` block
which closes it after use.

```clojure
(with-open [conn (docker/connect)]
  (docker/ping conn))
=> "OK"
```

#### Ping the Docker server
```clojure
(docker/ping conn)
=> "OK"
```

#### Get system-wide Docker info
```clojure
(docker/info conn)
=> {:arch "x86_64"
    :cgroup-driver "cgroupfs"
    :cluster-store ""
    :containers 0
    :cpu-cfs-period? true
    :cpu-cfs-quota? true
    :cpus 4
    :debug? true
    :docker-root-dir "/var/lib/docker"
    other system info...}
```

#### Build login info with Docker Hub
```clojure
(def login-info (docker/register conn "username" "password"))
```

### Image Handling

#### Pulling the `busybox:musl` image
```clojure
(docker/pull conn "busybox:musl")
=> "busybox:musl"
```

#### Building an image from a Dockerfile
```clojure
(docker/build
  conn
  "full path to directory containing a Dockerfile"
  "repo-name"
  "tag")
=> "9958e80071d4"
```

#### Pushing an image
```clojure
(docker/push conn "image id or <repo>:<tag>" login-info)
=> "myrepo/test:latest"
```

#### Removing an image
```clojure
(docker/image-rm conn "image id or <repo>:<tag>")
=> "myrepo/test:latest"
```

#### Listing all available images
```clojure
(docker/image-ls conn)
=> [{:created "1538500829"
     :id "feaa93c97400"
     :repo-tags ["busybox:musl"]
     :size 1358201}
    {:created "1536704390"
     :id "196d12cf6ab1"
     :repo-tags ["alpine:latest"]
     :size 4413370}]
```

#### Committing changes from a container to a new image
```clojure
(docker/commit-container
  conn
  "container id"
  "repo"
  "tag"
  "entry point command")
=> "9958e8aafed2"
```

### Container Handling

#### Creating a container with the `busybox:musl` image, a command, env vars and host->container port mappings.
```clojure
(docker/create conn "busybox:musl" "echo hello" {:env "testing"} {8000 8000})
=> "9a9ce5dc847c"
```

#### Listing all available containers
```clojure
(docker/ps conn) ; Only running containers
=> [{:command "docker-entrypoint.sh redis-server"
     :id "13c274fc67e6"
     :image "redis:alpine"
     :names ["friendly_einstein"]
     :ports [{:ip nil :private 6379 :public 0 :type :tcp}]
     :state :running
     :status "Up 34 seconds"}]

(docker/ps conn true) ; All containers
=> [{:command "docker-entrypoint.sh redis-server"
     :id "13c274fc67e6"
     :image "redis:alpine"
     :names ["friendly_einstein"]
     :ports [{:ip nil :private 6379 :public 0 :type :tcp}]
     :state :running
     :status "Up 34 seconds"}
    {:command "echo hello"
     :id "9a9ce5dc847c"
     :image "busybox:musl"
     :names ["festive_lovelace"]
     :ports []
     :state :created
     :status "Created"}]
```

#### Starting a container
```clojure
(docker/start conn "name or id")
=> "13c274fc67e6"
```

#### Stopping a container
```clojure
(docker/stop conn "name or id")
=> "13c274fc67e6"
```

#### Killing a container
```clojure
(docker/kill conn "name or id")
=> "13c274fc67e6"
```

#### Restarting a container
```clojure
(docker/restart conn "name or id")
=> "13c274fc67e6"
```

#### Pausing a container
```clojure
(docker/pause conn "name or id")
=> "13c274fc67e6"
```

#### Un-pausing a container
```clojure
(docker/un-pause conn "name or id")
=> "13c274fc67e6"
```

#### Waiting for a container to exit
```clojure
(docker/wait-container conn "name or id")
=> 0 ; Normal exit

(docker/wait-container conn "name or id")
=> 137 ; Abnormal exit
```

#### Running from an image directly
```clojure
(docker/run conn "image" "command" {:env "test"} {8100 8000}) ; Waits for container exit
=> "13c274fc67e6" ; after exit...

(docker/run conn "image" "command" {:env "test"} {8100 8000} true) ; Detached, returns immediately
=> "13c274fc67e6" ; immediately
```

#### Lazily getting logs from a container
```clojure
(docker/logs conn "name or id")
=> ("line 1" "line 2" ...)

```
```clojure
; Drop first 10 lines and take at most 30 lines from it
(->> (docker/logs conn "name or id")
     (drop 10)
     (take 30))
=> ("line 11" "line 12" ...)
```

#### Getting the current container state
```clojure
(docker/container-state conn "name or id")
=> {:error ""
    :exit-code 0
    :finished-at #inst "2018-11-20T20:41:46.904-00:00"
    :oom-killed? false
    :paused false
    :pid 0
    :restarting? false
    :running? false
    :started-at #inst "2018-11-20T20:41:46.813-00:00"
    :status :exited}
```

#### Removing a container
```clojure
(docker/rm conn "id or name") ; Remove non-running container
=> "00873a15ef06"

(docker/rm conn "id or name" true) ; Force remove non-running container
=> "00873a15ef06"
```

#### Copying a directory to a container
```clojure
(docker/cp conn "id or name" "source path on host" "dest path on container")
=> "00873a15ef06"
```

### Network Handling

#### Creating a new network
```clojure
(docker/network-create conn "sky-net")
=> "sky-net"

(docker/network-create conn "sky-net" true true) ; Check for duplicates and is attachable.
=> "sky-net"
```

#### List all networks
```clojure
(docker/network-ls conn)
=> ({:name "sky-net", :id "408ead00892d", :scope "local", :driver "bridge"}
    {:name "none", :id "3b2b15102d2d", :scope "local", :driver "null"}
    {:name "bridge", :id "eb4287b6fe7e", :scope "local", :driver "bridge"}
    {:name "host", :id "4e4c79263e3f", :scope "local", :driver "host"})
```

#### Connect a container to a network
```clojure
(docker/network-connect "sky-net" "container id")
=> "13c274fc67e6"
```

#### Disconnect a container from a network
```clojure
(docker/network-disconnect "sky-net" "container id")
=> "13c274fc67e6"
```

#### Remove a network
```clojure
(docker/network-rm "sky-net")
=> "sky-net"
```
