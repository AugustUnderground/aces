(ns analog-circuit-server.core
  (:require ;[org.httpkit.server :refer [run-server server-stop!]]
            [org.httpkit.server :as hk]
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
   [nil "--verbose" "Print Debug Output"]
   ["-h" "--help"]])

;; Run simulation and return results
(defn run-sim [amp sizing vrbs]
  (when vrbs
    (println "Simulating " amp " with")
    (println sizing))
  (let [_ (.set amp sizing)
        _ (.simulate amp)
        p (into {} (.getPerformanceValues amp)) ]
    (when vrbs
      (println "Results:")
      (println p))
    (zipmap (keys p)
            (map (fn [v] (if (Double/isNaN v) (double 0.0) v))
                 (vals p)))))

;; Simulation Request Handler
(defn on-sim-req [amp params vrbs]
  (when vrbs
    (println "Recevied Simulation Request for " amp ":")
    (println params))

  (if (or (-> params (keys) (count) (zero?)) (apply = (map count (vals params))))
    (let [headers {"Content-Type" "application/json; charset=utf-8"}
          len (-> params (vals) (first) (count))
          sizing (reduce (fn [m i] 
                        (cons (into {} (map (fn [p] [p (-> params (get p) (get i) (double))]) 
                                            (keys params)))
                              m)) [] (range len))
          performances (if (> len 0)
                         (map (fn [s] (run-sim amp s vrbs)) sizing)
                         [(run-sim amp {} vrbs)])
          results (reduce (fn [l r] (merge-with cons r l))
                          (zipmap (keys (first performances)) (repeat [])) 
                          performances)
          body (json/write-str results)
          status 200]
      (when vrbs
        (println "Simulation Results:")
        (println results)
        (println "Simulation Response:")
        (println body))
      {:status status 
       :headers headers 
       :body body})
    {:status 500 
     :headers {"Content-Type" "text/html"}
     :body "Parameter lists must be of equal length."}))

;; Retrieve available parameters or performances
(defn on-p-req [amp p vrbs]
  (when vrbs
    (println "Recevied " p " Request for " amp ":"))
  (let [ids (cond (= p "parameters")
                    {"parameters" (into [] (.getParameterIdentifiers amp))}
                  (= p "performances")
                    {"performances" (into [] (.getPerformanceIdentifiers amp))}
                  :else {})
        status (if (empty? ids) 400 200)
        headers {"Content-Type" "application/json; charset=utf-8"}]
    (when vrbs
      (println ids))
    {:status status :headers headers 
     :body (json/write-str ids)}))

;; Retrieve random sizing
(defn on-rng-req [amp vrbs]
  (when vrbs
    (println "Recevied Randomg Parameter Request for " amp ":"))
  (let [random-sizing (into {} (.getRandomValues amp))
        status 200
        headers {"Content-Type" "application/json; charset=utf-8"}]
    (when vrbs
      (println "Random Parameters:")
      (println random-sizing))
    {:status status :headers headers 
     :body (json/write-str random-sizing)}))

;; Request curated initial sizing parameters
(defn on-init-req [amp vrbs]
  (when vrbs
    (println "Recevied Initial Parameter Request for " amp ":"))
  (let [initial-sizing (into {} (.getInitValues amp))
        status 200
        headers {"Content-Type" "application/json; charset=utf-8"}]
    (when vrbs
      (println "Initial Parameters:")
      (println initial-sizing))
    {:status status :headers headers 
     :body (json/write-str initial-sizing)}))

;; Server setup
(defn start-server [port amps vrbs]
  (let [route-list (concat (flatten (map (fn [[id amp]] 
                                  [ (POST (str "/sim/" id)  
                                          {params :params}
                                          (on-sim-req amp params vrbs))
                                    (GET (str "/rng/" id) {} 
                                         (on-rng-req amp vrbs))
                                    (GET (str "/init/" id) {} 
                                         (on-init-req amp vrbs))
                                    (GET (str "/params/" id) {} 
                                         (on-p-req amp "parameters" vrbs))
                                    (GET (str "/perfs/" id) {} 
                                         (on-p-req amp "performances" vrbs))
                                  #_/ ])
                                amps))
                           [(route/not-found {:status 404 :body "Not found"})])
        routing (-> routes 
                    (apply route-list)
                    (wrap-json-params))
        server-opts {:port port
                     :thread 25
                     :worker-name-prefix "acl-worker-"
                     :server-header "acl-server"
                     :legacy-return-value true
                     #_/ } ]
    (hk/run-server routing server-opts)))

;; Kill all sessions and stop server
(defn stop-server [amps srvr]
  (clojure.core/shutdown-agents)
  (println "Stopping spectre sessions ...")
  (map #(.stop %) amps)
  (println "Stopping server ...")
  (srvr))

(defn -main [& args]
  (let [opts (-> args (parse-opts cli-options) (get :options)) 
        port (get opts :port)
        pdk  (get opts :tech)
        sim  (get opts :sim)
        ckt  (get opts :ckt)
        vrbs (get opts :verbose)
        amps {"op1" (mk-op1 sim pdk ckt)
              "op2" (mk-op2 sim pdk ckt)}
        _ (->> amps (vals) (map #(.start %)) (doall))
        srvr (start-server port amps vrbs)]
    (println "Server started on Port" port)
    (println "Press ENTER for graceful exit.")
    (read-line)
    (stop-server amps srvr)
    (println "Shutting down ...")
    (System/exit 0)))
