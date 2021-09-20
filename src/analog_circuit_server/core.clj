(ns analog-circuit-server.core
  (:require [org.httpkit.server :refer [run-server]]
            [compojure.core :refer [GET POST routes]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-params]]
            [clojure.data.json :as json]
            [clojure.string]
            [clojure.tools.cli :refer [parse-opts]])
  (:import edlab.eda.characterization.Opamp1XH035Characterization
           edlab.eda.characterization.Opamp2XH035Characterization)
  (:gen-class))

;; OpAmp Builders
(def mk-op1 #(edlab.eda.characterization.Opamp1XH035Characterization/build %1 %2 %3))
(def mk-op2 #(edlab.eda.characterization.Opamp2XH035Characterization/build %1 %2 %3))

;; Command Line Args
(def cli-options
  [["-p" "--port PORT" "Port number"
    :default 8888
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 0x10000) "Must be: 0 ≤ PORT ≤ 65536"]]
   ["-o" "--op OP" "Operational Amplifier"
    :default 1
    :parse-fn #(Integer/parseInt %)
    :validate [#(< 0 % 3 ) "1: MOA, 2: SYM"] ]
   ["-t" "--tech PDK" "Path to Technology/PDK" 
    :default "" 
    :validate [#(not (clojure.string/blank? %)) "--pdk is required"]]
   ["-c" "--ckt CKT" "Path to Circuit/Testbench" 
    :default "" 
    :validate [#(not (clojure.string/blank? %)) "--ckt is required"]]
   ["-s" "--sim SIM" "Path to store simulation results" 
    :default "/tmp" 
    :validate [#(not (clojure.string/blank? %)) "--sim defaults to /tmp"]]
   ["-h" "--help"]])

;; Run simulation and return results
(defn run-sim [amp sizing]
  (.set amp sizing)
  (.simulate amp)
  (into {} (.getPerformanceValues amp)))

;; Simulation Request Handler
(defn on-sim-req [amp params]
  (let [headers {"Content-Type" "application/json; charset=utf-8"}
        len (-> params (vals) (first) (count))
        sizing (reduce (fn [m i] 
                      (cons (into {} (map (fn [p] [p (-> params (get p) (get i))]) 
                                          (keys params)))
                        m)) 
                     [] (range len))
        performances (map (partial run-sim amp) sizing)
        results (reduce (fn [l r]
                          (map (fn [[p v]] (update l p #(into [v] %))) r))
                        (first performances) 
                        (rest performances))
        body (json/write-str results)
        status 200]
    {:status status 
     :headers headers 
     :body body}))

;; Retrieve random sizing
(defn on-rng-req [amp]
  (let [random-sizing (into {} (.getRandomValues amp))
        body (json/write-str random-sizing)
        status 200
        headers {"Content-Type" "application/json; charset=utf-8"}]
    {:status status :headers headers :body body}))

;; Server setup
(defn start-server [port amp]
  (let [routing (routes (POST "/sim" {params :params} (on-sim-req amp params))
                        (GET "/random" {} (on-rng-req amp))
                        (route/not-found {:status 404 :body "Not found"}))]
    (run-server (-> routing (wrap-json-params)) {:port port})
    (println "Server started on Port" port)))

(defn -main [& args]
  (let [opts (-> args (parse-opts cli-options) (get :options)) 
        port (get opts :port)
        pdk  (get opts :tech)
        sim  (get opts :sim)
        ckt  (get opts :ckt)
        op-id (get opts :op)
        amp (cond (= op-id 1) (mk-op1 sim pdk ckt)
                  (= op-id 2) (mk-op2 sim pdk ckt))]
    (.start amp)
    (start-server port amp)))
