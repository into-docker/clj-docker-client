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

### Breaking changes in 0.2.0
This release includes sizable code refactoring and reduction and Docker responses
from the Spotify lib are auto generated and is used verbatim to provide more info.

This means the key names in the maps are CamelCased not kebab-cased as it reads
the Java key names directly and auto camel->kebab case conversion in unreliable
due to inconsistent var names in the Spotify lib.

### Installation
Leiningen/Boot
```clojure
[lispyclouds/clj-docker-client "0.2.2"]
```

Clojure CLI/deps.edn
```clojure
{lispyclouds/clj-docker-client {:mvn/version "0.2.2"}}
```

Gradle
```groovy
compile 'lispyclouds:clj-docker-client:0.2.2'
```

Maven
```xml
<dependency>
  <groupId>lispyclouds</groupId>
  <artifactId>clj-docker-client</artifactId>
  <version>0.2.2</version>
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
=> {:Driver             "overlay2"
    :NFd                26
    :Architecture       "x86_64"
    :CpuCfsQuota        true
    :NGoroutines        53
    :Images             0
    :Containers         0
    :CgroupDriver       "cgroupfs"
    :ExperimentalBuild  false
    :KernelMemory       true
    :InitSha1           nil
    :IndexServerAddress "https://index.docker.io/v1/"
    :SystemTime         1550060192024
    :HttpsProxy         "gateway.docker.internal:3129"
    :ExecutionDriver    nil
    :Plugins            {:Volume  ["local"]
                         :Network ["bridge" "host" "macvlan" "null" "overlay"]}
    :SwapLimit          true
    :Debug              true
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
=> [{:Created     "1548886792",
     :Id          "sha256:caf27325b298a6730837023a8a342699c8b7b388b8d878966b064a1320043019",
     :ParentId    "",
     :RepoTags    ["alpine:latest"],
     :RepoDigests ["alpine@sha256:b3dbf31b77fd99d9c08f780ce6f5282aba076d70a513a8be859d8d3a4d0c92b8"],
     :Size        5529164,
     :VirtualSize 5529164,
     :Labels      nil}
    {:Created     "1546305828",
     :Id          "sha256:3cc47384c4cb779466fe40182420bd90ba761a5f26f8564580a114bcd0dfa911",
     :ParentId    "",
     :RepoTags    ["busybox:musl"],
     :RepoDigests ["busybox@sha256:366488474d5b8dfa2546ec5d220e86029925d6c2e54c3fdf45efbfdd06da8e4d"],
     :Size        1403257,
     :VirtualSize 1403257,
     :Labels      nil}]
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
(docker/create conn "busybox:musl" "echo hello" {:env "testing"} {"127.0.0.1:8000" 8000})
=> "9a9ce5dc847c"

; Binds on 0.0.0.0 in the host by default.
(docker/create conn "busybox:musl" "echo hello" {:env "testing"} {8000 8000} "/working/dir" "user")
=> "9a9ce5dc847c"
```

#### Listing all available containers
```clojure
(docker/ps conn) ; Only running containers, pass true for all containers
=> [{:Ports           [{:PrivatePort 6379, :PublicPort 0, :Type "tcp", :IP nil}],
     :Image           "redis:alpine",
     :Labels          {},
     :Id              "86fd4b8375e3dfac018e37efbddb7b39d3d4b28e1671331a97fb913e7d888f68",
     :SizeRw          nil,
     :Mounts          [{:Type        "volume",
                        :Name        "9669e9b669d5c18ebc8314a4217af4a2207f37d94684ab353b9da87275cad4d1",
                        :Source      "",
                        :Destination "/data",
                        :Driver      "local",
                        :Mode        "",
                        :RW          true,
                        :Propagation ""}],
     :SizeRootFs      nil,
     :Command         "docker-entrypoint.sh redis-server",
     :ImageID         "sha256:a5cff96d7b8f5d3332b43922e424d448172f68b118e0e32cb26270227faec083",
     :Names           ["/goofy_wilson"],
     :State           "running",
     :Created         1550060546,
     :NetworkSettings {:Ports                  {},
                       :LinkLocalIPv6Address   nil,
                       :GlobalIPv6PrefixLen    nil,
                       :SandboxID              nil,
                       :IPv6Gateway            nil,
                       :PortMapping            nil,
                       :Gateway                nil,
                       :EndpointID             nil,
                       :MacAddress             nil,
                       :IPPrefixLen            nil,
                       :GlobalIPv6Address      nil,
                       :Networks               {:bridge {:Aliases             nil,
                                                         :GlobalIPv6PrefixLen 0,
                                                         :IPv6Gateway         "",
                                                         :Gateway             "172.17.0.1",
                                                         :EndpointID          "6bf4bc46e3cc7cef4325a832be258ce492ed4acad6924c28852f3d8ebd104746",
                                                         :MacAddress          "02:42:ac:11:00:02",
                                                         :IPPrefixLen         16,
                                                         :GlobalIPv6Address   "",
                                                         :IPAddress           "172.17.0.2",
                                                         :NetworkID           "0c54060f7a84c94770358434c8ecd4a5078af12157825b91bb2dde68966368c7"}},
                       :Bridge                 nil,
                       :HairpinMode            nil,
                       :SandboxKey             nil,
                       :IPAddress              nil,
                       :LinkLocalIPv6PrefixLen nil},
     :Status          "Up 7 seconds"}]
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
=> {:Restarting false,
    :ExitCode   0,
    :Running    true,
    :Pid        12552,
    :StartedAt  1550060547196,
    :Paused     false,
    :Error      "",
    :FinishedAt -62135769600000,
    :OOMKilled  false,
    :Health     nil,
    :Status     "running"}
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

#### Getting an InputStream from a path in a container
Returns an InputStream to the tar archive of the path.
Create a TarArchiveInputStream to process the file(s) in it.
```clojure
(docker/stream-path conn "id or name" "path on container")
=> #object[org.glassfish.jersey.message.internal.EntityInputStream
           0x6c3522c0
           "org.glassfish.jersey.message.internal.EntityInputStream@6c3522c0"]
