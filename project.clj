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

(defproject lispyclouds/clj-docker-client "1.0.2"
  :author "Rahul De <rahul@mailbox.org>"
  :url "https://github.com/into-docker/clj-docker-client"
  :description "An idiomatic data-driven clojure client for Docker."
  :license {:name "LGPL 3.0"
            :url  "https://www.gnu.org/licenses/lgpl-3.0.en.html"}
  :dependencies [[clj-commons/clj-yaml "0.7.106"]
                 [metosin/jsonista "0.3.1"]
                 [unixsocket-http "1.0.6"]
                 [com.squareup.okhttp3/okhttp-tls "4.9.0"]]
  :plugins [[lein-ancient "0.6.15"]]
  :global-vars {*warn-on-reflection* true}
  :profiles {:kaocha {:dependencies [[lambdaisland/kaocha "1.0.732"]]}
             :rebl   {:repl-options   {:nrepl-middleware [nrebl.middleware/wrap-nrebl]}
                      :injections     [(require '[cognitect.rebl :as rebl])]
                      :dependencies   [[rickmoynihan/nrebl.middleware "0.3.1"]
                                       [org.clojure/core.async "1.3.610"]
                                       [lein-cljfmt "0.7.0"]
                                       [org.openjfx/javafx-fxml "15.0.1"]
                                       [org.openjfx/javafx-controls "15.0.1"]
                                       [org.openjfx/javafx-media "15.0.1"]
                                       [org.openjfx/javafx-swing "15.0.1"]
                                       [org.openjfx/javafx-base "15.0.1"]
                                       [org.openjfx/javafx-web "15.0.1"]]
                      :resource-paths [~(System/getenv "REBL_PATH")]}}
  :aliases {"kaocha" ["with-profile" "+kaocha" "run" "-m" "kaocha.runner" "--fail-fast" "--reporter" "kaocha.report.progress/report"]})
