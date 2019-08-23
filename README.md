## clj-docker-client [![Build Status](https://travis-ci.org/lispyclouds/clj-docker-client.svg?branch=master)](https://travis-ci.org/lispyclouds/clj-docker-client)

[![License: LGPL v3](https://img.shields.io/badge/license-LGPL%20v3-blue.svg?style=flat-square)](http://www.gnu.org/licenses/lgpl-3.0)
[![Clojars Project](https://img.shields.io/clojars/v/lispyclouds/clj-docker-client.svg?style=flat-square)](https://clojars.org/lispyclouds/clj-docker-client)

[![cljdoc badge](https://cljdoc.org/badge/lispyclouds/clj-docker-client)](https://cljdoc.org/d/lispyclouds/clj-docker-client/CURRENT)
[![Dependencies Status](https://versions.deps.co/lispyclouds/clj-docker-client/status.png)](https://versions.deps.co/lispyclouds/clj-docker-client)
[![Downloads](https://versions.deps.co/lispyclouds/clj-docker-client/downloads.svg)](https://versions.deps.co/lispyclouds/clj-docker-client)

An idiomatic Clojure Docker client based on [docker-java](https://github.com/docker-java/docker-java)

### Why not use the Java lib directly?
The lib though being excellent, has Java style vararg method calls,
non-standard variable passing and undocumented behaviour. This eases out these
things to make an idiomatic, clojure friendly API to Docker.

This is a work in progress and aims to be fully compliant and up to date with
the Docker API changes.

**Please raise issues here for any new feature requests!**

### Breaking changes in 0.3.0
- Switched to [docker-java](https://github.com/docker-java/docker-java) from [docker-client](https://github.com/spotify/docker-client)  
- Removal of separate login method in favor of credentials in daemon connect
- More detailed outputs with case changes
- Provision for async methods with callbacks for streaming APIs like stats and log

### Breaking changes in 0.2.0
This release includes sizable code refactoring and reduction and Docker responses
from the Spotify lib are auto generated and is used verbatim to provide more info.

This means the key names in the maps are CamelCased not kebab-cased as it reads
the Java key names directly and auto camel->kebab case conversion in unreliable
due to inconsistent var names in the Spotify lib.

### Installation
Leiningen/Boot
```clojure
[lispyclouds/clj-docker-client "0.2.3"]
```

Clojure CLI/deps.edn
```clojure
{lispyclouds/clj-docker-client {:mvn/version "0.2.3"}}
```

Gradle
```groovy
compile 'lispyclouds:clj-docker-client:0.2.3'
```

Maven
```xml
<dependency>
  <groupId>lispyclouds</groupId>
  <artifactId>clj-docker-client</artifactId>
  <version>0.2.3</version>
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
(docker/push conn "image id or <repo>:<tag>")
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
=> [{:Created 1565313830,
     :Id "sha256:bb6408c77dbf632fbd65b33c220438ea9534b9a4610eb058c0ccfc39128b0643",
     :ParentId "",
     :RepoTags ["postgres:alpine"],
     :Size 72462541,
     :VirtualSize 72462541}
    {:Created 1556816155,
     :Id "sha256:66637b2d3513e1524f646d6b8648efa78482ae7612c00cab1ff842dc89034afe",
     :ParentId "",
     :RepoTags ["bobcd/resource-git:latest"],
     :Size 976644873,
     :VirtualSize 976644873}
    {:Created 1556815030,
     :Id "sha256:3245ba9120f894f33c324403a7890ac61f91a7cb77fd59fa3174f56d52f05852",
     :ParentId "",
     :RepoTags ["bobcd/artifact-local:latest"],
     :Size 973466966,
     :VirtualSize 973466966}]
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
=> [{:Ports [],
     :Image "busybox:musl",
     :Labels {},
     :Id "fb676a53bc9b3fd7ad32142828cf8fbd7628dda83ec6fd200faba881ab890da2",
     :Mounts [],
     :HostConfig {:NetworkMode "default"},
     :Command "sh",
     :ImageID "sha256:65a3b9e8dac8c1e33c4af38a8f4a6c0be46b0703c3fb96d6b788ea517c636e9e",
     :Names ["/con"],
     :State "running",
     :Created 1566567943,
     :NetworkSettings {:Networks {:bridge {:Aliases nil,
                                           :GlobalIPv6PrefixLen 0,
                                           :IPv6Gateway "",
                                           :Gateway "172.17.0.1",
                                           :EndpointID "f1a89a78d3320a3ef3b57e99a5a5a8c9d2aa5afd1238d8a700b58d3595326c57",
                                           :MacAddress "02:42:ac:11:00:02",
                                           :IPPrefixLen 16,
                                           :GlobalIPv6Address "",
                                           :Links nil,
                                           :IPAMConfig nil,
                                           :IPAddress "172.17.0.2",
                                           :NetworkID "7221f9584ec74a880b5205471d70240ece3d4469470b04da4e2e191c0b7d835e"}}},
     :Status "Up 15 seconds"}]
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

#### Getting logs from a container
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

#### Live logs from a container

Takes a callback which is notified of Logs _as the appear_

**THIS BLOCKS UNTIL THE CONTAINER IS FINISHED**
```clojure
(docker/logs conn "name or id" #(println %))
=> "line 1"
   "line 2"
   ...
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
=> #object[com.github.dockerjava.jaxrs.util.WrappedResponseInputStream
           0xff125f9
           "com.github.dockerjava.jaxrs.util.WrappedResponseInputStream@40e34255"]
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

#### Getting container stats
```clojure
(stats conn "id or name")
=> {:read "2019-08-23T13:57:05.1122079Z",
    :networks {:eth0 {:rx_bytes 1178,
                      :rx_dropped 0,
                      :rx_errors 0,
                      :rx_packets 15,
                      :tx_bytes 0,
                      :tx_dropped 0,
                      :tx_errors 0,
                      :tx_packets 0}},
    :memory_stats {:stats {:active_anon 5484544,
                           :dirty 0,
                           :pgmajfault 0,
                           :mapped_file 10948608,
                           :total_writeback 0,
                           :rss_huge 0,
                           :total_cache 61628416,
                           :unevictable 0,
                           :inactive_file 41263104,
                           :rss 2752512,
                           :total_dirty 0,
                           :total_active_file 8929280,
                           :inactive_anon 8704000,
                           :total_pgpgout 29016,
                           :total_inactive_file 41263104,
                           :pgpgin 44734,
                           :total_rss 2752512,
                           :active_file 8929280,
                           :cache 61628416,
                           :hierarchical_memory_limit 9223372036854771712,
                           :hierarchical_memsw_limit 9223372036854771712,
                           :total_pgpgin 44734,
                           :total_unevictable 0,
                           :swap nil,
                           :pgpgout 29016,
                           :pgfault 38458,
                           :total_active_anon 5484544,
                           :total_swap nil,
                           :total_mapped_file 10948608,
                           :total_pgmajfault 0,
                           :total_rss_huge 0,
                           :writeback 0,
                           :total_inactive_anon 8704000,
                           :total_pgfault 38458},
                   :usage 69570560,
                   :max_usage 75354112,
                   :failcnt nil,
                   :limit 4139208704},
    :blkio_stats {:io_service_bytes_recursive [{:major 8, :minor 0, :op "Read", :value 0}
                                               {:major 8, :minor 0, :op "Write", :value 122953728}
                                               {:major 8, :minor 0, :op "Sync", :value 212992}
                                               {:major 8, :minor 0, :op "Async", :value 122740736}
                                               {:major 8, :minor 0, :op "Total", :value 122953728}],
                  :io_serviced_recursive [{:major 8, :minor 0, :op "Read", :value 0}
                                          {:major 8, :minor 0, :op "Write", :value 2527}
                                          {:major 8, :minor 0, :op "Sync", :value 1299}
                                          {:major 8, :minor 0, :op "Async", :value 1228}
                                          {:major 8, :minor 0, :op "Total", :value 2527}],
                  :io_queue_recursive [],
                  :io_service_time_recursive [],
                  :io_wait_time_recursive [],
                  :io_merged_recursive [],
                  :io_time_recursive [],
                  :sectors_recursive []},
    :cpu_stats {:cpu_usage {:total_usage 1778621831,
                            :percpu_usage [101966366 490612163 358957860 827085442],
                            :usage_in_kernelmode 950000000,
                            :usage_in_usermode 840000000},
                :system_cpu_usage 76068760000000,
                :online_cpus 4,
                :throttling_data {:periods 0, :throttled_periods 0, :throttled_time 0}},
    :precpu_stats {:cpu_usage {:total_usage 0, :percpu_usage nil, :usage_in_kernelmode 0, :usage_in_usermode 0},
                   :system_cpu_usage nil,
                   :online_cpus nil,
                   :throttling_data {:periods 0, :throttled_periods 0, :throttled_time 0}},
    :pids_stats {:current 7}}}
```

#### Live stats from a container

Takes a callback which is notified of Statistics/second.

**THIS BLOCKS UNTIL THE CONTAINER IS FINISHED**
```clojure
(docker/stats conn "name or id" #(println %))
=> {:stats ...}
   {:stats ...}
   ...
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
