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

(ns clj-docker-client.specs
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.walk :as walk]
            [clj-yaml.core :as yaml]))

(defn fetch-spec
  "Returns the spec for the Docker API for the supplied version.

  Pass nil for the latest spec."
  ([] (fetch-spec nil))
  ([version]
   (let [spec-file (if (nil? version)
                     "api/latest.yaml"
                     (format "api/%s.yaml" version))
         resource  (io/resource spec-file)]
     (if (nil? resource)
       (throw (IllegalArgumentException. (format "Unsupported version: %s" version)))
       (-> resource
           slurp
           (yaml/parse-string :keywords false))))))

(defn get-paths-of-category
  "Fetches the paths section of the swagger.yaml and the version.

  Filters paths starting with the specified category."
  [category version]
  (let [spec (fetch-spec version)]
    {:version (get-in spec ["info" "version"])
     :paths   (filter #(s/starts-with? (key %)
                                       (format "/%s"
                                               (-> category
                                                   name
                                                   str)))
                      (get spec "paths"))}))

(defn ->op
  "{req-method1 {summary     summary
                 operationId id
                 ...}} -> {method1 :req-method1
                           op      :op
                           doc     doc
                           params  [params]}"
  [desc]
  (let [method (key desc)
        data   (val desc)
        op     (data "operationId")
        doc    (format "%s\n%s"
                       (data "summary")
                       (data "description"))
        params (map #(dissoc % "description") (data "parameters"))]
    {:method (keyword method)
     :op     (keyword op)
     :doc    doc
     :params (walk/keywordize-keys params)}))

(defn ->endpoint
  "{path {req-method1 {meta-data..}}
          req-method2 {meta-data..}
          ...} -> {:path path :ops [output-of->op]}"
  [definition]
  (let [path (key definition)
        ops  (map ->op (val definition))]
    {:path path
     :ops  ops}))

(defn find-op-meta
  "Finds the matching operation by operationId in list of ops from the spec.

  Returns the method, doc and params form it, nil otherwise."
  [op ops]
  (->> ops
       (filter #(= op (:op %)))
       (map #(select-keys % [:method :doc :params]))
       first))

(defn request-info-of
  "Returns a map of path, method, doc and params of an op of a category.

  Prefixes the paths with the version from spec.

  Returns nil if not found."
  [category op version]
  (let [{:keys [paths version]} (get-paths-of-category category version)]
    (->> paths
         (map ->endpoint)
         (map #(assoc-in % [:ops] (find-op-meta op (:ops %))))
         (filter #(some? (:ops %)))
         (map #(hash-map :path   (format "/v%s%s"
                                         version
                                         (:path %))
                         :method (get-in % [:ops :method])
                         :doc    (get-in % [:ops :doc])
                         :params (get-in % [:ops :params])))
         first)))

(defn gather-request-params
  "Reducer fn generating categorized params from supplied params.

  Returns a map of query, path and body params."
  [supplied-params request-params {:keys [name in]}]
  (let [param (keyword name)]
    (if (not (contains? supplied-params param))
      request-params
      (update-in request-params [(keyword in)] assoc param (param supplied-params)))))

(comment
  (-> (io/resource "api/latest.yaml")
      slurp
      (yaml/parse-string :keywords false)
      (get-in ["info" "version"]))
  (->> (get-paths-of-category :images nil)
       :paths
       (take 1)
       (map ->endpoint))
  (->> (get-paths-of-category :containers nil)
       :paths
       (map ->endpoint)
       (mapcat :ops)
       (take 1)
       (find-op-meta :ContainerList))
  (->> (request-info-of :containers :PutContainerArchive nil)
       :params)
  (gather-request-params {:all true}
                         {}
                         {:name "all"
                          :in   "query"})
  (gather-request-params {:all true}
                         {}
                         {:name "all"
                          :in   "body"})
  (->> (request-info-of :containers :ContainerList nil)
       :params
       (reduce (partial gather-request-params
                        {:all   true
                         :limit 5})
               {}))
  (->> (request-info-of :images :ImagePush nil)
       :params
       (reduce (partial gather-request-params
                        {:tag             "tagz"
                         :X-Registry-Auth "reg-auth"})
               {}))
  (fetch-spec "v1.40")
  (fetch-spec))
