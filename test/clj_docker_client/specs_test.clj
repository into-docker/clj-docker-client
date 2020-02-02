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

(ns clj-docker-client.specs-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [clj-docker-client.specs :refer :all]))

(def latest-version "v1.40")

(deftest loading-specs
  (testing "latest spec can be loaded"
    (is (map? (fetch-spec))))

  (testing "versioned spec can be loaded"
    (is (map? (fetch-spec latest-version))))

  (testing "bad versioned spec"
    (is (thrown? IllegalArgumentException (fetch-spec "crap")))))

(deftest getting-paths-of-category
  (testing "paths of containers in latest spec"
    (is (every? #(s/starts-with? % "/containers")
                (->> (get-paths-of-category :containers nil)
                     :paths
                     (map first)))))

  (testing "paths of containers in versioned spec"
    (is (every? #(s/starts-with? % "/containers")
                (->> (get-paths-of-category :containers latest-version)
                     :paths
                     (map first))))))

(deftest extractions
  (let [desc {"get" {"summary"     "a-summary"
                     "operationId" "an-operationId"
                     "description" "a-description"
                     "parameters"  [{"name" "a-name"
                                     "in"   "a-position"}]}}
        definition {"/a/path" desc}]
    (testing "extracting operation from a description"
      (is (= {:method :get
              :op     :an-operationId
              :doc    "a-summary\na-description"
              :params [{:name "a-name" :in "a-position"}]}
             (->op (first desc)))))

    (testing "making an endpoint def out of a schema def"
      (is (= {:path "/a/path"
              :ops [{:method :get
                     :op     :an-operationId
                     :doc    "a-summary\na-description"
                     :params [{:name "a-name" :in "a-position"}]}]}
             (->endpoint (first definition)))))))

(deftest creating-request-info-map
  (testing "create a request map of an op in a category of latest version"
    (let [info (request-info-of :images :ImageList nil)]
      (is (= #{:path :method :doc :params} (into #{} (keys info))))
      (is (s/starts-with? (:path info)
                          (str "/" latest-version)))))

  (testing "create a request map of an op in a category of specified version"
    (is (s/starts-with? (:path (request-info-of :images :ImageList "v1.30"))
                        "/v1.30"))))

(deftest categorizing-request-params
  (testing "annotated params are correctly categorized"
    (is (= {:query {:all true}}
           (gather-request-params {:all true}
                                  {}
                                  {:name "all"
                                   :in   "query"})))))
