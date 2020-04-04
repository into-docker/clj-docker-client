## clj-docker-client [![](https://github.com/lispyclouds/clj-docker-client/workflows/Tests/badge.svg)](https://github.com/lispyclouds/clj-docker-client/actions?query=workflow%3ATests)

[![License: LGPL v3](https://img.shields.io/badge/license-LGPL%20v3-blue.svg?style=flat-square)](http://www.gnu.org/licenses/lgpl-3.0)
[![Clojars Project](https://img.shields.io/clojars/v/lispyclouds/clj-docker-client.svg?style=flat-square)](https://clojars.org/lispyclouds/clj-docker-client)

[![cljdoc badge](https://cljdoc.org/badge/lispyclouds/clj-docker-client)](https://cljdoc.org/d/lispyclouds/clj-docker-client/CURRENT)
[![Dependencies Status](https://versions.deps.co/lispyclouds/clj-docker-client/status.png)](https://versions.deps.co/lispyclouds/clj-docker-client)
[![Downloads](https://versions.deps.co/lispyclouds/clj-docker-client/downloads.svg)](https://versions.deps.co/lispyclouds/clj-docker-client)

[![project chat](https://img.shields.io/badge/slack-join_chat-brightgreen.svg)](https://clojurians.slack.com/messages/C0PME9N9X)

An idiomatic, data-driven, REPL friendly Clojure Docker client inspired from Cognitect's AWS [client](https://github.com/cognitect-labs/aws-api).

See [this](https://cljdoc.org/d/lispyclouds/clj-docker-client/0.3.2/doc/readme) for documentation for versions before **0.4.0**.

**The README here is for the current master branch and _may not reflect the released version_.**

**Please raise issues here for any new feature requests!**

### Installation
Leiningen/Boot
```clojure
[lispyclouds/clj-docker-client "0.5.3"]
```

Clojure CLI/deps.edn
```clojure
{lispyclouds/clj-docker-client {:mvn/version "0.5.3"}}
```

Gradle
```groovy
compile 'lispyclouds:clj-docker-client:0.5.3'
```

Maven
```xml
<dependency>
  <groupId>lispyclouds</groupId>
  <artifactId>clj-docker-client</artifactId>
  <version>0.5.3</version>
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

### Developing with Cognitect [REBL](http://rebl.cognitect.com/)
Since this is fully data driven, using REBL is really beneficial as it allows us to _walk_ through the output from Docker, see potential errors and be more productive with instant visual feedback.

This assumes Java 11+:
- [Download](http://rebl.cognitect.com/download.html) and unzip the REBL jar to a known location.
- Start the leiningen REPL with: `REBL_PATH=<PATH_TO_REBL_JAR> lein with-profile +rebl repl`.
- Connect your editor of choice to this REPL or start using the REBL/REPL directly.
- Evaluate `(rebl/ui)` to fire up the REBL UI.
- Then repeat after me 3 times: _ALL HAIL THE DATA_! 🙏🏽

### The Docker API
This uses Docker's HTTP REST API to run. See the API [version matrix](https://docs.docker.com/engine/api/#api-version-matrix) to find the corresponding API version for the Docker daemon you're running.

clj-docker-client works by parsing the Swagger 2.0 YAMLs from the docker client API and vendors it in this [directory](https://github.com/lispyclouds/clj-docker-client/tree/master/resources/api). **This defaults to using the latest version available there if no versions are pinned.** It is recommended to use a pinned version to have consistent behavior across different engine versions.

See the [page](https://docs.docker.com/develop/sdk/) about the docker REST API to learn more about the usage and params to pass.

### Usage

```clojure
(require '[clj-docker-client.core :as docker])
```

This library aims to be a _as thin layer as possible_ between you and Docker. This consists of following public functions:

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

Connect to the docker daemon's [UNIX socket](https://en.wikipedia.org/wiki/Unix_domain_socket) and
create a client scoped to the operations of a given category. Can be bound to an API version.
```clojure
(def images (docker/client {:category :images
                            :conn     {:uri "unix:///var/run/docker.sock"}})) ; Latest version

(def containers (docker/client {:category    :containers
                                :conn        {:uri "unix:///var/run/docker.sock"}
                                :api-version "v1.40"})) ; Container client for v1.40
```
Using a timeout for the connections. Thanks [olymk2](https://github.com/olymk2) for the suggestion.
Docker actions can take quite a long time so set the timeout accordingly. When you don't provide timeouts
then there will be no timeout clientside.
```
(def ping (docker/client {:category :_ping
                          :conn     {:uri      "unix:///var/run/docker.sock"
                                     :timeouts {:connect-timeout 10
                                                :read-timeout    30000
                                                :write-timeout   30000
                                                :call-timeout    30000}}}))
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
Takes an optional key `as`. Defaults to `:data`. Returns an InputStream if passed as `:stream`, the raw underlying network socket if passed as `:socket`. `:stream` is useful for streaming responses like logs, events etc, which run till the container is up. `:socket` is useful for events when bidirectional streams are returned by docker in operations like `:ContainerAttach`.
```clojure
{:op     :NameOfOp
 :params {:param-1 "value1"
          :param-2 true}
 :as     :stream}
```

Takes another optional key `:throw-exception?`. Defaults to `false`. If set to true will throw an exception for exceptional status codes from the Docker API i.e. `status >= 400`. Throws an `java.lang.RuntimeException` with the message.
```clojure
{:op               :NameOfOp
 :throw-exception? true}
```

### General guidelines
- Head over to the Docker API docs to get more info on the type of parameters you should be sending. eg: this [page](https://docs.docker.com/engine/api/v1.40/) for `v1.40` API docs.
- The type `stream` is mapped to `java.io.InputStream` and when the API needs a stream as an input, send an InputStream. When it returns a stream, the call can **possibly block** till the container or source is up and its recommended to pass the `as` param as `:stream` to the invoke call and read it asynchronously. See this [section](https://github.com/lispyclouds/clj-docker-client/tree/master#streaming-logs) for more info.

### Sample code for common scenarios

#### Pulling an image
```clojure
(def images (docker/client {:category :images
                            :conn     {:uri "unix:///var/run/docker.sock"}}))

(docker/invoke images {:op     :ImageCreate
                       :params {:fromImage "busybox:musl"}})
```

#### Creating a container
```clojure
(def containers (docker/client {:category :containers
                                :conn     {:uri "unix:///var/run/docker.sock"}}))

(docker/invoke containers {:op     :ContainerCreate
                           :params {:name "conny"
                                    :body {:Image "busybox:musl"
                                           :Cmd   ["sh"
                                                   "-c"
                                                   "i=1; while :; do echo $i; sleep 1; i=$((i+1)); done"]}}})
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
                                           :params {:id     "conny"
                                                    :follow true
                                                    :stdout true}
                                           :as     :stream}))

(react-to-stream log-stream println) ; prints the logs line by line when they come.
```

#### Attach to a container and send data to stdin
```clojure
;; This is a raw bidirectional java.net.Socket, so both reads and writes are possible.
;; conny-reader has been started with: docker run -d -i --name conny-reader alpine:latest sh -c "cat - >/out"
(def sock (docker/invoke containers {:op     :ContainerAttach
                                     :params {:id     "conny-reader"
                                              :stream true
                                              :stdin  true}
                                     :as     :socket}))

(clojure.java.io/copy "hello" (.getOutputStream sock))

(.close sock) ; Important for freeing up resources.
```

### Not so common scenarios

#### Accessing undocumented/experimental Docker APIs
There are some cases where you may need access to an API that is either experimental or is not in the swagger docs.
Docker [checkpoint](https://docs.docker.com/engine/reference/commandline/checkpoint/) is one such example. Thanks [@mk](https://github.com/mk) for bringing it up!

Since this uses the published APIs from the swagger spec, the way to access them is to use the lower level fn `fetch` from the `clj-docker-client/requests` ns. The caveat is the **response will be totally raw(data, stream or the socket itself)**.

fetch takes the following params as a map:
- conn: the connection to the daemon. Required.
- url: the relative path to the operation. Required.
- method: the method of the HTTP request as a keyword. Default: `:get`.
- query: the map of key-values to be passed as query params.
- path: the map of key-values to be passed as path params. Needed for interpolated path values like `/v1.40/containers/{id}/checkpoints`. Pass `{:id "conny"}` here.
- header: the map of key-values to be passed as HEADER params.
- body: the stream or map(will be converted to JSON) to be passed as body.
- as: takes the kind of response expected. One of :stream, :socket or :data. Same as `invoke`. Default: `:data`.

```clojure
(require '[clj-docker-client.requests :as req])
(require '[clj-docker-client.core :as docker])

;; This is the undocumented API in the Docker Daemon.
;; See https://github.com/moby/moby/pull/22049/files#diff-8038ade87553e3a654366edca850f83dR11
(req/fetch {:conn (req/connect* {:uri "unix:///var/run/docker.sock"})
            :url  "/v1.40/containers/conny/checkpoints"})
```

More examples of low level calls:
```clojure
;; Ping the server
(req/fetch {:conn (req/connect* {:uri "unix:///var/run/docker.sock"})
            :url  "/v1.40/_ping"})

;; Copy a folder to a container
(req/fetch {:conn   (req/connect* {:uri "unix:///var/run/docker.sock"})
            :url    "/v1.40/containers/conny/archive"
            :method :put
            :query  {:path "/root/src"}
            :body   (-> "src.tar.gz"
                        io/file
                        io/input-stream)})
```

And anything else is possible!

## License

Copyright © 2020 Rahul De and [contributors](https://github.com/lispyclouds/clj-docker-client/graphs/contributors).

Distributed under the LGPLv3+ License. See LICENSE.
