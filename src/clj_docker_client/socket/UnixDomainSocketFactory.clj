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

(ns clj-docker-client.socket.UnixDomainSocketFactory
  (:gen-class
   :extends javax.net.SocketFactory
   :init init
   :state path
   :constructors {[String] []}
   :exposes-methods {createSocket createSocketSuper})
  (:import (java.io File)
           (java.net InetAddress
                     InetSocketAddress)
           (jnr.unixsocket UnixSocketChannel)
           (clj_docker_client.socket TunnelingUnixSocket)))

(defn -init
  [^String path]
  [[] (File. path)])

(defn -createSocket
  ([^clj_docker_client.socket.UnixDomainSocketFactory this]
   (let [path    (.path this)
         channel (UnixSocketChannel/open)]
     (TunnelingUnixSocket. path channel)))
  ([^clj_docker_client.socket.UnixDomainSocketFactory this ^String host ^Integer port]
   (-> this
       .createSocket
       (.connect (InetSocketAddress. host port))))
  ([^clj_docker_client.socket.UnixDomainSocketFactory this ^String host ^Integer port ^InetAddress _ ^Integer _]
   (.createSocket this host port)))

(comment
  (compile 'clj-docker-client.socket.UnixDomainSocketFactory)

  (clj_docker_client.socket.UnixDomainSocketFactory. "/var/run/docker.sock"))
