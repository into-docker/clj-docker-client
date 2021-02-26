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

(defproject lispyclouds/clj-docker-client "1.0.3"
  :author       "Rahul De <rahul@mailbox.org>"
  :url          "https://github.com/into-docker/clj-docker-client"
  :description  "An idiomatic data-driven clojure client for Docker."
  :license      {:name "LGPL 3.0"
                 :url  "https://www.gnu.org/licenses/lgpl-3.0.en.html"}
  :dependencies [[clj-commons/clj-yaml "0.7.106"]
                 [metosin/jsonista "0.3.1"]
                 [unixsocket-http "1.0.8"]
                 [com.squareup.okhttp3/okhttp-tls "4.9.1"]
                 [into-docker/pem-reader "1.0.1"]]
  :plugins      [[lein-ancient "0.7.0"]]
  :global-vars  {*warn-on-reflection* true}
  :profiles     {:kaocha {:dependencies [[lambdaisland/kaocha "1.0.732"]]}
                 :reveal {:dependencies [[vlaaad/reveal "1.3.196"]]
                          :repl-options {:nrepl-middleware [vlaaad.reveal.nrepl/middleware]}}}
  :aliases      {"kaocha" ["with-profile"
                           "+kaocha"
                           "run"
                           "-m"
                           "kaocha.runner"
                           "--fail-fast"
                           "--reporter"
                           "kaocha.report.progress/report"]})