```

#### Inspecting a container
```clojure
(docker/inspect conn "id or name")
=> {:HostsPath    "/var/lib/docker/containers/86fd4b8375e3dfac018e37efbddb7b39d3d4b28e1671331a97fb913e7d888f68/hosts"
    :ProcessLabel ""
    :Path         "docker-entrypoint.sh"
    :Config       {:OnBuild          nil
                   :Env              ["PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                                      "REDIS_VERSION=5.0.3"
                                      "REDIS_DOWNLOAD_URL=http://download.redis.io/releases/redis-5.0.3.tar.gz"
                                      "REDIS_DOWNLOAD_SHA=e290b4ddf817b26254a74d5d564095b11f9cd20d8f165459efa53eb63cd93e02"]
                   :OpenStdin        false
                   :Image            "redis:alpine"
                   :Labels           {}
                   :Entrypoint       ["docker-entrypoint.sh"]
                   :Healthcheck      nil
                   :Tty              false
                   :WorkingDir       "/data"
                   :HostConfig       nil
                   :AttachStderr     true
                   :AttachStdout     true
                   :Domainname       ""
                   :StopSignal       nil
                   :Hostname         "86fd4b8375e3"
                   :ExposedPorts     ["6379/tcp"]
                   :MacAddress       nil
                   :NetworkDisabled  nil
                   :User             ""
                   :AttachStdin      false
                   :StdinOnce        false
                   :Volumes          ["/data"]
                   :NetworkingConfig nil
                   :PortSpecs        nil
                   :Cmd              ["redis-server"]}
    ... more info}
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
=> ({:IPAM       {:Driver "default", :Config [], :Options nil},
     :Labels     {},
     :Driver     "null",
     :Id         "3b2b15102d2df9a1dd7dec6fac1a1164892cc3bd999266523e739e634d39a13f",
     :EnableIPv6 false,
     :Containers {},
     :Scope      "local",
     :Attachable false,
     :Options    {},
     :Internal   false,
     :Name       "none"
     {:IPAM       {:Driver "default", :Config [], :Options nil},
      :Labels     {},
      :Driver     "host",
      :Id         "4e4c79263e3faff02062c6dd9953fb3e35d199e197f268034a4176b907712ba0",
      :EnableIPv6 false,
      :Containers {},
      :Scope      "local",
      :Attachable false,
      :Options    {},
      :Internal   false,
      :Name       "host"
                 {:IPAM       {:Driver "default", :Config [{:Subnet "172.17.0.0/16", :IPRange nil, :Gateway "172.17.0.1"}], :Options nil},
                  :Labels     {},
                  :Driver     "bridge",
                  :Id         "0c54060f7a84c94770358434c8ecd4a5078af12157825b91bb2dde68966368c7",
                  :EnableIPv6 false,
                  :Containers {},
                  :Scope      "local",
                  :Attachable false,
                  :Options    {:com.docker.network.bridge.default_bridge       "true",
                               :com.docker.network.bridge.enable_icc           "true",
                               :com.docker.network.bridge.enable_ip_masquerade "true",
                               :com.docker.network.bridge.host_binding_ipv4    "0.0.0.0",
                               :com.docker.network.bridge.name                 "docker0",
                               :com.docker.network.driver.mtu                  "1500"},
                  :Internal   false,
                  :Name       "bridge"}}
     {:IPAM       {:Driver "default", :Config [{:Subnet "172.20.0.0/16", :IPRange nil, :Gateway "172.20.0.1"}], :Options {}},
      :Labels     {},
      :Driver     "bridge",
      :Id         "df99a33349c5a5f6a93d631bd0bd5f033710bde1d31ede3c48410d0c8e4b705f",
      :EnableIPv6 false,
      :Containers {},
      :Scope      "local",
      :Attachable false,
      :Options    {},
      :Internal   false,
      :Name       "sky-net"}})
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
