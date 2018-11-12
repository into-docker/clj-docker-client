## clj-docker-client

[![License: GPL v3](https://img.shields.io/badge/license-GPL%20v3-blue.svg?style=flat-square)](http://www.gnu.org/licenses/gpl-3.0)
[![Clojars Project](https://img.shields.io/clojars/v/lispyclouds/clj-docker-client.svg?style=flat-square)](https://clojars.org/lispyclouds/clj-docker-client)
[![Build Status](https://travis-ci.org/lispyclouds/clj-docker-client.svg?branch=master)](https://travis-ci.org/lispyclouds/clj-docker-client)

``` clojure
[lispyclouds/clj-docker-client "0.1.3"]
```

An idiomatic Clojure Docker client based on the excellent JVM [client](https://github.com/spotify/docker-client) by Spotify.

This is a work in progress and aims to be fully compliant and up to date with the Docker API changes. 

### Usage

```clojure
(require '[clj-docker-client.core :as docker])
```

#### Creating a connection to the Local Docker daemon
```clojure
(def conn (docker/connect))
```

#### Ping the Docker server
```clojure
(docker/ping conn)
```

#### Pulling the `busybox:musl` image
```clojure
(docker/pull conn "busybox:musl")
```

#### Building an image from a Dockerfile
```clojure
(docker/build conn "full path to directory containing a Dockerfile")
```

#### Pushing an image
```clojure
(docker/push conn "image id or <repo>:<tag>")
```

#### Removing an image
```clojure
(docker/image-rm conn "image id or <repo>:<tag>")
```

#### List all available images
```clojure
(docker/image-ls conn)
```
