;   This file is part of clj-docker-client.
;
;   clj-docker-client is free software: you can redistribute it and/or modify
;   it under the terms of the GNU General Public License as published by
;   the Free Software Foundation, either version 3 of the License, or
;   (at your option) any later version.
;
;   clj-docker-client is distributed in the hope that it will be useful,
;   but WITHOUT ANY WARRANTY; without even the implied warranty of
;   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
;   GNU General Public License for more details.
;
;   You should have received a copy of the GNU General Public License
;   along with clj-docker-client. If not, see <http://www.gnu.org/licenses/>.

(ns clj-docker-client.utils-test
  (:require [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.properties :as prop]
            [clj-docker-client.utils :refer :all]))

(s/def ::plain-docker-id
  (s/and string?
         #(> (count %) id-length)))

(defspec format-id-formats-given-id
  100
  (prop/for-all [msg (s/gen ::plain-docker-id)]
    (<= (count (format-id msg)) id-length)))

(deftest shell-arg-tokenize-test
  (testing "tokenizing a Shell command"
    (is (= (sh-tokenize! "sh -c \"while sleep 1; do echo ${RANDOM}; done\"")
           ["sh" "-c" "while sleep 1; do echo ${RANDOM}; done"])))
  (testing "tokenizing a Shell command with escaped double quotes"
    (is (= (sh-tokenize! "sort -t \"\t\" -k2 test > test-sorted")
           ["sort" "-t" "\t" "-k2" "test" ">" "test-sorted"]))))
