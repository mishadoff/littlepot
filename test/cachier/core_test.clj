(ns cachier.core-test
  (:require [clojure.test :refer :all]
            [cachier.core :refer :all]))

(deftest test-auto-fill
  (let [batch-data-fn (fn []
                        (Thread/sleep 1000)
                        (range 10))
        cache (make-cache batch-data-fn)]
    ;; trigger data population
    (is (= :no-data (hit cache)))
    ;; wait to retrieved fully
    (Thread/sleep 1100)
    (is (= (range 10) (take 10 (repeatedly #(hit cache)))))
    ;; next portion is unavailable
    (is (every? #(= % :no-data) (take 100 (repeatedly #(hit cache)))))
    ))

(deftest test-multiple-consumers
  (let [batch-latency (rand-int 1000)
        consumer-latency (rand-int 10)
        number-of-consumers 10
        batches-active (atom 0)
        batch-data-fn (fn []
                        (swap! batches-active inc)
                        (Thread/sleep batch-latency)
                        (let [data (repeat 1000 1)]
                          (swap! batches-active dec)
                          data))
        cache (make-cache batch-data-fn)
        run (atom true)
        consumer (fn []
                   (future
                     ;; (println "Consumer" id "started")
                     (loop [ones 0]
                       (if @run
                         (do
                           (Thread/sleep consumer-latency)
                           (let [e (hit cache)]
                             (cond (= e 1) (recur (inc ones))
                                   (= e :no-data) (recur ones)
                                   :else (throw (IllegalArgumentException.)))))
                         ones))))
        ;; start 100 consumers
        consumers (take number-of-consumers (repeatedly consumer))]
    ;; track no more then 1 active batch
    (add-watch batches-active :no-more-than-one
               (fn [_ _ _ new-value]
                 (when (> new-value 1) ;; max parallel batches
                   (is false))))
    ;; kick-off computation
    (doall consumers)
    ;; wait 10 seconds for consumers to work
    (Thread/sleep 10000)
    ;; request stop
    (reset! run false)
    ;; wait batches in progress become 0
    (Thread/sleep batch-latency)
    (is (zero? (:batches-in-progress @cache)))
    (let [consumer-ones (reduce + (map deref consumers))
          total-produced (* 1000 (:batches-done @cache))
          left-in-cache (count (:queue @cache))]
      (println consumer-ones left-in-cache total-produced)
      (is (pos? total-produced))
      (is (= (+ consumer-ones left-in-cache) total-produced))
      (is (<= left-in-cache 1000))
      )))
