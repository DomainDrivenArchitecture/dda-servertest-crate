; Licensed to the Apache Software Foundation (ASF) under one
; or more contributor license agreements. See the NOTICE file
; distributed with this work for additional information
; regarding copyright ownership. The ASF licenses this file
; to you under the Apache License, Version 2.0 (the
; "License"); you may not use this file except in compliance
; with the License. You may obtain a copy of the License at
;
; http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns dda.pallet.dda-serverspec-crate.infra.core.test
  (:require
    [clojure.tools.logging :as logging]
    [schema.core :as s]
    [pallet.crate :as crate]
    [pallet.actions :as actions]
    [pallet.core.data-api :as data-api]
    [dda.pallet.dda-serverspec-crate.infra.core.fact :as fact]))

(def test-facility :dda-serverspec-test)

(def TestResult
  {:test-passed s/Bool
   :test-message s/Str
   :no-passed s/Num
   :no-failed s/Num})

(def TestResultHuman
  (merge
    TestResult
    {:input s/Any
     :summary s/Str}))

(def TestActionResult
  {:context s/Str
   :action-symbol s/Any
   :input s/Any
   :out s/Str
   :result TestResult
   :exit s/Num
   :summary s/Str})

(def fact-check-seed
 {:test-passed true
  :test-message ""
  :no-passed 0
  :no-failed 0})

(s/defn fact-result-to-test-result :- TestResultHuman
  [input :- s/Any
   fact-result :- TestResult]
  (merge
    fact-result
    {:input input
     :summary (str (if (:test-passed fact-result) "PASSED" "FAILED")
                   ", tests failed: " (:no-failed fact-result)
                   ", tests passed: " (:no-passed fact-result))}))

(s/defn test-action-result :- TestActionResult
  [context :- s/Str
   fact-key :- s/Keyword
   fact-key-name :- s/Str
   test-result :- TestResultHuman]
  (let [action-symbol (str "test-" fact-key-name)]
    {:context context
     :action-symbol action-symbol
     :input (-> test-result :input)
     :out (-> test-result :test-message)
     :result test-result
     :exit 0
     :summary-text (-> test-result :summary)}))


(s/defn test-it :- TestActionResult
  {:pallet/plan-fn true}
  [fact-key :- s/Keyword
   test-fn]
  (let [all-facts (crate/get-settings
                    fact/fact-facility
                    {:instance-id (crate/target-node)})
        facts (-> all-facts fact-key)
        fact-key-name (name fact-key)]
    (actions/as-action
      (logging/info (str "testing " fact-key-name))
      (let [input (:out @facts)
            context (str "test: dda-serverspec-test/" fact-key-name)
            test-result (apply test-fn (list input))
            action-result (test-action-result context fact-key fact-key-name test-result)]
        (logging/debug (str "input: " input))
        (logging/debug (str "result: " test-result))
        (logging/info (str "result for " fact-key-name " : " (-> action-result :out)))
        (logging/info (str "result for " fact-key-name " : " (-> action-result :summary)))
        action-result))))

(defn run-results [session]
  (:runs (data-api/session-data session)))

(defn node-results [session]
  (filter some? (:action-results (first (run-results session)))))
