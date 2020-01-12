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

(ns clj-docker-client.socket.TunnelingUnixSocket
  (:gen-class
   :extends jnr.unixsocket.UnixSocket
   :init init
   :state path
   :constructors {[java.io.File jnr.unixsocket.UnixSocketChannel] [jnr.unixsocket.UnixSocketChannel]}
   :exposes-methods {connect connectSuper})
  (:import (java.io File)
           (java.net SocketAddress)
           (jnr.unixsocket UnixSocketAddress
                           UnixSocketChannel)))

(defn -init
  [^File path ^UnixSocketChannel channel]
  [[channel] path])

(defn -connect
  ([^clj_docker_client.socket.TunnelingUnixSocket this ^SocketAddress _]
   (.connectSuper this (UnixSocketAddress. ^File (.path this)) 0))
  ([^clj_docker_client.socket.TunnelingUnixSocket this ^SocketAddress _ ^Integer timeout]
   (.connectSuper this (UnixSocketAddress. ^File (.path this)) timeout)))

(comment
  (compile 'clj-docker-client.socket.TunnelingUnixSocket)

  (clj_docker_client.socket.TunnelingUnixSocket. (File. "/var/run/docker.sock") (UnixSocketChannel/create)))
