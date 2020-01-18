## clj-docker-client [![Build Status](https://travis-ci.org/lispyclouds/clj-docker-client.svg?branch=master)](https://travis-ci.org/lispyclouds/clj-docker-client)

[![License: LGPL v3](https://img.shields.io/badge/license-LGPL%20v3-blue.svg?style=flat-square)](http://www.gnu.org/licenses/lgpl-3.0)
[![Clojars Project](https://img.shields.io/clojars/v/lispyclouds/clj-docker-client.svg?style=flat-square)](https://clojars.org/lispyclouds/clj-docker-client)

[![cljdoc badge](https://cljdoc.org/badge/lispyclouds/clj-docker-client)](https://cljdoc.org/d/lispyclouds/clj-docker-client/CURRENT)
[![Dependencies Status](https://versions.deps.co/lispyclouds/clj-docker-client/status.png)](https://versions.deps.co/lispyclouds/clj-docker-client)
[![Downloads](https://versions.deps.co/lispyclouds/clj-docker-client/downloads.svg)](https://versions.deps.co/lispyclouds/clj-docker-client)

An idiomatic, data-driven, REPL friendly Clojure Docker client inspired from Cognitect's AWS [client](https://github.com/cognitect-labs/aws-api).

See [this](https://cljdoc.org/d/lispyclouds/clj-docker-client/0.3.2/doc/readme) for documentation for versions before **0.4.0**.

**The README here is for the current master branch and _may not reflect the released version_.**

**Please raise issues here for any new feature requests!**

### Installation
Leiningen/Boot
```clojure
[lispyclouds/clj-docker-client "0.4.0"]
```

Clojure CLI/deps.edn
```clojure
{lispyclouds/clj-docker-client {:mvn/version "0.4.0"}}
```

Gradle
```groovy
compile 'lispyclouds:clj-docker-client:0.4.0'
```

Maven
```xml
<dependency>
  <groupId>lispyclouds</groupId>
  <artifactId>clj-docker-client</artifactId>
  <version>0.4.0</version>
</dependency>
```

### Build Requirements
- Leiningen 2.8+
- JDK 1.8+

### Running tests locally
- Install [leiningen](https://leiningen.org/)
- Install [Docker](https://www.docker.com/)
- `lein kaocha` to run all tests. (needs Docker and working internet)

Auto generated code docs can be found [here](https://cljdoc.org/d/lispyclouds/clj-docker-client/CURRENT)

This uses Docker's HTTP REST API to run. See the section **API version matrix** in https://docs.docker.com/develop/sdk/ to find the corresponding API version for the Docker daemon you're running.

See the [page](https://docs.docker.com/develop/sdk/) about the docker REST API to learn more about the params to pass.

### Usage

```clojure
(require '[clj-docker-client.core :as docker])
```

This library aims to be a _as thin layer as possible_ between you and Docker. This consists of following public functions:

#### connect
Connect to the docker daemon's [UNIX socket](https://en.wikipedia.org/wiki/Unix_domain_socket) and create a connection.
```clojure
(def conn (docker/connect {:uri "unix:///var/run/docker.sock"}))
```

#### categories
Lists the categories of operations supported. Can be bound to an API version.
```clojure
(docker/categories) ; Latest version

(docker/categories "v1.40") ; Locked to v1.40

#_=> #{:system
       :exec
       :images
       :secrets
       :events
       :_ping
       :containers
       :auth
       :tasks
       :volumes
       :networks
       :build
       :nodes
       :commit
       :plugins
       :info
       :swarm
       :distribution
       :version
       :services
       :configs
       :session}
```

#### client
Creates a client scoped to the operations of a given category. Can be bound to an API version.
```clojure
(def images (docker/client {:category :images
                            :conn     conn})) ; Latest version

(def containers (docker/client {:category    :containers
                                :conn        conn
                                :api-version "v1.40"})) ; Container client for v1.40
```

#### ops
Lists the supported ops by a client.
```clojure
(docker/ops images)

#_=> (:ImageList
      :ImageCreate
      :ImageInspect
      :ImageHistory
      :ImagePush
      :ImageTag
      :ImageDelete
      :ImageSearch
      :ImagePrune
      :ImageGet
      :ImageGetAll
      :ImageLoad)
```

#### doc
Returns the doc of an operation in a client.
```clojure
(docker/doc images :ImageList)

#_=> {:doc
      "List Images\nReturns a list of images on the server. Note that it uses a different, smaller representation of an image than inspecting a single image.",
      :params
      ({:name "all", :type "boolean"}
       {:name "filters", :type "string"}
       {:name "digests", :type "boolean"})}
```

#### invoke
Invokes an operation via the client and a given operation map and returns the result data.
```clojure
; Pulls the busybox:musl image from Docker hub
(docker/invoke images {:op     :ImageCreate
                       :params {:fromImage "busybox:musl"}})

; Creates a container named conny from it
(docker/invoke containers {:op     :ContainerCreate
                           :params {:name "conny"
                                    :body {:Image "busybox:musl"
                                           :Cmd   "ls"}}})
```

The operation map is of the following structure:
```clojure
{:op     :NameOfOp
 :params {:param-1 "value1"
          :param-2 true}}
```
Takes an optional key `as-stream?`. Returns an InputStream if passed as true. This is useful for streaming responses like logs, events etc, which run till the container is up.
```clojure
{:op         :NameOfOp
 :params     {:param-1 "value1"
              :param-2 true}
 :as-stream? true}
```

### General guidelines
- Head over to the Docker API docs to get more info on the type of parameters you should be sending. eg: this [page](https://docs.docker.com/engine/api/v1.40/) for `v1.40` API docs.
- The type `stream` is mapped to `java.io.InputStream` and when the API needs a stream as an input, send an InputStream. When it returns a stream, the call can **possibly block** till the container or source is up and its recommended to pass the `as-stream?` param as true to the invoke call and read it asynchronously. See this [section](https://github.com/lispyclouds/clj-docker-client/tree/master#streaming-logs) for more info.

### Sample code for common scenarios

#### Pulling an image
```clojure
(def conn (docker/connect {:uri "unix:///var/run/docker.sock"}))

(def images (docker/client {:category :images
                            :conn   conn}))

(docker/invoke images {:op     :ImageCreate
                       :params {:fromImage "busybox:musl"}})
```

#### Creating a container
```clojure
(def containers (docker/client {:category :containers
                                :conn     conn}))

(docker/invoke containers {:op     :ContainerCreate
                           :params {:name "conny"
                                    :body {:Image "busybox:musl"
                                           :Cmd   "sh -c 'i=1; while :; do echo $i; sleep 1; i=$((i+1)); done"}}})
```

#### Starting a container
```clojure

(docker/invoke containers {:op     :ContainerStart
                           :params {:id "conny"}})
```

#### Streaming logs
```clojure
; fn to react when data is available
(defn react-to-stream
  [stream reaction-fn]
  (future
    (with-open [rdr (clojure.java.io/reader stream)]
       (loop [r (java.io.BufferedReader. rdr)]
         (when-let [line (.readLine r)]
         (reaction-fn line)
         (recur r))))))

(def log-stream (docker/invoke containers {:op     :ContainerLogs
                                           :params {:id "conny"
                                                    :follow true
                                                    :stdout true}
                                           :as-stream? true}))

(react-to-stream log-stream println) ; prints the logs line by line when they come.
```

And anything else is possible!

## License

Copyright © 2020 Rahul De and [contributors](https://github.com/lispyclouds/clj-docker-client/graphs/contributors).

Distributed under the LGPLv3+ License. See LICENSE.
